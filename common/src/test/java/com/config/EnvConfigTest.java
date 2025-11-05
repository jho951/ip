package com.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


class EnvConfigTest {
    // 테스트 실행 전/후 환경 변수를 관리하기 위한 맵
    private static final Map<String, String> originalEnv = new HashMap<>();

    // 테스트를 위한 임시 디렉토리 (JUnit 5 제공)
    @TempDir
    static Path tempDir;

    // 테스트에서 사용할 임시 파일 경로
    private static Path homeDir;
    private static Path desktopDir;
    private static Path cwd;

    // 테스트 파일명
    private static final String TEST_FILENAME = "test-allow-ip.txt";
    private static final String DEFAULT_CONTENT = "1.1.1.1~1.1.1.10";
    private static final String NORMALIZED_CONTENT = "1.1.1.1-1.1.1.10";


    /**
     * 모든 테스트 전에 실행: 환경 변수 백업 및 기본 경로 설정
     */
    @BeforeAll
    static void setupAll() {
        // user.home 및 cwd 경로 설정
        homeDir = Paths.get(System.getProperty("user.home"));
        desktopDir = homeDir.resolve("Desktop");
        cwd = Paths.get("").toAbsolutePath();

        // Desktop 디렉토리가 없으면 생성 (테스트 환경에 따라 필요)
        try {
            Files.createDirectories(desktopDir);
        } catch (IOException e) {
            System.err.println("Desktop directory creation failed, tests might be inconsistent: " + e.getMessage());
        }

        // 테스트에 사용될 환경 변수들을 미리 백업
        backupEnv("DEFAULT_IP");
        backupEnv("ALLOW_IP_PATH");
        backupEnv("DEFAULT_FILE_NAME");
    }

    /**
     * 모든 테스트 후에 실행: 환경 변수 복원
     */
    @AfterAll
    static void tearDownAll() {
        // 백업된 환경 변수를 복원
        restoreEnv("DEFAULT_IP");
        restoreEnv("ALLOW_IP_PATH");
        restoreEnv("DEFAULT_FILE_NAME");
    }

    /**
     * 각 테스트 전에 실행: 환경 변수 초기화 (테스트 간의 독립성 보장)
     */
    @BeforeEach
    void setup() {
        // 테스트에서 사용할 환경 변수들을 초기화 (시스템 환경 변수에 직접 접근하는 방식은 복잡하므로,
        // EnvConfig에서 사용하는 System.getenv()를 간접적으로 제어하는 것은 어려움.
        // 대신, 각 테스트에서 setEnv()와 restoreEnv()를 호출한다고 가정하고 테스트 진행)

        // 여기서는 명시적으로 테스트를 위해 환경 변수를 unset 한다고 가정
        // 실제로는 Reflection 등을 사용해야 하지만, 여기서는 default 값 테스트에 중점을 둠.
    }

    // 환경 변수 백업 헬퍼
    private static void backupEnv(String key) {
        if (System.getenv(key) != null) {
            originalEnv.put(key, System.getenv(key));
        } else {
            originalEnv.put(key, null);
        }
    }

    // 환경 변수 복원 헬퍼
    private static void restoreEnv(String key) {
        // 실제 환경 변수를 변경할 수 있는 권한이 없으므로, 이 메서드는 실제 시스템 환경 변수를 변경하지 않음.
        // 이는 테스트 환경 설정에 따라 달라질 수 있음. (ex: System.clearProperty, setProperty 대신 System.getenv()를 사용하기 때문)
        // JUnit 5 Extension을 사용하여 환경 변수를 Mocking 하는 것이 이상적임.
    }

    // 임시 파일 생성 헬퍼
    private Path createTempFile(Path dir, String fileName, String content) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }

    // 임시 파일 및 디렉토리 정리 헬퍼
    private void cleanUpTempPaths(Path... paths) {
        for (Path p : paths) {
            try {
                if (Files.exists(p)) {
                    if (Files.isDirectory(p)) {
                        Files.walk(p)
                                .sorted((a, b) -> b.compareTo(a)) // 역순으로 정렬하여 깊은 곳부터 삭제
                                .forEach(path -> {
                                    try {
                                        Files.delete(path);
                                    } catch (IOException e) {
                                        // 무시
                                    }
                                });
                    } else {
                        Files.delete(p);
                    }
                }
            } catch (IOException e) {
                // 무시
            }
        }
    }

    // --- 테스트 메서드 시작 ---

    @Test
    void testEnvOrDefault_DefaultValue() {
        // 환경 변수가 설정되지 않았을 때 기본값 반환 확인
        String result = EnvConfig.envOrDefault("NON_EXISTENT_VAR", "default_val");
        assertEquals("default_val", result);
    }

    @Test
    void testNormalizeRules() {
        // '~'를 '-'로 정규화 확인
        String raw = "192.168.0.0~192.168.255.255 | 10.0.0.0~ 10.1.1.1";
        String expected = "192.168.0.0-192.168.255.255|10.0.0.0-10.1.1.1"; // 's*~' 정규식에 의해 공백도 제거
        assertEquals(expected, EnvConfig.normalizeRules(raw));

        // null 또는 빈 문자열 처리 확인
        assertNull(EnvConfig.normalizeRules(null));
        assertEquals("", EnvConfig.normalizeRules(""));
    }

    @Test
    void testReadStringSafe() throws IOException {
        // 파일 읽기 성공
        Path file = Files.createTempFile(tempDir, "read-test", ".txt");
        Files.writeString(file, "test content");
        assertEquals("test content", EnvConfig.readStringSafe(file));

        // 파일 없음 (IOException 발생) -> 빈 문자열 반환 확인
        Path nonExistent = tempDir.resolve("non-existent.txt");
        assertEquals("", EnvConfig.readStringSafe(nonExistent));
    }

    @Test
    void testDefaultIpRules_RawAndNormalized() {
        // 환경 변수 설정이 없을 때 기본값 확인
        String raw = EnvConfig.defaultIpRulesRaw();
        String expectedRaw = "10.0.0.0~10.255.255.255|172.16.0.0~172.31.255.255|192.168.0.0~192.168.255.255";
        assertEquals(expectedRaw, raw);

        // 정규화된 값 확인
        String normalized = EnvConfig.defaultIpRules();
        String expectedNormalized = "10.0.0.0-10.255.255.255|172.16.0.0-172.31.255.255|192.168.0.0-192.168.255.255";
        assertEquals(expectedNormalized, normalized);
    }

    @Test
    void testAllowDirAndAllowFileName_Default() {
        // 환경 변수 설정이 없을 때 기본값 확인
        assertEquals("Desktop", EnvConfig.allowDir());
        assertEquals("allow-ip.txt", EnvConfig.allowFileName());
    }

    @Test
    void testResolveAllowFile_Preference1_HomeDesktop() throws IOException {
        // 가정: Home/Desktop/fileName 만 존재
        cleanUpTempPaths(cwd.resolve(TEST_FILENAME));
        Path expectedFile = createTempFile(desktopDir, TEST_FILENAME, "test1");

        Path result = EnvConfig.resolveAllowFile("Desktop", TEST_FILENAME);
        assertEquals(expectedFile.toAbsolutePath().normalize(), result);
        cleanUpTempPaths(expectedFile);
    }

    @Test
    void testResolveAllowFile_Preference2_CwdDirHint() throws IOException {
        // 가정: CWD/dirHint/fileName 만 존재
        Path dirHintPath = cwd.resolve("config_dir");
        cleanUpTemp