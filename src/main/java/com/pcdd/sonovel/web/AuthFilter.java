package com.pcdd.sonovel.web;

import cn.hutool.json.JSONUtil;
import com.pcdd.sonovel.db.TokenRepository;
import com.pcdd.sonovel.db.UserRepository;
import com.pcdd.sonovel.model.AuthUser;
import com.pcdd.sonovel.web.model.JsonResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证过滤器 - 支持 Session Cookie 和 API Token 两种认证方式
 *
 * @author pcdd
 */
public class AuthFilter implements Filter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/login", "/register", "/admin-register",
            "/index.css", "/favicon.ico",
            "/api/auth/"
    );
    private static final ConcurrentHashMap<String, SessionData> SESSIONS = new ConcurrentHashMap<>();
    private static final long SESSION_TTL = 24 * 60 * 60 * 1000L; // 24 小时

    private final UserRepository userRepository = new UserRepository();
    private final TokenRepository tokenRepository = new TokenRepository();

    public record SessionData(int userId, String username, String role, long expiry) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expiry;
        }
    }

    /**
     * 创建会话
     */
    public static String createSession(int userId, String username, String role) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        SESSIONS.put(sessionId, new SessionData(userId, username, role,
                System.currentTimeMillis() + SESSION_TTL));
        return sessionId;
    }

    /**
     * 销毁会话
     */
    public static void destroySession(String sessionId) {
        if (sessionId != null) {
            SESSIONS.remove(sessionId);
        }
    }

    /**
     * 获取会话信息
     */
    public static SessionData getSession(String sessionId) {
        if (sessionId == null) return null;
        SessionData sd = SESSIONS.get(sessionId);
        if (sd == null || sd.isExpired()) {
            if (sd != null) SESSIONS.remove(sessionId);
            return null;
        }
        return sd;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI();

        // 静态资源和公开路径放行
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 1. 检查 Session Cookie
        String sessionId = extractSessionId(req);
        if (sessionId != null) {
            SessionData sd = getSession(sessionId);
            if (sd != null) {
                req.setAttribute("userId", sd.userId());
                req.setAttribute("username", sd.username());
                req.setAttribute("role", sd.role());
                chain.doFilter(request, response);
                return;
            }
        }

        // 2. 检查 Token 参数 (API 访问)
        String token = req.getParameter("token");
        if (token != null) {
            Integer userId = tokenRepository.findUserIdByToken(token);
            if (userId != null) {
                AuthUser user = userRepository.findById(userId);
                if (user != null && !user.isBanned()) {
                    req.setAttribute("userId", user.getId());
                    req.setAttribute("username", user.getUsername());
                    req.setAttribute("role", user.getRole());
                    req.setAttribute("authMethod", "token");
                    chain.doFilter(request, response);
                    return;
                }
            }
        }

        // 认证失败，返回 401
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().println(JSONUtil.toJsonStr(JsonResponse.error(401, "未登录或 Token 无效，请先登录或提供有效 token")));
    }

    private String extractSessionId(HttpServletRequest req) {
        jakarta.servlet.http.Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (var c : cookies) {
                if ("sonovel_session".equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    private boolean isPublicPath(String path) {
        // 精确匹配
        if (PUBLIC_PATHS.contains(path)) return true;
        // 前缀匹配
        if (path.startsWith("/api/auth/")) return true;
        if (path.startsWith("/api/public/")) return true;
        if (path.equals("/")) return true; // 首页 (会重定向或显示登录页)
        if (path.equals("/index.html")) return true;
        // 静态资源 (HTML, CSS, JS, 图标)
        if (path.endsWith(".html") || path.endsWith(".css") || path.endsWith(".js")
                || path.endsWith(".ico") || path.endsWith(".png") || path.endsWith(".svg")) {
            // login.html, register.html 公开; 其他 html 需认证
            if (path.endsWith(".html")) {
                return path.equals("/login.html") || path.equals("/register.html");
            }
            return true;
        }
        return false;
    }

}
