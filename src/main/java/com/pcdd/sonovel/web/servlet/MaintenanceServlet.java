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
            // 仅管理员可查看
            if (!"admin".equals(req.getAttribute("role"))) {
                RespUtils.writeError(resp, 403, "仅管理员");
                return;
            }
        }

        // 公开和管理员都返回相同数据
        String enabled = configRepo.get("maintenance_mode");
        String reason = configRepo.get("maintenance_reason");
        JSONObject data = JSONUtil.createObj()
                .set("enabled", "true".equals(enabled))
                .set("reason", reason == null ? "" : reason);
        RespUtils.writeJson(resp, data);
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
