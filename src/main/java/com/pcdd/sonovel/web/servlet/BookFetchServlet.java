package com.pcdd.sonovel.web.servlet;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.pcdd.sonovel.core.AppConfigLoader;
import com.pcdd.sonovel.core.Crawler;
import com.pcdd.sonovel.db.ConfigRepository;
import com.pcdd.sonovel.db.HistoryRepository;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.model.SearchResult;
import com.pcdd.sonovel.util.SourceUtils;
import com.pcdd.sonovel.web.DownloadTracker;
import com.pcdd.sonovel.web.util.RespUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * 书籍下载 API — 支持并发限制、SSE 进度追踪、dlid 文件匹配
 */
public class BookFetchServlet extends HttpServlet {

    private static final Set<String> ALLOWED_FORMATS = Set.of("epub", "txt", "html", "pdf");
    private static final Set<String> ALLOWED_LANGUAGES = Set.of("zh_cn", "zh_tw", "zh_hant");
    private static volatile Semaphore downloadSemaphore;
    private static volatile int semaphorePermits = 3;

    private static synchronized void initSemaphore() {
        String cfg = new ConfigRepository().get("max_concurrent_downloads");
        int max = (cfg != null) ? Integer.parseInt(cfg) : 3;
        if (downloadSemaphore == null || max != semaphorePermits) {
            downloadSemaphore = new Semaphore(max);
            semaphorePermits = max;
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        initSemaphore();
        try { downloadSemaphore.acquire(); } catch (InterruptedException e) { return; }
        try {
            String bookUrl = req.getParameter("url");
            String format = req.getParameter("format");
            String language = req.getParameter("language");
            String concurrencyStr = req.getParameter("concurrency");
            String dlid = req.getParameter("dlid");
            if (StrUtil.isBlank(dlid)) dlid = String.valueOf((int)(Math.random()*900000000)+100000000);

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

            String bookName = req.getParameter("bookName");
            String author = req.getParameter("author");
            if (bookName == null || bookName.isBlank()) bookName = "未知书名";

            // Register for SSE tracking
            DownloadProgressSseServlet.register(dlid, bookName);

            java.io.File dlDir = new java.io.File(cfg.getDownloadPath());
            dlDir.mkdirs();

            java.util.Set<String> preFiles = new java.util.HashSet<>();
            java.io.File[] existing = dlDir.listFiles(java.io.File::isFile);
            if (existing != null) for (java.io.File f : existing) preFiles.add(f.getName());

            double secs = new Crawler(cfg).crawl(sr.getUrl());
            if (secs == 0) {
                RespUtils.writeError(resp, 500, "源站章节目录为空，中止下载");
                return;
            }

            String filePath = null; long fileSize = 0;
            java.io.File[] allFiles = dlDir.listFiles(f -> f.isFile() && f.getName().endsWith("." + cfg.getExtName()));
            if (allFiles != null) {
                java.util.Arrays.sort(allFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (java.io.File f : allFiles) {
                    if (!preFiles.contains(f.getName())) { filePath = f.getName(); fileSize = f.length(); break; }
                }
                if (filePath == null) { filePath = allFiles[0].getName(); fileSize = allFiles[0].length(); }
            }
            if (filePath == null) {
                RespUtils.writeError(resp, 500, "未找到下载输出文件"); return;
            }

            DownloadTracker.put(dlid, filePath);

            String base = req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort();
            String downloadUrl = base + "/book-download?dlid=" + dlid +
                    (req.getParameter("token") != null ? "&token="+req.getParameter("token") : "");

            String srcName = SourceUtils.getRule(bookUrl).getName();
            Integer uid = (Integer) req.getAttribute("userId");
            String un = (String) req.getAttribute("username");
            if (uid != null && un != null) {
                new HistoryRepository().add(uid, bookName, author!=null?author:"",
                        srcName, cfg.getExtName(), filePath, fileSize);
                new HistoryRepository().addLog(un, bookName, author!=null?author:"", cfg.getExtName(), srcName);
            }

            // Remove from SSE tracking (download complete)
            DownloadProgressSseServlet.unregister(dlid);

            var result = new java.util.HashMap<String, Object>();
            result.put("message", "下载完成");
            result.put("timeSeconds", secs);
            result.put("dlid", dlid);
            result.put("fileName", filePath);
            result.put("downloadUrl", downloadUrl);
            RespUtils.writeJson(resp, result);

        } catch (Exception e) {
            String msg = e.getMessage();
            RespUtils.writeError(resp, 500, msg != null ? msg : "内部错误");
        } finally {
            downloadSemaphore.release();
        }
    }
}