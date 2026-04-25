package com.pcdd.sonovel.web.servlet;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.pcdd.sonovel.db.TokenRepository;
import com.pcdd.sonovel.model.ApiToken;
import com.pcdd.sonovel.web.util.RespUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * Token 管理 API - CRUD
 *
 * @author pcdd
 */
public class TokenServlet extends HttpServlet {

    private final TokenRepository tokenRepository = new TokenRepository();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        int userId = (int) req.getAttribute("userId");
        List<ApiToken> tokens = tokenRepository.findByUserId(userId);
        RespUtils.writeJson(resp, tokens);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        try {
            int userId = (int) req.getAttribute("userId");
            JSONObject body = JSONUtil.parseObj(req.getReader());
            String note = body.getStr("note", "");

            ApiToken token = tokenRepository.create(userId, note);
            RespUtils.writeJson(resp, token);
        } catch (Exception e) {
            RespUtils.writeError(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) {
        try {
            int userId = (int) req.getAttribute("userId");
            JSONObject body = JSONUtil.parseObj(req.getReader());
            int tokenId = body.getInt("id", 0);
            String note = body.getStr("note", "");

            if (tokenId <= 0) {
                RespUtils.writeError(resp, 400, "Token ID 不能为空");
                return;
            }
            tokenRepository.updateNote(tokenId, userId, note);
            RespUtils.writeJson(resp, JSONUtil.createObj().set("message", "更新成功"));
        } catch (Exception e) {
            RespUtils.writeError(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) {
        try {
            int userId = (int) req.getAttribute("userId");
            String tokenIdStr = req.getParameter("id");
            if (tokenIdStr == null) {
                RespUtils.writeError(resp, 400, "Token ID 不能为空");
                return;
            }
            int tokenId = Integer.parseInt(tokenIdStr);
            tokenRepository.delete(tokenId, userId);
            RespUtils.writeJson(resp, JSONUtil.createObj().set("message", "删除成功"));
        } catch (Exception e) {
            RespUtils.writeError(resp, 500, e.getMessage());
        }
    }

}
