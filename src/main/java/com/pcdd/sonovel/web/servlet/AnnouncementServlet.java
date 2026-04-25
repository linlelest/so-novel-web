package com.pcdd.sonovel.web.servlet;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.pcdd.sonovel.db.AnnouncementRepository;
import com.pcdd.sonovel.model.Announcement;
import com.pcdd.sonovel.web.util.RespUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公告 API - 公开查询 + 管理员 CRUD
 *
 * @author pcdd
 */
public class AnnouncementServlet extends HttpServlet {

    private final AnnouncementRepository repo = new AnnouncementRepository();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try {
            String path = req.getRequestURI();

            // 公开接口：获取公告列表（无 content）
            if (path.endsWith("/api/announcements/list")) {
                List<Announcement> list = repo.findPublicList();
                RespUtils.writeJson(resp, list);
                return;
            }
            // 公开接口：获取单条公告详情（含 content）
            if (path.endsWith("/api/announcements/detail")) {
                String idStr = req.getParameter("id");
                if (idStr == null) { RespUtils.writeError(resp, 400, "缺少 id 参数"); return; }
                Announcement a = repo.findById(Integer.parseInt(idStr));
                if (a == null) { RespUtils.writeError(resp, 404, "公告不存在"); return; }
                RespUtils.writeJson(resp, a);
                return;
            }
            // 管理员：获取全部公告（含 content）
            String role = (String) req.getAttribute("role");
            if ("admin".equals(role) && path.endsWith("/api/admin/announcements")) {
                List<Announcement> list = repo.findAll();
                RespUtils.writeJson(resp, list);
                return;
            }

            RespUtils.writeError(resp, 404, "未知接口");
        } catch (Exception e) {
            RespUtils.writeError(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        String role = (String) req.getAttribute("role");
        if (!"admin".equals(role)) { RespUtils.writeError(resp, 403, "仅管理员可操作"); return; }
        try {
            JSONObject body = JSONUtil.parseObj(req.getReader());
            String title = body.getStr("title", "").strip();
            String content = body.getStr("content", "");
            int pinned = body.getInt("pinned", 0);
            if (title.isEmpty()) { RespUtils.writeError(resp, 400, "标题不能为空"); return; }
            Announcement a = repo.create(title, content, pinned);
            RespUtils.writeJson(resp, JSONUtil.createObj().set("message", "创建成功").set("id", a.getId()));
        } catch (Exception e) { RespUtils.writeError(resp, 500, e.getMessage()); }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) {
        String role = (String) req.getAttribute("role");
        if (!"admin".equals(role)) { RespUtils.writeError(resp, 403, "仅管理员可操作"); return; }
        try {
            JSONObject body = JSONUtil.parseObj(req.getReader());
            int id = body.getInt("id", 0);
            String title = body.getStr("title", "").strip();
            String content = body.getStr("content", "");
            int pinned = body.getInt("pinned", 0);
            if (id <= 0) { RespUtils.writeError(resp, 400, "ID 不能为空"); return; }
            if (title.isEmpty()) { RespUtils.writeError(resp, 400, "标题不能为空"); return; }
            repo.update(id, title, content, pinned);
            RespUtils.writeJson(resp, JSONUtil.createObj().set("message", "更新成功"));
        } catch (Exception e) { RespUtils.writeError(resp, 500, e.getMessage()); }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) {
        String role = (String) req.getAttribute("role");
        if (!"admin".equals(role)) { RespUtils.writeError(resp, 403, "仅管理员可操作"); return; }
        try {
            String idStr = req.getParameter("id");
            if (idStr == null) { RespUtils.writeError(resp, 400, "缺少 id 参数"); return; }
            repo.delete(Integer.parseInt(idStr));
            RespUtils.writeJson(resp, JSONUtil.createObj().set("message", "删除成功"));
        } catch (Exception e) { RespUtils.writeError(resp, 500, e.getMessage()); }
    }

}
