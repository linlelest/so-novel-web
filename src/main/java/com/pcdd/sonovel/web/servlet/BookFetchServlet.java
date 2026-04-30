package com.pcdd.sonovel.web.servlet;

import cn.hutool.core.lang.Console;
import cn.hutool.core.util.StrUtil;
import com.pcdd.sonovel.core.AppConfigLoader;
import com.pcdd.sonovel.db.HistoryRepository;
import com.pcdd.sonovel.model.Rule;
import com.pcdd.sonovel.util.SourceUtils;
import com.pcdd.sonovel.web.util.RespUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Set;

/**
 * 书籍获取 API — 仅在服务器 downloads 目录中查找已有文件并返回路径，
 * 不再重新爬取源站。下载工作由原版后端下载器完成。
 */
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

            if (StrUtil.isNotBlank(format) && !ALLOWED_FORMATS.contains(format.toLowerCase())) {
                RespUtils.writeError(resp, 400, "不支持的下载格式: " + format);
                return;
            }
            if (StrUtil.isNotBlank(language) && !ALLOWED_LANGUAGES.contains(language.toLowerCase())) {
                RespUtils.writeError(resp, 400, "不支持的语言: " + language);
                return;
            }

            String finalFormat = StrUtil.isNotBlank(format) ? format.toLowerCase() : AppConfigLoader.APP_CONFIG.getExtName();
            Rule rule = SourceUtils.getRule(bookUrl);
            String bookName = req.getParameter("bookName");
            String author = req.getParameter("author");

            if (bookName == null || bookName.isBlank()) {
                RespUtils.writeError(resp, 400, "缺少 bookName 参数");
                return;
            }

            // Find file in downloads directory
            java.util.Map<String, Object> found = findExistingDownload(bookName, author, finalFormat);
            if (found == null) {
                RespUtils.writeError(resp, 404, "文件不存在，请先在原版下载器中下载");
                return;
            }

            String actualFileName = (String) found.get("fileName");
            long fileSize = (long) found.get("fileSize");

            // Record download history
            try {
                Integer userId = (Integer) req.getAttribute("userId");
                String username = (String) req.getAttribute("username");
                if (userId != null && username != null) {
                    historyRepository.add(userId, bookName, author != null ? author : "",
                            rule.getName(), finalFormat, actualFileName, fileSize);
                    historyRepository.addLog(username, bookName, author != null ? author : "",
                            finalFormat, rule.getName());
                }
            } catch (Exception ignored) {}

            var result = new java.util.HashMap<String, Object>();
            result.put("message", "下载完成");
            result.put("timeSeconds", 0);
            result.put("fileName", actualFileName);
            RespUtils.writeJson(resp, result);

        } catch (Exception e) {
            RespUtils.writeError(resp, 500, e.getMessage());
        }
    }

    /** Search download directory for a file matching bookName + format */
    private java.util.Map<String, Object> findExistingDownload(String bookName, String author, String fmt) {
        String ext = fmt.toUpperCase();
        java.io.File dlDir = new java.io.File(AppConfigLoader.APP_CONFIG.getDownloadPath());
        java.io.File[] allDirs = dlDir.listFiles(java.io.File::isDirectory);
        if (allDirs == null) return null;

        for (java.io.File sub : allDirs) {
            String name = sub.getName();
            if (!name.endsWith(" " + ext)) continue;
            if (!name.contains(bookName)) continue;
            if (author != null && !author.isEmpty() && !name.contains(author)) continue;
            java.io.File[] files = sub.listFiles((d, n) -> n.endsWith("." + fmt));
            if (files == null || files.length == 0) continue;
            java.util.Map<String, Object> r = new java.util.HashMap<>();
            r.put("fileName", name + "/" + files[0].getName());
            r.put("fileSize", files[0].length());
            return r;
        }
        return null;
    }
}