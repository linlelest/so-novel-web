package com.pcdd.sonovel.web.servlet;

import cn.hutool.json.JSONUtil;
import com.pcdd.sonovel.web.util.RespUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class UpdateServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        if (!"admin".equals(req.getAttribute("role"))) {
            RespUtils.writeError(resp, 403, "仅管理员");
            return;
        }
        try {
            RespUtils.writeJson(resp, UpdateService.checkUpdate());
        } catch (Exception e) {
            RespUtils.writeError(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        if (!"admin".equals(req.getAttribute("role"))) {
            RespUtils.writeError(resp, 403, "仅管理员");
            return;
        }
        String action = req.getParameter("action");
        if (!"apply".equals(action)) {
            RespUtils.writeError(resp, 400, "?action=apply");
            return;
        }

        try {
            boolean ok = UpdateService.applyUpdate();
            RespUtils.writeJson(resp, JSONUtil.createObj()
                    .set("success", ok)
                    .set("message", ok ? "更新已执行，服务重启中..." : "更新失败"));
        } catch (Exception e) {
            RespUtils.writeError(resp, 500, e.getMessage());
        }
    }
}

