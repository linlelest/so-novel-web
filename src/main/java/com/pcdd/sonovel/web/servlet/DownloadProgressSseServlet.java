package com.pcdd.sonovel.web.servlet;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DownloadProgressSseServlet extends HttpServlet {

    private static final Set<AsyncContext> clients = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, JSONObject> states = new ConcurrentHashMap<>();

    /** Register a download by dlid (bookName from BookFetchServlet) */
    public static void register(String dlid, String bookName) {
        states.put(dlid, JSONUtil.createObj()
                .set("dlid", dlid).set("bookName", bookName).set("index", 0).set("total", 0));
    }
    public static void unregister(String dlid) { states.remove(dlid); }

    /** Called by Crawler — parse progress, merge with state, broadcast list */
    public static void sendProgress(String json) {
        JSONObject incoming = JSONUtil.parseObj(json);
        if (!"download-progress".equals(incoming.getStr("type"))) return;
        // Find matching state by index/total (dlid unknown to Crawler, match by most recent zero-progress entry)
        for (var entry : states.entrySet()) {
            JSONObject s = entry.getValue();
            if (s.getLong("index") == 0) {
                s.set("index", incoming.getLong("index"));
                s.set("total", incoming.getLong("total"));
                break;
            }
        }
        broadcast();
    }

    private static void broadcast() {
        String json = JSONUtil.toJsonStr(JSONUtil.createObj()
                .set("type", "download-progress")
                .set("downloads", states.values()));
        byte[] bytes = ("data: " + json + "\n\n").getBytes();
        for (AsyncContext ctx : clients) {
            try {
                synchronized (ctx) {
                    ServletResponse resp = ctx.getResponse();
                    if (resp != null) resp.getOutputStream().write(bytes);
                    else clients.remove(ctx);
                }
            } catch (Exception e) { clients.remove(ctx); try { ctx.complete(); } catch (Exception ignored) {} }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("text/event-stream;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");
        resp.setHeader("X-Accel-Buffering", "no");

        AsyncContext asyncContext = req.startAsync();
        asyncContext.setTimeout(0);
        asyncContext.addListener(new AsyncListener() {
            public void onComplete(AsyncEvent event) { clients.remove(asyncContext); }
            public void onError(AsyncEvent event) { clients.remove(asyncContext); }
            public void onStartAsync(AsyncEvent event) {}
            public void onTimeout(AsyncEvent event) { clients.remove(asyncContext); event.getAsyncContext().complete(); }
        });
        clients.add(asyncContext);
        ServletOutputStream out = resp.getOutputStream();
        out.write("retry: 10000\n".getBytes());
        out.write(": connected\n\n".getBytes());
        out.flush();
    }
}