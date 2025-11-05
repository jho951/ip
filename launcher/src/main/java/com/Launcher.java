package com;

import org.apache.catalina.startup.Tomcat;

import java.net.*;

public final class Launcher {
    private Launcher() {}
    public static void main(String[] args) throws Exception {
        int p1 = portOf(System.getenv("DEFAULT_SERVER1"), 8081);
        int p2 = portOf(System.getenv("DEFAULT_SERVER2"), 8082);

        Tomcat t1 = Server1.start(p1);
        Tomcat t2 = Server2.start(p2);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                t1.stop();
            } catch (Exception ignored) {}
            try {
                t2.stop();
            } catch (Exception ignored) {}
        }));

        Thread.currentThread().join();
    }

    /** DEFAULT_SERVER1/2가 URL 형태일 때 포트 파싱. 없으면 fallback. */
    private static int portOf(String envUrl, int fallback) {
        try {
            if (envUrl == null || envUrl.isBlank()) return fallback;
            URI u = URI.create(envUrl.trim());
            int p = u.getPort();
            return p > 0 ? p : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
