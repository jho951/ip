package com.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** 프록시 고려 없이, 서버 인스턴스가 직접 받는 remoteAddr만 확인 */
public class IpGuardFilter implements Filter {
    private final boolean reloadEachRequest; // true: 매 요청마다 ENV/파일 재로드
    private volatile IpConfig cached;        // false일 때만 사용

    public IpGuardFilter(boolean reloadEachRequest) {
        this.reloadEachRequest = reloadEachRequest;
    }

    @Override public void init(FilterConfig filterConfig) {
        if (!reloadEachRequest) this.cached = IpConfig.fromEnv();
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        if (!(req instanceof HttpServletRequest) || !(res instanceof HttpServletResponse)) {
            chain.doFilter(req, res);
            return;
        }
        HttpServletRequest r = (HttpServletRequest) req;
        HttpServletResponse w = (HttpServletResponse) res;

        // ★ 매 요청마다 최신 ENV/파일 로드 (요구사항)
        IpConfig cfg = reloadEachRequest ? IpConfig.fromEnv() : cached;

        String ip = r.getRemoteAddr(); // ★ 오직 remoteAddr만 사용
        if (!cfg.isAllowed(ip)) {
            w.setStatus(HttpServletResponse.SC_FORBIDDEN);
            w.setContentType("text/plain; charset=UTF-8");
            w.getWriter().printf("Forbidden IP: %s%n", ip);
            return;
        }
        chain.doFilter(req, res);
    }

    @Override public void destroy() {}
}
