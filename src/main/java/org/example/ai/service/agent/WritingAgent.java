package org.example.ai.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

//@Slf4j
@Component
public class WritingAgent implements IAgent {

    private final ChatClient chatClient;
    private static final Logger log = LoggerFactory.getLogger(WritingAgent.class); // 手动定义
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WritingAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String execute(String input) {
        log.info("WritingAgent开始处理: {}", input);

        try {
            // 使用LLM进行内容创作
            String content = chatClient.prompt()
                    .system("""
                            你是一个专业的文案写手，请根据要求撰写高质量的内容。
                            要求：
                            1. 语言流畅，逻辑清晰
                            2. 内容详实，有深度
                            3. 格式规范，易于阅读
                            4. 使用中文撰写
                            5. 请确保你的回答是纯文本，不要包含任何JSON格式或特殊标记
                            """)
                    .user(input)
                    .call()
                    .content();

            // 确保返回有效的JSON格式
            ObjectNode response = objectMapper.createObjectNode();
            response.put("content", content != null ? content : "内容创作完成，但未返回有效结果");

            log.info("WritingAgent处理完成");
            return response.toString();

        } catch (Exception e) {
            log.error("WritingAgent处理异常: {}", e.getMessage(), e);
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "处理您的请求时出现错误: " + e.getMessage());
            return errorResponse.toString();
        }
    }

    @Override
    public String getName() {
        return "writing-agent";
    }

    @Override
    public String getDescription() {
        return "写作Agent，可以撰写各种类型的文档和内容";
    }
}