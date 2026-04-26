package com.pcdd.sonovel.web.servlet;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.StrUtil;
import com.pcdd.sonovel.core.AppConfigLoader;
import com.pcdd.sonovel.core.Crawler;
import com.pcdd.sonovel.db.HistoryRepository;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.model.Rule;
import com.pcdd.sonovel.model.SearchResult;
import com.pcdd.sonovel.parse.BookParser;
import com.pcdd.sonovel.util.SourceUtils;
import com.pcdd.sonovel.web.util.RespUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Set;

public class BookFetchServlet extends HttpServlet {

    private static final Set<String> ALLOWED_FORMATS = Set.of("epub", "txt", "html", "pdf");
    private static final Set<String> ALLOWED_LANGUAGES = Set.of("zh_cn", "zh_tw", "zh_hant");

    private final HistoryRepository historyRepository = new HistoryRepository();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try {
            String bookUrl = req.getParameter("url");
            String format = req.getParameter("format");
            String language = req.getParameter("language");
            String concurrencyStr = req.getParameter("concurrency");
            int id = SourceUtils.getRule(bookUrl).getId();

            if (StrUtil.isNotBlank(format) && !ALLOWED_FORMATS.contains(format.toLowerCase())) {
                RespUtils.writeError(resp, 400, "不支持的下载格式: " + format + "，可选: epub, txt, html, pdf");
                return;
            }

            if (StrUtil.isNotBlank(language) && !ALLOWED_LANGUAGES.contains(language.toLowerCase())) {
                RespUtils.writeError(resp, 400, "不支持的语言: " + language + "，可选: zh_CN, zh_TW, zh_Hant");
                return;
            }

            Integer concurrency = null;
            if (StrUtil.isNotBlank(concurrencyStr)) {
                concurrency = Integer.parseInt(concurrencyStr);
                int configConcurrency = AppConfigLoader.APP_CONFIG.getConcurrency();
                int maxAllowed = configConcurrency > 0 ? configConcurrency : 50;
                if (concurrency < 1 || concurrency > maxAllowed) {
                    RespUtils.writeError(resp, 400, "并发数须在 1~" + maxAllowed + " 之间");
                    return;
                }
            }

            SearchResult sr = SearchResult.builder()
                    .sourceId(id)
                    .url(bookUrl)
                    .build();

            // 先解析书籍名称/作者（用于历史记录，失败不影响下载）
            String bookName = "未知书名", author = "";
            Rule rule = SourceUtils.getRule(bookUrl);
            try {
                BookParser bp = new BookParser(AppConfigLoader.APP_CONFIG);
                var bookInfo = bp.parse(bookUrl);
                if (bookInfo != null) {
                    bookName = bookInfo.getBookName();
                    author = bookInfo.getAuthor();
                }
            } catch (Exception ignored) {}

            String finalFormat = StrUtil.isNotBlank(format) ? format.toLowerCase() : AppConfigLoader.APP_CONFIG.getExtName();

            // 执行下载（带SSE进度推送）
            double totalTimeSeconds = downloadFileToServer(sr, format, language, concurrency);
            if (totalTimeSeconds == 0) {
                RespUtils.writeError(resp, 500, "源站章节目录为空，中止下载");
                return;
            }

            // Get actual download dir for filename
            String downloadDir = AppConfigLoader.APP_CONFIG.getDownloadPath();
            String fileNamePattern = com.pcdd.sonovel.util.FileUtils.sanitizeFileName(
                    "%s (%s) %s".formatted(bookName, author, finalFormat.toUpperCase()));
            java.io.File dir = new java.io.File(downloadDir, fileNamePattern);
            java.io.File[] files = dir.listFiles((d, n) -> n.endsWith("." + finalFormat));
            String actualFileName = (files != null && files.length > 0) ? files[0].getName() : "";
            long fileSize = (files != null && files.length > 0) ? files[0].length() : 0;

            // 记录下载历史
            try {
                Integer userId = (Integer) req.getAttribute("userId");
                String username = (String) req.getAttribute("username");
                if (userId != null && username != null) {
                    historyRepository.add(userId, bookName, author, rule.getName(), finalFormat, actualFileName, fileSize);
                    historyRepository.addLog(username, bookName, author, finalFormat, rule.getName());
                }
            } catch (Exception ignored) {}

            // Return filename for frontend auto-download trigger
            var result = new java.util.HashMap<String, Object>();
            result.put("message", "下载完成");
            result.put("timeSeconds", totalTimeSeconds);
            result.put("fileName", actualFileName);
            RespUtils.writeJson(resp, result);

        } catch (Exception e) {
            RespUtils.writeError(resp, 500, e.getMessage());
        }
    }

    private double downloadFileToServer(SearchResult sr, String format, String language, Integer concurrency) {
        AppConfig cfg = BeanUtil.copyProperties(AppConfigLoader.APP_CONFIG, AppConfig.class);
        cfg.setSourceId(sr.getSourceId());

        if (StrUtil.isNotBlank(format)) {
            cfg.setExtName(format.toLowerCase());
        }
        if (StrUtil.isNotBlank(language)) {
            cfg.setLanguage(language);
        }
        if (concurrency != null) {
            cfg.setConcurrency(concurrency);
        }

        // Must register as web-mode so Crawler knows to send SSE progress
        cfg.setWebEnabled(1);
        Console.log("<== 正在获取源站章节目录...");

        return new Crawler(cfg).crawl(sr.getUrl());
    }

}