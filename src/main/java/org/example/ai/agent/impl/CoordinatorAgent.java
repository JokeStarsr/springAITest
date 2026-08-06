package org.example.ai.agent.impl;

import org.example.ai.agent.core.*;
import org.example.ai.agent.skill.WebSearchSkill;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 协调 Agent（元 Agent）- 负责拆解复杂任务，分派给子 Agent，汇总结果。
 * 这是真正的"Agent 之 Agent"，是系统的核心调度器。
 */
@Component
public class CoordinatorAgent extends BaseAgent {

    private final Map<String, BaseAgent> subAgents;

    public CoordinatorAgent(ChatClient chatClient, WebSearchSkill webSearchSkill,
                            WeatherAgent weatherAgent, ResearchAgent researchAgent,
                            WritingAgent writingAgent) {
        super(chatClient, "coordinator",
            "任务协调专家，负责分析复杂任务，拆解为子任务，分派给专业的子Agent执行，并汇总结果为最终输出。");
        addSkill(webSearchSkill);

        this.subAgents = new LinkedHashMap<>();
        this.subAgents.put(weatherAgent.getName(), weatherAgent);
        this.subAgents.put(researchAgent.getName(), researchAgent);
        this.subAgents.put(writingAgent.getName(), writingAgent);
    }

    /**
     * 协调执行：分解任务 → 分派 → 汇总
     */
    @Override
    public String execute(String userInput) {
        context.log("Coordinator 开始协调处理任务: " + userInput);

        // 0. 快速路由：根据关键词直接匹配，避免 LLM 误判
        List<TaskAssignment> quickAssignments = quickRoute(userInput);
        if (!quickAssignments.isEmpty()) {
            context.log("快速路由: " + quickAssignments.size() + " 个子任务");
            return executeAssignments(quickAssignments, userInput);
        }

        // 1. 分析任务，决定是否需要分解
        String plan = analyzeTask(userInput);
        context.log("任务分析: " + truncate(plan, 200));

        // 2. 提取子任务和对应的 Agent
        List<TaskAssignment> assignments = parseAssignments(plan, userInput);
        context.log("分解为 " + assignments.size() + " 个子任务");

        if (assignments.isEmpty()) {
            // 无法分解，直接处理
            return super.execute(userInput);
        }

        return executeAssignments(assignments, userInput);
    }

    /** 快速路由：根据关键词直接匹配Agent */
    private List<TaskAssignment> quickRoute(String input) {
        List<TaskAssignment> result = new ArrayList<>();
        boolean hasWeather = input.contains("天气") || input.contains("温度") || input.contains("气温") 
                          || input.contains("下雨") || input.contains("刮风") || input.contains("出行");
        boolean hasWrite = input.contains("写") || input.contains("撰写") || input.contains("文章") 
                        || input.contains("文案") || input.contains("报告") || input.contains("方案");
        boolean hasResearch = input.contains("研究") || input.contains("分析") || input.contains("搜索") 
                           || input.contains("调查") || input.contains("原理");

        if (hasWeather && (hasResearch || hasWrite)) {
            // 多关键词：需要 LLM 分析
            return result;
        }
        if (hasWeather) {
            result.add(new TaskAssignment("weather-agent", "天气查询", input));
            return result;
        }
        if (hasWrite && !hasResearch) {
            result.add(new TaskAssignment("writing-agent", "写作任务", input));
            return result;
        }
        return result;
    }

    /** 执行任务分配列表 */
    private String executeAssignments(List<TaskAssignment> assignments, String userInput) {

        // 3. 顺序执行子任务（Agent 间通过 context 共享数据）
        List<String> subResults = new ArrayList<>();
        for (TaskAssignment ta : assignments) {
            BaseAgent agent = subAgents.get(ta.agentName);
            if (agent == null) {
                context.log("未找到 Agent: " + ta.agentName);
                continue;
            }

            // 设置共享上下文
            agent.setContext(context);

            // 发送跨 Agent 消息
            context.sendMessage(AgentMessage.request(
                "coordinator", ta.agentName, ta.subject, ta.task));

            // 执行
            context.log("分派给 " + ta.agentName + ": " + ta.task);
            String result = agent.execute(ta.task);
            subResults.add("[" + ta.agentName + "] " + result);

            // 结果写入共享上下文
            context.put(ta.agentName + "_result", result);
            context.put("last_result", result);

            // 发送完成消息
            context.sendMessage(AgentMessage.result(
                ta.agentName, "coordinator", ta.subject, truncate(result, 200)));
        }

        // 4. 汇总所有子任务结果
        String summary = aggregateResults(userInput, subResults);
        context.log("任务汇总完成");
        return summary;
    }

