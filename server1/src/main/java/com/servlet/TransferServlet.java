package com.servlet;

import com.config.IpConfig;
import jakarta.servlet.http.*;
import jakarta.servlet.ServletException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class TransferServlet extends HttpServlet {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private static int portOf(String envUrl, int fallback) {
        try {
            if (envUrl == null || envUrl.isBlank()) return fallback;
            String s = envUrl.trim();
            if (s.matches("\\d+")) return Integer.parseInt(s);
            if (s.matches(":\\d+")) return Integer.parseInt(s.substring(1));
            int p = URI.create(s).getPort();
            return p > 0 ? p : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String clientIp = IpConfig.clientIPv4(req, false);
        boolean allowed = IpConfig.fromEnv().isAllowed(clientIp);

        req.setAttribute("ip.client", clientIp);
        req.setAttribute("ip.allowed", allowed);
        req.setAttribute("defaultFolder", "inbox");
        req.setAttribute("defaultFilename", "allow-ip.txt");
        req.getRequestDispatcher("/WEB-INF/index.jsp").forward(req, resp);
    }
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String folder = sanitizeName(nvl(req.getParameter("folderName"), "inbox"));
        String name   = sanitizeName(nvl(req.getParameter("fileName"), "test.txt"));

        String clientIp = IpConfig.clientIPv4(req, false);
        boolean allowed = IpConfig.fromEnv().isAllowed(clientIp);

        int s2 = portOf(System.getenv("DEFAULT_SERVER2"), 8082);
        String qs = "folder=" + enc(folder) + "&name=" + enc(name);
        URI uri = URI.create("http://localhost:" + s2 + "/files?" + qs);

        // 저장 루트: ENV S1_SAVE_ROOT 없으면 ~/s1data
        Path s1root  = Path.of(System.getenv().getOrDefault("S1_SAVE_ROOT",
                Path.of(System.getProperty("user.home"), "Desktop").toString())).toAbsolutePath().normalize();
        Path saveDir = s1root.resolve(folder).normalize();
        Path saveFile= saveDir.resolve(name).normalize();

        String msg;
        int code = 500;
        String err = null;

        // 루트 밖으로 이탈 방지
        if (!saveFile.startsWith(s1root)) {
            msg = "잘못된 경로 요청(루트 이탈): " + saveFile;
            code = 400;
        } else {
            try {
                var httpReq = HttpRequest.newBuilder(uri)
                        .header("X-Client-IP", clientIp)
                        .header("X-Ip-Allowed", String.valueOf(allowed))
                        .GET().build();

                var httpRes = CLIENT.send(httpReq, HttpResponse.BodyHandlers.ofInputStream());
                code = httpRes.statusCode();

                if (code == 200) {
                    Files.createDirectories(saveDir);
                    try (var in = httpRes.body();
                         var out = Files.newOutputStream(saveFile,
                                 StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                        long copied = in.transferTo(out);
                        msg = String.format("저장 완료: %s (%,d bytes) from server2:%d", saveFile, copied, s2);
                    }
                } else {
                    msg = "server2 응답 코드: " + code;
                    try (var in = httpRes.body()) { in.readAllBytes(); } // 스트림 소비
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                err = "server2 call interrupted";
                msg = "전송 중단(Interrupted)";
            } catch (Exception e) {
                err = "server2 call failed: " + e.getMessage();
                msg = "전송 실패: " + e.getMessage();
            }
        }

        req.setAttribute("message",
                String.format("folder=%s, file=%s, myIp=%s, allowed=%s, s2.status=%d, saved=%s%s",
                        folder, name, clientIp, allowed, code, saveFile, (err != null ? (", err=" + err) : "")));
        req.setAttribute("defaultFolder", folder);
        req.setAttribute("defaultFilename", name);
        req.getRequestDispatcher("/WEB-INF/index.jsp").forward(req, resp);
    }

    private static String sanitizeName(String s) {
        if (s == null) return "";
        s = s.replace("\\", "/");
        s = s.replace("..", "");
        s = s.replaceAll("[\\r\\n]", "");
        while (s.startsWith("/")) s = s.substring(1);
        while (s.endsWith("/"))   s = s.substring(0, s.length()-1);
        return s;
    }


    private static String nvl(String s, String d) { return (s == null || s.isBlank()) ? d : s; }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
