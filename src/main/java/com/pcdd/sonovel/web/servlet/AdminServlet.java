package com.pcdd.sonovel.web.servlet;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.pcdd.sonovel.db.*;
import com.pcdd.sonovel.model.*;
import com.pcdd.sonovel.web.util.RespUtils;
import jakarta.servlet.http.*;
import java.sql.*;
import java.util.*;

public class AdminServlet extends HttpServlet {
    private final UserRepository userRepo = new UserRepository();
    private final HistoryRepository histRepo = new HistoryRepository();
    private final TokenRepository tokenRepo = new TokenRepository();
    private final ConfigRepository configRepo = new ConfigRepository();
    private final InviteCodeRepository inviteCodeRepo = new InviteCodeRepository();
    private final DatabaseManager db = DatabaseManager.getInstance();

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        if(notAdmin(req)){RespUtils.writeError(resp,403,"仅管理员");return;}
        String p=req.getRequestURI();
        try {
            if(p.endsWith("/api/admin/users")) listUsers(resp);
            else if(p.endsWith("/api/admin/logs")) listLogs(resp);
            else if(p.endsWith("/api/admin/bannedlog")) listBannedLog(resp);
            else if(p.endsWith("/api/admin/config")) listConfig(resp);
            else if(p.endsWith("/api/admin/invite-codes")) listInviteCodes(resp);
            else RespUtils.writeError(resp,404,"?");
        }catch(Exception e){RespUtils.writeError(resp,500,e.getMessage());}
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        if(notAdmin(req)){RespUtils.writeError(resp,403,"仅管理员");return;}
        try {
            JSONObject b = JSONUtil.parseObj(req.getReader());
            String action = b.getStr("action","");
            int uid = b.getInt("userId",0);

            switch(action) {
                case "ban" -> {
                    userRepo.setBanned(uid, 1);
                    revokeTokens(uid);
                    banUserIp(uid);
                    RespUtils.writeJson(resp, JSONUtil.createObj().set("message","已封禁"));
                }
                case "unban" -> {
                    userRepo.setBanned(uid, 0);
                    unbanUserIp(uid);
                    RespUtils.writeJson(resp, JSONUtil.createObj().set("message","已解封"));
                }
                case "delete" -> {
                    String reason = b.getStr("reason","").strip();
                    if(reason.isEmpty()) { RespUtils.writeError(resp,400,"必须填写删除理由"); return; }
                    AuthUser u = userRepo.findById(uid);
                    if(u==null) { RespUtils.writeError(resp,404,"用户不存在"); return; }
                    if("admin".equals(u.getRole())) { RespUtils.writeError(resp,400,"不能删除管理员"); return; }
                    revokeTokens(uid);
                    banUserIp(uid);
                    try (Connection c=db.getConnection();
                         PreparedStatement ps=c.prepareStatement("INSERT INTO banned_users_log(username,reason,action,created_at) VALUES(?,?,?,?)")) {
                        ps.setString(1,u.getUsername()); ps.setString(2,reason); ps.setString(3,"delete");
                        ps.setLong(4,System.currentTimeMillis()); ps.executeUpdate();
                    }
                    // Soft-delete: mark deleted_at, data kept for 30min after user tries to login
                    try (Connection c=db.getConnection();
                         PreparedStatement ps=c.prepareStatement("UPDATE users SET deleted_at=?,updated_at=? WHERE id=?")) {
                        ps.setLong(1, System.currentTimeMillis());
                        ps.setLong(2, System.currentTimeMillis());
                        ps.setInt(3, uid); ps.executeUpdate();
                    }
                    RespUtils.writeJson(resp, JSONUtil.createObj().set("message","已删除"));
                }
                case "config" -> {
                    String key = b.getStr("key",""); String val = b.getStr("value","");
                    if(!key.isEmpty()) configRepo.set(key, val);
                    RespUtils.writeJson(resp, JSONUtil.createObj().set("message","配置已更新"));
                }
                default -> {
                    // invite-code operations
                    switch(action) {
                        case "invite-gen" -> {
                            int count = b.getInt("count", 1);
                            int maxUses = b.getInt("maxUses", 1);
                            String createdBy = (String) req.getAttribute("username");
                            String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
                            Random rnd = new Random();
                            List<String> codes = new ArrayList<>();
                            for (int i = 0; i < count; i++) {
                                StringBuilder sb = new StringBuilder();
                                for (int j = 0; j < 8; j++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
                                inviteCodeRepo.create(sb.toString(), maxUses, createdBy);
                                codes.add(sb.toString());
                            }
                            RespUtils.writeJson(resp, JSONUtil.createObj().set("codes", codes));
                        }
                        case "invite-del" -> {
                            inviteCodeRepo.delete(b.getInt("id", 0));
                            RespUtils.writeJson(resp, JSONUtil.createObj().set("message","已删除"));
                        }
                        case "invite-batch-del" -> {
                            List<Integer> ids = b.getBeanList("ids", Integer.class);
                            inviteCodeRepo.batchDelete(ids);
                            RespUtils.writeJson(resp, JSONUtil.createObj().set("message","已批量删除"));
                        }
                        case "invite-config" -> {
                            configRepo.set("invite_code_enabled", b.getStr("enabled","false"));
                            configRepo.set("invite_code_prompt", b.getStr("prompt",""));
                            RespUtils.writeJson(resp, JSONUtil.createObj().set("message","已保存"));
                        }
                        default -> RespUtils.writeError(resp,400,"未知操作: "+action);
                    }
                }
            }
        }catch(Exception e){RespUtils.writeError(resp,500,e.getMessage());}
    }

