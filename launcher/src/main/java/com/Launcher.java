package com;

import org.apache.catalina.startup.Tomcat;

import java.net.URI;
import java.util.Objects;

public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) throws Exception {
        // 1) 포트 결정 (ENV: 정수/URL/":8081" 모두 허용)
        int p1 = parsePort(System.getenv("DEFAULT_SERVER1"), 8081);
        int p2 = parsePort(System.getenv("DEFAULT_SERVER2"), 8082);

        if (p1 == p2) {
            throw new IllegalStateException("server1과 server2 포트가 같습니다: " + p1);
        }

        // 2) 서버 기동
        Tomcat s1 = null, s2 = null;
        try {
            s1 = Server1.start(p1);  // JSP 서버
            s2 = Server2.start(p2);  // API 서버

            System.out.println("[launcher] server1: " + urlOf("localhost", p1));
            System.out.println("[launcher] server2: " + urlOf("localhost", p2));

            // 3) 종료 훅(순서 s1 -> s2)
            Tomcat fS1 = s1, fS2 = s2;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdownPair(fS1, fS2)));

            // 4) 블록 (server1 기준으로 대기; 둘 다 살아있으면 OK)
            s1.getServer().await();

        } catch (Throwable t) {
            System.err.println("[launcher] 기동 실패: " + t.getMessage());
            t.printStackTrace(System.err);
            // 부분 기동 시 안전 정리
            shutdownPair(s1, s2);
            // 비정상 종료 코드
            System.exit(1);
        }
    }

    /** 안전 종료 */
    private static void shutdownPair(Tomcat s1, Tomcat s2) {
        shutdownOne(s1, "server1");
        shutdownOne(s2, "server2");
    }

    private static void shutdownOne(Tomcat t, String name) {
        if (t == null) return;
        try { t.stop(); } catch (Exception ignored) {}
        try { t.destroy(); } catch (Exception ignored) {}
        System.out.println("[launcher] stopped " + name);
    }

    /** ENV에서 포트 파싱: 정수("8081"), URL("http://x:8081"), 콜론(":8081") 지원 */
    private static int parsePort(String env, int fallback) {
        try {
            if (env == null || env.isBlank()) return fallback;
            String s = env.trim();

            // 정수만 들어온 경우
            if (s.matches("\\d+")) {
                return Integer.parseInt(s);
            }
            // 앞에 콜론만 있는 경우
            if (s.matches(":\\d+")) {
                return Integer.parseInt(s.substring(1));
            }
            // URL인 경우
            URI u = URI.create(s);
            int p = u.getPort();
            return p > 0 ? p : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String urlOf(String host, int port) {
        return "http://" + Objects.toString(host, "localhost") + ":" + port + "/";
    }
}
