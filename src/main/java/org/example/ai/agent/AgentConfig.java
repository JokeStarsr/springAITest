package org.example.ai.agent;

import org.example.ai.agent.skill.CalculatorSkill;
import org.example.ai.agent.skill.TextAnalysisSkill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置类 - 注册本地 Skill Bean。
 * WeatherSkill / WebSearchSkill 通过 @Component 自动注册。
 * 快模型不在此注册为 Bean（避免与自动配置的 ChatModel 冲突），见 FastChatModelFactory。
 */
@Configuration
public class AgentConfig {

    @Bean
    public CalculatorSkill calculatorSkill() {
        return new CalculatorSkill();
    }

    @Bean
    public TextAnalysisSkill textAnalysisSkill() {
        return new TextAnalysisSkill();
    }
}