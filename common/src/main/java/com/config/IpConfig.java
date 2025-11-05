package com.config;

import com.constant.ErrorCode;
import com.constant.IPRegex;
import com.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IpConfig
 *  - EnvConfig로부터 규칙을 읽고(~ → - 정규화, 파일 병합)
 *  - mergedRules 보관
 *  - isAllowed(ip) 로 직접 검사 (CIDR / Range / Wildcard / Single IPv4)
 *  - assertValidRules(...) 로 사전 검증 가능
 */
public final class IpConfig {
    // 문자열을 분리하는 정규식 패턴
    private static final Pattern SEP = Pattern.compile("(?:,|\\||\\R|;)+");

    private final String mergedRules;
    // allow-ip 파일 경로
    private final Path allowFile;

    public String mergedRules() { return mergedRules; }

    public Path allowFile() { return allowFile; }

    private IpConfig(String mergedRules, Path allowFile) {
        this.mergedRules = mergedRules;
        this.allowFile = allowFile;
    }

    /**
     *
     * @param ipToValidate
     * @param patternString
     * @return
     */
    private static boolean isValidIP(String ipToValidate, String patternString) {
        if (ipToValidate == null || ipToValidate.isBlank()) return false;
        if (patternString == null || patternString.isBlank()) return false;

        String[] patterns = SEP.split(patternString);
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
            long endIp   = ipv4ToLong(parts[1].trim());
            long check   = ipv4ToLong(ip);
            long lo = Math.min(startIp, endIp);
            long hi = Math.max(startIp, endIp);
            return check >= lo && check <= hi;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isIpInWildcard(String ip, String wildcardPattern) {
        // 예: 174.30.1.*  →  ^174\.30\.1\.\d{1,3}$
        String regex = "^" + Pattern.quote(wildcardPattern)
                .replace("\\*", "\\E\\\\d{1,3}\\Q") + "$";
        return Pattern.compile(regex).matcher(ip).matches();
    }

    private static int ipv4ToInt(String ip) throws UnknownHostException {
        byte[] b = InetAddress.getByName(ip).getAddress();
        if (b.length != 4) throw new IllegalArgumentException("IPv4 only");
        return ((b[0] & 0xFF) << 24)
                | ((b[1] & 0xFF) << 16)
                | ((b[2] & 0xFF) << 8)
                |  (b[3] & 0xFF);
    }

    private static long ipv4ToLong(String ip) throws UnknownHostException {
        return ((long) ipv4ToInt(ip)) & 0xFFFFFFFFL;
    }


    /** ENV와 파일을 읽어 최종 병합; 값이 없어도 예외 없이 동작 */
    public static IpConfig fromEnv() {
        // 1) DEFAULT_IP (정규화된 문자열: ~ → -)
        String envRules = EnvConfig.defaultIpRules();

        // 2) allow-ip 파일 로드(+정규화)
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

    private static final Pattern IPV6_MAPPED_V4 =
            Pattern.compile("^::ffff:(\\d+\\.\\d+\\.\\d+\\.\\d+)$", Pattern.CASE_INSENSITIVE);

    /** IPv6 문자열을 IPv4로 변환 가능한 경우 변환해서 반환. 변환 불가면 null. */
    public static String toIPv4IfPossible(String ip) {
        if (ip == null) return null;
        String s = ip.trim();
        if (s.isEmpty()) return null;

        // 이미 IPv4
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
     * (프록시가 없으면 false 권장)
     * @param req
     * @param trustProxyHeaders
     * @return
     */
    public static String clientIPv4(HttpServletRequest req, boolean trustProxyHeaders) {
        String ip = null;

        if (trustProxyHeaders) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                // "client, proxy1, proxy2" 형태 → 첫 번째 토큰 사용
                ip = xff.split(",")[0].trim();
            } else {
                String xri = req.getHeader("X-Real-IP");
                if (xri != null && !xri.isBlank()) ip = xri.trim();
            }
        }

        if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();

        String v4 = toIPv4IfPossible(ip);
        return (v4 != null) ? v4 : ip;
    }


}
