package org.example.ai.agent.core;

import java.util.Map;

/**
 * Skill - Agent 可调用的工具/能力。
 * 每个 Skill 是一个原子操作，Agent 通过组合多个 Skill 完成复杂任务。
 */
public interface Skill {

    /** 技能唯一标识 */
    String name();

    /** 技能描述（供 Agent 决策时参考） */
    String description();

    /** 技能参数说明（供 LLM 理解如何调用） */
    default String parametersSchema() {
        return "{}";
    }

    /**
     * 执行技能
     * @param params 参数，key 为参数名，value 为参数值
     * @return 执行结果
     */
    ToolResult execute(Map<String, Object> params);
}