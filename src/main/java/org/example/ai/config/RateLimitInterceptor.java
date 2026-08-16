package org.example.ai.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单固定窗口限流（按 IP 计数，窗口内超限返回 429）。
 * 防止 AI 调用接口被刷导致费用失控。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private record Bucket(int windowStart, AtomicInteger count) {}

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.rate-limit.per-minute:30}")
    private int perMinute;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!enabled) return true;

        String ip = remoteAddr(request);
        long now = System.currentTimeMillis() / 1000 / 60; // 分钟窗口
        Bucket bucket = buckets.compute(ip, (k, old) ->
                old == null || old.windowStart() != now
                        ? new Bucket((int) now, new AtomicInteger(0))
                        : old);
        if (bucket.count().incrementAndGet() > perMinute) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"请求过于频繁，请稍后再试\"}");
            return false;
        }
        return true;
    }

    private String remoteAddr(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}