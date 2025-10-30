
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class CommonMain {
    public static void main(String[] args) throws Exception {
        InetAddress local = InetAddress.getLocalHost();
        System.out.println("내 로컬 IP: " + local.getHostAddress());

        System.out.println("기본 InetAddress: " + InetAddress.getLocalHost().getHostAddress());

        System.out.println("---- 활성 네트워크 인터페이스 ----");
        // 내 PC에 있는 모든 네트워크 인터페이스(와이파이, 이더넷, 도커 브리지 등)를 가져와요.
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            // 비활성(꺼짐), 루프백(127.0.0.1), 가상 어댑터는 건너뜁니다.
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
            System.out.println("인터페이스: " + ni.getName());
            // 해당 인터페이스에 붙은 IP 주소 목록(IPv4/IPv6 둘 다 가능)을 꺼냅니다.
            ni.getInterfaceAddresses().forEach(ia -> {
                var addr = ia.getAddress().getHostAddress();
                if (addr.contains(".")) // IPv4만 출력
                    System.out.println("  IP → " + addr);
            });
        }
    }
}