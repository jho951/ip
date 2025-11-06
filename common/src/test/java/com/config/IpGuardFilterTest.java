
package com.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IpGuardFilter 테스트:
 * - 사용자 규칙 매칭 시 allowed:user(...)
 * - 기본 규칙 매칭 시   allowed:default(...)
 * - 미매칭 시           denied:no-match
 * - IPv6(예: 2001:db8::1) → denied:ip-format-not-supported(...)
 *
 * 주의: HeaderKeys/AttributeKeys의 실제 키 문자열을 모를 수 있으므로
 *       setHeader(anyString(), <value>) 형태로 값만 검증한다.
 */
class IpGuardFilterTest {

    /**
     * 공통 유틸: 필터 실행
     */
    private void runFilter(IpGuardFilter filter,
                           HttpServletRequest req,
                           HttpServletResponse res,
                           FilterChain chain) throws Exception {
        filter.doFilter(req, res, chain);
    }

    /**
     * 사용자 규칙에 매칭되는 경우:
     * - userRaw 에 172.30.1.10-173.30.1.45 포함
     * - 요청 IP: 172.30.1.20
     * 기대: allowed=true, reason = allowed:user(172.30.1.10-173.30.1.45)
     */
    @Test
    void userRuleMatch_setsAllowedTrue_andUserReason() throws Exception {
        // --- given ---
        String userRaw = "172.30.1.10-173.30.1.45|174.30.1.*";
        String defaultRaw = "10.0.0.0-10.255.255.255|172.16.0.0-172.31.255.255|192.168.0.0-192.168.255.255";
        String clientIp = "172.30.1.20";

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn(clientIp);

        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        // IpConfig.fromEnv() → IpConfig mock (isAllowed(clientIp) = true)
        IpConfig cfgMock = mock(IpConfig.class);
        when(cfgMock.isAllowed(clientIp)).thenReturn(true);
        when(cfgMock.mergedRules()).thenReturn(defaultRaw + "|" + userRaw);

        try (MockedStatic<IpConfig> ipStatic = mockStatic(IpConfig.class);
             MockedStatic<EnvConfig> envStatic = mockStatic(EnvConfig.class)) {

            ipStatic.when(IpConfig::fromEnv).thenReturn(cfgMock);

            envStatic.when(EnvConfig::loadAllowFileRulesNormalized).thenReturn(userRaw);
            envStatic.when(EnvConfig::defaultIpRules).thenReturn(defaultRaw);
            envStatic.when(EnvConfig::actualAllowFilePath).thenReturn(Path.of("dummy"));

            // --- when ---
            IpGuardFilter filter = new IpGuardFilter();
            runFilter(filter, req, res, chain);

            // --- then (헤더 값 검증: 키는 anyString) ---
            ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> valCap = ArgumentCaptor.forClass(String.class);
            verify(res, atLeastOnce()).setHeader(keyCap.capture(), valCap.capture());

            // allowed 값이 true인지
            assertTrue(valCap.getAllValues().stream().anyMatch("true"::equals));

            // reason이 allowed:user(...) 형태인지
            String reason = valCap.getAllValues().stream()
                    .filter(v -> v.startsWith("allowed:user("))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no allowed:user(...) reason header"));

            assertTrue(reason.contains("172.30.1.10-173.30.1.45"));
            verify(chain, times(1)).doFilter(any(ServletRequest.class), any(ServletResponse.class));
        }
    }

    /**
     * 기본 규칙 매칭되는 경우:
     * - userRaw 는 비어있음
     * - defaultRaw 에 192.168.0.0-192.168.255.255
     * - 요청 IP: 192.168.1.10
     * 기대: allowed=true, reason = allowed:default(192.168.0.0-192.168.255.255)
     */
    @Test
    void defaultRuleMatch_setsAllowedTrue_andDefaultReason() throws Exception {
        String userRaw = ""; // 사용자 규칙 없음
        String defaultRaw = "10.0.0.0-10.255.255.255|172.16.0.0-172.31.255.255|192.168.0.0-192.168.255.255";
        String clientIp = "192.168.1.10";

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn(clientIp);

        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        IpConfig cfgMock = mock(IpConfig.class);
        when(cfgMock.isAllowed(clientIp)).thenReturn(true);
        when(cfgMock.mergedRules()).thenReturn(defaultRaw);

        try (MockedStatic<IpConfig> ipStatic = mockStatic(IpConfig.class);
             MockedStatic<EnvConfig> envStatic = mockStatic(EnvConfig.class)) {

            ipStatic.when(IpConfig::fromEnv).thenReturn(cfgMock);
            envStatic.when(EnvConfig::loadAllowFileRulesNormalized).thenReturn(userRaw);
            envStatic.when(EnvConfig::defaultIpRules).thenReturn(defaultRaw);
            envStatic.when(EnvConfig::actualAllowFilePath).thenReturn(null);

            IpGuardFilter filter = new IpGuardFilter();
            runFilter(filter, req, res, chain);

            ArgumentCaptor<String> valCap = ArgumentCaptor.forClass(String.class);
            verify(res, atLeastOnce()).setHeader(anyString(), valCap.capture());

            assertTrue(valCap.getAllValues().stream().anyMatch("true"::equals)); // allowed=true

            String reason = valCap.getAllValues().stream()
                    .filter(v -> v.startsWith("allowed:default("))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no allowed:default(...) reason header"));

            assertTrue(reason.contains("192.168.0.0-192.168.255.255"));
            verify(chain, times(1)).doFilter(any(), any());
        }
    }

