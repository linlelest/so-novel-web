package com.pcdd.sonovel.web;

import com.pcdd.sonovel.core.AppConfigLoader;
import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * 下载文件自动清理器 — 每 60 秒扫描，删除超过 15 分钟的下载目录
 */
public class DownloadCleaner {

    private static final long TTL_MS = TimeUnit.MINUTES.toMillis(15);
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
        if (downloadPath != null && !downloadPath.isBlank()) {
            File dir = new File(downloadPath);
            if (dir.isDirectory()) {
                File[] subdirs = dir.listFiles(File::isDirectory);
                if (subdirs != null) {
                    long now = System.currentTimeMillis();
                    for (File sub : subdirs) {
                        if (!sub.getName().endsWith(" EPUB") && !sub.getName().endsWith(" TXT")
                                && !sub.getName().endsWith(" HTML") && !sub.getName().endsWith(" PDF"))
                            continue;
                        if (now - sub.lastModified() > TTL_MS) deleteRecursively(sub);
                    }
                }
            }
        }
        // Also clean up soft-deleted users (30min after deletion was triggered)
        try (java.sql.Connection c = com.pcdd.sonovel.db.DatabaseManager.getInstance().getConnection();
             java.sql.Statement s = c.createStatement()) {
            s.execute("DELETE FROM users WHERE deleted_at>0 AND deleted_at<" + (System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30)));
        } catch (Exception ignored) {}
    }

    private static void deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        f.delete();
    }
}
