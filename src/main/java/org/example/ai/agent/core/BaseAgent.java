package org.example.ai.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.*;

/**
 * BaseAgent - 带有 ReAct 循环（推理→行动→观察）的 Agent 基类。
 * 每个 Agent 拥有一组 Skill，通过 LLM 推理决定调用哪个 Skill，
 * 循环执行直到任务完成或达到最大步数。
 */
public abstract class BaseAgent {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ChatClient chatClient;
    protected final String name;
    protected final String description;
    protected final List<Skill> skills = new ArrayList<>();
    protected AgentContext context;
    protected int maxSteps = 5;

    protected BaseAgent(ChatClient chatClient, String name, String description) {
        this.chatClient = chatClient;
        this.name = name;
        this.description = description;
    }

    /** 注册技能 */
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
     * 执行 Agent 任务 - ReAct 循环入口
     * @param userInput 用户输入
     * @return 最终回复
     */
    public String execute(String userInput) {
        context.log("Agent [" + name + "] 开始处理任务: " + userInput);

        StringBuilder thoughtLog = new StringBuilder();
        String currentObservation = userInput;

        for (int step = 0; step < maxSteps; step++) {
            context.log("Step " + (step + 1) + "/" + maxSteps);

            // 1. 推理：决定下一步行动
            String action = reason(currentObservation, step);
            thoughtLog.append("[Step ").append(step + 1).append(" 推理] ").append(action).append("\n");

            // 2. 判断是否完成
            if (isFinished(action)) {
                String finalAnswer = extractFinalAnswer(action);
                context.log("Agent [" + name + "] 任务完成");
                thoughtLog.append("[最终回答] ").append(finalAnswer);
                return finalAnswer;
            }

            // 3. 行动：执行选中的 Skill
            SkillCall call = parseSkillCall(action);
            if (call == null) {
                // 如果无法解析到具体动作，直接让 LLM 回答
                String finalAnswer = generateDirectAnswer(userInput, currentObservation);
                context.log("Agent [" + name + "] 无法解析动作，直接回答");
                return finalAnswer;
            }

            ToolResult result = executeSkill(call);
            context.log("执行技能: " + call.skillName + " → " + (result.isSuccess() ? "成功" : "失败"));
            thoughtLog.append("[技能 ").append(call.skillName).append("] ").append(result.formatForAgent()).append("\n");

            // 4. 观察：将结果作为下一轮输入
            currentObservation = result.isSuccess() ? result.getData() : "技能执行失败: " + result.getError();
        }

        // 达到最大步数，生成最终回答
        String finalAnswer = generateFinalAnswer(userInput, thoughtLog.toString());
        context.log("Agent [" + name + "] 达到最大步数，生成最终回答");
        return finalAnswer;
    }

    /**
     * ReAct 推理：让 LLM 决定下一步做什么
     */
    protected String reason(String observation, int step) {
        String skillsDesc = buildSkillsDescription();
        String prompt = String.format("""
            你是一个叫 %s 的 AI Agent，你的职责是: %s

            你可以使用以下技能:
            %s

            当前是第 %d 步。用户任务和历史观察:
            ---
            %s
            ---

            请决定下一步行动。回复格式:
            - 如果需要调用技能: SKILL: <技能名> | <参数JSON>
            - 如果任务已完成: FINISH: <最终回答>
            """, name, description, skillsDesc, step + 1, observation);

        return chatClient.prompt().user(prompt).call().content();
    }

    protected boolean isFinished(String action) {
        return action != null && action.trim().toUpperCase().startsWith("FINISH:");
    }

    protected String extractFinalAnswer(String action) {
        if (action == null) return "任务处理完成";
        String trimmed = action.trim();
        if (trimmed.toUpperCase().startsWith("FINISH:")) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    /** 解析 Skill 调用指令 */
    protected SkillCall parseSkillCall(String action) {
        if (action == null) return null;
        String trimmed = action.trim();
        if (trimmed.toUpperCase().startsWith("SKILL:")) {
            String content = trimmed.substring(6).trim();
            int pipeIdx = content.indexOf('|');
            if (pipeIdx > 0) {
                String skillName = content.substring(0, pipeIdx).trim();
                String paramsJson = content.substring(pipeIdx + 1).trim();
                return new SkillCall(skillName, paramsJson);
            }
            return new SkillCall(content, "{}");
        }
        return null;
    }

    /** 执行技能 */
    private ToolResult executeSkill(SkillCall call) {
        Skill skill = findSkill(call.skillName);
        if (skill == null) {
            return ToolResult.failure(call.skillName, "未知技能: " + call.skillName);
        }
        Map<String, Object> params = parseParams(call.paramsJson);
        return skill.execute(params);
    }

    private Skill findSkill(String name) {
        return skills.stream()
            .filter(s -> s.name().equalsIgnoreCase(name.trim()))
            .findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return Map.of();
        }
        try {
            // 简单 JSON 解析（不依赖 Jackson）
            Map<String, Object> map = new HashMap<>();
            String content = json.trim();
            if (content.startsWith("{") && content.endsWith("}")) {
                content = content.substring(1, content.length() - 1);
            }
            for (String pair : content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replaceAll("^\"|\"$", "");
                    String value = kv[1].trim().replaceAll("^\"|\"$", "");
                    map.put(key, value);
                }
            }
            return map;
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }

    private String buildSkillsDescription() {
        StringBuilder sb = new StringBuilder();
        for (Skill s : skills) {
            sb.append("- ").append(s.name()).append(": ").append(s.description())
              .append(" 参数: ").append(s.parametersSchema()).append("\n");
        }
        return sb.toString();
    }

    protected String generateDirectAnswer(String userInput, String observation) {
        return chatClient.prompt()
            .system("你是" + name + "，你的职责是: " + description)
            .user("用户输入: " + userInput + "\n\n相关信息: " + observation)
            .call().content();
    }

    protected String generateFinalAnswer(String userInput, String thoughtLog) {
        return chatClient.prompt()
            .system("你是" + name + "。请根据以下思考过程生成最终回答。")
            .user("用户原始问题: " + userInput + "\n\n思考过程:\n" + thoughtLog + "\n\n请生成简洁友好的最终回答。")
            .call().content();
    }

    /** Skill 调用描述 */
    protected record SkillCall(String skillName, String paramsJson) {}
}