package com.servlet;

import com.config.EnvConfig;
import com.config.FileConfig;
import com.config.IpConfig;
import com.constant.AttributeKeys;
import jakarta.servlet.http.*;
import jakarta.servlet.ServletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;

/**
 * <h1>TransferServlet</h1>
 * <p>
 * 서버1(JSP 폼)에서 입력받은 <b>폴더/파일명</b>을 기준으로,
 * 서버2의 파일 조회 API(<code>/files?name=...</code>)를 호출해 바이너리를 내려받고
 * 서버1의 저장 루트에 파일을 저장한 뒤 JSP로 결과 메시지를 전달한다.
 * </p>
 *
 * <h2>요청 흐름</h2>
 * <ol>
 *   <li><b>GET</b> <code>/transfer</code>: 초기 화면. 필터가 심은 IP/허용/이유를 읽어 JSP에 바인딩.</li>
 *   <li><b>POST</b> <code>/transfer</code>: 입력 폴더/파일을 정리 → 서버2에 GET 호출 →
 *       200이면 저장, 아니면 상태/본문을 메시지로 표시 → JSP forward.</li>
 * </ol>
 *
 * <h2>환경 변수/기본값</h2>
 * <ul>
 *   <li><code>S1_SAVE_ROOT</code>: 서버1 저장 루트(없으면 데스크톱). </li>
 *   <li><code>DEFAULT_SERVER2</code>: 서버2 포트 힌트(없으면 8082).</li>
 * </ul>
 *
 * <h2>보안/안정성</h2>
 * <ul>
 *   <li><b>경로 이탈 방지</b>: {@link FileConfig#sanitizeName(String)} + {@link FileConfig#isSafeUnder(Path, Path)} 사용.</li>
 *   <li><b>IP 허용/이유</b>: 필터가 넣은 요청 속성 {@link AttributeKeys} 우선 사용(필터 미적용 시 자체 계산).</li>
 *   <li><b>응답 커밋 전 forward</b>: <code>forward</code> 전에 절대 바디를 쓰지 않는다(커밋되면 forward 불가).</li>
 *   <li><b>Thread interrupt</b>: 서버2 호출 중 인터럽트 시 <code>interrupt flag</code> 복구 후 메시지 반환.</li>
 * </ul>
 */
public class TransferServlet extends HttpServlet {
    /** 서버2 호출용 HTTP 클라이언트(재사용). */
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Logger log = LoggerFactory.getLogger(TransferServlet.class);

