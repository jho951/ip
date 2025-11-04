package com.config;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public final class EnvConfig {
    private EnvConfig() {
    }

    /**
     * null/blank면 기본값, 끝의 세미콜론(;)은 실수로 붙였을 수 있어 제거
     */
    private static String envOrDefault(String key, String def) {
        String v = System.getenv(key);
        if (v == null) return def;
        v = v.trim();
        if (v.isBlank()) return def;
        else return v;
    }

    /**
     * 규칙 문자열 정규화: 범위구분자 '~' → '-'
     */
    private static String normalizeRules(String s) {
        if (s == null || s.isBlank()) return s;
        return s.replaceAll("s*~", "-").trim();
    }

    /**
     * 안전 파일 읽기(없으면 빈 문자열)
     */
    public static String readStringSafe(Path p) {
        if (p == null) return "";
        try {
            return Files.readString(p);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * DEFAULT_IP – 원본 그대로(~ 포함 가능)
     */
    public static String defaultIpRulesRaw() {
        return envOrDefault("DEFAULT_IP", "10.0.0.0~10.255.255.255|172.16.0.0~172.31.255.255|192.168.0.0~192.168.255.255");
    }

    /**
     * DEFAULT_IP 정규화(~ → -)
     */
    public static String defaultIpRules() {
        return normalizeRules(defaultIpRulesRaw());
    }

    /**
     * ALLOW_IP_PATH(디렉터리 위치)
     */
    public static String allowDir() {
        return envOrDefault("ALLOW_IP_PATH", "Desktop");
    }

    /**
     * DEFAULT_FILE_NAME(파일명)
     */
    public static String allowFileName() {
        return envOrDefault("DEFAULT_FILE_NAME", "allow-ip.txt");
    }

    /**
     * allow-ip 파일 실제 경로 탐색(존재하는 첫 후보 반환, 없으면 null)
     * 우선순위:
     * - ${HOME}/Desktop/fileName → CWD/dirHint/fileName → ${HOME}/dirHint/fileName → CWD/fileName
     * - dirHint/fileName → CWD/fileName
     * - CWD/dirHint/fileName → ${HOME}/dirHint/fileName → ${HOME}/Desktop/fileName → CWD/fileName
     */
    public static Path resolveAllowFile(String dirHint, String fileName) {
        final String home = System.getProperty("user.home");
        final Path cwd = Paths.get("").toAbsolutePath();

        List<Path> cands = new ArrayList<>();
        if (dirHint != null && !dirHint.isBlank()) {
            if (dirHint.equalsIgnoreCase("desktop")) {
                cands.add(Paths.get(home, "Desktop", fileName));
                cands.add(cwd.resolve(dirHint).resolve(fileName));
                cands.add(Paths.get(home).resolve(dirHint).resolve(fileName));
            } else {
                Path hint = Paths.get(dirHint);
                if (hint.isAbsolute()) {
                    cands.add(hint.resolve(fileName));
                } else {
                    cands.add(cwd.resolve(dirHint).resolve(fileName));
                    cands.add(Paths.get(home).resolve(dirHint).resolve(fileName));
                    cands.add(Paths.get(home, "Desktop", fileName));
                }
            }
        } else {
            cands.add(Paths.get(home, "Desktop", fileName));
        }
        cands.add(cwd.resolve(fileName));

        for (Path p : cands) {
            try {
                if (p != null && Files.exists(p)) return p.toAbsolutePath().normalize();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * allow-ip 파일 내용 읽기(+정규화)
     */
    public static String loadAllowFileRulesNormalized() {
        Path file = resolveAllowFile(allowDir(), allowFileName());
        String raw = readStringSafe(file);
        return normalizeRules(raw);
    }

    /**
     * 실제 사용된 allow-ip 파일 경로(없으면 null)
     */
    public static Path actualAllowFilePath() {
        return resolveAllowFile(allowDir(), allowFileName());
    }
}
