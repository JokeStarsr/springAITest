package org.example.ai.agent;

import org.example.ai.agent.skill.CalculatorSkill;
import org.example.ai.agent.skill.TextAnalysisSkill;
import org.example.ai.agent.skill.WeatherSkill;
import org.example.ai.agent.skill.WebSearchSkill;
import org.example.ai.service.WeatherService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置类 - 注册所有 Skill Bean 供 Agent 依赖注入
 */
@Configuration
public class AgentConfig {

    @Bean
    public WeatherSkill weatherSkill(WeatherService weatherService) {
        return new WeatherSkill(weatherService);
    }

    @Bean
    public CalculatorSkill calculatorSkill() {
        return new CalculatorSkill();
    }

    @Bean
    public WebSearchSkill webSearchSkill() {
        return new WebSearchSkill();
    }

    @Bean
    public TextAnalysisSkill textAnalysisSkill() {
        return new TextAnalysisSkill();
    }
}