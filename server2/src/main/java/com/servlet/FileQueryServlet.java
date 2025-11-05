package com.servlet;

import jakarta.servlet.http.*;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class FileQueryServlet extends HttpServlet {

    // 파일 루트: 환경변수 S2_FILE_ROOT 우선, 없으면 ~/Desktop
    private static final Path ROOT = resolveRoot();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String folder  = sanitizeName(req.getParameter("folder"));
        String name    = sanitizeName(req.getParameter("name"));

        System.out.println("folder: " + folder);
        System.out.println("name: " + name);

        // 서버1이 보낸 헤더만 신뢰해서 사용 (요청사항)
        String clientIp = req.getHeader("X-Client-IP");
        String allowed  = req.getHeader("X-Ip-Allowed");
        boolean ok      = Boolean.parseBoolean(allowed);


        // 경로 조립 및 이탈 방지
        Path dir  = ROOT.resolve(folder).normalize();
        Path file = dir.resolve(name).normalize();
        if (!file.startsWith(ROOT)) {
            resp.setStatus(400);
            resp.setContentType("text/plain; charset=UTF-8");
            resp.getOutputStream().write("bad path".getBytes(StandardCharsets.UTF_8));
            return;
        }

        // 파일 존재 확인
        if (!Files.isRegularFile(file)) {
            resp.setStatus(404);
            resp.setContentType("text/plain; charset=UTF-8");
            resp.getOutputStream().write(("not found: " + file).getBytes(StandardCharsets.UTF_8));
            return;
        }

        // 메타 헤더 (참고용)
        resp.setHeader("X-Client-IP", Objects.toString(clientIp, ""));
        resp.setHeader("X-Ip-Allowed", String.valueOf(ok));
        resp.setHeader("X-File-Path", file.toString());
        resp.setHeader("X-File-Length", String.valueOf(Files.size(file)));

        // 바이너리 전송 (서버1이 바이트/스트림으로 받음)
        resp.setStatus(200);
        resp.setContentType("application/octet-stream");
        resp.setHeader("Content-Disposition", "inline; filename=\"" + name + "\"");

        try (OutputStream out = resp.getOutputStream()) {
            Files.copy(file, out);
        }
    }

    // ===== helpers =====

    private static Path resolveRoot() {
        String env = System.getenv("S2_FILE_ROOT");
        Path root = (env != null && !env.isBlank())
                ? Paths.get(env.trim())
                : Paths.get(System.getProperty("user.home"), "Desktop");
        return root.toAbsolutePath().normalize();
    }

    /** 폴더/파일명에 대한 간단한 경로 이탈 방지 */
    private static String sanitizeName(String s) {
        if (s == null) return "";
        s = s.replace("\\", "/");
        s = s.replace("..", "");
        s = s.replaceAll("[\\r\\n]", "");
        while (s.startsWith("/")) s = s.substring(1);
        while (s.endsWith("/"))   s = s.substring(0, s.length()-1);
        return s;
    }
}
