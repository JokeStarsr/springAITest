package org.example.ai.agent.impl;

import org.example.ai.agent.core.BaseAgent;
import org.example.ai.agent.core.AgentContext;
import org.example.ai.agent.skill.WeatherSkill;
import org.example.ai.agent.skill.CalculatorSkill;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 天气 Agent - 配备天气查询和计算技能
 * 可查询天气、比较城市温度、计算温差等
 */
@Component
public class WeatherAgent extends BaseAgent {

    public WeatherAgent(ChatClient chatClient, WeatherSkill weatherSkill, CalculatorSkill calculatorSkill) {
        super(chatClient, "weather-agent",
            "天气查询专家，负责查询和比较各城市天气，提供出行建议。可查询单日天气和未来多日预报，并进行温度对比分析。");
        addSkill(weatherSkill);
        addSkill(calculatorSkill);
    }

    @Override
    public String execute(String userInput) {
        // 先从上下文获取可能已有的城市信息
        if (context != null) {
            String cachedCity = context.get("target_city");
            if (cachedCity != null && !userInput.contains(cachedCity)) {
                userInput = userInput + " 城市: " + cachedCity;
            }
        }
        return super.execute(userInput);
    }
}