package com.config;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public final class EnvConfig {
    private EnvConfig() {}

    /**
     * <h5>환경변수를 확인해 값이 없으면 기본값을 반환합니다.</h5>
     * @param envKey 환경 변수 키
     * @param defaultValue 기본값
     * @return 환경 변수 값 null 또는 빈 값이면 기본값
     */
    private static String envOrDefault(String envKey, String defaultValue) {
        String value = System.getenv(envKey);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    /**
     * <h5>IP 범위 구분자를 정규화합니다.</h5>
     * <p>범위 구분자 <code>~</code>를 <code>-</code>로 치환합니다.</p>
     * @param ipRange 원본 IP 문자열
     * @return 정규화된 IP 문자열, null 또는 빈 값이면 그대로 반환
     */
    private static String normalizeRules(String ipRange) {
       return ipRange == null || ipRange.isBlank()? ipRange: ipRange.replaceAll("\\s*~\\s*", "-").trim();
    }

    /**
     * <h5>텍스트 파일 내용을 문자열로 반환합니다.</h5>
     * @param filePath 파일 경로
     * @return 파일 내용 문자열, 파일이 없거나 읽기 실패 시 빈 문자열
     */
    public static String readStringSafe(Path filePath) {
        if (filePath == null) return "";
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * <h5>{@code DEFAULT_IP} 기본 체크 IP를 문자열로 반환합니다.</h5>
     * @return DEFAULT_IP, 없으면 기본값(RFC1918)
     */
    public static String defaultIpRulesRaw() {
        return envOrDefault("DEFAULT_IP", "10.0.0.0~10.255.255.255|172.16.0.0~172.31.255.255|192.168.0.0~192.168.255.255");
    }

    /**
     * <h5>{@code DEFAULT_IP} 규칙 문자열을 정규화(~ → -)합니다.</h5>
     * @return 정규화된 DEFAULT_IP 규칙 문자열
     */
    public static String defaultIpRules() {
        return normalizeRules(defaultIpRulesRaw());
    }

    /**
     * <h5>{@code ALLOW_IP_PATH} 환경변수를 읽어 규칙 파일의 디렉터리 위치를 반환합니다.</h5>
     * <p>기본값은 {@code "Desktop"}입니다.</p>
     * @return 규칙 파일 디렉터리 힌트
     */
    public static String allowDir() {
        return envOrDefault("ALLOW_IP_PATH", "Desktop");
    }

    /**
     * <h5>{@code DEFAULT_FILE_NAME} 환경변수를 읽어 규칙 파일명을 반환합니다.</h5>
     * <p>기본값은 {@code "allow-ip.txt"}입니다.</p>
     *
     * @return 규칙 파일명
     */
    public static String allowFileName() {
        return envOrDefault("DEFAULT_FILE_NAME", "allow-ip.txt");
    }

    /**
     * 허용 IP 규칙 파일의 실제 경로를 탐색합니다.
     * <p>
     * dirHint와 fileName을 기반으로 여러 후보 경로를 만들고,
     * <b>존재하는</b> 첫 번째 경로를 반환합니다. 없으면 {@code null}입니다.
     * </p>
     *
     * <h3>탐색 전략(요약)</h3>
     * <ul>
     * <li>dirHint가 "desktop"이면 우선 {@code ${HOME}/Desktop/fileName}</li>
     * <li>dirHint가 절대경로면 {@code dirHint/fileName}</li>
     * <li>그 외에는 {@code ${CWD}/dirHint/fileName} → {@code ${HOME}/dirHint/fileName} → {@code ${HOME}/Desktop/fileName}</li>
     * <li>마지막으로 항상 {@code ${CWD}/fileName}도 확인</li>
     * </ul>
     *
     * @param dirHint 디렉터리 힌트(ALLOW_IP_PATH)
     * @param fileName 파일명(DEFAULT_FILE_NAME)
     * @return 존재하는 파일의 절대 경로, 없으면 {@code null}
     */
    public static Path resolveAllowFile(String dirHint, String fileName) {
        final String home = System.getProperty("user.home");
        final Path cwd = Paths.get("").toAbsolutePath();

        // 순서 유지 + 중복 제거
        java.util.LinkedHashSet<Path> cands = new java.util.LinkedHashSet<>();

        String hint = dirHint == null ? "" : dirHint.trim();
        String expanded = expandPlaceholders(hint);

        // 1) 먼저 Home 기반(Desktop 포함) 후보를 넣는다
        cands.add(detectDesktop().resolve(fileName));      // Desktop (OneDrive/한글 포함 탐지)
        cands.add(Path.of(home, "Desktop", fileName));     // 일반 Desktop 직결
        cands.add(Path.of(home).resolve(fileName));        // 홈 바로 아래 (예비)

        // 2) 그 다음 CWD(현재 작업 디렉터리)
        cands.add(cwd.resolve(fileName));

        // 3) 마지막에 Env 힌트 적용 (절대/상대/토큰 포함)
        if (!hint.isEmpty()) {
            if (hint.regionMatches(true, 0, "DESKTOP:", 0, "DESKTOP:".length())) {
                String sub = hint.substring("DESKTOP:".length()).replaceAll("^[/\\\\]+", "");
                Path base = sub.isBlank() ? detectDesktop() : detectDesktop().resolve(sub);
                cands.add(base.resolve(fileName));
            } else {
                if (!expanded.isBlank()) {
                    Path p = Paths.get(expanded);
                    if (p.isAbsolute()) {
                        cands.add(p.resolve(fileName));                 // 절대 경로 힌트
                    } else {
                        cands.add(cwd.resolve(expanded).resolve(fileName));     // CWD+힌트
                        cands.add(Path.of(home).resolve(expanded).resolve(fileName)); // HOME+힌트
                    }
                }
            }
        }

        for (Path p : cands) {
            try {
                if (p != null && Files.isRegularFile(p) && Files.isReadable(p)) {
                    return p.toAbsolutePath().normalize();
                }
            } catch (Exception ignore) {}
        }
        return null;
    }


    /**
     * 허용 IP 규칙 파일을 읽어 <b>정규화(~ → -)</b>된 문자열로 반환합니다.
     * <p>파일이 없거나 읽기 실패 시 빈 문자열을 반환합니다.</p>
     *
     * @return 정규화된 규칙 문자열(없으면 빈 문자열)
     */
    public static String loadAllowFileRulesNormalized() {
        Path file = resolveAllowFile(allowDir(), allowFileName());
        String raw = readStringSafe(file);
        return normalizeRules(raw);
    }

    /**
     * 실제 사용 중인 허용 IP 규칙 파일의 절대 경로를 반환합니다.
     *
     * @return 존재하는 규칙 파일의 절대 경로, 없으면 {@code null}
     */
    public static Path actualAllowFilePath() {
        return resolveAllowFile(allowDir(), allowFileName());
    }


    private static String expandPlaceholders(String s) {
        if (s == null || s.isBlank()) return s;

        String home = System.getProperty("user.home");

        // ~, $HOME, ${HOME} → user.home
        s = s.replaceFirst("^~", home);
        s = s.replace("${HOME}", home);
        s = s.replace("$HOME", home);

        // Windows 스타일 %USERPROFILE% / %HOMEPATH% 지원
        String userProfile = System.getenv("USERPROFILE");
        if (userProfile != null && !userProfile.isBlank()) {
            s = s.replace("%USERPROFILE%", userProfile);
        }
        String homePath = System.getenv("HOMEPATH");
        String homeDrive = System.getenv("HOMEDRIVE"); // 보조
        if (homePath != null && !homePath.isBlank() && s.contains("%HOMEPATH%")) {
            String hp = (homeDrive != null ? homeDrive : "") + homePath;
            s = s.replace("%HOMEPATH%", hp);
        }

        // ${VAR} 일반 환경변수 치환 (간단 버전)
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\$\\{([A-Za-z0-9_]+)}")
                .matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String val = System.getenv(key);
            if (val == null) val = ""; // 없으면 빈문자
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(val));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static Path detectDesktop() {
        String home = System.getProperty("user.home");
        List<Path> cands = new ArrayList<>();
        // 일반 Desktop
        cands.add(Path.of(home, "Desktop"));
        // 한국어 윈도우
        cands.add(Path.of(home, "바탕 화면"));
        // OneDrive Desktop (회사/학교 계정일 수 있음)
        String oneDrive = System.getenv("OneDrive");
        if (oneDrive != null && !oneDrive.isBlank()) {
            cands.add(Path.of(oneDrive, "Desktop"));
        }
        for (Path p : cands) {
            try {
                if (Files.exists(p)) return p.toAbsolutePath().normalize();
            } catch (Exception ignore) {}
        }
        // 없으면 홈 경로로 폴백(최소한 존재하는 디렉터리)
        return Path.of(home).toAbsolutePath().normalize();
    }



}