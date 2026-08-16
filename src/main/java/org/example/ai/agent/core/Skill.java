package org.example.ai.agent.core;

/**
 * Skill - Agent 可注册的能力载体。
 * 实际工具逻辑通过 @Tool 注解方法暴露给 Spring AI（工具调用框架自动路由执行）。
 */
public interface Skill {

    /** 技能唯一标识 */
    String name();

    /** 技能描述（供 Agent 决策时参考） */
    String description();
}