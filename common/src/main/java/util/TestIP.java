package util;

import constant.IP_REGEX;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

public class TestIP {

    public static boolean isValidIP(String ipToValidate, String patternString) {
        String[] patterns = patternString.split("(?:,|\\||\\R)+");


        for (String pattern : patterns) {
            String trimmedPattern = pattern.trim();

            if (trimmedPattern.isEmpty()) {
                continue;
            }

            // 각 패턴의 유형을 확인하여 매칭을 시도합니다.
            if (IP_REGEX.CIDR.matches(trimmedPattern)) {
                // CIDR 패턴 처리 로직
                try {
                    if (isIpInCidr(ipToValidate, trimmedPattern)) {
                        return true;
                    }
                } catch (UnknownHostException e) {
                    // 유효하지 않은 IP 또는 CIDR일 경우, 다음 패턴으로 넘어갑니다.
                }
            } else if (IP_REGEX.RANGE.matches(trimmedPattern)) {
                // RANGE 패턴 처리 로직
                if (isIpInRange(ipToValidate, trimmedPattern)) {
                    return true;
                }
            } else if (IP_REGEX.WILDCARD.matches(trimmedPattern)) {
                // WILDCARD 패턴 처리 로직
                if (isIpInWildcard(ipToValidate, trimmedPattern)) {
                    return true;
                }
            } else if (IP_REGEX.IPV4.matches(trimmedPattern)) {
                // 단일 IPV4 주소 패턴
                if (ipToValidate.equals(trimmedPattern)) {
                    return true;
                }
            }
        }

        return false;
    }

    // IP 주소가 CIDR 범위 내에 있는지 확인하는 메서드
    private static boolean isIpInCidr(String ip, String cidr) throws UnknownHostException {
        String[] parts = cidr.split("/");
        String networkAddress = parts[0];
        int prefixLength = Integer.parseInt(parts[1]);

        byte[] ipBytes = InetAddress.getByName(ip).getAddress();
        byte[] networkBytes = InetAddress.getByName(networkAddress).getAddress();

        int mask = -(1 << (32 - prefixLength));
        for (int i = 0; i < 4; i++) {
            if ((ipBytes[i] & networkBytes[i] & (mask >> (i * 8))) != (networkBytes[i] & (mask >> (i * 8)))) {
                return false;
            }
        }
        return true;
    }

    // IP 주소가 RANGE 범위 내에 있는지 확인하는 메서드
    private static boolean isIpInRange(String ip, String range) {
        String[] parts = range.split("[-~]");
        try {
            long startIp = ipToLong(parts[0].trim());
            long endIp = ipToLong(parts[1].trim());
            long checkIp = ipToLong(ip);
            return checkIp >= startIp && checkIp <= endIp;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    // IP 주소가 와일드카드 패턴에 일치하는지 확인하는 메서드
    private static boolean isIpInWildcard(String ip, String wildcardPattern) {
        // 와일드카드 패턴을 정규식으로 변환 (예: 174.30.1.* -> 174\.30\.1\..*)
        String regex = wildcardPattern.replace(".", "\\.").replace("*", ".*");
        return Pattern.matches(regex, ip);
    }

    // IP 주소를 롱(long) 타입으로 변환하는 헬퍼 메서드
    private static long ipToLong(String ipAddress) throws UnknownHostException {
        InetAddress ip = InetAddress.getByName(ipAddress);
        byte[] octets = ip.getAddress();
        long result = 0;
        for (byte octet : octets) {
            result <<= 8;
            result |= octet & 0xFF;
        }
        return result;
    }
}
