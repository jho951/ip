byte[] getAddress() //IP주소를 byte배열로 반환한다.
static InetAddress[] getAllByName(String host) //도메인명(host)을 통해 IP주소를 얻는다.
static InetAddess getByAddess(byte[] addr) //byte배열을 통해 IP주소를 얻는다.
static InetAddress getByName(String host) //도메인명(host)에 지정된 모든 호스트의 IP주소를 배열에 담아 반환한다.
static InetAddress getLocalHost() //지역호스트의 IP주소를 반환한다.
String getCanonicalHostName() //Fully Qualified Domain Name을 반환한다.
String getHostAddress() //호스트의 IP주소를 반환한다.
String getHostName() //호스트의 이름을 반환한다.
boolean isMulticastAddress() //IP주소가 멀티캐스트 주소인지 알려준다.
boolean isLoopbackAddress() //IP주소가 loopback 주소(127.0.0.1)인지 알려준다.

Inet4Address.getLocalHost().getHostAddress();

바이트 배열을 쓰는 이유?

컴퓨터 시스템은 데이터를 저장할 때 메모리에 바이트를 저장하는 순서(엔디안)가 시스템마다 다를 수 있습니다 (리틀 엔디안 또는 빅 엔디안).
하지만 네트워크를 통해 데이터를 주고받을 때는 빅 엔디안(Big-Endian) 순서로 통일하기로 약속되어 있습니다 (네트워크 바이트 순서).
IP 주소를 바이트 배열로 변환하면 시스템의 기본 엔디안에 관계없이 항상 일관된 네트워크 바이트 순서로 데이터를 처리하고 전송할 수 있습니다.