package com.pcdd.sonovel.web.servlet;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.pcdd.sonovel.core.AppConfigLoader;
import com.pcdd.sonovel.core.Crawler;
import com.pcdd.sonovel.db.HistoryRepository;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.model.SearchResult;
import com.pcdd.sonovel.util.SourceUtils;
import com.pcdd.sonovel.web.util.RespUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Set;

/**
 * 书籍下载 API — 调用原版 Crawler，同步获取输出文件路径，记录历史并返回给前端
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

            if (StrUtil.isNotBlank(format) && !ALLOWED_FORMATS.contains(format.toLowerCase())) {
                RespUtils.writeError(resp, 400, "不支持的格式"); return;
            }
            if (StrUtil.isNotBlank(language) && !ALLOWED_LANGUAGES.contains(language.toLowerCase())) {
                RespUtils.writeError(resp, 400, "不支持的语言"); return;
            }

            int id = SourceUtils.getRule(bookUrl).getId();
            SearchResult sr = SearchResult.builder().sourceId(id).url(bookUrl).build();

            AppConfig cfg = BeanUtil.copyProperties(AppConfigLoader.APP_CONFIG, AppConfig.class);
            cfg.setSourceId(id);
            if (StrUtil.isNotBlank(format)) cfg.setExtName(format.toLowerCase());
            if (StrUtil.isNotBlank(language)) cfg.setLanguage(language);
            if (StrUtil.isNotBlank(concurrencyStr)) cfg.setConcurrency(Integer.parseInt(concurrencyStr));
            cfg.setWebEnabled(1);

            // Call original Crawler
            double secs = new Crawler(cfg).crawl(sr.getUrl());
            if (secs == 0) {
                RespUtils.writeError(resp, 500, "源站章节目录为空，中止下载");
                return;
            }

            // Find output file — match directory by bookName from request params
            java.io.File dlDir = new java.io.File(cfg.getDownloadPath());
            String ext = cfg.getExtName().toUpperCase();
            String bn = req.getParameter("bookName");
            String au = req.getParameter("author");
            java.io.File[] dirs = dlDir.listFiles(f -> f.isDirectory() && f.getName().endsWith(" " + ext));
            java.io.File outDir = null;
            if (dirs != null && dirs.length > 0) {
                java.util.Arrays.sort(dirs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (java.io.File d : dirs) {
                    String name = d.getName();
                    if (bn != null && !bn.isBlank() && !name.contains(bn)) continue;
                    if (au != null && !au.isBlank() && name.contains("(") && !name.contains("(" + au)) continue;
                    outDir = d;
                    break;
                }
                if (outDir == null) outDir = dirs[0]; // no name match → fallback to newest
            }
            if (outDir == null) {
                RespUtils.writeError(resp, 500, "未找到下载文件");
                return;
            }
            java.io.File[] files = outDir.listFiles((d, n) -> n.endsWith("." + cfg.getExtName()));
            if (files == null || files.length == 0) {
                RespUtils.writeError(resp, 500, "未找到下载文件");
                return;
            }
            String filePath = outDir.getName() + "/" + files[0].getName();
            long fileSize = files[0].length();

            // Record history
            String srcName = SourceUtils.getRule(bookUrl).getName();
            Integer uid = (Integer) req.getAttribute("userId");
            String un = (String) req.getAttribute("username");
            if (uid != null && un != null) {
                new HistoryRepository().add(uid, bn != null ? bn : "", au != null ? au : "",
                        srcName, cfg.getExtName(), filePath, fileSize);
                new HistoryRepository().addLog(un, bn != null ? bn : "", au != null ? au : "",
                        cfg.getExtName(), srcName);
            }

            // Return file path to frontend
            var result = new java.util.HashMap<String, Object>();
            result.put("message", "下载完成");
            result.put("timeSeconds", secs);
            result.put("fileName", filePath);
            RespUtils.writeJson(resp, result);

        } catch (Exception e) {
            RespUtils.writeError(resp, 500, e.getMessage());
        }
    }
}