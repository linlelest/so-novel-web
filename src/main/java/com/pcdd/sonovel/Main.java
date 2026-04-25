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
 *
 * @author pcdd
 */
public class Main {

    static {
        if (EnvUtils.isDev()) {
            Console.log(render("当前为开发环境！", "red"));
            Console.log("-".repeat(100));
        }
        ConsoleLog.setLevel(Level.OFF);
        if (EnvUtils.isProd()) {
            XRLog.listRegisteredLoggers().forEach(l -> XRLog.setLevel(l, java.util.logging.Level.OFF));
        }
    }

    public static void main(String[] args) {
        AppConfig cfg = AppConfigLoader.APP_CONFIG;
        HttpClientContext.set(OkHttpClientFactory.create(cfg));

        int port = cfg.getWebPort() > 0 ? cfg.getWebPort() : 7765;
        Console.log(render("SoNovel v{} 服务端模式启动", "blue"), AppConfigLoader.APP_CONFIG.getVersion());
        Console.log(render("➜ Local: http://localhost:{}/login.html", "green"), port);

        new WebServer().start();

        HttpClientContext.clear();
    }

}