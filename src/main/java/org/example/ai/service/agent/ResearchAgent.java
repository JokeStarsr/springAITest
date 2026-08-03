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
public class ResearchAgent implements IAgent {

    private final ChatClient chatClient;
    private static final Logger log = LoggerFactory.getLogger(ResearchAgent.class); // 手动定义
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResearchAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String execute(String input) {
        log.info("ResearchAgent开始处理: {}", input);

        try {
            // 使用LLM进行深度研究
            String researchResult = chatClient.prompt()
                    .system("""
                            你是一个专业的研究员，请对用户提出的问题进行深入分析。
                            要求：
                            1. 提供详细的背景信息
                            2. 分析关键要点
                            3. 给出专业建议
                            4. 使用中文回答
                            5. 请确保你的回答是纯文本，不要包含任何JSON格式或特殊标记
                            """)
                    .user(input)
                    .call()
                    .content();

            // 确保返回有效的JSON格式
            ObjectNode response = objectMapper.createObjectNode();
            response.put("content", researchResult != null ? researchResult : "研究完成，但未返回有效结果");

            log.info("ResearchAgent处理完成");
            return response.toString();

        } catch (Exception e) {
            log.error("ResearchAgent处理异常: {}", e.getMessage(), e);
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "处理您的请求时出现错误: " + e.getMessage());
            return errorResponse.toString();
        }
    }

    @Override
    public String getName() {
        return "research-agent";
    }

    @Override
    public String getDescription() {
        return "研究Agent，可以对复杂问题进行深度分析和研究";
    }
}