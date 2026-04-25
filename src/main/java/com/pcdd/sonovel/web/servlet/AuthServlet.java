package com.pcdd.sonovel.web.servlet;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.pcdd.sonovel.db.UserRepository;
import com.pcdd.sonovel.model.AuthUser;
import com.pcdd.sonovel.web.AuthFilter;
import com.pcdd.sonovel.web.model.JsonResponse;
import com.pcdd.sonovel.web.util.RespUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 认证 API - 登录、注册、管理员注册、登出、会话检查
 *
 * @author pcdd
 */
public class AuthServlet extends HttpServlet {

    private final UserRepository userRepository = new UserRepository();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        try {
            String path = req.getRequestURI();
            JSONObject body = JSONUtil.parseObj(req.getReader());

            if (path.endsWith("/api/auth/login")) {
                handleLogin(body, resp);
            } else if (path.endsWith("/api/auth/register")) {
                handleRegister(body, resp);
            } else if (path.endsWith("/api/auth/admin-register")) {
                handleAdminRegister(body, resp);
            } else if (path.endsWith("/api/auth/logout")) {
                handleLogout(req, resp);
            } else {
                RespUtils.writeError(resp, 404, "未知的认证接口");
            }
        } catch (Exception e) {
            RespUtils.writeError(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try {
            String path = req.getRequestURI();
            if (path.endsWith("/api/auth/check")) {
                handleCheckSession(req, resp);
            } else if (path.endsWith("/api/auth/check-admin")) {
                boolean hasAdmin = userRepository.hasAdmin();
                RespUtils.writeJson(resp, JSONUtil.createObj().set("hasAdmin", hasAdmin));
            } else {
                RespUtils.writeError(resp, 404, "未知的认证接口");
            }
        } catch (Exception e) {
            RespUtils.writeError(resp, 500, e.getMessage());
        }
    }

    private void handleLogin(JSONObject body, HttpServletResponse resp) {
        String username = body.getStr("username", "").strip();
        String password = body.getStr("password", "");

        if (username.isEmpty() || password.isEmpty()) {
            RespUtils.writeError(resp, 400, "用户名和密码不能为空");
            return;
        }

        AuthUser user = userRepository.findByUsername(username);
        if (user == null) {
            RespUtils.writeError(resp, 401, "用户名或密码错误");
            return;
        }
        if (user.isBanned()) {
            RespUtils.writeError(resp, 403, "账号已被封禁，请联系管理员");
            return;
        }
        if (!userRepository.verifyPassword(user, password)) {
            RespUtils.writeError(resp, 401, "用户名或密码错误");
            return;
        }

        // 创建会话
        String sessionId = AuthFilter.createSession(user.getId(), user.getUsername(), user.getRole());

        // 设置 Cookie
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("sonovel_session", sessionId);
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60); // 24 小时
        cookie.setHttpOnly(true);
        resp.addCookie(cookie);

        RespUtils.writeJson(resp, JSONUtil.createObj()
                .set("sessionId", sessionId)
                .set("username", user.getUsername())
                .set("role", user.getRole()));
    }

    private void handleRegister(JSONObject body, HttpServletResponse resp) {
        String username = body.getStr("username", "").strip();
        String password = body.getStr("password", "");

        if (username.isEmpty() || password.isEmpty()) {
            RespUtils.writeError(resp, 400, "用户名和密码不能为空");
            return;
        }
        if (username.length() < 2 || username.length() > 20) {
            RespUtils.writeError(resp, 400, "用户名长度需在 2-20 个字符之间");
            return;
        }
        if (password.length() < 4) {
            RespUtils.writeError(resp, 400, "密码长度不能少于 4 个字符");
            return;
        }

        // 检查是否已存在
        if (userRepository.findByUsername(username) != null) {
            RespUtils.writeError(resp, 409, "用户名已存在");
            return;
        }

        userRepository.create(username, password, "user");
        RespUtils.writeJson(resp, JSONUtil.createObj().set("message", "注册成功"));
    }

    private void handleAdminRegister(JSONObject body, HttpServletResponse resp) {
        // 检查是否已存在管理员
        if (userRepository.hasAdmin()) {
            RespUtils.writeError(resp, 403, "管理员已存在，无法重复注册");
            return;
        }

        String username = body.getStr("username", "").strip();
        String password = body.getStr("password", "");

        if (username.isEmpty() || password.isEmpty()) {
            RespUtils.writeError(resp, 400, "用户名和密码不能为空");
            return;
        }
        if (password.length() < 6) {
            RespUtils.writeError(resp, 400, "管理员密码长度不能少于 6 个字符");
            return;
        }

        userRepository.create(username, password, "admin");

        // 自动登录
        AuthUser user = userRepository.findByUsername(username);
        String sessionId = AuthFilter.createSession(user.getId(), user.getUsername(), user.getRole());
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("sonovel_session", sessionId);
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60);
        cookie.setHttpOnly(true);
        resp.addCookie(cookie);

        RespUtils.writeJson(resp, JSONUtil.createObj()
                .set("sessionId", sessionId)
                .set("username", user.getUsername())
                .set("role", user.getRole())
                .set("message", "管理员注册成功"));
    }

    private void handleLogout(HttpServletRequest req, HttpServletResponse resp) {
        jakarta.servlet.http.Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (var c : cookies) {
                if ("sonovel_session".equals(c.getName())) {
                    AuthFilter.destroySession(c.getValue());
                    c.setMaxAge(0);
                    c.setPath("/");
                    resp.addCookie(c);
                    break;
                }
            }
        }
        RespUtils.writeJson(resp, JSONUtil.createObj().set("message", "已登出"));
    }

    private void handleCheckSession(HttpServletRequest req, HttpServletResponse resp) {
        jakarta.servlet.http.Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (var c : cookies) {
                if ("sonovel_session".equals(c.getName())) {
                    AuthFilter.SessionData sd = AuthFilter.getSession(c.getValue());
                    if (sd != null) {
                        RespUtils.writeJson(resp, JSONUtil.createObj()
                                .set("authenticated", true)
                                .set("username", sd.username())
                                .set("role", sd.role()));
                        return;
                    }
                }
            }
        }
        RespUtils.writeJson(resp, JSONUtil.createObj().set("authenticated", false));
    }

}
