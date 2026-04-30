package com.pcdd.sonovel.web.servlet;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.pcdd.sonovel.core.AppConfigLoader;
import com.pcdd.sonovel.web.DownloadTracker;
import com.pcdd.sonovel.web.util.RespUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 文件下载 — 支持 filename 或 dlid 参数
 */
public class BookDownloadServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        String filename = req.getParameter("filename");
        String dlid = req.getParameter("dlid");

        // Resolve dlid to actual path
        if (StrUtil.isBlank(filename) && StrUtil.isNotBlank(dlid)) {
            filename = DownloadTracker.getAndRemove(dlid);
        }
        if (StrUtil.isBlank(filename)) {
            RespUtils.writeError(resp, 400, "缺少 filename 或 dlid 参数");
            return;
        }

        downloadFileToLocal(resp, filename);
    }

    @SneakyThrows
    private void downloadFileToLocal(HttpServletResponse resp, String filename) {
        File file = new File(AppConfigLoader.APP_CONFIG.getDownloadPath(), filename);

        if (!file.exists()) {
            RespUtils.writeError(resp, 404, "文件不存在");
            return;
        }

        // Use actual filename (last segment) for Content-Disposition
        String displayName = file.getName();

        resp.setContentType("application/octet-stream");
        resp.setHeader("Content-Disposition", "attachment;filename=" + URLUtil.encode(displayName));
        resp.setHeader("Content-Length", String.valueOf(file.length()));

        Files.copy(Paths.get(file.getAbsolutePath()), resp.getOutputStream());
    }
}