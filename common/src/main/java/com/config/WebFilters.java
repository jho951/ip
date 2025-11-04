package com.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContext;

import java.util.EnumSet;

/** 컨테이너에 전역 필터 매핑 */
public final class WebFilters {
    private WebFilters() {}

    /** IP 필터 전역 매핑 */
    public static void registerIpGuard(ServletContext sc, boolean reloadEachRequest) {
        var filter = new IpGuardFilter(reloadEachRequest);
        var reg = sc.addFilter("ip-guard", filter);
        reg.addMappingForUrlPatterns(
                EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC),
                false,
                "/*"
        );
    }
}
