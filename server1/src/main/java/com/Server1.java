package com;

import com.servlet.TransferServlet;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

import java.nio.file.Path;

public class Server1 {

    public static Tomcat start(int port) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.getConnector();
        tomcat.getConnector().setProperty("URIEncoding", "UTF-8");
        tomcat.getConnector().setProperty("useBodyEncodingForURI", "true");



        Path webapp = Path.of("server1/src/main/webapp").toAbsolutePath();
        Context ctx = tomcat.addWebapp("", webapp.toString());
        if (ctx instanceof StandardContext sc) sc.setReloadable(true);

        FilterDef def = new FilterDef();
        def.setFilterName("ipGuard");
        def.setFilter(new com.config.IpGuardFilter());
        ctx.addFilterDef(def);

        FilterMap map = new FilterMap();
        map.setFilterName("ipGuard");
        map.addURLPattern("/*");
        ctx.addFilterMap(map);

        Wrapper w = Tomcat.addServlet(ctx, "transferServlet", new TransferServlet());
        w.setLoadOnStartup(1);
        try { w.addMapping("/transfer"); } catch (Throwable ignored) {}
        ctx.addServletMappingDecoded("/transfer", "transferServlet");

        tomcat.start();
        return tomcat;
    }
}
