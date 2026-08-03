package org.example.ai.controller;


import org.example.ai.service.agent.AgentOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
//@RequiredArgsConstructor
public class AgentController {

    private final AgentOrchestrator agentOrchestrator;

    private static final Logger log = LoggerFactory.getLogger(AgentController.class); // 手动定义

    @Autowired // 添加这个注解
    public AgentController(AgentOrchestrator agentOrchestrator) {
        this.agentOrchestrator = agentOrchestrator;
    }

    /**
     * 智能Agent路由 - 自动选择合适的Agent
     */
    @PostMapping("/process")
    public String process(@RequestBody ProcessRequest request) {
        log.info("收到任务: {}", request.getTask());
        return agentOrchestrator.process(request.getTask());
    }

    /**
     * 获取所有可用的Agent
     */
    @GetMapping("/agents")
    public Map<String, String> getAgents() {
        return agentOrchestrator.getAvailableAgents();
    }

    /**
     * 直接调用指定Agent
     */
    @PostMapping("/execute/{agentName}")
    public String executeAgent(@PathVariable String agentName,
                               @RequestBody ProcessRequest request) {
        return agentOrchestrator.processWithAgent(agentName, request.getTask());
    }

    // 内部类
    public static class ProcessRequest {
        private String task;

        public String getTask() {
            return task;
        }

        public void setTask(String task) {
            this.task = task;
        }
    }
}