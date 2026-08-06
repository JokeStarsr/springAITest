package org.example.ai.agent.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentContext - Agent 之间共享的"黑板"。
 * 各 Agent 可以读写共享数据，实现跨 Agent 信息传递。
 * 线程安全，支持并发 Agent 访问。
 */
public class AgentContext {

    private final String sessionId;
    private final Instant createdAt;
    private final Map<String, Object> sharedData = new ConcurrentHashMap<>();
    private final List<AgentMessage> messages = Collections.synchronizedList(new ArrayList<>());
    private final List<String> executionLog = Collections.synchronizedList(new ArrayList<>());

    public AgentContext(String sessionId) {
        this.sessionId = sessionId;
        this.createdAt = Instant.now();
    }

    // ====== 共享数据存取 ======

    /** 写入共享数据 */
    public void put(String key, Object value) {
        sharedData.put(key, value);
        log("共享数据写入: " + key + " = " + truncate(value));
    }

    /** 读取共享数据 */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) sharedData.get(key);
    }

    /** 读取共享数据，带默认值 */
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) sharedData.getOrDefault(key, defaultValue);
    }

    /** 条件写入（仅当 key 不存在时写入） */
    public void putIfAbsent(String key, Object value) {
        sharedData.putIfAbsent(key, value);
    }

    public Map<String, Object> snapshot() {
        return new LinkedHashMap<>(sharedData);
    }

    // ====== Agent 间消息 ======

    /** 发送消息给其他 Agent */
    public void sendMessage(AgentMessage msg) {
        messages.add(msg);
        log("消息: " + msg.getFromAgent() + " → " + msg.getToAgent() + " [" + msg.getSubject() + "]");
    }

    /** 获取发给指定 Agent 的未读消息 */
    public List<AgentMessage> getMessagesFor(String agentName) {
        List<AgentMessage> result = new ArrayList<>();
        for (AgentMessage msg : messages) {
            if (msg.getToAgent().equals(agentName) || msg.getToAgent().equals("*")) {
                result.add(msg);
            }
        }
        return result;
    }

    // ====== 执行日志 ======

    public void log(String entry) {
        executionLog.add("[" + Instant.now() + "] " + entry);
    }

    public List<String> getExecutionLog() {
        return new ArrayList<>(executionLog);
    }

    public String getExecutionLogAsString() {
        return String.join("\n", executionLog);
    }

    // ====== 元数据 ======

    public String getSessionId() { return sessionId; }
    public Instant getCreatedAt() { return createdAt; }

    private String truncate(Object value) {
        String s = String.valueOf(value);
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}