package org.example.ai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 管理端点鉴权过滤器。
 * 通过环境变量 ADMIN_TOKEN 配置；未配置时仅允许 loopback 访问（防公网裸奔）。
 */
@Component
public class AdminTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminTokenFilter.class);

    @Value("${app.admin-token:}")
    private String adminToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/admin")) {
            chain.doFilter(request, response);
            return;
        }

        boolean ok;
        if (adminToken != null && !adminToken.isBlank()) {
            ok = adminToken.equals(request.getHeader("X-Admin-Token"));
        } else {
            // 未配置 token 时退化为仅本机可访问
            ok = "127.0.0.1".equals(request.getRemoteAddr()) || "0:0:0:0:0:0:0:1".equals(request.getRemoteAddr());
        }

        if (!ok) {
            log.warn("管理端点被拒绝: {} {} remote={}", request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"管理端点需要有效的 X-Admin-Token\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}