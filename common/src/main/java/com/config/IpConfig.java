package com.config;

import com.constant.ErrorCode;
import com.constant.IPRegex;
import com.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.constant.RegexConst.IPV6_MAPPED_V4;
import static com.constant.RegexConst.RULE_SEP;
import static java.util.regex.Pattern.matches;

/**
 * IpConfig
 *  - EnvConfig로부터 규칙을 읽고(~ → - 정규화, 파일 병합)
 *  - mergedRules 보관
 *  - isAllowed(ip) 로 직접 검사 (CIDR / Range / Wildcard / Single IPv4)
 *  - assertValidRules(...) 로 사전 검증 가능
 */
public final class IpConfig {

    private final String mergedRules;
    // allow-ip 파일 경로
    private final Path allowFile;

    public String mergedRules() { return mergedRules; }

    public Path allowFile() { return allowFile; }

    private IpConfig(String mergedRules, Path allowFile) {
        this.mergedRules = mergedRules;
        this.allowFile = allowFile;
    }


    private static boolean isValidIP(String ipToValidate, String patternString) {
        if (ipToValidate == null || ipToValidate.isBlank()) return false;
        if (patternString == null || patternString.isBlank()) return false;

        String[] patterns = RULE_SEP.split(patternString);
        for (String raw : patterns) {
            String p = raw == null ? "" : raw.trim();
            if (p.isEmpty()) continue;

            if (IPRegex.CIDR.matches(p)) {
                if (isIpInCidr(ipToValidate, p)) return true;

            } else if (IPRegex.RANGE.matches(p)) {
                if (isIpInRange(ipToValidate, p)) return true;

            } else if (IPRegex.WILDCARD.matches(p)) {
                if (isIpInWildcard(ipToValidate, p)) return true;

            } else if (IPRegex.IPV4.matches(p)) {
                if (ipToValidate.equals(p)) return true;
            }
        }
        return false;
    }

