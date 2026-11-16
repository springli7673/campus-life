package com.li.campuslife.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求日志：记录每个请求的方法、路径、状态码与耗时。
 *
 * <p>项目原本只记录异常，成功请求没有任何痕迹，排查"前端到底有没有打到后端"时很费劲。
 * 这里统一补上，超过 500ms 的慢请求用 WARN 单独标出来。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    /** 慢请求阈值，超过用 WARN */
    private static final long SLOW_MILLIS = 500L;

    /** 查询串最多打印的字符数，避免日志被长参数刷屏 */
    private static final int MAX_QUERY_LENGTH = 200;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            String target = buildTarget(request);
            if (cost >= SLOW_MILLIS) {
                log.warn("[http] {} {} -> {} {}ms (slow)", request.getMethod(), target,
                        response.getStatus(), cost);
            } else {
                log.info("[http] {} {} -> {} {}ms", request.getMethod(), target,
                        response.getStatus(), cost);
            }
        }
    }

    private String buildTarget(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isEmpty()) {
            return request.getRequestURI();
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH) + "...";
        }
        return request.getRequestURI() + "?" + query;
    }
}
