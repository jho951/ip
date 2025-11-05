
package com.config;

import com.exception.AppException;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IpConfigTest {

    // --- 리플렉션으로 private 생성자 호출해 mergedRules 주입 ---
    private static IpConfig newWithRules(String mergedRules) throws Exception {
        Constructor<IpConfig> ctor = IpConfig.class.getDeclaredConstructor(String.class, Path.class);
        ctor.setAccessible(true);
        return ctor.newInstance(mergedRules, null);
    }

    // ============ 1) 규칙 유효성 사전 검증(assertValidRules) ============
    @Test
    void assertValidRules_accepts_supported_tokens() {
        // 단일 / CIDR / 와일드카드 / 범위(~, - 모두) → 예외 없어야 함
        String ok = String.join("|",
                "203.0.113.7",
                "10.0.0.0/8",
                "192.168.1.*",
                "172.30.1.10~172.30.1.20",
                "172.30.1.30-172.30.1.40"
        );
        assertDoesNotThrow(() -> IpConfig.assertValidRules(ok));
    }

    @Test
    void assertValidRules_throws_on_invalid_token() {
        String bad = "999.999.1.1|hello-world|10.0.0.0/33";
        assertThrows(AppException.class, () -> IpConfig.assertValidRules(bad));
    }

    // ============ 2) isAllowed() 매칭: 단일/CIDR/와일드카드/범위 ============
    @Test
    void isAllowed_matches_all_supported_forms() throws Exception {
        String rules = String.join("|",
                "203.0.113.7",           // 단일
                "10.0.0.0/8",            // CIDR
                "192.168.1.*",           // 와일드카드
                "172.30.1.10-172.30.1.20"// 범위
        );
        IpConfig cfg = newWithRules(rules);

        assertTrue(cfg.isAllowed("203.0.113.7"));
        assertTrue(cfg.isAllowed("10.123.45.67"));
        assertTrue(cfg.isAllowed("192.168.1.200"));
        assertTrue(cfg.isAllowed("172.30.1.15"));

        assertFalse(cfg.isAllowed("8.8.8.8"));
        assertFalse(cfg.isAllowed("192.168.2.1"));
        assertFalse(cfg.isAllowed("172.30.1.9"));
        assertFalse(cfg.isAllowed("172.30.1.21"));
    }

    // ============ 3) toIPv4IfPossible() ============
    @Test
    void toIPv4IfPossible_handles_ipv6_mapped_and_loopback() {
        assertEquals("192.168.0.5", IpConfig.toIPv4IfPossible("::ffff:192.168.0.5"));
        assertEquals("127.0.0.1", IpConfig.toIPv4IfPossible("::1"));
        assertNull(IpConfig.toIPv4IfPossible("2001:db8::1"));   // 일반 IPv6는 변환 불가 → null
        assertEquals("203.0.113.10", IpConfig.toIPv4IfPossible("203.0.113.10")); // 이미 IPv4 → 그대로
    }

    // ============ 4) clientIPv4(): 프록시 헤더 신뢰/비신뢰 시나리오 ============
    @Test
    void clientIPv4_noProxyHeaders_uses_remoteAddr() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("192.168.0.23");
        assertEquals("192.168.0.23", IpConfig.clientIPv4(req, false));
    }

    @Test
    void clientIPv4_trustProxyHeaders_uses_xff_first_token() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn("203.0.113.60, 10.0.0.5");
        when(req.getHeader("X-Real-IP")).thenReturn(null);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");

        assertEquals("203.0.113.60", IpConfig.clientIPv4(req, true));
    }

    @Test
    void clientIPv4_trustProxyHeaders_fallback_to_xRealIp_or_remote() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn("");
        when(req.getHeader("X-Real-IP")).thenReturn("198.51.100.9");
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");

        assertEquals("198.51.100.9", IpConfig.clientIPv4(req, true));

        // X-Real-IP도 없으면 remoteAddr
        HttpServletRequest req2 = mock(HttpServletRequest.class);
        when(req2.getHeader("X-Forwarded-For")).thenReturn(null);
        when(req2.getHeader("X-Real-IP")).thenReturn(null);
        when(req2.getRemoteAddr()).thenReturn("172.16.0.10");
        assertEquals("172.16.0.10", IpConfig.clientIPv4(req2, true));
    }

    @Test
    void clientIPv4_converts_ipv6_mapped_remoteAddr() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("::ffff:192.168.1.77");
        assertEquals("192.168.1.77", IpConfig.clientIPv4(req, false));
    }

    // ============ 5) fromEnv() 기본 동작 연기 테스트(스모크) ============
    // 실제 환경/파일에 의존하지 않고 NPE 없이 생성되는지만 확인
    @Test
    void fromEnv_smoke() {
        IpConfig cfg = IpConfig.fromEnv();
        assertNotNull(cfg);
        assertNotNull(cfg.mergedRules()); // 빈 문자열일 수도 있으나 NPE는 없어야 함
    }
}
