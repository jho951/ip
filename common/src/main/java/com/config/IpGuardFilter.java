package com.config;

import com.constant.AttributeKeys;
import com.constant.HeaderKeys;
import com.constant.IPRegex;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class IpGuardFilter implements Filter {

    // 규칙 토큰 분리: 쉼표, 파이프, 개행, 세미콜론
    private static final Pattern SEP = Pattern.compile("(?:,|\\||\\R|;)+");

    @Override
    public void init(FilterConfig filterConfig) {}

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // 1) IP 추출 + 루프백 IPv6 정규화
        String ip = request.getRemoteAddr();
        if ("::1".equals(ip) || "0:0:0:0:0:0:0:1".equalsIgnoreCase(ip)) ip = "127.0.0.1";
        // (일반 IPv6은 아래 match에서 자동 불일치 → denied:ip-format-not-supported 로 처리 가능)

        // 2) 규칙 로드 (Env + allow-ip.txt 병합)
        IpConfig cfg      = IpConfig.fromEnv();                 // mergedRules 보유
        String   userRaw  = EnvConfig.loadAllowFileRulesNormalized(); // 파일 규칙(정규화)
        String   envRaw   = EnvConfig.defaultIpRules();         // 기본 규칙(정규화)
        Path     allowFile= EnvConfig.actualAllowFilePath();    // 참고용

        // 3) 불린 판단 (IpConfig 활용)
        boolean allowed = cfg.isAllowed(ip);

        // 4) reason 계산: 사용자 규칙 → 기본 규칙 순서로 “매칭된 토큰”을 찾아 라벨링
        String reason;
        String hit = findMatchedToken(ip, userRaw);
        if (hit != null) {
            reason = "allowed:user(" + hit + ")";
        } else if ((hit = findMatchedToken(ip, envRaw)) != null) {
            reason = "allowed:default(" + hit + ")";
        } else {
            // IPv6 등 미지원 포맷이면 여기서 따로 표기해주고 싶다면:
            if (ip.contains(":")) {
                reason = "denied:ip-format-not-supported(" + ip + ")";
            } else {
                reason = "denied:no-match";
            }
        }

        // 5) 서버 내부 속성 (enum 키 사용)
        request.setAttribute(AttributeKeys.CLIENT.getKey(),  ip);
        request.setAttribute(AttributeKeys.ALLOWED.getKey(), allowed);
        request.setAttribute(AttributeKeys.REASON.getKey(),  reason);
        // 필요하면 디버깅용으로 규칙/파일 경로도 속성으로 전달 가능
        // request.setAttribute("ip.rules.merged", cfg.mergedRules());
        // request.setAttribute("ip.rules.file", allowFile);

        // 6) 응답 헤더 (enum 키 사용)
        response.setHeader(HeaderKeys.ALLOWED.getKey(), String.valueOf(allowed));
        response.setHeader(HeaderKeys.REASON.getKey(),  reason);

        // 차단 없이 통과
        chain.doFilter(request, response);
    }

    @Override public void destroy() {}

    // ===== 토큰 매칭 유틸 =====

    private static String findMatchedToken(String ip, String patternString) {
        if (patternString == null || patternString.isBlank()) return null;
        List<String> tokens = Arrays.stream(SEP.split(patternString))
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isEmpty())
                .toList();

        for (String token : tokens) {
            if (matches(ip, token)) return token; // 첫 매칭 토큰 반환
        }
        return null;
    }

    private static boolean matches(String ip, String token) {
        if (ip == null || token == null || token.isBlank()) return false;
        // IPv6는 현재 미지원: 필요시 여기서 바로 false
        if (ip.contains(":")) return false;

        if (IPRegex.CIDR.matches(token))       return isIpInCidr(ip, token);
        if (IPRegex.RANGE.matches(token))      return isIpInRange(ip, token);
        if (IPRegex.WILDCARD.matches(token))   return isIpInWildcard(ip, token);
        if (IPRegex.IPV4.matches(token))       return ip.equals(token);
        return false;
    }

    private static boolean isIpInCidr(String ip, String cidr) {
        // ex) 192.168.0.0/16
        String[] parts = cidr.split("/");
        if (parts.length != 2) return false;
        try {
            int prefix = Integer.parseInt(parts[1].trim());
            if (prefix < 0 || prefix > 32) return false;
            int ipInt  = ipv4ToInt(ip);
            int netInt = ipv4ToInt(parts[0].trim());
            int mask   = (prefix == 0) ? 0 : (int)(0xFFFFFFFFL << (32 - prefix));
            return (ipInt & mask) == (netInt & mask);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isIpInRange(String ip, String range) {
        // 하이픈(-) 또는 틸드(~) 모두 지원 (IPRegex가 [-~]를 허용)
        String[] parts = range.split("\\s*[-~]\\s*");
        if (parts.length != 2) return false;
        try {
            long start = ipv4ToLong(parts[0].trim());
            long end   = ipv4ToLong(parts[1].trim());
            long v     = ipv4ToLong(ip);
            long lo = Math.min(start, end), hi = Math.max(start, end);
            return v >= lo && v <= hi;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isIpInWildcard(String ip, String wildcard) {
        // A.B.C.* (옥텟별 * 허용)
        String[] ipOct = ip.split("\\.");
        String[] tkOct = wildcard.split("\\.");
        if (ipOct.length != 4 || tkOct.length != 4) return false;
        for (int i = 0; i < 4; i++) {
            if ("*".equals(tkOct[i])) continue;
            if (!ipOct[i].equals(tkOct[i])) return false;
        }
        return true;
    }

    private static int ipv4ToInt(String ip) throws java.net.UnknownHostException {
        byte[] b = java.net.InetAddress.getByName(ip).getAddress();
        if (b.length != 4) throw new IllegalArgumentException("IPv4 only");
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }
    private static long ipv4ToLong(String ip) throws java.net.UnknownHostException {
        return ((long) ipv4ToInt(ip)) & 0xFFFFFFFFL;
    }
}
