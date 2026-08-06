package org.example.ai.controller;

import org.example.ai.agent.core.AgentContext;
import org.example.ai.agent.impl.CoordinatorAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Agent 控制器 - 基于新架构的多 Agent 协作入口
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final CoordinatorAgent coordinator;
    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    // 保存最近的会话上下文（用于演示，生产环境应使用 Redis 等）
    private final Map<String, AgentContext> sessions = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, AgentContext> eldest) {
            return size() > 50;
        }
    };

    public AgentController(CoordinatorAgent coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * 智能 Agent 路由 - 自动协调多 Agent 协作
     */
    @PostMapping("/process")
    public Map<String, Object> process(@RequestBody ProcessRequest request) {
        String task = request.getTask();
        String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();
        log.info("收到任务: {}", task);

        // 创建或获取会话上下文
        AgentContext context = sessions.computeIfAbsent(sessionId, k -> new AgentContext(k));
        coordinator.setContext(context);

        long startTime = System.currentTimeMillis();
        String result = coordinator.execute(task);
        long duration = System.currentTimeMillis() - startTime;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", result);
        response.put("type", "agent_coordinated");
        response.put("sessionId", sessionId);
        response.put("duration", duration + "ms");
        response.put("executionLog", context.getExecutionLog());
        response.put("sharedData", context.snapshot());
        return response;
    }

    /**
     * 直接调用指定 Agent
     */
    @PostMapping("/execute/{agentName}")
    public Map<String, Object> executeAgent(@PathVariable String agentName,
                                            @RequestBody ProcessRequest request) {
        String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();
        AgentContext context = sessions.computeIfAbsent(sessionId, k -> new AgentContext(k));
        coordinator.setContext(context);

        // 使用 Coordinator 的目标 Agent 路由
        String task = "使用 " + agentName + " 处理: " + request.getTask();
        long startTime = System.currentTimeMillis();
        String result = coordinator.execute(task);
        long duration = System.currentTimeMillis() - startTime;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", result);
        response.put("type", "agent_direct");
        response.put("agentName", agentName);
        response.put("sessionId", sessionId);
        response.put("duration", duration + "ms");
        response.put("executionLog", context.getExecutionLog());
        return response;
    }

    /**
     * 获取所有可用 Agent 及其技能
     */
    @GetMapping("/agents")
    public List<Map<String, Object>> getAgents() {
        return coordinator.getAgentsDetail();
    }

    /**
     * 获取会话上下文
     */
    @GetMapping("/sessions/{sessionId}")
    public Map<String, Object> getSession(@PathVariable String sessionId) {
        AgentContext context = sessions.get(sessionId);
        if (context == null) {
            return Map.of("error", "会话不存在", "sessionId", sessionId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", context.getSessionId());
        result.put("createdAt", context.getCreatedAt().toString());
        result.put("executionLog", context.getExecutionLog());
        result.put("sharedData", context.snapshot());
        return result;
    }

    // 内部类
    public static class ProcessRequest {
        private String task;
        private String sessionId;

        public String getTask() { return task; }
        public void setTask(String task) { this.task = task; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }
}