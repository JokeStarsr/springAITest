package org.example.ai.agent;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 快模型工厂：不注册为 Spring bean（避免与自动配置的 openAiChatModel 冲突），
 * 由调用方在启用 fast-model 路由时按需构建（构建成本极低）。
 */
@Component
public class FastChatModelFactory {

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${app.fast-model:deepseek-v4-flash}")
    private String fastModel;

    public ChatModel create() {
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