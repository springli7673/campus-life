package com.li.campuslife.config;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 管理端鉴权：校验请求头 X-Admin-Token。
 *
 * <p>项目没有角色体系（tb_user 无 role 字段），管理接口用独立令牌隔离，
 * 不走用户登录态。令牌未配置时一律拒绝，避免裸奔。</p>
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthInterceptor.class);

    @Value("${campus.admin.token:}")
    private String adminToken;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (StrUtil.isBlank(adminToken)) {
            log.warn("[admin] 未配置 campus.admin.token，管理接口已拒绝访问");
            reject(response, "管理端未启用");
            return false;
        }
        String given = request.getHeader("X-Admin-Token");
        if (!adminToken.equals(given)) {
            log.warn("[admin] 令牌校验失败 uri={} ip={}", request.getRequestURI(), request.getRemoteAddr());
            reject(response, "管理端未授权");
            return false;
        }
        return true;
    }

    private void reject(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"errorMsg\":\"" + message + "\"}");
    }
}
