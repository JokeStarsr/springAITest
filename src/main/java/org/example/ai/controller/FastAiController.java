package org.example.ai.controller;

import org.example.ai.service.ConversationManager;
import org.example.ai.service.UsageTracker;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

// Spring AI 示例（启动快，资源优）
@RestController
public class FastAiController {

    private final ChatClient chatClient;
    private final UsageTracker usageTracker;
    private final ConversationManager conversations;

    public FastAiController(ChatClient chatClient, UsageTracker usageTracker, ConversationManager conversations) {
        this.chatClient = chatClient;
        this.usageTracker = usageTracker;
        this.conversations = conversations;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String msg) {
        if (msg.isBlank()) {
            throw new IllegalArgumentException("msg 不能为空");
        }
        long start = System.currentTimeMillis();
        ChatResponse response = chatClient.prompt()
                .user(msg)
                .call()
                .chatResponse();
        String result = response.getResult() != null ? response.getResult().getOutput().getText() : "";
        recordUsage("/chat", msg, result, start, response);
        return result;
    }

    /**
     * 多轮对话接口（真上下文）：携带 sessionId 时复用历史，缺省创建新会话
     */
    @PostMapping("/chat/context")
    public String chatWithContext(@RequestBody ChatRequest request) {
        String message = request.getMessage();
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }
        String sessionId = request.getSessionId() != null ? request.getSessionId() : java.util.UUID.randomUUID().toString();

        String history = conversations.historyText(sessionId, ConversationManager.MAX_HISTORY_TURNS);
        long start = System.currentTimeMillis();
        ChatResponse response = chatClient.prompt()
                .system("你是一个专业的助手，请用中文回答问题。若提供了【会话历史】，请结合先前对话保持连贯。")
                .user(history.isBlank() ? message : "【会话历史】\n" + history + "\n【当前问题】\n" + message)
                .call()
                .chatResponse();
        String result = response.getResult() != null ? response.getResult().getOutput().getText() : "";
        conversations.appendExchange(sessionId, message, result);
        recordUsage("/chat/context", message, result, start, response);
        return result;
    }

    /**
     * 流式响应接口
     */
    @GetMapping(value = "/chat/stream", produces = "text/event-stream")
    public Flux<String> chatStream(@RequestParam String msg) {
        if (msg.isBlank()) {
            throw new IllegalArgumentException("msg 不能为空");
        }
        return chatClient.prompt()
                .user(msg)
                .stream()
                .content();
    }

    private void recordUsage(String endpoint, String input, String output, long start, ChatResponse response) {
        Usage usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
        long in = -1, out = -1;
        if (usage != null) {
            in = usage.getPromptTokens() != null ? usage.getPromptTokens() : -1;
            out = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : -1;
        }
        usageTracker.recordWithUsage(endpoint, input, output, System.currentTimeMillis() - start, in, out);
    }

    // 请求体类
    public static class ChatRequest {
        private String message;
        private String sessionId;

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }
}