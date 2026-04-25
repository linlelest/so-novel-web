package com.pcdd.sonovel.web.servlet;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.pcdd.sonovel.db.TokenRepository;
import com.pcdd.sonovel.model.ApiToken;
import com.pcdd.sonovel.web.util.RespUtils;
import jakarta.servlet.http.*;
import java.util.List;

public class TokenServlet extends HttpServlet {
    private final TokenRepository repo = new TokenRepository();

    @Override protected void doGet(HttpServletRequest r, HttpServletResponse w) {
        int uid=(int)r.getAttribute("userId"); RespUtils.writeJson(w,repo.findByUserId(uid));
    }
    @Override protected void doPost(HttpServletRequest r, HttpServletResponse w) {
        try{ int uid=(int)r.getAttribute("userId");
            JSONObject b=JSONUtil.parseObj(r.getReader());
            ApiToken t=repo.create(uid,b.getStr("note","")); RespUtils.writeJson(w,t);
        }catch(Exception e){RespUtils.writeError(w,500,e.getMessage());}
    }
    @Override protected void doPut(HttpServletRequest r, HttpServletResponse w) {
        try{ int uid=(int)r.getAttribute("userId");
            JSONObject b=JSONUtil.parseObj(r.getReader());
            int id=b.getInt("id",0); if(id<=0){RespUtils.writeError(w,400,"ID missing");return;}
            repo.updateNote(id,uid,b.getStr("note","")); RespUtils.writeJson(w,JSONUtil.createObj().set("message","OK"));
        }catch(Exception e){RespUtils.writeError(w,500,e.getMessage());}
    }
    @Override protected void doDelete(HttpServletRequest r, HttpServletResponse w) {
        try{ int uid=(int)r.getAttribute("userId");
            // Support both query param ?id= and body {"id":}
            String idStr = r.getParameter("id");
            if(idStr==null){
                JSONObject b=JSONUtil.parseObj(r.getReader());
                idStr=b.getStr("id"); // try body
            }
            if(idStr==null||idStr.isEmpty()){RespUtils.writeError(w,400,"Token ID missing");return;}
            int id = Integer.parseInt(idStr.trim());
            repo.delete(id,uid); RespUtils.writeJson(w,JSONUtil.createObj().set("message","OK"));
        }catch(Exception e){RespUtils.writeError(w,500,e.getMessage());}
    }
}
