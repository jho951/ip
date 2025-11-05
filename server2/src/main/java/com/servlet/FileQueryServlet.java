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
        // ✅ 폴더 파라미터는 더 이상 받지 않음 (또는 무시)
        // String folder  = sanitizeName(req.getParameter("folder"));  // 제거
        String name    = sanitizeName(req.getParameter("name"));

        // 참고용 헤더(차단 X)
        String clientIp = req.getHeader("X-Client-IP");
        String allowed  = req.getHeader("X-Ip-Allowed");

        // ✅ optional: S2_SOURCE_FOLDER 환경변수로 루트 하위 고정 소스 폴더 지정 가능 (비워도 됨)
        String srcSub = System.getenv("S2_SOURCE_FOLDER");
        Path base = (srcSub != null && !srcSub.isBlank())
                ? ROOT.resolve(sanitizeName(srcSub)).normalize()
                : ROOT;

        Path file = base.resolve(name).normalize();

        // 루트(또는 base) 이탈 방지
        if (!file.startsWith(ROOT)) {
            resp.setStatus(400);
            resp.setContentType("text/plain; charset=UTF-8");
            resp.getOutputStream().write("bad path".getBytes(StandardCharsets.UTF_8));
            return;
        }

        if (!Files.isRegularFile(file)) {
            resp.setStatus(404);
            resp.setContentType("text/plain; charset=UTF-8");
            resp.getOutputStream().write(("not found: " + file).getBytes(StandardCharsets.UTF_8));
            return;
        }

        // 메타 (차단 X)
        resp.setHeader("X-Client-IP-Observed", Objects.toString(clientIp, ""));
        resp.setHeader("X-Ip-Allowed-Observed", Objects.toString(allowed, ""));
        resp.setHeader("X-File-Path", file.toString());
        resp.setHeader("X-File-Length", String.valueOf(Files.size(file)));

        // 바이너리 전송
        resp.setStatus(200);
        resp.setContentType("application/octet-stream");
        resp.setHeader("Content-Disposition", "inline; filename=\"" + name + "\"");
        try (OutputStream out = resp.getOutputStream()) {
            Files.copy(file, out);
        }
    }


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
