package org.example.ai.agent.core;

import org.example.ai.service.UsageTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.*;

/**
 * BaseAgent - 基于 Spring AI 原生 @Tool 注解的 Agent 基类（无状态）。
 * <p>
 * Agent 实例是单例、无状态的：会话上下文通过 execute() 参数传入，
 * 避免并发请求共享可变字段导致串台。
 * 调用时自动携带会话历史（多轮对话），并按真实 TokenUsage 记录用量。
 */
public abstract class BaseAgent {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ChatClient chatClient;
    protected final UsageTracker usageTracker;
    protected final String name;
    protected final String description;
    protected final List<Skill> skills = new ArrayList<>();

    protected BaseAgent(ChatClient chatClient, UsageTracker usageTracker, String name, String description) {
        this.chatClient = chatClient;
        this.usageTracker = usageTracker;
        this.name = name;
        this.description = description;
    }

    /** 注册技能（同时作为 Spring AI Tool 注册） */
    protected void addSkill(Skill skill) {
        skills.add(skill);
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<Skill> getSkills() { return skills; }

    /**
     * 执行 Agent 任务（携带会话上下文）。
     * @param userInput 用户输入
     * @param context 会话上下文（黑板 + 历史），可空
     * @return 最终回复
     */
    public String execute(String userInput, AgentContext context) {
        if (context != null) {
            context.log("Agent [" + name + "] 开始处理任务: " + truncate(userInput));
        }

        try {
            long start = System.currentTimeMillis();
            ChatResponse response = chatClient.prompt()
                .system(buildSystemPrompt())
                .user(buildUserPrompt(userInput, context))
                .tools(buildToolArray())
                .call()
                .chatResponse();

            String result = response.getResult() != null ? response.getResult().getOutput().getText() : null;
            recordUsage(userInput, result, start, response.getMetadata() != null ? response.getMetadata().getUsage() : null);

            if (context != null) {
                context.log("Agent [" + name + "] 任务完成");
            }
            return result != null && !result.isBlank() ? result : "Agent 处理完成，但未生成回复。";

        } catch (Exception e) {
            log.error("Agent [{}] 执行异常: {}", name, e.getMessage());
            if (context != null) {
                context.log("Agent [" + name + "] 执行异常: " + e.getMessage());
            }
            return "处理失败: " + e.getMessage();
        }
    }

    /** 用户提示 = 会话历史 + 当前输入 */
    protected String buildUserPrompt(String userInput, AgentContext context) {
        String history = context != null ? context.getHistoryText() : "";
        if (history.isBlank()) {
            return userInput;
        }
        return "【会话历史（先前多轮对话，供你保持连贯）】\n" + history
                + "\n【当前问题】\n" + userInput;
    }

    private void recordUsage(String input, String output, long durationMs, Usage usage) {
        long in = -1, out = -1;
        if (usage != null) {
            in = usage.getPromptTokens() != null ? usage.getPromptTokens() : -1;
            out = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : -1;
        }
        usageTracker.recordWithUsage("agent." + name, input, output, durationMs, in, out);
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
            5. 若提供了【会话历史】，请结合先前的对话保持回答连贯
            """, name, description, buildToolsDescription());
    }

    /** 构建工具描述（供 system prompt 使用） */
    private String buildToolsDescription() {
        StringBuilder sb = new StringBuilder();
        for (Skill s : skills) {
            sb.append("- ").append(s.name()).append(": ").append(s.description()).append("\n");
        }
        return sb.toString();
    }

    /** 构建工具数组（传给 ChatClient.tools()） */
    private Object[] buildToolArray() {
        return skills.toArray();
    }

    protected String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}