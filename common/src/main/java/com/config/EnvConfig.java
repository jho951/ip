package com.config;

import com.constant.ErrorCode;
import com.exception.AppException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import static com.constant.RegexConst.RULE_ENV;

public final class EnvConfig {
    private EnvConfig() {}

    private static final String DESKTOP_PREFIX = "DESKTOP:";

    /**
     * env 공백 제거
     * @param envKey env 키
     * @return 없으면 null, 있으면 공백제거 후 env 값 추출
     */
    public static String env(String envKey) {
        if (envKey == null) return null;
        String envValue = System.getenv(envKey).trim();
        return envValue.isEmpty() ? null :envValue;
    }

    /**
     * 기본 경로
     * @return detectDesktop();
     */
    public static Path desktop() {
        return detectDesktop();
    }

    /**
     * 루트 경로 반환
     * @param envKey 환경설정 키
     * @param defaultPath 기본 경로
     * @return root.toAbsolutePath().normalize() 해당 경로 정규화
     */
    public static Path rootPath(String envKey, Path defaultPath) {
        String envValue = env(envKey);
        Path root = (envValue != null) ? Paths.get(envValue) : detectDesktop();
        return root.toAbsolutePath().normalize();
    }
    
    /**
     * IP 범위 구분자 정규화(~ → -)
     * @param ipRange ip 범위를 물결로 구분한 문자열
     * @return ipRange || normalizeIpRange 빈문자열이거나 null인 경우 ipRange 있으면 normalizeIpRange
     */
    public static String normalizeRules(String ipRange) {
        String normalizeIpRange =ipRange.replaceAll("\\s*~\\s*", "-").trim();
        return (ipRange == null || ipRange.isBlank()) ? ipRange : normalizeIpRange;
    }
    
