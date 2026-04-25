package com.pcdd.sonovel.web.servlet;

import com.pcdd.sonovel.db.HistoryRepository;
import com.pcdd.sonovel.model.DownloadHistory;
import com.pcdd.sonovel.web.util.RespUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * 下载历史 API
 *
 * @author pcdd
 */
public class HistoryServlet extends HttpServlet {

    private final HistoryRepository historyRepository = new HistoryRepository();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        int userId = (int) req.getAttribute("userId");
        List<DownloadHistory> history = historyRepository.findByUserId(userId);
        RespUtils.writeJson(resp, history);
    }

}
