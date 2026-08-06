package org.example.ai.agent.core;

import java.time.Instant;

/**
 * Agent 间消息 - 用于跨 Agent 数据传递和协作请求
 */
public class AgentMessage {

    private final String fromAgent;
    private final String toAgent;      // "*" 表示广播
    private final String subject;      // 消息主题
    private final String body;         // 消息内容
    private final MessageType type;
    private final Instant timestamp;

    public enum MessageType {
        REQUEST,      // 请求其他 Agent 执行任务
        RESULT,       // 返回执行结果
        NOTIFY,       // 通知数据更新
        QUERY         // 查询共享数据
    }

    public AgentMessage(String fromAgent, String toAgent, String subject, String body, MessageType type) {
        this.fromAgent = fromAgent;
        this.toAgent = toAgent;
        this.subject = subject;
        this.body = body;
        this.type = type;
        this.timestamp = Instant.now();
    }

    public static AgentMessage request(String from, String to, String subject, String body) {
        return new AgentMessage(from, to, subject, body, MessageType.REQUEST);
    }

    public static AgentMessage result(String from, String to, String subject, String body) {
        return new AgentMessage(from, to, subject, body, MessageType.RESULT);
    }

    public static AgentMessage notify(String from, String to, String subject, String body) {
        return new AgentMessage(from, to, subject, body, MessageType.NOTIFY);
    }

    public String getFromAgent() { return fromAgent; }
    public String getToAgent() { return toAgent; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public MessageType getType() { return type; }
    public Instant getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "[" + type + "] " + fromAgent + " → " + toAgent + " | " + subject + ": " + body;
    }
}