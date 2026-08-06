package org.example.ai.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.*;

/**
 * BaseAgent - 基于 Spring AI 原生 @Tool 注解的 Agent 基类。
 * 升级前：手动 ReAct 循环（LLM输出文本 → 正则匹配 SKILL:/FINISH:）
 * 升级后：Spring AI 自动函数调用（LLM输出结构化JSON → 框架自动路由执行）
 * <p>
 * 核心变化：
 * - 不再自己解析 LLM 输出、不再手动循环
 * - 将 Skill 对象（带 @Tool 方法）直接传给 ChatClient.tools()
 * - Spring AI 内部自动处理 ReAct 循环（Reason → ToolCall → Result → 自动回传）
 */
public abstract class BaseAgent {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ChatClient chatClient;
    protected final String name;
    protected final String description;
    protected final List<Skill> skills = new ArrayList<>();
    protected AgentContext context;

    protected BaseAgent(ChatClient chatClient, String name, String description) {
        this.chatClient = chatClient;
        this.name = name;
        this.description = description;
    }

    /** 注册技能（同时作为 Spring AI Tool 注册） */
    protected void addSkill(Skill skill) {
        skills.add(skill);
    }

    /** 设置共享上下文 */
    public void setContext(AgentContext context) {
        this.context = context;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<Skill> getSkills() { return skills; }

    /**
     * 执行 Agent 任务 —— 使用 Spring AI 原生 Tool Calling。
     * 框架内部自动处理：LLM推理 → 工具调用 → 结果回传 → 再推理 → 最终回答。
     * @param userInput 用户输入
     * @return 最终回复
     */
    public String execute(String userInput) {
        if (context != null) {
            context.log("Agent [" + name + "] 开始处理任务（Spring AI Tool Calling）: " + truncate(userInput));
        }

        try {
            String result = chatClient.prompt()
                .system(buildSystemPrompt())
                .user(userInput)
                .tools(buildToolArray())
                .call()
                .content();

            if (context != null) {
                context.log("Agent [" + name + "] 任务完成");
            }
            return result != null ? result : "Agent 处理完成，但未生成回复。";

        } catch (Exception e) {
            log.error("Agent [{}] 执行异常: {}", name, e.getMessage());
            if (context != null) {
                context.log("Agent [" + name + "] 执行异常: " + e.getMessage());
            }
            return "处理失败: " + e.getMessage();
        }
    }

    /** 构建系统提示词 */
    private String buildSystemPrompt() {
        return String.format("""
            你是 %s，一个专业的 AI Agent。
            你的职责: %s

            你有以下工具可用（系统会自动调用，你只需决定何时使用）:
            %s

            重要规则:
            1. 如果任务需要工具，系统会自动调用工具并返回结果给你
            2. 收到工具结果后，基于结果继续推理或给出最终回答
            3. 最终回答要简洁、准确、友好
            4. 如果工具调用失败，尝试其他方式或如实告知用户
            """, name, description, buildToolsDescription());
    }

    /** 构建工具描述（供 system prompt 使用） */
    private String buildToolsDescription() {
        StringBuilder sb = new StringBuilder();
        for (Skill s : skills) {
            sb.append("- ").append(s.name()).append(": ").append(s.description())
              .append(" 参数: ").append(s.parametersSchema()).append("\n");
        }
        return sb.toString();
    }

    /** 构建工具数组（传给 ChatClient.tools()） */
    private Object[] buildToolArray() {
        return skills.toArray();
    }

    private String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
