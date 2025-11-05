package com.constant;
import java.util.regex.Pattern;

/**
 * IP 주소 범위 표기법
 */
public enum IPRegex {
    CIDR("^\\d+\\.\\d+\\.\\d+\\.\\d+/\\d{1,2}$", "CIDR 패턴"),
    RANGE("^(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s*[-~]\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)$", "IP 범위 패턴"),
    WILDCARD("^(\\d+|\\*)\\.(\\d+|\\*)\\.(\\d+|\\*)\\.(\\d+|\\*)$", "와일드카드 패턴"),
    IPV4("^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$", "일반 IPv4 주소 패턴");;

    private final Pattern pattern;
    private final String description;
    
    IPRegex(String regex, String description) {
        this.pattern = Pattern.compile(regex);
        this.description = description;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public String getDescription() {
        return description;
    }

    public boolean matches(String input) {
        return this.pattern.matcher(input).matches();
    }
}
