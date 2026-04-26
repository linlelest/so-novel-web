package com.pcdd.sonovel;

import cn.hutool.core.lang.Console;
import cn.hutool.log.dialect.console.ConsoleLog;
import cn.hutool.log.level.Level;
import com.openhtmltopdf.util.XRLog;
import com.pcdd.sonovel.context.HttpClientContext;
import com.pcdd.sonovel.core.AppConfigLoader;
import com.pcdd.sonovel.core.OkHttpClientFactory;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.util.EnvUtils;
import com.pcdd.sonovel.web.WebServer;

import static org.fusesource.jansi.AnsiRenderer.render;

/**
 * SoNovel 服务端启动入口
 */
public class Main {

    static {
        ConsoleLog.setLevel(Level.OFF);
        if (EnvUtils.isProd()) {
            XRLog.listRegisteredLoggers().forEach(l -> XRLog.setLevel(l, java.util.logging.Level.OFF));
        }
    }

    public static void main(String[] args) {
        AppConfig cfg = AppConfigLoader.APP_CONFIG;
        HttpClientContext.set(OkHttpClientFactory.create(cfg));

        int port = cfg.getWebPort() > 0 ? cfg.getWebPort() : 7765;

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        int winDevMode = AppConfigLoader.APP_CONFIG.getWinDevmode() != null ? AppConfigLoader.APP_CONFIG.getWinDevmode() : 0;

        if (isWindows && winDevMode == 0) {
            // Windows + win_devmode=0: 后台托盘（无控制台窗口），由 Launch4j headerType=gui 保证无窗口
            new Thread(() -> new WebServer().start(), "web-server").start();
            com.pcdd.sonovel.launch.WinLauncher.launch();
        } else if (isWindows && winDevMode == 1) {
            // Windows + win_devmode=1: 后台托盘 + 控制台窗口（从终端启动时可见）
            Console.log(render("SoNovel v{} Web 服务启动", "blue"), AppConfigLoader.APP_CONFIG.getVersion());
            Console.log(render("➜ http://localhost:{}/login.html", "green"), port);
            new Thread(() -> new WebServer().start(), "web-server").start();
            com.pcdd.sonovel.launch.WinLauncher.launch();
        } else {
            // Linux/macOS: 纯控制台模式（无托盘）
            Console.log(render("SoNovel v{} 服务端模式启动", "blue"), AppConfigLoader.APP_CONFIG.getVersion());
            Console.log(render("➜ http://localhost:{}/login.html", "green"), port);
            new WebServer().start();
        }

        HttpClientContext.clear();
    }

}