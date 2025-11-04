package com.servlet;

import java.net.URI;

import java.io.IOException;

import jakarta.servlet.http.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;

@WebServlet(name = "transferServlet", urlPatterns = {"/", "/transfer"})
public class TransferServlet extends HttpServlet {

    private static int portOf(String envUrl, int fallback) {
        try {
            if (envUrl == null || envUrl.isBlank()) return fallback;
            int p = URI.create(envUrl.trim()).getPort();
            return p > 0 ? p : fallback;
        } catch (Exception e) { return fallback; }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setAttribute("defaultFolder", "inbox");
        req.setAttribute("defaultFilename", "sample.txt");
        req.getRequestDispatcher("/WEB-INF/views/index.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String folder = req.getParameter("folderName");
        String name   = req.getParameter("fileName");

        // TODO: 여기서 server2에 HTTP 호출하여 파일 데이터 가져와서 server1에 저장
        // (launcher로 같이 뜨므로 기본 8082로 접근 가능)
        int s2 = portOf(System.getenv("DEFAULT_SERVER2"), 8082);

        String msg = "전송 요청 완료 (server2:" + s2 + ") folder=" + folder + ", file=" + name;
        req.setAttribute("message", msg);
        req.setAttribute("defaultFolder", folder);
        req.setAttribute("defaultFilename", name);
        req.getRequestDispatcher("/WEB-INF/views/index.jsp").forward(req, resp);
    }
}