    private void listUsers(HttpServletResponse resp) {
        List<AuthUser> users = userRepo.findAll(); List<JSONObject> list = new ArrayList<>();
        for(AuthUser u: users) list.add(JSONUtil.createObj().set("id",u.getId()).set("username",u.getUsername()).set("role",u.getRole()).set("banned",u.getBanned()).set("createdAt",u.getCreatedAt()));
        RespUtils.writeJson(resp,list);
    }
    private void listLogs(HttpServletResponse resp) { RespUtils.writeJson(resp, histRepo.findAllLogs()); }

    private void listBannedLog(HttpServletResponse resp) {
        List<JSONObject> list = new ArrayList<>();
        try(Connection c=db.getConnection(); Statement s=c.createStatement();
            ResultSet rs=s.executeQuery("SELECT * FROM banned_users_log ORDER BY created_at DESC")) {
            while(rs.next()) list.add(JSONUtil.createObj().set("id",rs.getInt("id")).set("username",rs.getString("username")).set("reason",rs.getString("reason")).set("action",rs.getString("action")).set("createdAt",rs.getLong("created_at")));
        }catch(Exception e){}
        RespUtils.writeJson(resp, list);
    }

    private void listConfig(HttpServletResponse resp) {
        Map<String,String> map = new LinkedHashMap<>();
        map.put("contact_info",configRepo.get("contact_info"));
        map.put("gh_update_proxy",configRepo.get("gh_update_proxy"));
        map.put("invite_code_enabled",configRepo.get("invite_code_enabled"));
        map.put("invite_code_prompt",configRepo.get("invite_code_prompt"));
        map.put("api_search_rate",configRepo.get("api_search_rate"));
        map.put("api_download_rate",configRepo.get("api_download_rate"));
        map.put("web_search_rate",configRepo.get("web_search_rate"));
        map.put("web_download_rate",configRepo.get("web_download_rate"));
        map.put("web_visit_rate",configRepo.get("web_visit_rate"));
        map.put("max_concurrent_downloads",configRepo.get("max_concurrent_downloads"));
        RespUtils.writeJson(resp, map);
    }

    private boolean notAdmin(HttpServletRequest r) { return !"admin".equals(r.getAttribute("role")); }

    private void revokeTokens(int uid) {
        List<ApiToken> tokens = tokenRepo.findByUserId(uid);
        for(ApiToken t: tokens) tokenRepo.delete(t.getId(), uid);
    }

    private void banUserIp(int uid) {
        AuthUser u = userRepo.findById(uid); if(u==null) return;
        String ip = u.getLastIp();
        if (ip != null && !ip.isEmpty() && !ip.startsWith("127.") && !ip.startsWith("0.")) {
            AuthServlet.banIp(ip);
        }
    }
    private void unbanUserIp(int uid) {
        AuthUser u = userRepo.findById(uid); if(u==null) return;
        String ip = u.getLastIp();
        if (ip != null && !ip.isEmpty()) {
            AuthServlet.unbanIp(ip);
        }
    }

    private void listInviteCodes(HttpServletResponse resp) {
        RespUtils.writeJson(resp, inviteCodeRepo.findAll());
    }

}
