// Server1.java
package com;

import com.config.WebFilters;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Server1 {
    public static Tomcat start(int port) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.getConnector();
        
        Path webapp = Paths.get("server1", "src", "main", "webapp").toAbsolutePath();
        Files.createDirectories(webapp);
        Path idx = webapp.resolve("index.jsp");
        if (!Files.exists(idx)) {
            Files.writeString(idx,
                    "<%@ page contentType=\"text/html; charset=UTF-8\" %>\n" +
                            "<!doctype html><html><body><h1>Server1 JSP OK</h1></body></html>");
        }

        Context ctx = tomcat.addWebapp("", webapp.toString());
        ctx.setReloadable(true);

        // ⬇️ 모든 요청에 IP 체크 (요청마다 규칙 재로드)
        WebFilters.registerIpGuard(ctx.getServletContext(), true);

        tomcat.start();
        System.out.println("[server1] JSP on http://localhost:" + port + "/");
        return tomcat;
    }
}
