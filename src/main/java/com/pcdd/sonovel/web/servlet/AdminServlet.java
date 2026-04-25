package com.pcdd.sonovel.web.servlet;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.pcdd.sonovel.db.HistoryRepository;
import com.pcdd.sonovel.db.UserRepository;
import com.pcdd.sonovel.model.AuthUser;
import com.pcdd.sonovel.model.DownloadHistory;
import com.pcdd.sonovel.web.util.RespUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理员 API - 用户管理、下载日志
 *
 * @author pcdd
 */
public class AdminServlet extends HttpServlet {

    private final UserRepository userRepository = new UserRepository();
    private final HistoryRepository historyRepository = new HistoryRepository();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        // 检查管理员权限
        String role = (String) req.getAttribute("role");
        if (!"admin".equals(role)) {
            RespUtils.writeError(resp, 403, "仅管理员可访问");
            return;
        }

        String path = req.getRequestURI();

        try {
            if (path.endsWith("/api/admin/users")) {
                handleListUsers(resp);
            } else if (path.endsWith("/api/admin/logs")) {
                handleListLogs(resp);
            } else {
                RespUtils.writeError(resp, 404, "未知的管理接口");
            }
        } catch (Exception e) {
            RespUtils.writeError(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        String role = (String) req.getAttribute("role");
        if (!"admin".equals(role)) {
            RespUtils.writeError(resp, 403, "仅管理员可访问");
            return;
        }

        try {
            JSONObject body = JSONUtil.parseObj(req.getReader());
            int userId = body.getInt("userId", 0);
            String action = body.getStr("action", "");

            if (userId <= 0) {
                RespUtils.writeError(resp, 400, "用户 ID 不能为空");
                return;
            }

            // 不能操作自己
            int currentUserId = (int) req.getAttribute("userId");
            if (userId == currentUserId) {
                RespUtils.writeError(resp, 400, "不能操作自己的账号");
                return;
            }

            switch (action) {
                case "ban" -> {
                    userRepository.setBanned(userId, 1);
                    RespUtils.writeJson(resp, JSONUtil.createObj().set("message", "已封禁"));
                }
                case "unban" -> {
                    userRepository.setBanned(userId, 0);
                    RespUtils.writeJson(resp, JSONUtil.createObj().set("message", "已解封"));
                }
                default -> RespUtils.writeError(resp, 400, "未知操作: " + action);
            }
        } catch (Exception e) {
            RespUtils.writeError(resp, 500, e.getMessage());
        }
    }

    private void handleListUsers(HttpServletResponse resp) {
        List<AuthUser> users = userRepository.findAll();
        // 脱敏处理 - 不返回密码 hash 和 salt
        List<JSONObject> safeList = new ArrayList<>();
        for (AuthUser u : users) {
            safeList.add(JSONUtil.createObj()
                    .set("id", u.getId())
                    .set("username", u.getUsername())
                    .set("role", u.getRole())
                    .set("banned", u.getBanned())
                    .set("createdAt", u.getCreatedAt()));
        }
        RespUtils.writeJson(resp, safeList);
    }

    private void handleListLogs(HttpServletResponse resp) {
        List<DownloadHistory> logs = historyRepository.findAllLogs();
        RespUtils.writeJson(resp, logs);
    }

}
