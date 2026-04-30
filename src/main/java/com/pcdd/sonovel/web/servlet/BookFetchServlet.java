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

            SearchResult sr = SearchResult.builder().sourceId(id).url(bookUrl).build();
            Rule rule = SourceUtils.getRule(bookUrl);
            String finalFormat = StrUtil.isNotBlank(format) ? format.toLowerCase() : AppConfigLoader.APP_CONFIG.getExtName();

            // Execute download with SSE progress, get back output directory path
            java.util.Map<String, Object> dlResult = downloadFileToServer(sr, format, language, concurrency, rule);
            if (dlResult == null) {
                RespUtils.writeError(resp, 500, "源站章节目录为空，中止下载");
                return;
            }
            double totalTimeSeconds = (double) dlResult.get("timeSeconds");
            String bookName = (String) dlResult.getOrDefault("bookName", "未知书名");
            String author = (String) dlResult.getOrDefault("author", "");
            String actualFileName = (String) dlResult.getOrDefault("fileName", "");
            long fileSize = (long) dlResult.getOrDefault("fileSize", 0L);

            // Record download history
            try {
                Integer userId = (Integer) req.getAttribute("userId");
                String username = (String) req.getAttribute("username");
                if (userId != null && username != null) {
                    historyRepository.add(userId, bookName, author, rule.getName(), finalFormat, actualFileName, fileSize);
                    historyRepository.addLog(username, bookName, author, finalFormat, rule.getName());
                }
            } catch (Exception ignored) {}

            var result = new java.util.HashMap<String, Object>();
            result.put("message", "下载完成");
            result.put("timeSeconds", totalTimeSeconds);
            result.put("fileName", actualFileName);
            RespUtils.writeJson(resp, result);

        } catch (Exception e) {
            RespUtils.writeError(resp, 500, e.getMessage());
        }
    }

    private java.util.Map<String, Object> downloadFileToServer(SearchResult sr, String format, String language, Integer concurrency, Rule rule) {
        AppConfig cfg = BeanUtil.copyProperties(AppConfigLoader.APP_CONFIG, AppConfig.class);
        cfg.setSourceId(sr.getSourceId());
        if (StrUtil.isNotBlank(format)) cfg.setExtName(format.toLowerCase());
        if (StrUtil.isNotBlank(language)) cfg.setLanguage(language);
        if (concurrency != null) cfg.setConcurrency(concurrency);
        cfg.setWebEnabled(1);

        Console.log("<== 正在获取源站章节目录...");
        Crawler crawler = new Crawler(cfg);
        double timeSeconds = crawler.crawl(sr.getUrl());
        if (timeSeconds == 0) return null;

        // Get output directory (same pattern Crawler uses)
        String downloadPath = cfg.getDownloadPath();
        String ext = cfg.getExtName().toUpperCase();
        // List subdirectories in download path sorted by modification time (newest first)
        java.io.File[] subdirs = new java.io.File(downloadPath)
                .listFiles(f -> f.isDirectory() && f.getName().endsWith(" " + ext));
        if (subdirs == null || subdirs.length == 0) {
            subdirs = new java.io.File(downloadPath).listFiles(java.io.File::isDirectory);
        }
        if (subdirs == null || subdirs.length == 0) return null;
        java.util.Arrays.sort(subdirs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        java.io.File dir = subdirs[0];
        String dirName = dir.getName(); // e.g. "凡人修仙传 (忘语) EPUB"
        // Parse bookName and author from dir name: "书名 (作者) EXT"
        String bookName = dirName, author = "";
        int lp = dirName.lastIndexOf(" (");
        int rp = dirName.lastIndexOf(") ");
        if (lp > 0 && rp > lp) {
            bookName = dirName.substring(0, lp);
            author = dirName.substring(lp + 2, rp);
        }

        // Find the final output file and return relative path
        java.io.File[] files = dir.listFiles((d, n) -> n.endsWith("." + ext.toLowerCase()));
        String relFileName = (files != null && files.length > 0) ? (dirName + "/" + files[0].getName()) : "";
        long fileSize = (files != null && files.length > 0) ? files[0].length() : 0L;

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("timeSeconds", timeSeconds);
        result.put("bookName", bookName);
        result.put("author", author);
        result.put("fileName", relFileName);
        result.put("fileSize", fileSize);
        return result;
    }

}