package com.pcdd.sonovel.web;

import cn.hutool.json.JSONUtil;
import com.pcdd.sonovel.db.ConfigRepository;
import com.pcdd.sonovel.db.TokenRepository;
import com.pcdd.sonovel.db.UserRepository;
import com.pcdd.sonovel.model.AuthUser;
import com.pcdd.sonovel.util.RateLimiter;
import com.pcdd.sonovel.web.model.JsonResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthFilter implements Filter {
    private static final Set<String> PUBLIC_PATHS = Set.of("/login","/register","/admin-register","/index.css","/favicon.ico","/api/auth/");
    private static final ConcurrentHashMap<String, SessionData> SESSIONS = new ConcurrentHashMap<>();
    private static final long SESSION_TTL = 24 * 60 * 60 * 1000L;
    private final UserRepository userRepo = new UserRepository();
    private final TokenRepository tokenRepo = new TokenRepository();
    private final ConfigRepository configRepo = new ConfigRepository();

    public record SessionData(int userId, String username, String role, long expiry) {
        public boolean isExpired() { return System.currentTimeMillis() > expiry; }
    }
    public static String createSession(int userId, String username, String role) {
        String sid = UUID.randomUUID().toString().replace("-", "");
        SESSIONS.put(sid, new SessionData(userId, username, role, System.currentTimeMillis() + SESSION_TTL));
        return sid;
    }
    public static void destroySession(String sid) { if (sid != null) SESSIONS.remove(sid); }
    public static SessionData getSession(String sid) {
        if (sid == null) return null;
        SessionData sd = SESSIONS.get(sid);
        if (sd == null || sd.isExpired()) { if (sd != null) SESSIONS.remove(sid); return null; }
        return sd;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI();
        String ip = getClientIp(req);

        // web visit rate limit
        String vr = configRepo.get("web_visit_rate");
        if (vr != null) {
            String[] p = vr.split("/"); int max = Integer.parseInt(p[0]); long sec = Long.parseLong(p[1]);
            if (!RateLimiter.allow("v:"+ip, max, sec*1000)) {
                resp.setStatus(429); resp.setContentType("application/json;charset=UTF-8");
                resp.getWriter().println(JSONUtil.toJsonStr(JsonResponse.error(429,"请求过于频繁，请稍后再试")));
                return;
            }
        }

        // IP blacklist
        if (isIpBanned(ip)) {
            resp.setStatus(403); resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().println(JSONUtil.toJsonStr(JsonResponse.error(403,"IP已被封禁，5天后自动解封。"+configRepo.get("contact_info"))));
            return;
        }

        // Maintenance mode (checked BEFORE public/private routing to block registration and non-admin access)
        String mm = configRepo.get("maintenance_mode");
        if ("true".equals(mm)) {
            // Maintenance-safe paths: login, check, static resources, maintenance page
            if (isMaintenanceSafePath(path)) {
                chain.doFilter(request, response);
                return;
            }
            // Admin via session bypass
            String sid = extractSid(req);
            if (sid != null) {
                SessionData sd = getSession(sid);
                if (sd != null && "admin".equals(sd.role())) {
                    req.setAttribute("userId", sd.userId());
                    req.setAttribute("username", sd.username());
                    req.setAttribute("role", sd.role());
                    chain.doFilter(request, response);
                    return;
                }
            }
            // Admin via token bypass
            String token = req.getParameter("token");
            if (token != null) {
                Integer uid = tokenRepo.findUserIdByToken(token);
                if (uid != null) {
                    AuthUser u = userRepo.findById(uid);
                    if (u != null && !u.isBanned() && "admin".equals(u.getRole())) {
                        req.setAttribute("userId", u.getId());
                        req.setAttribute("username", u.getUsername());
                        req.setAttribute("role", u.getRole());
                        req.setAttribute("authMethod", "token");
                        chain.doFilter(request, response);
                        return;
                    }
                }
            }
            // Block non-admin
            String accept = req.getHeader("Accept");
            boolean browser = accept != null && (accept.contains("text/html") || accept.contains("application/xhtml"));
            if (browser || path.equals("/") || path.equals("/index.html") || !path.startsWith("/api/")) {
                resp.sendRedirect("/maintenance.html");
            } else {
                resp.setStatus(501);
                resp.setContentType("application/json;charset=UTF-8");
                resp.getWriter().println(JSONUtil.toJsonStr(JsonResponse.error(501, "正在维护中")));
            }
            return;
        }

        if (isPublicPath(path)) { chain.doFilter(request, response); return; }

        // Session
        String sid = extractSid(req);
        if (sid != null) {
            SessionData sd = getSession(sid);
            if (sd != null) {
                AuthUser u = userRepo.findById(sd.userId());
                if (u != null && u.isBanned()) { destroySession(sid);
                    resp.setStatus(403); resp.setContentType("application/json;charset=UTF-8");
                    resp.getWriter().println(JSONUtil.toJsonStr(JsonResponse.error(403,"账号已被封禁，"+configRepo.get("contact_info")))); return; }
                req.setAttribute("userId",sd.userId()); req.setAttribute("username",sd.username()); req.setAttribute("role",sd.role());
                chain.doFilter(request, response); return;
            }
        }

        // Token
        String token = req.getParameter("token");
        if (token != null) {
            Integer uid = tokenRepo.findUserIdByToken(token);
            if (uid != null) {
                AuthUser u = userRepo.findById(uid);
                if (u != null && !u.isBanned()) {
                    req.setAttribute("userId",u.getId()); req.setAttribute("username",u.getUsername()); req.setAttribute("role",u.getRole());
                    req.setAttribute("authMethod","token");
                    chain.doFilter(request, response); return;
                }
            }
        }

        // Not authed
        String accept = req.getHeader("Accept");
        boolean browser = accept != null && (accept.contains("text/html")||accept.contains("application/xhtml"));
        if (browser || path.equals("/") || path.equals("/index.html") || !path.startsWith("/api/")) {
            resp.sendRedirect("/login.html");
        } else {
            resp.setStatus(401); resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().println(JSONUtil.toJsonStr(JsonResponse.error(401,"未登录或Token无效")));
        }
    }

    private String extractSid(HttpServletRequest r) {
        jakarta.servlet.http.Cookie[] cs = r.getCookies();
        if (cs != null) for (var c : cs) if ("sonovel_session".equals(c.getName())) return c.getValue();
        return null;
    }
    private boolean isPublicPath(String p) {
        if (PUBLIC_PATHS.contains(p)) return true;
        if (p.startsWith("/api/auth/")) return true; if (p.startsWith("/api/public/")) return true;
        if (p.endsWith(".css")||p.endsWith(".js")||p.endsWith(".ico")||p.endsWith(".png")||p.endsWith(".svg")) return true;
        if (p.equals("/login.html")||p.equals("/register.html")||p.equals("/maintenance.html")) return true;
        return false;
    }
    /** Paths permitted through AuthFilter during maintenance mode — login, static resources, public API only. */
    private boolean isMaintenanceSafePath(String p) {
        if (p.endsWith(".css")||p.endsWith(".js")||p.endsWith(".ico")||p.endsWith(".png")||p.endsWith(".svg")) return true;
        if (p.equals("/login.html")||p.equals("/maintenance.html")) return true;
        if (p.equals("/api/auth/login")||p.equals("/api/auth/check")||p.equals("/api/auth/check-admin")) return true;
        if (p.startsWith("/api/public/")) return true;
        return false;
    }
    private String getClientIp(HttpServletRequest r) {
        String ip = r.getHeader("X-Forwarded-For"); if (ip!=null&&!ip.isEmpty()) return ip.split(",")[0].trim();
        return r.getRemoteAddr();
    }
    public static boolean isIpBanned(String ip) {
        try (Connection c = com.pcdd.sonovel.db.DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM ip_blacklist WHERE ip=? AND expires_at>?")) {
            ps.setString(1,ip); ps.setLong(2,System.currentTimeMillis()); return ps.executeQuery().next();
        } catch(Exception e){ return false; }
    }
}