    /**
     * 텍스트 파일 내용 읽기
     * @param filePath 파일 경로
     * @return 텍스트 파일 내용 문자열
     */
    public static String readStringSafe(Path filePath) {
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            throw new AppException(ErrorCode.BAD_REQUEST_READ_FILE,"파일 읽기 에러");
        }
    }

    /** DEFAULT_IP 원본 */
    public static String defaultIpRulesRaw() {
        return envOrDefault(
                "DEFAULT_IP",
                "10.0.0.0~10.255.255.255|172.16.0.0~172.31.255.255|192.168.0.0~192.168.255.255"
        );
    }

    /** DEFAULT_IP 정규화 */
    public static String defaultIpRules() {
        return normalizeRules(defaultIpRulesRaw());
    }

    /** 규칙 파일 디렉터리 힌트 */
    public static String allowDir() {
        return envOrDefault("ALLOW_IP_PATH", "Desktop");
    }

    /** 규칙 파일명 */
    public static String allowFileName() {
        return envOrDefault("DEFAULT_FILE_NAME", "allow-ip.txt");
    }

    /**
     * 허용 IP 규칙 파일의 실제 경로 탐색
     * (중복 후보 정리: detectDesktop()가 일반 Desktop 포함하므로 별도 Desktop 후보 제거)
     */
    public static Path resolveAllowFile(String dirHint, String fileName) {
        final String home = System.getProperty("user.home");
        final Path cwd = Paths.get("").toAbsolutePath();

        LinkedHashSet<Path> cands = new LinkedHashSet<>();

        String hint = (dirHint == null) ? "" : dirHint.trim();
        String expanded = expandPlaceholders(hint);

        // 1) Desktop(OneDrive/한글 포함 탐지) + HOME 바로 아래
        cands.add(detectDesktop().resolve(fileName));
        cands.add(Path.of(home).resolve(fileName));

        // 2) CWD
        cands.add(cwd.resolve(fileName));

        // 3) Env 힌트 적용
        if (!hint.isEmpty()) {
            if (hint.regionMatches(true, 0, DESKTOP_PREFIX, 0, DESKTOP_PREFIX.length())) {
                String sub = hint.substring(DESKTOP_PREFIX.length()).replaceAll("^[/\\\\]+", "");
                Path base = sub.isBlank() ? detectDesktop() : detectDesktop().resolve(sub);
                cands.add(base.resolve(fileName));
            } else if (!expanded.isBlank()) {
                Path p = Paths.get(expanded);
                if (p.isAbsolute()) {
                    cands.add(p.resolve(fileName));
                } else {
                    cands.add(cwd.resolve(expanded).resolve(fileName));
                    cands.add(Path.of(home).resolve(expanded).resolve(fileName));
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

    /** 규칙 파일 로드(+정규화) */
    public static String loadAllowFileRulesNormalized() {
        Path filePath = resolveAllowFile(allowDir(), allowFileName());
        String raw = readStringSafe(filePath);
        return normalizeRules(raw);
    }

    /** 실제 사용 중 파일 경로 */
    public static Path actualAllowFilePath() {
        return resolveAllowFile(allowDir(), allowFileName());
    }

    /** 합성 규칙: DEFAULT_IP | 파일규칙 */
    public static String combinedIpRulesNormalized() {
        String def = defaultIpRules();
        String file = loadAllowFileRulesNormalized();
        if (file == null || file.isBlank()) return def;
        if (def == null || def.isBlank()) return file;
        return def + "|" + file;
    }




    private static String expandPlaceholders(String s) {
        if (s == null || s.isBlank()) return s;

        String home = System.getProperty("user.home");

        // ~, $HOME, ${HOME}
        s = s.replaceFirst("^~", home);
        s = s.replace("${HOME}", home);
        s = s.replace("$HOME", home);

        // Windows 환경 변수
        String userProfile = System.getenv("USERPROFILE");
        if (userProfile != null && !userProfile.isBlank()) {
            s = s.replace("%USERPROFILE%", userProfile);
        }
        String homePath = System.getenv("HOMEPATH");
        String homeDrive = System.getenv("HOMEDRIVE");
        if (homePath != null && !homePath.isBlank() && s.contains("%HOMEPATH%")) {
            String hp = (homeDrive != null ? homeDrive : "") + homePath;
            s = s.replace("%HOMEPATH%", hp);
        }

        // ${VAR} 일반 치환
        Matcher m = RULE_ENV.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String val = System.getenv(key);
            if (val == null) val = "";
            m.appendReplacement(out, Matcher.quoteReplacement(val));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** OS/언어/OneDrive를 고려한 Desktop 디렉터리 탐색 */
    private static Path detectDesktop() {
        String home = System.getProperty("user.home");
        List<Path> cands = new ArrayList<>();
        cands.add(Path.of(home, "Desktop"));     // 일반 Desktop
        cands.add(Path.of(home, "바탕 화면"));       // 한국어 Windows
        String oneDrive = System.getenv("OneDrive");
        if (oneDrive != null && !oneDrive.isBlank()) {
            cands.add(Path.of(oneDrive, "Desktop")); // OneDrive Desktop
        }
        for (Path p : cands) {
            try {
                if (Files.exists(p)) return p.toAbsolutePath().normalize();
            } catch (Exception ignore) {}
        }
        // 없으면 홈 경로로 폴백
        return Path.of(home).toAbsolutePath().normalize();
    }


    /** DEFAULT_SERVER1/2가 URL 형태일 때 포트 파싱. 없으면 fallback. */
    public static int portOf(String envUrl, int fallback) {
        try {
            if (envUrl == null || envUrl.isBlank()) return fallback;
            URI u = URI.create(envUrl.trim());
            int p = u.getPort();
            return p > 0 ? p : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }


    /**
     * env + 기본값 (env() 재사용)
     * @param envKey 환경설정 키
     * @param defaultValue 환경설정 값이 없을경우 대체
     * @return envValue | defaultValue 값이 있으면 envValue 없으면 defaultValue
     */
    private static String envOrDefault(String envKey, String defaultValue) {
        String envValue = env(envKey);
        return (envValue == null) ? defaultValue : envValue;
    }
}
