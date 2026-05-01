package com.pcdd.sonovel.web;

import cn.hutool.core.lang.Console;
import com.pcdd.sonovel.core.AppConfigLoader;
import com.pcdd.sonovel.web.servlet.*;
import org.eclipse.jetty.ee11.servlet.DefaultServlet;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Jetty;
import org.eclipse.jetty.util.resource.ResourceFactory;

import static org.fusesource.jansi.AnsiRenderer.render;

public class WebServer {

    private static volatile Server INSTANCE;

    public static void shutdown() {
        Server s = INSTANCE;
        if (s != null) {
            try { s.stop(); } catch (Exception ignored) {}
        }
    }

    public void start() {
        int port = AppConfigLoader.APP_CONFIG.getWebPort();
        // 服务端模式下默认端口
        if (port == 7765 && "server".equalsIgnoreCase(System.getProperty("mode", "web"))) {
            // 使用默认端口
        }
        Server server = new Server(port);
        INSTANCE = server;
        ServletContextHandler context = createServletContext();
        registerServlets(context);
        registerFilters(context);
        server.setHandler(context);
        try {
            server.start();
            Console.log("SoNovel {}", "v" + AppConfigLoader.APP_CONFIG.getVersion());
            Console.log(render("✔ Web server started (Jetty {})", "green"), Jetty.VERSION);
            Console.log(render("➜ Local: http://localhost:{}/", "blue"), port);
            // 启动自动更新定时器（每周日 UTC 07:00 检测并安装）
            AutoUpdater.start();
            // 启动下载文件自动清理（15 分钟 TTL）
            DownloadCleaner.start();
            server.join();
        } catch (Exception e) {
            Console.error(e, render("✖ Startup failed.", "red"));
        }
    }

    private ServletContextHandler createServletContext() {
        ServletContextHandler context = new ServletContextHandler("/");
        context.setBaseResource(ResourceFactory.of(context)
                .newResource(WebServer.class.getClassLoader().getResource("static")));
        return context;
    }

    private void registerServlets(ServletContextHandler context) {
        // 原有 Servlets
        context.addServlet(BookFetchServlet.class, "/book-fetch");
        context.addServlet(BookDownloadServlet.class, "/book-download");
        context.addServlet(LocalBookListServlet.class, "/local-books");
        context.addServlet(AggregatedSearchServlet.class, "/search/aggregated");
        context.addServlet(DownloadProgressSseServlet.class, "/download-progress");
        context.addServlet(ConfigServlet.class, "/config");

        // 认证相关 Servlets
        context.addServlet(AuthServlet.class, "/api/auth/*");
        context.addServlet(TokenServlet.class, "/api/tokens");
        context.addServlet(AdminServlet.class, "/api/admin/*");

        // 公告
        context.addServlet(AnnouncementServlet.class, "/api/announcements/*");
        context.addServlet(AnnouncementServlet.class, "/api/announcements/list");
        context.addServlet(AnnouncementServlet.class, "/api/announcements/detail");
        context.addServlet(AnnouncementServlet.class, "/api/announcements/login-page");
        context.addServlet(AnnouncementServlet.class, "/api/admin/announcements");

        // 更新检查
        context.addServlet(UpdateServlet.class, "/api/admin/update");

        // 维护模式
        context.addServlet(MaintenanceServlet.class, "/api/admin/maintenance");
        context.addServlet(MaintenanceServlet.class, "/api/public/maintenance");
        context.addServlet(MaintenanceServlet.class, "/api/public/bannedlog");
        context.addServlet(MaintenanceServlet.class, "/api/public/invite-status");

        // 默认 Servlet 提供静态文件
        ServletHolder staticHolder = new ServletHolder("default", DefaultServlet.class);
        staticHolder.setInitParameter("dirAllowed", "false");
        context.addServlet(staticHolder, "/");
    }

    private void registerFilters(ServletContextHandler context) {
        context.addFilter(AuthFilter.class, "/*", java.util.EnumSet.of(jakarta.servlet.DispatcherType.REQUEST));
    }

}