    /**
     * <h3>GET /transfer</h3>
     * 초기 폼을 보여준다.
     * <ul>
     *   <li>필터가 심어둔 요청 속성에서 <b>클라이언트 IP</b>, <b>허용 여부</b>, <b>이유</b>를 우선 읽어 JSP에 바인딩.</li>
     *   <li>필터가 없으면 {@link IpConfig#clientIPv4(HttpServletRequest, boolean)} 및 {@link IpConfig#fromEnv()}로 대체 평가.</li>
     * </ul>
     *
     * @param req  요청
     * @param res  응답
     * @throws ServletException forward 시 예외
     * @throws IOException      I/O 예외
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        // 필터가 심어준 값 우선 사용, 없으면 fallback
        String clientIp = (String) req.getAttribute(AttributeKeys.CLIENT.getKey());
        if (clientIp == null) clientIp = IpConfig.clientIPv4(req, false);

        Boolean allowedObj = (Boolean) req.getAttribute(AttributeKeys.ALLOWED.getKey());
        boolean allowed = (allowedObj != null) ? allowedObj : IpConfig.fromEnv().isAllowed(clientIp);

        String reason = (String) req.getAttribute(AttributeKeys.REASON.getKey());
        if (reason == null) reason = "unknown"; // 필터 미적용 등 예외 대비

        // JSP 바인딩
        req.setAttribute("ip.client", clientIp);
        req.setAttribute("ip.allowed", allowed);
        req.setAttribute("ip.reason",  reason);

        req.setAttribute("defaultFolder", "");
        req.setAttribute("defaultFilename", "");

        // 주의: forward 이전에 응답 바디/스트림 접근 금지(커밋 방지)
        req.getRequestDispatcher("/WEB-INF/index.jsp").forward(req, res);
    }

    /**
     * <h3>POST /transfer</h3>
     * <ol>
     *   <li>입력 폴더/파일명 정리(이름 정규화 및 기본값 보정).</li>
     *   <li>필터 요청 속성에서 IP/허용/이유를 우선 읽기(필터 미적용 시 자체 평가).</li>
     *   <li>서버2 <code>/files?name=...</code> 호출(헤더로 IP/허용 전달).</li>
     *   <li>200이면 저장 루트 + 대상 폴더에 파일 저장(폴더 자동 생성).</li>
     *   <li>상태/이유/허용여부/저장경로를 메시지로 구성해 JSP로 forward.</li>
     * </ol>
     *
     * @param req  요청
     * @param res  응답
     * @throws ServletException forward 시 예외
     * @throws IOException      I/O 예외
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8"); // 파라미터 인코딩

        // 1) 입력 정리(이름 sanitize + 기본값)
        String destFolder = FileConfig.sanitizeName(
                FileConfig.nvl(req.getParameter("folderName"), "temp"));
        String fileName   = FileConfig.sanitizeName(
                FileConfig.nvl(req.getParameter("fileName"), "test.txt"));

        // 2) 필터가 심은 값 먼저 사용(없으면 스스로 계산)
        String clientIp = (String) req.getAttribute(AttributeKeys.CLIENT.getKey());
        if (clientIp == null) clientIp = IpConfig.clientIPv4(req, false);

        Boolean allowedObj = (Boolean) req.getAttribute(AttributeKeys.ALLOWED.getKey());
        boolean allowed = (allowedObj != null) ? allowedObj : IpConfig.fromEnv().isAllowed(clientIp);

        String reason = (String) req.getAttribute(AttributeKeys.REASON.getKey());
        if (reason == null) reason = "unknown";

        // 3) 서버2 호출 준비
        int s2 = EnvConfig.portOf(System.getenv("DEFAULT_SERVER2"), 8082);
        String qs  = "name=" + FileConfig.enc(fileName);
        URI uri    = URI.create("http://localhost:" + s2 + "/files?" + qs);

        // 4) 서버1 저장 루트/경로 계산 (경로 이탈 방지)
        Path s1root   = EnvConfig.rootPath("S1_SAVE_ROOT", EnvConfig.desktop());
        Path saveDir  = s1root.resolve(destFolder).normalize();
        Path saveFile = saveDir.resolve(fileName).normalize();

        String msg;
        int code = 500;
        String err = null;

        // 루트 이탈 예방(중요)
        if (!FileConfig.isSafeUnder(saveFile, s1root)) {
            msg = "잘못된 경로 요청(루트 이탈): " + saveFile;
            code = 400;
        } else {
            try {
                // 5) 서버2 호출
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
                                 StandardOpenOption.CREATE,
                                 StandardOpenOption.TRUNCATE_EXISTING,
                                 StandardOpenOption.WRITE)) {
                        long copied = in.transferTo(out);
                        log.info(String.format("저장 완료: %s (%,d bytes) from server2:%d",
                                saveFile, copied, s2));
                    }
                } else {
                    // 서버2에서 텍스트 응답이면 그대로 메시지에 포함(UTF-8 가정)
                    byte[] bodyBytes = httpRes.body().readAllBytes();
                    String bodyStr   = new String(bodyBytes, StandardCharsets.UTF_8);
                    log.info("server2 응답 코드: " + code + ", body=" + bodyStr);

                }
            } catch (InterruptedException ie) {
                // 인터럽트는 반드시 플래그 복구
                Thread.currentThread().interrupt();
                err = "server2 call interrupted";
                msg = "전송 중단(Interrupted)";
            } catch (Exception e) {
                err = "server2 call failed: " + e.getMessage();
                msg = "전송 실패: " + e.getMessage();
            }
        }

        // 7) JSP에 보여줄 메시지(허용/이유/상태/저장경로 포함)
        req.setAttribute("message",
                String.format("folder(dest)=%s, file(src)=%s, myIp=%s, allowed=%s, reason=%s, s2.status=%d, saved=%s%s",
                        destFolder, fileName, clientIp, allowed, reason, code, saveFile,
                        (err != null ? (", err=" + err) : "")));

        // 폼 값 유지
        req.setAttribute("defaultFolder", destFolder);
        req.setAttribute("defaultFilename", fileName);

        // 8) JSP로 forward (forward 전에 바디 출력/커밋 금지)
        req.getRequestDispatcher("/WEB-INF/index.jsp").forward(req, res);
    }
}
