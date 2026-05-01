package com.pcdd.sonovel.web.servlet;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.pcdd.sonovel.db.ConfigRepository;
import com.pcdd.sonovel.web.util.RespUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;

/**
 * 维护模式管理
 *
 * @author pcdd
 */
public class MaintenanceServlet extends HttpServlet {

    private final ConfigRepository configRepo = new ConfigRepository();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        String path = req.getRequestURI();

        if (path.equals("/api/admin/maintenance")) {
            if (!"admin".equals(req.getAttribute("role"))) {
                RespUtils.writeError(resp, 403, "仅管理员");
                return;
            }
        }

        if (path.equals("/api/public/bannedlog")) {
            listBannedLog(resp);
            return;
        }
        if (path.equals("/api/public/update-status")) {
            JSONObject data = JSONUtil.createObj()
                    .set("updating", "true".equals(configRepo.get("update_in_progress")))
                    .set("progress", com.pcdd.sonovel.web.servlet.UpdateService.getProgress());
            RespUtils.writeJson(resp, data);
            return;
        }
        if (path.equals("/api/public/invite-status")) {
            JSONObject data = JSONUtil.createObj()
                    .set("enabled", "true".equals(configRepo.get("invite_code_enabled")))
                    .set("prompt", configRepo.get("invite_code_prompt"));
            RespUtils.writeJson(resp, data);
            return;
        }

        // 公开和管理员都返回相同数据
        String enabled = configRepo.get("maintenance_mode");
        String reason = configRepo.get("maintenance_reason");
        JSONObject data = JSONUtil.createObj()
                .set("enabled", "true".equals(enabled))
                .set("reason", reason == null ? "" : reason);
        RespUtils.writeJson(resp, data);
    }

    private void listBannedLog(HttpServletResponse resp) {
        java.util.List<JSONObject> list = new java.util.ArrayList<>();
        try (java.sql.Connection c = com.pcdd.sonovel.db.DatabaseManager.getInstance().getConnection();
             java.sql.Statement s = c.createStatement();
             java.sql.ResultSet rs = s.executeQuery("SELECT * FROM banned_users_log ORDER BY created_at DESC")) {
            while (rs.next()) list.add(JSONUtil.createObj()
                    .set("id", rs.getInt("id"))
                    .set("username", rs.getString("username"))
                    .set("reason", rs.getString("reason"))
                    .set("action", rs.getString("action"))
                    .set("createdAt", rs.getLong("created_at")));
        } catch (Exception e) {}
        RespUtils.writeJson(resp, list);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        if (!"admin".equals(req.getAttribute("role"))) {
            RespUtils.writeError(resp, 403, "仅管理员");
            return;
        }

        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = req.getReader()) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONObject body = JSONUtil.parseObj(sb.toString());

            String action = body.getStr("action", "");
            String reason = body.getStr("reason", "");

            if ("on".equals(action)) {
                configRepo.set("maintenance_mode", "true");
            } else if ("off".equals(action)) {
                configRepo.set("maintenance_mode", "false");
            }

            // 始终保存理由（即使 action 为 keep 也会更新）
            configRepo.set("maintenance_reason", reason);

            String enabled = configRepo.get("maintenance_mode");
            JSONObject data = JSONUtil.createObj()
                    .set("enabled", "true".equals(enabled))
                    .set("reason", reason);
            RespUtils.writeJson(resp, data);
        } catch (Exception e) {
            RespUtils.writeError(resp, 500, e.getMessage());
        }
    }

}
