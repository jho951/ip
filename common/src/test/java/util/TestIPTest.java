package util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestIPTest {

    // 테스트용 패턴 문자열 (줄바꿈·파이프 모두 테스트)
    private static final String TEST_PATTERNS = String.join("\n",
            "10.0.0.0/8",
            "172.30.1.10~173.30.1.45",
            "174.30.1.*",
            "192.168.0.1",
            "203.0.113.0/24"
//            "172.30.1.10~173.30.1.45|174.30.1.*",
//            "10.0.0.0~10.255.255.255|172.16.0.0~172.31.255.255|192.168.0.0~192.168.255.255"
    );

    @Test
    void testMatchCidr() {
        assertTrue(TestIP.isValidIP("10.12.3.4", TEST_PATTERNS),
                "10.12.3.4는 10.0.0.0/8 범위에 허용됩니다.");
        assertFalse(TestIP.isValidIP("205.0.113.77", TEST_PATTERNS),
                "203.0.113.77는 범위에 허용되지 않습니다.");
    }

    @Test
    void testMatchRange() {
        assertTrue(TestIP.isValidIP("172.30.1.20", TEST_PATTERNS),
                "172.30.1.20는 172.30.1.10~173.30.1.45 범위에 포함됩니다.");
        assertFalse(TestIP.isValidIP("172.31.1.20", TEST_PATTERNS),
                "172.31.1.20 포함되지 않습니다.");
    }

    @Test
    void testMatchWildcard() {
        assertTrue(TestIP.isValidIP("174.30.1.50", TEST_PATTERNS),
                "174.30.1.50 should match 174.30.1.*");
        assertFalse(TestIP.isValidIP("175.30.1.50", TEST_PATTERNS),
                "175.30.1.50 should not match wildcard");
    }

    @Test
    void testMatchSingleIPv4() {
        assertTrue(TestIP.isValidIP("192.168.0.1", TEST_PATTERNS),
                "192.168.0.1 should match exact IPv4 entry");
        assertFalse(TestIP.isValidIP("192.168.0.2", TEST_PATTERNS),
                "192.168.0.2 should not match since only .1 listed");
    }

    @Test
    void testInvalidInputs() {
        assertFalse(TestIP.isValidIP(null, TEST_PATTERNS),
                "null input must return false");
        assertFalse(TestIP.isValidIP("not-an-ip", TEST_PATTERNS),
                "invalid IP format must return false");
        assertFalse(TestIP.isValidIP("300.1.1.1", TEST_PATTERNS),
                "out-of-range IPv4 must return false");
    }

    @Test
    void testPipeSeparatedPatterns() {
        String pipePatterns = "10.0.0.0/8 | 192.168.0.1 | 203.0.113.0/24";
        assertTrue(TestIP.isValidIP("10.20.30.40", pipePatterns));
        assertTrue(TestIP.isValidIP("192.168.0.1", pipePatterns));
        assertTrue(TestIP.isValidIP("203.0.113.77", pipePatterns));
    }
}
