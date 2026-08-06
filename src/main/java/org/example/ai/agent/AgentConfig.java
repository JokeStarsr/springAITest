package org.example.ai.agent;

import org.example.ai.agent.skill.CalculatorSkill;
import org.example.ai.agent.skill.TextAnalysisSkill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置类 - 注册本地 Skill Bean。
 * WeatherSkill 和 WebSearchSkill 已通过 @Component 自动注册（它们使用 @Value 注入外部API配置）。
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
