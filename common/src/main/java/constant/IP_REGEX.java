package constant;

import java.util.List;
import java.util.regex.Pattern;

public enum IP_REGEX {

    CIDR("^\\d+\\.\\d+\\.\\d+\\.\\d+/\\d{1,2}$", "IP 주소와 CIDR 접두사 길이 패턴"),
    RANGE("^(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s*[-~]\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)$", "IP 주소 범위 패턴"),
    WILDCARD("^(\\d+|\\*)\\.(\\d+|\\*)\\.(\\d+|\\*)\\.(\\d+|\\*)$", "와일드카드가 포함된 IP 주소 패턴"),
    IPV4("^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$", "일반적인 IPv4 주소 패턴");

    private final List<String> DEFAULT_IP = List.of(
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16"
    );
    private final Pattern pattern;
    private final String description;




    IP_REGEX(String regex, String description) {
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
