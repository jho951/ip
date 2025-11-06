package com.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileConfigTest {

    @TempDir
    Path tempRoot;


    @Test
    @DisplayName("sanitizeName: null -> 빈 문자열")
    void sanitizeName_null_returnsEmpty() {
        assertEquals("", FileConfig.sanitizeName(null));
    }

    @Test
    @DisplayName("sanitizeName: 역슬래시->슬래시, .. 제거, 개행 제거, 앞뒤 슬래시 제거")
    void sanitizeName_basic() {
        String raw = "\\..//up\\loads/\n";
        String sanitized = FileConfig.sanitizeName(raw);
        assertEquals("uploads", sanitized);
    }

    @Test
    @DisplayName("sanitizeName: 시작/끝 슬래시 반복 제거")
    void sanitizeName_stripLeadingTrailingSlashes() {
        assertEquals("a/b", FileConfig.sanitizeName("///a/b///"));
    }

    // ---------- resolveBase ----------

    @Test
    @DisplayName("resolveBase: sub가 비면 root 반환")
    void resolveBase_blankSub_returnsRoot() {
        Path base = FileConfig.resolveBase(tempRoot, "   ");
        assertEquals(tempRoot.normalize(), base);
    }

    @Test
    @DisplayName("resolveBase: 정상 sub는 root/sub 하위 경로")
    void resolveBase_normalSub() throws Exception {
        Path base = FileConfig.resolveBase(tempRoot, "uploads");
        assertTrue(base.startsWith(tempRoot));
        assertEquals(tempRoot.resolve("uploads").normalize(), base);
        // 실제 디렉터리가 없어도 동작. 필요시 생성 예시:
        Files.createDirectories(base);
        assertTrue(Files.isDirectory(base));
    }

    @Test
    @DisplayName("resolveBase: 경로 이탈 시도(../ 등)는 root로 강등")
    void resolveBase_traversal_downgradeToRoot() {
        Path base = FileConfig.resolveBase(tempRoot, "../etc/../../outside");
        assertEquals(tempRoot.normalize(), base);
    }

    @Test
    @DisplayName("resolveBase: sanitizeName이 적용되므로 혼합 구분자/개행도 정리")
    void resolveBase_sanitizesSub() {
        String raw = "\\..//up\\loads/\r\n";
        Path base = FileConfig.resolveBase(tempRoot, raw);
        assertEquals(tempRoot.resolve("uploads").normalize(), base);
    }

    // ---------- isSafeUnder ----------

    @Test
    @DisplayName("isSafeUnder: base 하위 파일이면 true")
    void isSafeUnder_trueWhenUnderBase() {
        Path base = tempRoot.resolve("data").normalize();
        Path file = base.resolve("img").resolve("a.png").normalize();
        assertTrue(FileConfig.isSafeUnder(file, base));
    }

    @Test
    @DisplayName("isSafeUnder: base 바깥(형제/상위)이면 false")
    void isSafeUnder_falseWhenOutside() {
        Path base = tempRoot.resolve("data").normalize();
        Path outside = tempRoot.resolve("other").resolve("a.png").normalize();
        assertFalse(FileConfig.isSafeUnder(outside, base));
    }

    @Test
    @DisplayName("isSafeUnder: null 인자면 false")
    void isSafeUnder_nullArgs() {
        assertFalse(FileConfig.isSafeUnder(null, tempRoot));
        assertFalse(FileConfig.isSafeUnder(tempRoot, null));
        assertFalse(FileConfig.isSafeUnder(null, null));
    }

    // ---------- nvl ----------

    @Test
    @DisplayName("nvl: null/blank면 기본값, 그 외 입력 유지")
    void nvl_basic() {
        assertEquals("def", FileConfig.nvl(null, "def"));
        assertEquals("def", FileConfig.nvl("", "def"));
        assertEquals("def", FileConfig.nvl("   ", "def"));
        assertEquals("val", FileConfig.nvl("val", "def"));
    }

    // ---------- enc ----------

    @Test
    @DisplayName("enc: UTF-8 URL 인코딩(공백은 + 로 인코딩됨)")
    void enc_utf8() {
        String s = "한글 파일.png";
        String encoded = FileConfig.enc(s);
        // URLEncoder는 application/x-www-form-urlencoded 규칙을 따르므로 공백은 '+'.
        // 한글은 퍼센트 인코딩됨.
        assertEquals("%ED%95%9C%EA%B8%80+%ED%8C%8C%EC%9D%BC.png", encoded);
    }
}