    /**
     * 미매칭 경우:
     * - userRaw/defaultRaw 모두 대상 아님
     * - 요청 IP: 8.8.8.8
     * 기대: allowed=false, reason = denied:no-match
     */
    @Test
    void noMatch_setsAllowedFalse_andDeniedNoMatch() throws Exception {
        String userRaw = "172.30.1.10-173.30.1.45|174.30.1.*";
        String defaultRaw = "10.0.0.0-10.255.255.255|172.16.0.0-172.31.255.255|192.168.0.0-192.168.255.255";
        String clientIp = "8.8.8.8";

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn(clientIp);

        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        IpConfig cfgMock = mock(IpConfig.class);
        when(cfgMock.isAllowed(clientIp)).thenReturn(false); // 허용 안 함
        when(cfgMock.mergedRules()).thenReturn(defaultRaw + "|" + userRaw);

        try (MockedStatic<IpConfig> ipStatic = mockStatic(IpConfig.class);
             MockedStatic<EnvConfig> envStatic = mockStatic(EnvConfig.class)) {

            ipStatic.when(IpConfig::fromEnv).thenReturn(cfgMock);
            envStatic.when(EnvConfig::loadAllowFileRulesNormalized).thenReturn(userRaw);
            envStatic.when(EnvConfig::defaultIpRules).thenReturn(defaultRaw);

            IpGuardFilter filter = new IpGuardFilter();
            runFilter(filter, req, res, chain);

            ArgumentCaptor<String> valCap = ArgumentCaptor.forClass(String.class);
            verify(res, atLeastOnce()).setHeader(anyString(), valCap.capture());

            // allowed=false
            assertTrue(valCap.getAllValues().stream().anyMatch("false"::equals));

            // reason = denied:no-match
            assertTrue(valCap.getAllValues().stream().anyMatch("denied:no-match"::equals));
            verify(chain, times(1)).doFilter(any(), any());
        }
    }

    /**
     * IPv6 (루프백이 아닌) 의 경우:
     * - 요청 IP: 2001:db8::1
     * - 기대: allowed=false, reason = denied:ip-format-not-supported(2001:db8::1)
     */
    @Test
    void ipv6NotSupported_setsDeniedIpFormat() throws Exception {
        String userRaw = "";
        String defaultRaw = "10.0.0.0-10.255.255.255|172.16.0.0-172.31.255.255|192.168.0.0-192.168.255.255";
        String clientIp = "2001:db8::1";

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn(clientIp);

        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        IpConfig cfgMock = mock(IpConfig.class);
        when(cfgMock.isAllowed(clientIp)).thenReturn(false);
        when(cfgMock.mergedRules()).thenReturn(defaultRaw);

        try (MockedStatic<IpConfig> ipStatic = mockStatic(IpConfig.class);
             MockedStatic<EnvConfig> envStatic = mockStatic(EnvConfig.class)) {

            ipStatic.when(IpConfig::fromEnv).thenReturn(cfgMock);
            envStatic.when(EnvConfig::loadAllowFileRulesNormalized).thenReturn(userRaw);
            envStatic.when(EnvConfig::defaultIpRules).thenReturn(defaultRaw);

            IpGuardFilter filter = new IpGuardFilter();
            runFilter(filter, req, res, chain);

            ArgumentCaptor<String> valCap = ArgumentCaptor.forClass(String.class);
            verify(res, atLeastOnce()).setHeader(anyString(), valCap.capture());

            // allowed=false
            assertTrue(valCap.getAllValues().stream().anyMatch("false"::equals));

            // reason starts with denied:ip-format-not-supported(
            String reason = valCap.getAllValues().stream()
                    .filter(v -> v.startsWith("denied:ip-format-not-supported("))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no denied:ip-format-not-supported(...) reason header"));

            assertTrue(reason.contains(clientIp));
            verify(chain, times(1)).doFilter(any(), any());
        }
    }

    /**
     * IPv6 루프백(::1)은 127.0.0.1로 변환되어 매칭됨:
     * - defaultRaw에 127.0.0.1 단일 IP를 넣어두고
     * - 요청 RemoteAddr = ::1
     * 기대: allowed=true, allowed:default(127.0.0.1)
     */
    @Test
    void ipv6Loopback_mapsTo127001_andMatches() throws Exception {
        String userRaw = "";
        String defaultRaw = "127.0.0.1|10.0.0.0-10.255.255.255";
        String clientIpRaw = "::1";     // 요청에서 들어오는 값
        String mapped = "127.0.0.1";    // 필터 내에서 변환 기대값

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn(clientIpRaw);

        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        IpConfig cfgMock = mock(IpConfig.class);
        when(cfgMock.isAllowed(mapped)).thenReturn(true);       // 변환된 127.0.0.1 기준 허용
        when(cfgMock.mergedRules()).thenReturn(defaultRaw);

        try (MockedStatic<IpConfig> ipStatic = mockStatic(IpConfig.class);
             MockedStatic<EnvConfig> envStatic = mockStatic(EnvConfig.class)) {

            ipStatic.when(IpConfig::fromEnv).thenReturn(cfgMock);
            envStatic.when(EnvConfig::loadAllowFileRulesNormalized).thenReturn(userRaw);
            envStatic.when(EnvConfig::defaultIpRules).thenReturn(defaultRaw);

            IpGuardFilter filter = new IpGuardFilter();
            runFilter(filter, req, res, chain);

            ArgumentCaptor<String> valCap = ArgumentCaptor.forClass(String.class);
            verify(res, atLeastOnce()).setHeader(anyString(), valCap.capture());

            assertTrue(valCap.getAllValues().stream().anyMatch("true"::equals)); // allowed=true
            String reason = valCap.getAllValues().stream()
                    .filter(v -> v.startsWith("allowed:default("))
                    .findFirst()
                    .orElseThrow();
            assertTrue(reason.contains("127.0.0.1"));
            verify(chain, times(1)).doFilter(any(), any());
        }
    }
}
