package com.pcdd.sonovel.web.servlet;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.pcdd.sonovel.db.*;
import com.pcdd.sonovel.model.AuthUser;
import com.pcdd.sonovel.web.AuthFilter;
import com.pcdd.sonovel.web.util.RespUtils;
import jakarta.servlet.http.*;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class AuthServlet extends HttpServlet {
    private final UserRepository userRepo = new UserRepository();
    private final TokenRepository tokenRepo = new TokenRepository();
    private final ConfigRepository configRepo = new ConfigRepository();
    private final InviteCodeRepository inviteCodeRepo = new InviteCodeRepository();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        try { String p = req.getRequestURI();
            if (p.endsWith("/api/auth/logout")) { logout(req, resp); return; }
            JSONObject b = JSONUtil.parseObj(req.getReader());
            if (p.endsWith("/api/auth/login")) login(b, req, resp);
            else if (p.endsWith("/api/auth/register")) register(b, req, resp);
            else if (p.endsWith("/api/auth/admin-register")) adminReg(b, resp);
        } catch(Exception e) { RespUtils.writeError(resp,500,e.getMessage()); }
    }
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try { String p = req.getRequestURI();
            if (p.endsWith("/api/auth/check")) checkSession(req, resp);
            else if (p.endsWith("/api/auth/check-admin")) RespUtils.writeJson(resp, JSONUtil.createObj().set("hasAdmin",userRepo.hasAdmin()));
        } catch(Exception e) { RespUtils.writeError(resp,500,e.getMessage()); }
    }

    private void login(JSONObject b, HttpServletRequest req, HttpServletResponse resp) {
        String u = b.getStr("username","").strip(), pw = b.getStr("password","");
        if(u.isEmpty()||pw.isEmpty()) { RespUtils.writeError(resp,400,"用户名密码不能为空"); return; }
        String ip = getIp(req);
        if(AuthFilter.isIpBanned(ip)) { RespUtils.writeError(resp,403,"IP已被封禁。"+configRepo.get("contact_info")); return; }
        AuthUser user = userRepo.findByUsername(u);
        if(user==null) {
            if(AuthFilter.isUserDeleted(u)) { RespUtils.writeError(resp,410,"您的账户已被删除，"+configRepo.get("contact_info")); return; }
            RespUtils.writeError(resp,401,"用户名或密码错误"); return;
        }
        if(user.isBanned()) { RespUtils.writeError(resp,403,"账号已被封禁，"+configRepo.get("contact_info")); return; }
        if(!userRepo.verifyPassword(user,pw)) { RespUtils.writeError(resp,401,"用户名或密码错误"); return; }
        String sid = AuthFilter.createSession(user.getId(),user.getUsername(),user.getRole());
        boolean remember = b.getBool("remember", true);
        jakarta.servlet.http.Cookie ck = new jakarta.servlet.http.Cookie("sonovel_session",sid);
        ck.setPath("/"); ck.setMaxAge(remember ? 604800 : -1); ck.setHttpOnly(true); resp.addCookie(ck);
        RespUtils.writeJson(resp, JSONUtil.createObj().set("username",user.getUsername()).set("role",user.getRole()));
    }

    private void register(JSONObject b, HttpServletRequest req, HttpServletResponse resp) {
        String u = b.getStr("username","").strip(), pw = b.getStr("password","");
        if(u.isEmpty()||pw.isEmpty()) { RespUtils.writeError(resp,400,"用户名密码不能为空"); return; }
        if(u.length()<2||u.length()>20) { RespUtils.writeError(resp,400,"用户名2-20字符"); return; }
        if(pw.length()<4) { RespUtils.writeError(resp,400,"密码至少4字符"); return; }
        String ip = getIp(req);
        if(AuthFilter.isIpBanned(ip)) { RespUtils.writeError(resp,403,"IP已被封禁。"+configRepo.get("contact_info")); return; }
        if(userRepo.findByUsername(u)!=null) { RespUtils.writeError(resp,409,"用户名已存在"); return; }
        // Check invite code if enabled
        if ("true".equals(configRepo.get("invite_code_enabled"))) {
            String code = b.getStr("inviteCode","").strip();
            if (code.isEmpty()) { RespUtils.writeError(resp,400,configRepo.get("invite_code_prompt")); return; }
            if (inviteCodeRepo.findByCode(code) == null) { RespUtils.writeError(resp,400,"邀请码无效"); return; }
            if (!inviteCodeRepo.useCode(code)) { RespUtils.writeError(resp,400,"邀请码已用完"); return; }
        }
        userRepo.create(u,pw,"user");
        RespUtils.writeJson(resp, JSONUtil.createObj().set("message","注册成功，请返回登录"));
    }

    private void adminReg(JSONObject b, HttpServletResponse resp) {
        if(userRepo.hasAdmin()) { RespUtils.writeError(resp,403,"管理员已存在"); return; }
        String u=b.getStr("username","").strip(), pw=b.getStr("password","");
        if(u.isEmpty()||pw.isEmpty()) { RespUtils.writeError(resp,400,"不能为空"); return; }
        if(pw.length()<6) { RespUtils.writeError(resp,400,"密码至少6字符"); return; }
        userRepo.create(u,pw,"admin");
        AuthUser user = userRepo.findByUsername(u);
        String sid = AuthFilter.createSession(user.getId(),user.getUsername(),"admin");
        jakarta.servlet.http.Cookie ck = new jakarta.servlet.http.Cookie("sonovel_session",sid);
        ck.setPath("/"); ck.setMaxAge(86400); ck.setHttpOnly(true); resp.addCookie(ck);
        RespUtils.writeJson(resp, JSONUtil.createObj().set("username",user.getUsername()).set("role","admin").set("message","管理员注册成功"));
    }

    private void logout(HttpServletRequest req, HttpServletResponse resp) {
        jakarta.servlet.http.Cookie[] cs = req.getCookies();
        if(cs!=null) for(var c : cs) if("sonovel_session".equals(c.getName())) {
            AuthFilter.destroySession(c.getValue());
            // Clear the cookie by setting MaxAge=0 with same attributes
            jakarta.servlet.http.Cookie clearCookie = new jakarta.servlet.http.Cookie("sonovel_session", "");
            clearCookie.setPath("/");
            clearCookie.setMaxAge(0);
            clearCookie.setHttpOnly(true);
            resp.addCookie(clearCookie);
        }
        RespUtils.writeJson(resp, JSONUtil.createObj().set("message","已登出"));
    }

    private void checkSession(HttpServletRequest req, HttpServletResponse resp) {
        jakarta.servlet.http.Cookie[] cs = req.getCookies();
        if(cs!=null) for(var c : cs) if("sonovel_session".equals(c.getName())) {
            AuthFilter.SessionData sd = AuthFilter.getSession(c.getValue());
            if(sd!=null) { RespUtils.writeJson(resp, JSONUtil.createObj().set("authenticated",true).set("username",sd.username()).set("role",sd.role())); return; }
        }
        RespUtils.writeJson(resp, JSONUtil.createObj().set("authenticated",false));
    }

    private String getIp(HttpServletRequest r) {
        String ip = r.getHeader("X-Forwarded-For"); return (ip!=null&&!ip.isEmpty())?ip.split(",")[0].trim():r.getRemoteAddr();
    }

    // called by AdminServlet on ban/user delete
    static void banIp(String ip) {
        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO ip_blacklist(ip,reason,banned_at,expires_at) VALUES(?,'封禁',?,?)")){
            long now = System.currentTimeMillis(); ps.setString(1,ip); ps.setLong(2,now);
            ps.setLong(3, now + 5L*86400*1000); ps.executeUpdate();
        }catch(Exception e){e.printStackTrace();}
    }
    static void unbanIp(String ip) {
        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM ip_blacklist WHERE ip=?")) { ps.setString(1,ip); ps.executeUpdate(); }
        catch(Exception e){e.printStackTrace();}
    }
}
