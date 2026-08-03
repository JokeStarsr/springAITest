package org.example.ai.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.example.ai.controller.AgentController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

//@Slf4j
@Service
public class AgentOrchestrator {

    private final Map<String, IAgent> agents = new HashMap<>();

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class); // 手动定义


    public AgentOrchestrator(WeatherAgent weatherAgent,
                             ResearchAgent researchAgent,
                             WritingAgent writingAgent) {
        // 注册所有Agent
        agents.put(weatherAgent.getName(), weatherAgent);
        agents.put(researchAgent.getName(), researchAgent);
        agents.put(writingAgent.getName(), writingAgent);
    }

    /**
     * 根据任务类型选择合适的Agent
     */
    public String process(String task) {
        log.info("开始处理任务: {}", task);

        // 1. 判断任务类型
        String agentName = determineAgent(task);

        // 2. 获取对应的Agent
        IAgent agent = agents.get(agentName);

        if (agent == null) {
            return "无法识别任务类型，请重新描述您的需求";
        }

        // 3. 执行Agent
        String result = agent.execute(task);

        log.info("任务处理完成: {}", result);
        return result;
    }

    /**
     * 判断应该使用哪个Agent
     */
    private String determineAgent(String task) {
        task = task.toLowerCase();

        // 天气查询关键词
        if (task.contains("天气") || task.contains("气温") || task.contains("温度")) {
            return "weather-agent";
        }

        // 研究分析关键词
        if (task.contains("分析") || task.contains("研究") || task.contains("为什么")
                || task.contains("原理") || task.contains("背景")) {
            return "research-agent";
        }

        // 写作创作关键词
        if (task.contains("写") || task.contains("撰写") || task.contains("创作")
                || task.contains("文章") || task.contains("报告")) {
            return "writing-agent";
        }

        // 默认使用研究Agent
        return "research-agent";
    }

    /**
     * 获取所有可用的Agent
     */
    public Map<String, String> getAvailableAgents() {
        Map<String, String> agentInfo = new HashMap<>();
        for (IAgent agent : agents.values()) {
            agentInfo.put(agent.getName(), agent.getDescription());
        }
        return agentInfo;
    }

    /**
     * 使用指定Agent处理任务
     */
    public String processWithAgent(String agentName, String task) {
        log.info("使用指定Agent处理任务: agentName={}, task={}", agentName, task);

        IAgent agent = agents.get(agentName);
        if (agent == null) {
            return "{\"content\":\"未找到Agent: " + agentName + "\",\"type\":\"text\"}";
        }

        String result = agent.execute(task);
        log.info("指定Agent任务处理完成");
        return result;
    }
}