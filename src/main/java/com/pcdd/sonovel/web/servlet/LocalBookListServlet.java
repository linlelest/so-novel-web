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
import java.util.List;

public class LocalBookListServlet extends HttpServlet {

    private final HistoryRepository historyRepo = new HistoryRepository();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        int userId = (int) req.getAttribute("userId");

        List<DownloadHistory> userHistory = historyRepo.findByUserId(userId);
        File dir = new File(AppConfigLoader.APP_CONFIG.getDownloadPath());
        List<LocalBookItem> list = new ArrayList<>();

        for (DownloadHistory h : userHistory) {
            String fn = h.getFileName();
            if (fn == null || fn.isEmpty()) continue;
            File f = new File(dir, fn);
            if (f.exists()) {
                LocalBookItem item = new LocalBookItem();
                item.setName(fn);
                item.setSize(f.length());
                item.setTimestamp(f.lastModified());
                list.add(item);
            }
        }

        RespUtils.writeJson(resp, list);
    }

}