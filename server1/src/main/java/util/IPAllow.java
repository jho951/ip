package util;

import constant.IP_REGEX;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class IPAllow {

    private final Path allowFile;

    public IPAllow(Path allowFile) {
        this.allowFile = allowFile;
    }

    public boolean isAllowed(String ip) {
        if (ip == null || ip.isBlank()) return false;
        try {
            if (!Files.exists(allowFile)) return false;
            List<String> lines = Files.readAllLines(allowFile);
            List<long[]> ranges = parseAll(lines);
            long v = ipv4ToLong(ip);
            for (long[] r : ranges) if (v >= r[0] && v <= r[1]) return true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<long[]> parseAll(List<String> lines) {
        List<long[]> out = new ArrayList<>();

        for (String raw : lines) {
            String s = raw.trim();
            if (s.isEmpty() || s.startsWith("#")) continue;

            // (선택) 파이프 구분자 지원
            String[] tokens = s.split("\\|");
            for (String tokenRaw : tokens) {
                String token = tokenRaw.trim();
                if (token.isEmpty()) continue;

                if (IP_REGEX.CIDR.matches(token)) {
                    out.add(cidrToRange(token));
                } else if (IP_REGEX.RANGE.matches(token)) {
                    String[] ab = token.split("[-~]");
                    long a = ipv4ToLong(ab[0].trim());
                    long b = ipv4ToLong(ab[1].trim());
                    out.add(new long[]{Math.min(a, b), Math.max(a, b)});
                } else if (IP_REGEX.WILDCARD.matches(token)) {
                    out.add(wildcardToRange(token));
                } else if (IP_REGEX.IPV4.matches(token)) { // ← 여기 수정
                    long v = ipv4ToLong(token);
                    out.add(new long[]{v, v});
                } else {
                    // 잘못된 라인 무시(로그 권장)
                }
            }
        }
        return out;
    }

    private static long[] cidrToRange(String cidr) {
        String[] parts = cidr.split("/");
        long base = ipv4ToLong(parts[0]);
        int mask = Integer.parseInt(parts[1]);
        if (mask < 0 || mask > 32) throw new IllegalArgumentException("CIDR mask out of range: " + mask);
        long netmask = (mask == 0) ? 0 : (0xFFFFFFFFL << (32 - mask)) & 0xFFFFFFFFL;
        long start = base & netmask;
        long end   = start | (~netmask & 0xFFFFFFFFL);
        return new long[]{start, end};
    }

    private static long[] wildcardToRange(String wc) {
        String[] p = wc.split("\\.");
        long lo = 0, hi = 0;
        for (int i = 0; i < 4; i++) {
            if ("*".equals(p[i])) { lo <<= 8; hi = (hi << 8) | 0xFF; }
            else {
                int v = Integer.parseInt(p[i]);
                lo = (lo << 8) | v; hi = (hi << 8) | v;
            }
        }
        return new long[]{lo, hi};
    }

    private static long ipv4ToLong(String ip) {
        String[] parts = ip.split("\\.");
        return (Long.parseLong(parts[0]) << 24)
                | (Long.parseLong(parts[1]) << 16)
                | (Long.parseLong(parts[2]) << 8)
                |  Long.parseLong(parts[3]);
    }
}
