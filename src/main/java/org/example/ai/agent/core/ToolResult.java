package org.example.ai.agent.core;

/**
 * Skill 执行结果
 */
public class ToolResult {

    private final boolean success;
    private final String data;
    private final String error;
    private final String skillName;

    public ToolResult(String skillName, boolean success, String data, String error) {
        this.skillName = skillName;
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static ToolResult success(String skillName, String data) {
        return new ToolResult(skillName, true, data, null);
    }

    public static ToolResult failure(String skillName, String error) {
        return new ToolResult(skillName, false, null, error);
    }

    public boolean isSuccess() { return success; }
    public String getData() { return data; }
    public String getError() { return error; }
    public String getSkillName() { return skillName; }

    /** 格式化输出供 Agent 阅读 */
    public String formatForAgent() {
        if (success) {
            return "[技能 " + skillName + " 执行成功]\n" + data;
        } else {
            return "[技能 " + skillName + " 执行失败] " + error;
        }
    }

    @Override
    public String toString() {
        return formatForAgent();
    }
}