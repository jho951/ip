package com;

import com.config.EnvConfig;
import org.apache.catalina.startup.Tomcat;

import java.net.*;

public final class Launcher {
    private Launcher() {}
    public static void main(String[] args) throws Exception {
        int p1 = EnvConfig.portOf(System.getenv("DEFAULT_SERVER1"), 8081);
        int p2 = EnvConfig.portOf(System.getenv("DEFAULT_SERVER2"), 8082);

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

}