    /**
     *
     * @param ip
     * @param cidr
     * @return
     */
    private static boolean isIpInCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) return false;

            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > 32) return false;

            int ipInt  = ipv4ToInt(ip);
            int netInt = ipv4ToInt(parts[0]);

            int mask = (prefix == 0) ? 0 : (int)(0xFFFFFFFFL << (32 - prefix));
            return (ipInt & mask) == (netInt & mask);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     *
     * @param ip
     * @param range
     * @return
     */
    private static boolean isIpInRange(String ip, String range) {
        String[] parts = range.split("[-~]");
        if (parts.length != 2) return false;
        try {
            long startIp = ipv4ToLong(parts[0].trim());
            long endIp = ipv4ToLong(parts[1].trim());
            long check = ipv4ToLong(ip);
            long lo = Math.min(startIp, endIp);
            long hi = Math.max(startIp, endIp);
            return check >= lo && check <= hi;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isIpInWildcard(String ip, String wildcardPattern) {
        String regex = wildcardPattern.replace(".", "\\.");
        regex = regex.replace("*", "\\d{1,3}");
        regex = "^" + regex + "$";

        if (!ip.matches(regex)) return false;

        String[] parts = ip.split("\\.");
        for (String part : parts) {
            int val = Integer.parseInt(part);
            if (val < 0 || val > 255) return false;
        }
        return true;
    }


    private static int ipv4ToInt(String ip) throws UnknownHostException {
        byte[] b = InetAddress.getByName(ip).getAddress();
        if (b.length != 4) throw new IllegalArgumentException("IPv4 only");
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }

    private static long ipv4ToLong(String ip) throws UnknownHostException {
        return ((long) ipv4ToInt(ip)) & 0xFFFFFFFFL;
    }


    public static IpConfig fromEnv() {
        String envRules = EnvConfig.defaultIpRules();
        String fileRules = EnvConfig.loadAllowFileRulesNormalized();
        Path usedFile = EnvConfig.actualAllowFilePath();

        String merged = (fileRules == null || fileRules.isBlank())
                ? envRules
                : envRules + "|" + fileRules;
        return new IpConfig(merged, usedFile);
    }

    /** 주어진 IPv4가 mergedRules 상 허용되는지 (null/blank는 false) */
    public boolean isAllowed(String ip) {
        return isValidIP(ip, mergedRules);
    }

    /** 규칙 문자열 사전 검증 (유효하지 않은 토큰이 있으면 IllegalArgumentException) */
    public static void assertValidRules(String patternString) {
        if (patternString == null || patternString.isBlank()) return;
        String[] patterns = patternString.split("(?:,|\\||\\R)+");
        int idx = 0;
        for (String raw : patterns) {
            idx++;
            String p = raw == null ? "" : raw.trim();
            if (p.isEmpty()) continue;
            boolean ok = IPRegex.CIDR.matches(p)
                    || IPRegex.RANGE.matches(p)
                    || IPRegex.WILDCARD.matches(p)
                    || IPRegex.IPV4.matches(p);
            if (!ok) throw new AppException(ErrorCode.INVALID_IP_FORMAT, "Invalid rule token at #" + idx + ": [" + p + "]");
        }
    }



    /** IPv6 문자열을 IPv4로 변환 가능한 경우 변환해서 반환. 변환 불가면 null. */
    public static String toIPv4IfPossible(String ip) {
        if (ip == null) return null;
        String s = ip.trim();
        if (s.isEmpty()) return null;
        // IPv4인 경우
        if (!s.contains(":")) return s;
        // IPv6 루프백 → IPv4 루프백
        if ("::1".equals(s) || "0:0:0:0:0:0:0:1".equalsIgnoreCase(s)) return "127.0.0.1";
        // IPv6-mapped IPv4 (::ffff:a.b.c.d)
        Matcher m = IPV6_MAPPED_V4.matcher(s);
        if (m.matches()) return m.group(1);
        // 그 외 순수 IPv6은 변환 불가
        return null;
    }

    /**
     * 요청에서 클라이언트 IPv4를 추출.
     * trustProxyHeaders=true면 X-Forwarded-For / X-Real-IP를 우선 사용.
     * @param req
     * @param trustProxyHeaders
     * @return
     */
    public static String clientIPv4(HttpServletRequest req, boolean trustProxyHeaders) {
        String ip = req.getRemoteAddr();

//        if (trustProxyHeaders) {
//            String xff = req.getHeader("X-Forwarded-For");
//            if (xff != null && !xff.isBlank()) {
//                // "client, proxy1, proxy2" 형태 → 첫 번째 토큰 사용
//                ip = xff.split(",")[0].trim();
//            } else {
//                String xri = req.getHeader("X-Real-IP");
//                if (xri != null && !xri.isBlank()) ip = xri.trim();
//            }
//        }
        String v4 = toIPv4IfPossible(ip);
        return (v4 != null) ? v4 : ip;
    }

    /**
     * 단일 IP / CIDR / 범위 / 와일드카드 중 하나라도 맞으면 true.
     * 일반 IPv6는 미지원(루프백 외).
     */
    private static boolean matches(String ip, String token) {
        if (ip == null || token == null || token.isBlank()) return false;
        // IPv6는 현재 미지원
        if (ip.contains(":")) return false;
        if (IPRegex.CIDR.matches(token)) return isIpInCidr(ip, token);
        if (IPRegex.RANGE.matches(token)) return isIpInRange(ip, token);
        if (IPRegex.WILDCARD.matches(token)) return isIpInWildcard(ip, token);
        if (IPRegex.IPV4.matches(token)) return ip.equals(token);
        return false;
    }


    /**
     * 규칙 문자열(구분자 혼합 허용)을 토큰으로 나누고, IP에 첫 번째로 매칭되는 토큰을 반환.
     * 매칭 없으면 null.
     */
    public static String findMatchedToken(String ip, String patternString) {
        if (patternString == null || patternString.isBlank()) return null;
        List<String> tokens = Arrays.stream(RULE_SEP.split(patternString))
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isEmpty())
                .toList();
        for (String token : tokens) {
            if (matches(ip, token)) return token; // 첫 매칭 토큰 반환
        }
        return null;
    }
}
