package com.pcdd.sonovel.web.servlet;

import com.pcdd.sonovel.core.AppConfigLoader;
import com.pcdd.sonovel.db.HistoryRepository;
import com.pcdd.sonovel.model.DownloadHistory;
import com.pcdd.sonovel.web.model.LocalBookItem;
import com.pcdd.sonovel.web.util.RespUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LocalBookListServlet extends HttpServlet {

    private final HistoryRepository historyRepo = new HistoryRepository();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        int userId = (int) req.getAttribute("userId");

        // Get filenames from this user's download history
        List<DownloadHistory> userHistory = historyRepo.findByUserId(userId);
        Set<String> userFiles = new HashSet<>();
        for (DownloadHistory h : userHistory) {
            String fn = h.getFileName();
            if (fn != null && !fn.isEmpty()) userFiles.add(fn);
        }

        // List all files in download directory
        File dir = new File(AppConfigLoader.APP_CONFIG.getDownloadPath());
        File[] files = dir.listFiles(File::isFile);

        List<LocalBookItem> list = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                // Only include files belonging to this user
                if (!userFiles.contains(f.getName())) continue;
                LocalBookItem item = new LocalBookItem();
                item.setName(f.getName());
                item.setSize(f.length());
                item.setTimestamp(f.lastModified());
                list.add(item);
            }
        }

        RespUtils.writeJson(resp, list);
    }

}