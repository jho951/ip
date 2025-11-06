package com.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EnvConfig 단위 테스트
 * - System.getenv()는 런타임 수정이 어렵기 때문에, allowDir()/allowFileName() 기반
 *   메서드(loadAllowFileRulesNormalized, combinedIpRulesNormalized)는
 *   여기서는 직접 테스트하지 않고, resolveAllowFile(...)의 동작을 다양한 힌트로 검증함.
 */
class EnvConfigTest {

    @TempDir
    Path temp; // JUnit이 매 테스트마다 깨끗한 임시 디렉터리 제공

    /**
     * <h5>normalizeRules 메서드 테스트</h5>
     * <p>테스트 내용</p>
     * <ul>
     *     <li>공백과 ~의 형태 변환</li>
     *     <li>~의 형태 변환</li>
     *     <li>인자가 null일 때</li>
     *     <li>인자가 빈 값일 때</li>
     * </ul>
     */
    @Test
    void normalizeRulesTest() {
        assertEquals("10.0.0.1-10.0.0.10", EnvConfig.normalizeRules("10.0.0.1 ~ 10.0.0.10"));
        assertEquals("10.0.0.1-10.0.0.10", EnvConfig.normalizeRules("10.0.0.1~10.0.0.10"));
        assertNull(EnvConfig.normalizeRules(null));
        assertEquals("", EnvConfig.normalizeRules(""));
    }

    /**
     * <h5>readStringSafe 메서드 테스트</h5>
     * <p>테스트 내용</p>
     * <ul>
     *     <li>공백과 ~의 형태 변환</li>
     *     <li>~의 형태 변환</li>
     *     <li>인자가 null일 때</li>
     *     <li>인자가 빈 값일 때</li>
     * </ul>
     */
    @Test
    void readStringSafeTest()throws IOException {
        assertEquals("", EnvConfig.readStringSafe(null));

        Path missing = temp.resolve("nope.txt");
        assertEquals("", EnvConfig.readStringSafe(missing));

        Path file = temp.resolve("allow-ip.txt");
        Files.writeString(file, "192.168.0.0/16\n10.0.0.0/8\n");
        assertEquals("192.168.0.0/16\n10.0.0.0/8\n", EnvConfig.readStringSafe(file));
    }

    /* ========== resolveRoot (env 미사용 시 기본 경로 사용) ========== */

    @Test
    void resolveRootTest() {
        Path def = temp.resolve("fallback");
        Path resolved = EnvConfig.rootPath("ENV_KEY_NOT_SET", def);
        assertEquals(def.toAbsolutePath().normalize(), resolved);
    }

    /* ========== resolveAllowFile: 절대 경로 힌트 ========== */

    @Test
    void resolveAllowFileTest() throws IOException {
        Path dir = Files.createDirectories(temp.resolve("abs-dir"));
        Path f = dir.resolve("allow-ip.txt");
        Files.writeString(f, "10.0.0.0/8");

        Path found = EnvConfig.resolveAllowFile(dir.toString(), "allow-ip.txt");
        assertNotNull(found);
        assertEquals(f.toAbsolutePath().normalize(), found);
    }

    /* ========== resolveAllowFile: 상대 경로 힌트 (user.dir를 임시 변경) ========== */

    @Test
    void resolveAllowFileTest2() throws IOException {
        // 상대경로 기준을 바꾸기 위해 user.dir 임시 변경
        String origUserDir = System.getProperty("user.dir");
        Path newCwd = Files.createDirectories(temp.resolve("cwd"));
        System.setProperty("user.dir", newCwd.toString());
        try {
            Path hintedDir = newCwd.resolve("sub");
            Files.createDirectories(hintedDir);
            Path f = hintedDir.resolve("allow-ip.txt");
            Files.writeString(f, "172.16.0.0/12");

            // 상대 힌트 "sub" + 파일명
            Path found = EnvConfig.resolveAllowFile("sub", "allow-ip.txt");
            assertNotNull(found);
            assertEquals(f.toAbsolutePath().normalize(), found);
        } finally {
            System.setProperty("user.dir", origUserDir);
        }
    }

    /* ========== resolveAllowFile: 절대 경로 + 파일명 다르게 ========== */

    @Test
    void resolveAllowFile_customFileName_alsoWorks() throws IOException {
        Path dir = Files.createDirectories(temp.resolve("dir2"));
        Path f = dir.resolve("my-allow.txt");
        Files.writeString(f, "192.168.0.0/16");

        Path found = EnvConfig.resolveAllowFile(dir.toString(), "my-allow.txt");
        assertNotNull(found);
        assertEquals(f.toAbsolutePath().normalize(), found);
    }

    /* ========== loadAllowFileRulesNormalized: 간접 검증 ==========
     * 이 메서드는 allowDir()/allowFileName() (환경 변수 의존)을 사용하므로 직접 테스트가 까다롭다.
     * 대신 resolveAllowFile + readStringSafe + normalizeRules의 조합으로 간접 시나리오 검증.
     */
    @Test
    void normalize_then_read_behavesAsExpected() throws IOException {
        Path dir = Files.createDirectories(temp.resolve("rules"));
        Path f = dir.resolve("allow-ip.txt");
        Files.writeString(f, "10.0.0.1 ~ 10.0.0.10|192.168.0.*");

        Path found = EnvConfig.resolveAllowFile(dir.toString(), "allow-ip.txt");
        assertNotNull(found);

        String raw = EnvConfig.readStringSafe(found);
        assertEquals("10.0.0.1 ~ 10.0.0.10|192.168.0.*", raw);

        String norm = EnvConfig.normalizeRules(raw);
        assertEquals("10.0.0.1-10.0.0.10|192.168.0.*", norm);
    }


    @Test
    void defaultIpRules_hasRfc1918_whenEnvMissing() {
        // 환경변수가 없다면 이 기본 문자열을 반환하도록 설계됨
        String def = EnvConfig.defaultIpRules();
        assertNotNull(def);
        // 틸드가 들어있지 않도록 정규화 확인
        assertFalse(def.contains("~"));
        // 최소한 세 구간은 포함하는지 가볍게 체크
        List<String> parts = List.of(def.split("\\|"));
        assertTrue(parts.size() >= 3);
    }
}
