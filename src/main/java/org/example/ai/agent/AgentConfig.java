package org.example.ai.agent;

import org.example.ai.agent.skill.CalculatorSkill;
import org.example.ai.agent.skill.TextAnalysisSkill;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置类 - 注册本地 Skill Bean 与快模型（成本路由）。
 * WeatherSkill / WebSearchSkill 通过 @Component 自动注册。
 * 快模型仅用于协调/汇总/文件骨架等对质量不敏感的结构任务（app.fast-model-enabled=true 开启）。
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

    @Bean(name = "fastChatModel")
    public ChatModel fastChatModel(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${app.fast-model:deepseek-v4-flash}") String fastModel) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(fastModel)
                        .temperature(0.3)
                        .build())
                .build();
    }
}