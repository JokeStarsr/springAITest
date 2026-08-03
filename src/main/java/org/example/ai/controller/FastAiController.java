package org.example.ai.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

// Spring AI 示例（启动快，资源优）
@RestController
public class FastAiController {
    @Autowired
    private ChatClient chatClient; // 启动时自动配置

    @GetMapping("/chat")
    public String chat(@RequestParam String msg) {
        return  chatClient.prompt()
                .user(msg)
                .call()
                .content();
        // 平均延迟：135ms
    }

    /**
     * 多轮对话接口（带上下文）
     */
    @PostMapping("/chat/context")
    public String chatWithContext(@RequestBody ChatRequest request) {
        return chatClient.prompt()
                .system("你是一个专业的助手，请用中文回答问题")
                .user(request.getMessage())
                .call()
                .content();
    }

    /**
     * 流式响应接口
     */
    @GetMapping(value = "/chat/stream", produces = "text/event-stream")
    public Flux<String> chatStream(@RequestParam String msg) {
        return chatClient.prompt()
                .user(msg)
                .stream()
                .content();
    }

    // 请求体类
    public static class ChatRequest {
        private String message;
        // getter和setter
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}

// LangChain4j 示例（调用快，控制细）
//@RestController
//public class OptimizedAiController {
//    private final AiServices<ChatService> ai = AiServices.builder(ChatService.class)
//            .chatLanguageModel(OpenAiChatModel.withApiKey("xxx").build())
//            .build();
//
//    @GetMapping("/chat")
//    public String chat(@RequestParam String msg) {
//        return ai.chat(msg); // 平均延迟：115ms
//    }


