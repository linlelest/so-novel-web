package com.pcdd.sonovel.web.servlet;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.pcdd.sonovel.core.AppConfigLoader;
import com.pcdd.sonovel.core.Crawler;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.model.SearchResult;
import com.pcdd.sonovel.util.SourceUtils;
import com.pcdd.sonovel.web.util.RespUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Set;

/**
 * 书籍下载 — 调用原版 Crawler 下载到服务器，前端负责从 /local-books 发现新文件
 */
public class BookFetchServlet extends HttpServlet {

    private static final Set<String> ALLOWED_FORMATS = Set.of("epub", "txt", "html", "pdf");
    private static final Set<String> ALLOWED_LANGUAGES = Set.of("zh_cn", "zh_tw", "zh_hant");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try {
            String bookUrl = req.getParameter("url");
            String format = req.getParameter("format");
            String language = req.getParameter("language");
            String concurrencyStr = req.getParameter("concurrency");
            int id = SourceUtils.getRule(bookUrl).getId();

            if (StrUtil.isNotBlank(format) && !ALLOWED_FORMATS.contains(format.toLowerCase())) {
                RespUtils.writeError(resp, 400, "不支持的格式: " + format); return;
            }
            if (StrUtil.isNotBlank(language) && !ALLOWED_LANGUAGES.contains(language.toLowerCase())) {
                RespUtils.writeError(resp, 400, "不支持的语言: " + language); return;
            }

            SearchResult sr = SearchResult.builder().sourceId(id).url(bookUrl).build();
            AppConfig cfg = BeanUtil.copyProperties(AppConfigLoader.APP_CONFIG, AppConfig.class);
            cfg.setSourceId(id);
            if (StrUtil.isNotBlank(format)) cfg.setExtName(format.toLowerCase());
            if (StrUtil.isNotBlank(language)) cfg.setLanguage(language);
            if (StrUtil.isNotBlank(concurrencyStr)) cfg.setConcurrency(Integer.parseInt(concurrencyStr));
            cfg.setWebEnabled(1);

            double timeSeconds = new Crawler(cfg).crawl(sr.getUrl());
            if (timeSeconds == 0) {
                RespUtils.writeError(resp, 500, "源站章节目录为空，中止下载");
                return;
            }

            RespUtils.writeJson(resp, java.util.Map.of("message", "下载完成", "timeSeconds", timeSeconds));

            // Record history in background (book name from frontend params, for per-user listing)
            final String bn = req.getParameter("bookName");
            final String au = req.getParameter("author");
            final String fmt = cfg.getExtName();
            final Integer uid = (Integer) req.getAttribute("userId");
            final String un = (String) req.getAttribute("username");
            if (uid != null && un != null) {
                new Thread(() -> {
                    try {
                        // Give filesystem a moment to flush
                        Thread.sleep(500);
                        // Find latest matching directory
                        java.io.File dlDir = new java.io.File(AppConfigLoader.APP_CONFIG.getDownloadPath());
                        java.io.File[] dirs = dlDir.listFiles(f -> f.isDirectory() && f.getName().endsWith(" " + fmt.toUpperCase()));
                        if (dirs != null && dirs.length > 0) {
                            java.util.Arrays.sort(dirs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                            java.io.File outDir = dirs[0];
                            java.io.File[] files = outDir.listFiles((d, n) -> n.endsWith("." + fmt));
                            if (files != null && files.length > 0) {
                                String rn = SourceUtils.getRule(bookUrl).getName();
                                String path = outDir.getName() + "/" + files[0].getName();
                                long sz = files[0].length();
                                new com.pcdd.sonovel.db.HistoryRepository().add(uid, bn != null ? bn : "",
                                        au != null ? au : "", rn, fmt, path, sz);
                                new com.pcdd.sonovel.db.HistoryRepository().addLog(un, bn != null ? bn : "",
                                        au != null ? au : "", fmt, rn);
                            }
                        }
                    } catch (Exception ignored) {}
                }, "history-recorder").start();
            }
        } catch (Exception e) {
            RespUtils.writeError(resp, 500, e.getMessage());
        }
    }
}
            if (StrUtil.isNotBlank(language) && !ALLOWED_LANGUAGES.contains(language.toLowerCase())) {
                RespUtils.writeError(resp, 400, "不支持的语言: " + language);
                return;
            }

            Integer concurrency = null;
            if (StrUtil.isNotBlank(concurrencyStr)) {
                concurrency = Integer.parseInt(concurrencyStr);
                int maxAllowed = AppConfigLoader.APP_CONFIG.getConcurrency() > 0 ? AppConfigLoader.APP_CONFIG.getConcurrency() : 50;
                if (concurrency < 1 || concurrency > maxAllowed) {
                    RespUtils.writeError(resp, 400, "并发数须在 1~" + maxAllowed + " 之间");
                    return;
                }
            }

            Rule rule = SourceUtils.getRule(bookUrl);
            String finalFormat = StrUtil.isNotBlank(format) ? format.toLowerCase() : AppConfigLoader.APP_CONFIG.getExtName();
            String bookName = req.getParameter("bookName");
            String author = req.getParameter("author");

            // Step 1: check if already downloaded
            String foundPath = null;
            long foundSize = 0;
            if (bookName != null && !bookName.isBlank()) {
                java.util.Map<String, Object> existing = findExisting(bookName, author, finalFormat);
                if (existing != null) {
                    foundPath = (String) existing.get("fileName");
                    foundSize = (long) existing.get("fileSize");
                }
            }

            // Step 2: not found → call original Crawler
            if (foundPath == null) {
                SearchResult sr = SearchResult.builder().sourceId(id).url(bookUrl).build();
                AppConfig cfg = BeanUtil.copyProperties(AppConfigLoader.APP_CONFIG, AppConfig.class);
                cfg.setSourceId(id);
                if (StrUtil.isNotBlank(format)) cfg.setExtName(format.toLowerCase());
                if (StrUtil.isNotBlank(language)) cfg.setLanguage(language);
                if (concurrency != null) cfg.setConcurrency(concurrency);
                cfg.setWebEnabled(1);

                double secs = new Crawler(cfg).crawl(sr.getUrl());
                if (secs == 0) {
                    RespUtils.writeError(resp, 500, "源站章节目录为空，中止下载");
                    return;
                }

                // Find the newly created output: most recent dir matching format
                java.io.File dlDir = new java.io.File(AppConfigLoader.APP_CONFIG.getDownloadPath());
                java.io.File[] dirs = dlDir.listFiles(f -> f.isDirectory() && f.getName().endsWith(" " + finalFormat.toUpperCase()));
                if (dirs == null || dirs.length == 0) {
                    RespUtils.writeError(resp, 500, "未找到下载输出文件");
                    return;
                }
                java.util.Arrays.sort(dirs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                java.io.File outDir = dirs[0];
                java.io.File[] files = outDir.listFiles((d, n) -> n.endsWith("." + finalFormat));
                if (files == null || files.length == 0) {
                    RespUtils.writeError(resp, 500, "未找到下载输出文件");
                    return;
                }
                foundPath = outDir.getName() + "/" + files[0].getName();
                foundSize = files[0].length();
            }

            // Record history
            try {
                Integer userId = (Integer) req.getAttribute("userId");
                String username = (String) req.getAttribute("username");
                if (userId != null && username != null) {
                    String bn = bookName != null ? bookName : "";
                    String au = author != null ? author : "";
                    historyRepository.add(userId, bn, au, rule.getName(), finalFormat, foundPath, foundSize);
                    historyRepository.addLog(username, bn, au, finalFormat, rule.getName());
                }
            } catch (Exception ignored) {}

            var result = new java.util.HashMap<String, Object>();
            result.put("message", "下载完成");
            result.put("timeSeconds", 0);
            result.put("fileName", foundPath);
            RespUtils.writeJson(resp, result);

        } catch (Exception e) {
            RespUtils.writeError(resp, 500, e.getMessage());
        }
    }

    private java.util.Map<String, Object> findExisting(String bookName, String author, String fmt) {
        String ext = fmt.toUpperCase();
        java.io.File dlDir = new java.io.File(AppConfigLoader.APP_CONFIG.getDownloadPath());
        java.io.File[] allDirs = dlDir.listFiles(java.io.File::isDirectory);
        if (allDirs == null) return null;
        for (java.io.File sub : allDirs) {
            String name = sub.getName();
            if (!name.endsWith(" " + ext)) continue;
            if (!name.contains(bookName)) continue;
            if (author != null && !author.isBlank() && !name.contains(author)) continue;
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