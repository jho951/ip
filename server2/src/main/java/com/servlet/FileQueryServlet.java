package com.servlet;

import com.config.EnvConfig;
import com.config.FileConfig;
import com.constant.MimeConst;

import jakarta.servlet.http.*;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;

/**
 * 서버2의 파일 조회 엔드포인트.
 * 쿼리 파라미터 {@code name}을 받아, 설정된 소스 경로에서 파일을 찾아 반환
 *
 * <h2>동작 개요</h2>
 * <ol>
 *   <li>입력 이름 정리: {@link FileConfig#sanitizeName(String)}.</li>
 *   <li>기준 경로 계산: {@code ROOT} 아래 {@code S2_SOURCE_FOLDER} 하위로만 접근.</li>
 *   <li>보안 검사: 기준 경로 이탈이면 400, 존재하지 않으면 404.</li>
 *   <li>MIME 판별: {@code Files.probeContentType} → {@link MimeConst#guessByName(String)} → 기본값.</li>
 *   <li>텍스트면 UTF-8로, 바이너리면 스트림 복사로 전송.</li>
 *   <li>브라우저 표시용 {@code Content-Disposition}은 RFC 5987 스타일로 설정.</li>
 * </ol>
 *
 * <h2>환경 변수</h2>
 * <ul>
 *   <li>{@code S2_FILE_ROOT}: 파일 루트(없으면 Desktop).</li>
 *   <li>{@code S2_SOURCE_FOLDER}: 루트 하위 소스 폴더(옵션).</li>
 * </ul>
 *
 * <h2>보안/주의</h2>
 * <ul>
 *   <li><b>경로 이탈 방지</b>: sanitize + normalize + {@link FileConfig#isSafeUnder(Path, Path)}.</li>
 *   <li><b>헤더는 ASCII</b>: HTTP/1.1 헤더 값은 ASCII만 안전. 한글/비ASCII를 헤더에 담을 경우
 *       톰캣이 제거할 수 있음. 필요 시 별도 <i>*-Encoded</i> 헤더(URLEncoded/Base64)를 병행 권장.</li>
 *   <li>텍스트 응답은 명시적으로 <code>charset=UTF-8</code> 지정.</li>
 * </ul>
 */
public class FileQueryServlet extends HttpServlet {

    /** 파일 루트: S2_FILE_ROOT 없으면 Desktop. */
    private static final Path ROOT = EnvConfig.rootPath("S2_FILE_ROOT", EnvConfig.desktop());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // 1) 입력 이름 정리 (슬래시/.. 제거, 앞뒤 / 제거)
        String name = FileConfig.sanitizeName(req.getParameter("name"));

        // (옵션) 관찰용 헤더 수신 값
        String clientIp = req.getHeader("X-Client-IP");
        String allowed  = req.getHeader("X-Ip-Allowed");

        // 2) 기준(base) 경로 계산: ROOT/(S2_SOURCE_FOLDER)
        String srcSub = EnvConfig.env("S2_SOURCE_FOLDER");
        Path base = FileConfig.resolveBase(ROOT, srcSub);
        Path file = base.resolve(name).normalize();

        // 3) 보안/존재 검사
        if (!FileConfig.isSafeUnder(file, base)) {
            plainText(res, 400, "bad path");
            return;
        }
        if (!Files.isRegularFile(file)) {
            plainText(res, 404, "not found: " + file);
            return;
        }

        // 헤더
        res.setHeader("X-Client-IP-Observed", Objects.toString(clientIp, ""));
        res.setHeader("X-Ip-Allowed-Observed", Objects.toString(allowed, ""));
        res.setHeader("X-File-Path", file.toString());
        res.setHeader("X-File-Length", String.valueOf(Files.size(file)));

        // 5) MIME 판별: probeContentType → fallback 매핑 → 기본값
        String mime = Files.probeContentType(file);
        if (mime == null) mime = MimeConst.guessByName(name);
        if (mime == null) mime = "application/octet-stream";

        // 6) 브라우저 inline 표시를 시도 (RFC 5987 filename*=UTF-8'')
        String cd = contentDispositionInline(name);

        // 7) 텍스트/바이너리 분기
        if (MimeConst.isTextual(mime)) {
            // 텍스트: UTF-8로 읽어 writer에 씀
            res.setStatus(200);
            res.setCharacterEncoding("UTF-8");
            res.setContentType(mime + "; charset=UTF-8");
            res.setHeader("Content-Disposition", cd);

            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                 PrintWriter writer = res.getWriter()) {
                char[] buf = new char[8192];
                int n;
                while ((n = reader.read(buf)) != -1) writer.write(buf, 0, n);
            }
        } else {
            // 바이너리: 그대로 스트림 복사
            res.setStatus(200);
            res.setContentType(mime);
            res.setHeader("Content-Disposition", cd);
            try (OutputStream out = res.getOutputStream()) {
                Files.copy(file, out);
            }
        }
    }

    /**
     * <p>브라우저가 파일명을 최대한 정확히 처리하도록
     * <code>inline; filename="ASCII"; filename*=UTF-8''percent-encoded</code> 형태로 생성.</p>
     * <p>RFC 5987/6266 패턴. ASCII 외 문자는 {@code filename*}에 담긴다.</p>
     */
    private static String contentDispositionInline(String filename) {
        String asciiFallback = filename.replaceAll("[\\r\\n\"]", "_"); // 헤더 안전용
        String enc = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "inline; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + enc;
    }

    /**
     * 단순 텍스트 응답 헬퍼(UTF-8).
     *
     * @param status HTTP 상태 코드
     * @param body   메시지 본문(UTF-8)
     */
    private static void plainText(HttpServletResponse res, int status, String body) throws IOException {
        res.setStatus(status);
        res.setCharacterEncoding("UTF-8");
        res.setContentType("text/plain; charset=UTF-8");
        try (OutputStream out = res.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }
}
