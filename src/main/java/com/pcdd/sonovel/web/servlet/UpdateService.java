package com.pcdd.sonovel.web.servlet;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.http.Header;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.pcdd.sonovel.core.AppConfigLoader;
import com.pcdd.sonovel.db.ConfigRepository;
import com.pcdd.sonovel.util.RandomUA;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 更新检查与安装服务（UpdateServlet / AutoUpdater 共用）
 */
public class UpdateService {

    private static final ConfigRepository configRepo = new ConfigRepository();
    private static final String URL = "https://api.github.com/repos/linlelest/so-novel-web/releases";

    /**
     * 检查是否有新版本
     */
    public static JSONObject checkUpdate() {
        JSONObject r = JSONUtil.createObj();
        try (HttpResponse res = HttpUtil.createGet(URL).timeout(8000)
                .header(Header.USER_AGENT, RandomUA.generate()).execute()) {
            if (!res.isOk()) {
                r.set("error", "GitHub API 请求失败，状态码: " + res.getStatus());
                return r;
            }
            JSONArray arr = JSONUtil.parseArray(res.body());
            JSONObject latest = JSONUtil.parseObj(arr.getFirst());
            String cur = "v" + AppConfigLoader.APP_CONFIG.getVersion();
            String lat = latest.get("tag_name", String.class);
            String url = latest.get("html_url", String.class);
            r.set("currentVersion", cur);
            r.set("latestVersion", lat);
            r.set("hasUpdate", !cur.equals(lat) && compareVersion(cur, lat) < 0);
            r.set("url", url);
        } catch (Exception e) {
            r.set("error", e.getMessage());
        }
        return r;
    }

    /**
     * 执行更新：开启维护模式 → 下载 → 解压覆盖 → Linux 下重启
     */
    public static boolean applyUpdate() throws Exception {
        configRepo.set("maintenance_mode", "true");
        Console.log("[update] 已开启维护模式");

        String os = System.getProperty("os.name", "").toLowerCase();
        String workDir = System.getProperty("user.dir");
        String proxy = configRepo.get("gh_update_proxy");

        if (os.contains("win")) {
            String dlUrl = getLatestDownloadUrl("windows_x64");
            if (dlUrl == null) return false;
            // Apply proxy prefix only for download (not for check)
            if (proxy != null && !proxy.isBlank()) {
                dlUrl = proxy.replaceAll("/+$", "") + "/" + dlUrl;
                Console.log("[update] 使用代理下载: {}", dlUrl);
            }
            Path tmp = Paths.get(workDir, "sonovel-update.tar.gz");
            Console.log("[update] 下载更新包: {}", dlUrl);
            HttpUtil.downloadFile(dlUrl, tmp.toFile());
            ProcessBuilder pb = new ProcessBuilder("tar", "xzf", tmp.toString(), "-C", workDir,
                    "--strip-components=1", "--overwrite");
            Process p = pb.start();
            p.waitFor();
            Files.deleteIfExists(tmp);
            Console.log("[update] Windows 更新完成，exitCode={}", p.exitValue());
            return p.exitValue() == 0;
        } else {
            String dlUrl = getLatestDownloadUrl("linux_x64");
            if (dlUrl == null) return false;
            if (proxy != null && !proxy.isBlank()) {
                dlUrl = proxy.replaceAll("/+$", "") + "/" + dlUrl;
                Console.log("[update] 使用代理下载: {}", dlUrl);
            }
            Path script = Paths.get(workDir, "update.sh");
            if (!Files.exists(script)) {
                Console.error("[update] update.sh 不存在: {}", script);
                return false;
            }
            Process p = new ProcessBuilder("bash", script.toString(), dlUrl)
                    .directory(new File(workDir)).start();
            String out = IoUtil.readUtf8(p.getInputStream());
            Console.log("[update] {}", out);
            return p.waitFor() == 0;
        }
    }

    /**
     * 简单语义化版本比较。v1.0.0 → [1,0,0]，逐段比较
     */
    private static int compareVersion(String v1, String v2) {
        String[] parts1 = v1.replace("v", "").split("\\.");
        String[] parts2 = v2.replace("v", "").split("\\.");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int n2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (n1 != n2) return Integer.compare(n1, n2);
        }
        return 0;
    }

    private static String getLatestDownloadUrl(String suffix) {
        try (HttpResponse r = HttpUtil.createGet(URL).timeout(5000)
                .header(Header.USER_AGENT, RandomUA.generate()).execute()) {
            JSONArray arr = JSONUtil.parseArray(r.body());
            JSONObject latest = JSONUtil.parseObj(arr.getFirst());
            JSONArray assets = latest.getJSONArray("assets");
            for (int i = 0; i < assets.size(); i++) {
                String name = assets.getJSONObject(i).getStr("name");
                if (name != null && name.contains(suffix)) {
                    return assets.getJSONObject(i).getStr("browser_download_url");
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
