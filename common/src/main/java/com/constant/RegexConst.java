
package com.constant;

import java.util.regex.Pattern;

public final class RegexConst {
    private RegexConst() {}

    /**
     * 규칙 토큰 분리자: 쉼표, 파이프, 줄바꿈(모든 라인 구분자), 세미콜론을 연속 허용.
     * 예) "a,b|c\nd;e" → ["a", "b", "c", "d", "e"]
     */
    public static final Pattern RULE_SEP = Pattern.compile("(?:,|\\||\\R|;)+");
    public static final Pattern RULE_ENV = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");
    public static final Pattern IPV6_MAPPED_V4 = Pattern.compile("^::ffff:(\\d+\\.\\d+\\.\\d+\\.\\d+)$", Pattern.CASE_INSENSITIVE);
}
