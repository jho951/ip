package com.filter;

import com.config.EnvConfig;
import com.config.IpConfig;
import com.constant.AttributeKeys;
import com.constant.HeaderKeys;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.Inet4Address;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static java.util.regex.Pattern.matches;


/**
 * 요청 보낸 IP를통해 허용/비허용 + 이유(reason)” 요청 속성(Request Attribute)과 응답 헤더(Response Header)에 제공
 *
 * <ol>
 *   <li>클라이언트 IP를 추출한다.</li>
 *   <li>환경변수와 allow-ip.txt를 합친 규칙으로 허용 여부를 계산한다.</li>
 *   <li>어떤 규칙에 걸렸는지 “이유(reason)” 문자열을 만든다. (user &rarr; default 우선)</li>
 *   <li>결과를 <b>요청 속성</b>과 <b>응답 헤더</b>에 기록한다.</li>
 *   <li>요청은 <b>막지 않고 그대로 통과</b>시킨다.</li>
 * </ol>
 *
 * <h2>규칙 포맷(예)</h2>
 * <ul>
 *   <li>단일 IPv4: {@code 192.168.0.10}</li>
 *   <li>CIDR: {@code 192.168.0.0/16}</li>
 *   <li>범위: {@code 10.0.0.1 - 10.0.0.200} 또는 {@code 10.0.0.1~10.0.0.200}</li>
 *   <li>와일드카드: {@code 172.30.1.*}</li>
 *   <li>토큰 구분: 쉼표, 파이프, 세미콜론, 개행 등을 지원( {@link com.constant.RegexConst#RULE_SEP} ).</li>
 * </ul>
 *
 * <h2>속성/헤더 키</h2>
 * <ul>
 *   <li>요청 속성: {@code AttributeKeys.CLIENT, ALLOWED, REASON}</li>
 *   <li>응답 헤더: {@code HeaderKeys.ALLOWED, HeaderKeys.REASON}</li>
 * </ul>
 *
 * <h2>차단 시</h2>
 * <p>{@code allowed==false} 시 {@code response.sendError(403)} 등을 바로 호출하거나,
 * {@code (boolean) request.getAttribute(AttributeKeys.ALLOWED.getKey())}를 보고 차단하면 됨.</p>
 */
public class IpGuardFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(IpGuardFilter.class);
    @Override
    public void init(FilterConfig filterConfig) {
    }


    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String ip = Inet4Address.getLocalHost().getHostAddress();

        // 규칙
        IpConfig cfg = IpConfig.fromEnv();
        String userRaw = EnvConfig.loadAllowFileRulesNormalized();
        String envRaw = EnvConfig.defaultIpRules();

        // 허용 여부
        boolean allowed = cfg.isAllowed(ip);

        // 이유
        String reason;
        String hit = IpConfig.findMatchedToken(ip, userRaw);
        if (hit != null) {
            reason = "user IP(" + hit + ")";
        } else if ((hit = IpConfig.findMatchedToken(ip, envRaw)) != null) {
            reason = "default IP(" + hit + ")";
        } else {
            if (ip.contains(":")) {
                reason = "unavailable format(" + ip + ")";
            } else {
                reason = "no match IP(" + ip + ")";
            }
        }

        // 서버 내부에서 참조할 요청 속성으로 저장
        request.setAttribute(AttributeKeys.CLIENT.getKey(), ip);
        request.setAttribute(AttributeKeys.ALLOWED.getKey(), allowed);
        request.setAttribute(AttributeKeys.REASON.getKey(), reason);
        // request.setAttribute("ip.rules.merged", cfg.mergedRules());
        // request.setAttribute("ip.rules.file", allowFile);

        // 응답 헤더
        response.setHeader(HeaderKeys.ALLOWED.getKey(), String.valueOf(allowed));
        response.setHeader(HeaderKeys.REASON.getKey(), reason);

        MDC.put("clientIp", ip);
        MDC.put("ipAllowed", String.valueOf(allowed));
        MDC.put("ipReason", reason);
        MDC.put("path", request.getRequestURI());
        MDC.put("method", request.getMethod());

        try {
            log.info("IP guard check - allowed={}, reason={}, {} {}",
                    allowed, reason, request.getMethod(), request.getRequestURI());

            // 차단 시
            //response.sendError(HttpServletResponse.SC_FORBIDDEN);
            chain.doFilter(request, response);
        } finally {
            // 쓰레드 로컬 정리 필수!
            MDC.clear();
        }
    }

}