package com;

import com.filter.IpGuardFilter;
import com.servlet.TransferServlet;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

import java.nio.file.Path;

/** Tomcat 서버1 인스턴스 설정 및 시작 */
public class Server1 {

    /**
     * 주어진 포트에서 Tomcat 서버 인스턴스를 초기화하고 시작합니다.
     * 기본 설정 구성,특정 서블릿과 필터를 등록합니다.
     *
     * @param port 서버 포트 번호
     * @return Tomcat 인스턴스
     * @throws Exception Tomcat 시작 중 오류가 발생하면 예외가 발생합니다.
     */
    public static Tomcat start(int port) throws Exception {
        // Tomcat 인스턴스 초기화
        Tomcat tomcat = new Tomcat();
        // 포트 설정
        tomcat.setPort(port);
        // URI 인코딩을 UTF-8로 설정해 한글 지원
        tomcat.getConnector().setProperty("URIEncoding", "UTF-8");
        // 요청 본문에 설정된 인코딩을 URI 인코딩에도 사용하도록 설정 (GET 요청 파라미터 처리 등에 도움)
        tomcat.getConnector().setProperty("useBodyEncodingForURI", "true");

        // jsp 경로 설정
        Path webapp = Path.of("server1/src/main/webapp").toAbsolutePath();
        // Tomcat 인스턴스에 웹 애플리케이션 추가 (컨텍스트 경로: "", 즉 루트 경로)
        Context ctx = tomcat.addWebapp("", webapp.toString());
        // 컨텍스트가 StandardContext 타입인 경우, 리로드 가능
        if (ctx instanceof StandardContext sc) sc.setReloadable(true);

        // 필터 정의 : 필터 이름과 실제 필터 클래스 인스턴스 연결
        FilterDef def = new FilterDef();
        def.setFilterName("ipGuard");
        def.setFilter(new IpGuardFilter());
        ctx.addFilterDef(def);

        // 필터 맵핑 : 필터를 어떤 URL 패턴에 적용할지 정의
        FilterMap map = new FilterMap();
        map.setFilterName("ipGuard");
        map.addURLPattern("/*");
        ctx.addFilterMap(map);

        // TransferServlet 인스턴스를 "transferServlet" 이름으로 컨텍스트에 추가
        Wrapper w = Tomcat.addServlet(ctx, "transferServlet", new TransferServlet());
        // 서버 시작 시 서블릿을 즉시 로드 (값이 1이면 서버 시작과 함께 로드)
        w.setLoadOnStartup(1);
        try {
            // 서블릿에 URL 맵핑 추가 (구식 API 또는 잠재적 예외 처리)
            w.addMapping("/transfer");
        } catch (Throwable ignored) {}
        // 서블릿 맵핑을 공식적으로 추가: "/transfer" URL 패턴을 "transferServlet"에 맵핑
        ctx.addServletMappingDecoded("/transfer", "transferServlet");

        tomcat.start();
        return tomcat;
    }
}