    /** 让 LLM 分析任务并生成执行计划 */
    private String analyzeTask(String userInput) {
        String agentsDesc = buildAgentsDescription();
        return chatClient.prompt()
            .system("""
                你是一个任务协调专家。分析用户任务，决定由哪个Agent来处理。

                可用的专业Agent（必须从以下选择）:
                %s

                重要规则:
                1. 天气查询、温度比较、出行建议 → 必须使用 weather-agent
                2. 深度研究、数据分析、搜索 → 必须使用 research-agent
                3. 写作、文案、文章 → 必须使用 writing-agent
                4. 复杂任务可分解为多个Agent协作

                请按以下格式输出执行计划（每行一个子任务）:
                AGENT: <agent-name> | SUBJECT: <主题> | TASK: <具体任务描述>

                如果任务简单不需要分解，输出: DIRECT: <直接回答>
                """.formatted(agentsDesc))
            .user("用户任务: " + userInput)
            .call().content();
    }

    private List<TaskAssignment> parseAssignments(String plan, String originalInput) {
        List<TaskAssignment> assignments = new ArrayList<>();
        for (String line : plan.split("\\n")) {
            line = line.trim();
            if (line.startsWith("AGENT:") || line.startsWith("AGENT：")) {
                String content = line.replaceFirst("AGENT[：:]\\s*", "");
                String[] parts = content.split("\\|");
                String agentName = "";
                String subject = "";
                String task = "";

                for (String part : parts) {
                    part = part.trim();
                    if (part.toUpperCase().startsWith("SUBJECT:") || part.startsWith("SUBJECT：")) {
                        subject = part.replaceFirst("(?i)SUBJECT[：:]\\s*", "").trim();
                    } else if (part.toUpperCase().startsWith("TASK:") || part.startsWith("TASK：")) {
                        task = part.replaceFirst("(?i)TASK[：:]\\s*", "").trim();
                    } else if (!part.isEmpty()) {
                        agentName = part.trim();
                    }
                }

                if (!agentName.isEmpty() && !task.isEmpty()) {
                    if (subject.isEmpty()) subject = task;
                    assignments.add(new TaskAssignment(agentName, subject, task));
                }
            }
        }

        // 如果没有解析到子任务，创建默认分配
        if (assignments.isEmpty() && !plan.contains("DIRECT:")) {
            // 根据关键词智能分配
            if (originalInput.contains("天气") || originalInput.contains("温度")) {
                assignments.add(new TaskAssignment("weather-agent", "天气查询", originalInput));
            } else if (originalInput.contains("写") || originalInput.contains("撰写") || originalInput.contains("文章")) {
                assignments.add(new TaskAssignment("writing-agent", "写作任务", originalInput));
            } else {
                assignments.add(new TaskAssignment("research-agent", "研究分析", originalInput));
            }
        }
        return assignments;
    }

    /** 汇总多个 Agent 的结果 */
    private String aggregateResults(String originalInput, List<String> subResults) {
        String combined = String.join("\n\n", subResults);
        return chatClient.prompt()
            .system("你是一个结果汇总专家。请将多个Agent的执行结果整合为一份完整的最终回答。")
            .user("原始任务: " + originalInput + "\n\n各Agent执行结果:\n" + combined + "\n\n请生成一份完整、连贯的最终回答。")
            .call().content();
    }

    /** 获取所有 Agent 的描述 */
    public Map<String, String> getAvailableAgents() {
        Map<String, String> result = new LinkedHashMap<>();
        for (BaseAgent agent : subAgents.values()) {
            StringBuilder sb = new StringBuilder(agent.getDescription());
            sb.append(" | 技能: ");
            for (Skill s : agent.getSkills()) {
                sb.append(s.name()).append(",");
            }
            result.put(agent.getName(), sb.toString());
        }
        return result;
    }

    /** 获取所有 Agent 的详细信息（含技能列表） */
    public List<Map<String, Object>> getAgentsDetail() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (BaseAgent agent : subAgents.values()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("name", agent.getName());
            detail.put("description", agent.getDescription());
            List<Map<String, String>> skillList = new ArrayList<>();
            for (Skill s : agent.getSkills()) {
                skillList.add(Map.of("name", s.name(), "description", s.description()));
            }
            detail.put("skills", skillList);
            result.add(detail);
        }
        return result;
    }

    private String buildAgentsDescription() {
        StringBuilder sb = new StringBuilder();
        for (BaseAgent agent : subAgents.values()) {
            sb.append("- ").append(agent.getName()).append(": ").append(agent.getDescription()).append("\n");
        }
        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private record TaskAssignment(String agentName, String subject, String task) {}
}