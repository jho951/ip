// src/test/java/com/config/EnvConfigTest.java
package com.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnvConfigTest {

    @TempDir
    Path temp; // JUnit이 테스트마다 자동 생성/삭제하는 임시 디렉터리

    // ---- 1) 기본 IP 정규화(~ -> -) 확인 ----
    @Test
    void defaultIpRules_shouldNormalizeTildeToDash() {
        // DEFAULT_IP env가 없는 경우 EnvConfig의 기본 문자열에 ~가 포함되어 있어야 하며
        // defaultIpRules()는 ~를 -로 치환해야 한다.
        String normalized = EnvConfig.defaultIpRules();
        assertTrue(normalized.contains("10.0.0.0-10.255.255.255"));
        assertTrue(normalized.contains("172.16.0.0-172.31.255.255"));
        assertTrue(normalized.contains("192.168.0.0-192.168.255.255"));
        assertFalse(normalized.contains("~"), "Normalized string must not contain '~'");
    }

    // ---- 2) 파일이 없을 때 readStringSafe는 빈 문자열 ----
    @Test
    void readStringSafe_missingFile_returnsEmpty() {
        Path missing = temp.resolve("no-file-here.txt");
        String s = EnvConfig.readStringSafe(missing);
        assertEquals("", s);
    }

    // ---- 3) 절대경로 힌트: resolveAllowFile가 정확히 찾는지 ----
    @Test
    void resolveAllowFile_absoluteDir_findsFile() throws IOException {
        String fileName = "allow-ip.txt";
        Path dir = Files.createDirectories(temp.resolve("absDir"));
        Path f = dir.resolve(fileName);
        Files.writeString(f, "172.30.1.10~173.30.1.45");

        Path found = EnvConfig.resolveAllowFile(dir.toString(), fileName);
        assertNotNull(found);
        assertEquals(f.toAbsolutePath().normalize(), found);
    }

    // ---- 4) 현재 작업 디렉터리 기준 상대 경로 힌트 ----
    @Test
    void resolveAllowFile_relativeToCwd_findsFile() throws IOException {
        // CWD 하위에 임시 폴더/파일을 만들어서 상대 힌트가 작동하는지 확인
        String fileName = "allow-ip.txt";
        Path cwd = Paths.get("").toAbsolutePath();
        Path relDir = cwd.resolve("envcfg_test_rel_" + System.nanoTime());
        Files.createDirectories(relDir);
        Path f = relDir.resolve(fileName);
        Files.writeString(f, "174.30.1.*");

        Path found = EnvConfig.resolveAllowFile(relDir.getFileName().toString(), fileName);
        assertNotNull(found);
        assertEquals(f.toAbsolutePath().normalize(), found);

        // cleanup
        Files.deleteIfExists(f);
        Files.deleteIfExists(relDir);
    }

    // ---- 5) ${HOME} 플레이스홀더 치환 확인 ----
    @Test
    void resolveAllowFile_homePlaceholder_findsFile() throws IOException {
        String home = System.getProperty("user.home");
        String fileName = "allow-ip.txt";
        Path sub = Paths.get(home, "envcfg_test_home_" + System.nanoTime());
        Files.createDirectories(sub);
        Path f = sub.resolve(fileName);
        Files.writeString(f, "10.50.0.0/16");

        String hint = "${HOME}/" + sub.getFileName(); // expandPlaceholders 대상
        Path found = EnvConfig.resolveAllowFile(hint, fileName);
        assertNotNull(found);
        assertEquals(f.toAbsolutePath().normalize(), found);

        // cleanup
        Files.deleteIfExists(f);
        Files.deleteIfExists(sub);
    }

    // ---- 6) DESKTOP: 서브폴더 토큰 확인 ----
    @Test
    void resolveAllowFile_desktopTokenWithSubdir_findsFile() throws IOException {
        String fileName = "allow-ip.txt";
        Path desktopBase = detectDesktopLike();
        Path sub = desktopBase.resolve("envcfg_test_desktop_" + System.nanoTime());
        Files.createDirectories(sub);
        Path f = sub.resolve(fileName);
        Files.writeString(f, "203.0.113.77");

        Path found = EnvConfig.resolveAllowFile("DESKTOP:" + sub.getFileName(), fileName);
        assertNotNull(found);
        assertEquals(f.toAbsolutePath().normalize(), found);

        // cleanup
        Files.deleteIfExists(f);
        Files.deleteIfExists(sub);
    }

    // ---- 7) resolveAllowFile가 없을 때 null 반환 ----
    @Test
    void resolveAllowFile_returnsNullWhenAllCandidatesMissing() {
        Path found = EnvConfig.resolveAllowFile("some/nowhere/" + System.nanoTime(), "nope.txt");
        // 후보 어느 곳에도 파일이 없으면 null이 맞다.
        assertNull(found);
    }

    // ---- 8) loadAllowFileRulesNormalized는 직접 경로 제어가 어려우므로
    // resolveAllowFile + readStringSafe + 정규화 조합을 별도로 검증 ----
    @Test
    void readAndNormalize_pipelineWorks() throws IOException {
        String fileName = "allow-ip.txt";
        Path dir = Files.createDirectories(temp.resolve("normDir"));
        Path f = dir.resolve(fileName);
        Files.writeString(f, "172.30.1.10 ~ 173.30.1.45 | 174.30.1.*");

        Path found = EnvConfig.resolveAllowFile(dir.toString(), fileName);
        assertNotNull(found);

        String raw = EnvConfig.readStringSafe(found);
        assertTrue(raw.contains("~"), "raw must contain '~' before normalization");

        // normalizeRules는 private이므로 defaultIpRules()에서 간접 확인했지만
        // 여기서는 간단히 수동 치환으로 기대값을 비교(파이프라인 개념 검증)
        String normalized = raw.replaceAll("\\s*~\\s*", "-").trim();
        assertTrue(normalized.contains("172.30.1.10-173.30.1.45"));
    }

    // ===== 테스트 보조: EnvConfig.detectDesktop()의 동작을 안전하게 근사 =====
    private static Path detectDesktopLike() {
        String home = System.getProperty("user.home");
        List<Path> cands = new ArrayList<>();
        cands.add(Paths.get(home, "Desktop"));
        cands.add(Paths.get(home, "바탕 화면"));
        String oneDrive = System.getenv("OneDrive");
        if (oneDrive != null && !oneDrive.isBlank()) {
            cands.add(Paths.get(oneDrive, "Desktop"));
        }
        for (Path p : cands) {
            if (Files.exists(p)) return p.toAbsolutePath().normalize();
        }
        // 없으면 홈 폴더 반환(EnvConfig.detectDesktop과 동일 폴백)
        return Paths.get(home).toAbsolutePath().normalize();
    }
}
