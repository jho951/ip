package com;

import com.config.WebFilters;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.file.Files;

public class Server2 {
    public static Tomcat start(int port) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.getConnector();
        tomcat.setBaseDir(Files.createTempDirectory("s2-tomcat").toString());

        // 빈 컨텍스트 생성 (webapp 없이)
        Context ctx = tomcat.addContext("", Files.createTempDirectory("s2-doc").toString());

        // 모든 요청에 IP 체크 필터 전역 적용 (매 요청 재로드 = true)
        WebFilters.registerIpGuard(ctx.getServletContext(), true);

        // /hello 서블릿 등록
        Tomcat.addServlet(ctx, "hello", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("text/plain; charset=UTF-8");
                resp.getWriter().println("Hello from server2");
            }
        });
        ctx.addServletMappingDecoded("/hello", "hello");

        tomcat.start();
        System.out.println("[server2] API on http://localhost:" + port + "/hello");
        return tomcat;
    }
}
