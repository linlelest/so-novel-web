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

    public void start() {
        int port = AppConfigLoader.APP_CONFIG.getWebPort();
        // 服务端模式下默认端口
        if (port == 7765 && "server".equalsIgnoreCase(System.getProperty("mode", "web"))) {
            // 使用默认端口
        }
        Server server = new Server(port);
        ServletContextHandler context = createServletContext();
        registerServlets(context);
        registerFilters(context);
        server.setHandler(context);
        try {
            server.start();
            Console.log("SoNovel {}", "v" + AppConfigLoader.APP_CONFIG.getVersion());
            Console.log(render("✔ Web server started (Jetty {})", "green"), Jetty.VERSION);
            Console.log(render("➜ Local: http://localhost:{}/", "blue"), port);
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
        context.addServlet(HistoryServlet.class, "/api/history");

        // 默认 Servlet 提供静态文件
        ServletHolder staticHolder = new ServletHolder("default", DefaultServlet.class);
        staticHolder.setInitParameter("dirAllowed", "false");
        context.addServlet(staticHolder, "/");
    }

    private void registerFilters(ServletContextHandler context) {
        context.addFilter(AuthFilter.class, "/*", java.util.EnumSet.of(jakarta.servlet.DispatcherType.REQUEST));
    }

}