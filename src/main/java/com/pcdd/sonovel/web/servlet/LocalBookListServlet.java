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
        File dir = new File(AppConfigLoader.APP_CONFIG.getDownloadPath());
        List<LocalBookItem> list = new ArrayList<>();

        List<DownloadHistory> userHistory = historyRepo.findByUserId(userId);
        if (!userHistory.isEmpty()) {
            // Use history records (per-user)
            for (DownloadHistory h : userHistory) {
                String fn = h.getFileName();
                if (fn == null || fn.isEmpty()) continue;
                File f = new File(dir, fn);
                if (!f.exists() && !fn.contains("/")) {
                    File[] subs = dir.listFiles(File::isDirectory);
                    if (subs != null) for (File sub : subs) {
                        File c = new File(sub, fn); if (c.exists()) { f = c; fn = sub.getName()+"/"+fn; break; }
                    }
                }
                if (f.exists()) { LocalBookItem item = new LocalBookItem(); item.setName(fn); item.setSize(f.length()); item.setTimestamp(f.lastModified()); list.add(item); }
            }
        } else {
            // No history yet — fallback: scan all download dirs (originally from old backend)
            File[] subdirs = dir.listFiles(File::isDirectory);
            if (subdirs != null) for (File sub : subdirs) {
                File[] files = sub.listFiles(File::isFile);
                if (files != null) for (File f : files) {
                    String fn = sub.getName() + "/" + f.getName();
                    LocalBookItem item = new LocalBookItem(); item.setName(fn); item.setSize(f.length()); item.setTimestamp(f.lastModified()); list.add(item);
                }
            }
        }

        RespUtils.writeJson(resp, list);
    }
                }
            }
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