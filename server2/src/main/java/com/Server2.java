package com;

import com.servlet.FileQueryServlet;
import com.filter.IpGuardFilter;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

import java.nio.file.Files;

public class Server2 {
    public static Tomcat start(int port) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.getConnector().setProperty("URIEncoding", "UTF-8");
        tomcat.getConnector().setProperty("useBodyEncodingForURI", "true");
        tomcat.setBaseDir(Files.createTempDirectory("s2-tomcat").toString());
        Context ctx = tomcat.addContext("", Files.createTempDirectory("s2-doc").toString());

        FilterDef def = new FilterDef();
        def.setFilterName("ipGuard");
        def.setFilter(new IpGuardFilter());
        ctx.addFilterDef(def);

        FilterMap map = new FilterMap();
        map.setFilterName("ipGuard");
        map.addURLPattern("/*");
        ctx.addFilterMap(map);

        Wrapper files = Tomcat.addServlet(ctx, "fileQueryServlet", new FileQueryServlet());
        files.setLoadOnStartup(1);
        try { files.addMapping("/files"); } catch (Throwable ignored) {}
        ctx.addServletMappingDecoded("/files", "fileQueryServlet");

        tomcat.start();
        return tomcat;
    }
}
