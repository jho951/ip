package com.servlet;

import jakarta.servlet.http.*;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@WebServlet(name="fileQuery", urlPatterns="/files")
public class FileQueryServlet extends HttpServlet {

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String folder = req.getParameter("folder");
        String name   = req.getParameter("name");

        // TODO: 실제로는 folder/name 기준으로 파일 조회 → 바이트/스트림 전송
        // 여기서는 데모로 간단한 텍스트 응답
        String content = "server2: folder=" + folder + ", name=" + name;

        resp.setStatus(200);
        resp.setContentType("text/plain; charset=UTF-8");
        resp.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
    }
}
