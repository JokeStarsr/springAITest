package org.example.ai.controller;

import org.example.ai.agent.core.AgentContext;
import org.example.ai.agent.impl.CoordinatorAgent;
import org.example.ai.service.ConversationManager;
import org.example.ai.service.UsageTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Agent 控制器 - 基于新架构的多 Agent 协作入口（会话并发安全）
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final CoordinatorAgent coordinator;
    private final UsageTracker usageTracker;
    private final ConversationManager conversations;
    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    public AgentController(CoordinatorAgent coordinator, UsageTracker usageTracker, ConversationManager conversations) {
        this.coordinator = coordinator;
        this.usageTracker = usageTracker;
        this.conversations = conversations;
    }

    /**
     * 智能 Agent 路由 - 自动协调多 Agent 协作（携带会话历史，sessionId 复用上下文）
     */
    @PostMapping("/process")
    public Map<String, Object> process(@RequestBody ProcessRequest request) {
        String task = request.getTask().trim();
        String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();
        log.info("收到任务: {} session={}", task, sessionId);

        AgentContext context = conversations.getOrCreateContext(sessionId);

        long startTime = System.currentTimeMillis();
        String result = coordinator.execute(task, context);
        long duration = System.currentTimeMillis() - startTime;
        conversations.appendExchange(sessionId, task, result);

        usageTracker.record("agent/process", task, result, duration);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", result);
        response.put("type", "agent_coordinated");
        response.put("sessionId", sessionId);
        response.put("duration", duration + "ms");
        response.put("executionLog", context.getExecutionLog());
        response.put("sharedData", context.snapshot());
        response.put("usage", usageTracker.getStats());
        return response;
    }

    /**
     * 直接调用指定 Agent
     */
    @PostMapping("/execute/{agentName}")
    public Map<String, Object> executeAgent(@PathVariable String agentName,
                                            @RequestBody ProcessRequest request) {
        String task = request.getTask().trim();
        String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();
        AgentContext context = conversations.getOrCreateContext(sessionId);

        String routed = "使用 " + agentName + " 处理: " + task;
        long startTime = System.currentTimeMillis();
        String result = coordinator.execute(routed, context);
        long duration = System.currentTimeMillis() - startTime;
        conversations.appendExchange(sessionId, task, result);

        usageTracker.record("agent/execute/" + agentName, task, result, duration);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", result);
        response.put("type", "agent_direct");
        response.put("agentName", agentName);
        response.put("sessionId", sessionId);
        response.put("duration", duration + "ms");
        response.put("executionLog", context.getExecutionLog());
        response.put("usage", usageTracker.getStats());
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
        AgentContext context = conversations.getOrCreateContext(sessionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", context.getSessionId());
        result.put("createdAt", context.getCreatedAt().toString());
        result.put("executionLog", context.getExecutionLog());
        result.put("sharedData", context.snapshot());
        return result;
    }

    /**
     * 清空会话（历史与共享数据）
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Map<String, String> clearSession(@PathVariable String sessionId) {
        conversations.clear(sessionId);
        return Map.of("status", "ok", "sessionId", sessionId);
    }

    public static class ProcessRequest {
        private String task;
        private String sessionId;

        public String getTask() { return task; }
        public void setTask(String task) { this.task = task; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }
}