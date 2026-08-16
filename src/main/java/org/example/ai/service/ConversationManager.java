package org.example.ai.service;

import org.example.ai.agent.core.AgentContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话管理器：并发安全的用户会话存储（代替原 Controller 内 LinkedHashMap）。
 * 每个会话持有 AgentContext（agent 黑板）+ 多轮对话历史（user/assistant 交替）。
 * 容量上限 50，超限驱逐最久未访问的会话。
 */
@Service
public class ConversationManager {

    private static final int MAX_CONVERSATIONS = 50;
    public static final int MAX_HISTORY_TURNS = 8;

    private record Conversation(AgentContext context, List<String[]> history, AtomicLong lastAccess) {}

    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();

    public AgentContext getOrCreateContext(String sessionId) {
        Conversation conv = getOrCreate(sessionId);
        conv.context().setHistoryText(historyText(sessionId, MAX_HISTORY_TURNS));
        return conv.context();
    }

    public void appendExchange(String sessionId, String userMessage, String assistantMessage) {
        Conversation conv = conversations.get(sessionId);
        if (conv == null) return;
        synchronized (conv.history()) {
            if (userMessage != null && !userMessage.isBlank()) conv.history().add(new String[]{userMessage});
            if (assistantMessage != null && !assistantMessage.isBlank()) conv.history().add(new String[]{assistantMessage});
        }
        conv.lastAccess().set(System.currentTimeMillis());
    }

    /** 最近 N 轮历史的文本块（无历史返回空串） */
    public String historyText(String sessionId, int maxTurns) {
        Conversation conv = conversations.get(sessionId);
        if (conv == null) return "";
        List<String[]> h = conv.history();
        synchronized (h) {
            if (h.isEmpty()) return "";
            int start = Math.max(0, h.size() - maxTurns * 2);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < h.size(); i++) {
                String role = (i % 2 == 0) ? "用户" : "助手";
                sb.append(role).append(": ").append(h.get(i)[0]).append("\n");
            }
            return sb.toString();
        }
    }

    public void clear(String sessionId) {
        conversations.remove(sessionId);
    }

    private Conversation getOrCreate(String sessionId) {
        long now = System.currentTimeMillis();
        Conversation conv = conversations.computeIfAbsent(sessionId, k ->
                new Conversation(new AgentContext(k), new ArrayList<>(), new AtomicLong(now)));
        conv.lastAccess().set(now);
        evictIfNeeded();
        return conv;
    }

    private void evictIfNeeded() {
        if (conversations.size() <= MAX_CONVERSATIONS) return;
        String oldestKey = null;
        long oldestAccess = Long.MAX_VALUE;
        for (Map.Entry<String, Conversation> e : conversations.entrySet()) {
            long last = e.getValue().lastAccess().get();
            if (last < oldestAccess) {
                oldestAccess = last;
                oldestKey = e.getKey();
            }
        }
        if (oldestKey != null) {
            conversations.remove(oldestKey);
        }
    }
}