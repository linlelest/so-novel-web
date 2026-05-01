package com.pcdd.sonovel.web;

import com.pcdd.sonovel.core.AppConfigLoader;
import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * 下载文件自动清理器 — 每 60 秒扫描，删除超过 1 小时的下载文件
 */
public class DownloadCleaner {

    private static final long TTL_MS = TimeUnit.MINUTES.toMillis(60);
    private static final long SCAN_INTERVAL_MS = TimeUnit.SECONDS.toMillis(60);

    public static void start() {
        Thread t = new Thread(() -> {
            while (true) {
                try { Thread.sleep(SCAN_INTERVAL_MS); } catch (InterruptedException e) { break; }
                try { cleanup(); } catch (Exception ignored) {}
            }
        }, "download-cleaner");
        t.setDaemon(true);
        t.start();
    }

    private static void cleanup() {
        String downloadPath = AppConfigLoader.APP_CONFIG.getDownloadPath();
        if (downloadPath == null || downloadPath.isBlank()) return;
        File dir = new File(downloadPath);
        if (!dir.isDirectory()) return;

        long now = System.currentTimeMillis();
        File[] files = dir.listFiles(File::isFile);
        if (files != null) {
            for (File f : files) {
                if (now - f.lastModified() > TTL_MS) {
                    f.delete();
                    // Also remove from download_history
                    try (java.sql.Connection c = com.pcdd.sonovel.db.DatabaseManager.getInstance().getConnection();
                         java.sql.PreparedStatement ps = c.prepareStatement("DELETE FROM download_history WHERE file_name=?")) {
                        ps.setString(1, f.getName()); ps.executeUpdate();
                    } catch (Exception ignored) {}
                }
            }
        }
        // Also clean up soft-deleted users (30min after deletion triggered)
        try (java.sql.Connection c = com.pcdd.sonovel.db.DatabaseManager.getInstance().getConnection();
             java.sql.Statement s = c.createStatement()) {
            s.execute("DELETE FROM users WHERE deleted_at>0 AND deleted_at<" + (now - TimeUnit.MINUTES.toMillis(30)));
        } catch (Exception ignored) {}
    }
}
