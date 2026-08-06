package org.example.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用量追踪器 - 记录每次API调用的token消耗和费用估算。
 * 由于Spring AI ChatClient不直接暴露原始token用量，
 * 这里采用字符数估算：中文约1.5字符/token，英文约4字符/token。
 * 
 * DeepSeek-chat 定价（2025）:
 *   - 输入: ￥1 / 1M tokens
 *   - 输出: ￥2 / 1M tokens
 */
@Service
public class UsageTracker {

    private static final Logger log = LoggerFactory.getLogger(UsageTracker.class);

    // 估算系数
    private static final double CHARS_PER_TOKEN = 3.0;  // 混合中英文约3字符/token
    private static final double INPUT_PRICE_PER_1M = 1.0;   // ￥1 / 1M tokens
    private static final double OUTPUT_PRICE_PER_1M = 2.0;  // ￥2 / 1M tokens

    // 累计统计
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong totalInputTokens = new AtomicLong(0);
    private final AtomicLong totalOutputTokens = new AtomicLong(0);
    private final AtomicLong totalTokens = new AtomicLong(0);
    private final AtomicLong totalCostFen = new AtomicLong(0); // 分（1元=100分）

    // 最近调用记录（保留最近100条）
    private final List<UsageRecord> recentRecords = Collections.synchronizedList(new LinkedList<>());
    private static final int MAX_RECORDS = 100;

    /** 记录一次调用 */
    public void record(String endpoint, String input, String output, long durationMs) {
        long inputTokens = estimateTokens(input);
        long outputTokens = estimateTokens(output);
        long costFen = calcCost(inputTokens, outputTokens);

        totalCalls.incrementAndGet();
        totalInputTokens.addAndGet(inputTokens);
        totalOutputTokens.addAndGet(outputTokens);
        totalTokens.addAndGet(inputTokens + outputTokens);
        totalCostFen.addAndGet(costFen);

        UsageRecord record = new UsageRecord(
            Instant.now(), endpoint, inputTokens, outputTokens, costFen, durationMs
        );
        recentRecords.add(record);
        if (recentRecords.size() > MAX_RECORDS) {
            recentRecords.remove(0);
        }

        log.debug("用量记录: {} -> in={} out={} cost={}分", endpoint, inputTokens, outputTokens, costFen);
    }

    /** 估算 token 数 */
    private long estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(1, Math.round(text.length() / CHARS_PER_TOKEN));
    }

    /** 计算费用（分） */
    private long calcCost(long inputTokens, long outputTokens) {
        double inputCost = (inputTokens / 1_000_000.0) * INPUT_PRICE_PER_1M * 100;
        double outputCost = (outputTokens / 1_000_000.0) * OUTPUT_PRICE_PER_1M * 100;
        return Math.round(inputCost + outputCost);
    }

    /** 获取统计摘要 */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCalls", totalCalls.get());
        stats.put("totalInputTokens", totalInputTokens.get());
        stats.put("totalOutputTokens", totalOutputTokens.get());
        stats.put("totalTokens", totalTokens.get());
        stats.put("totalCostYuan", String.format("%.4f", totalCostFen.get() / 100.0));
        stats.put("totalCostFen", totalCostFen.get());
        stats.put("modelName", "deepseek-chat");
        stats.put("pricing", "输入￥1/M tokens, 输出￥2/M tokens");
        stats.put("recentRecords", getRecentRecords());
        return stats;
    }

    /** 获取最近记录 */
    public List<Map<String, Object>> getRecentRecords() {
        List<Map<String, Object>> result = new ArrayList<>();
        synchronized (recentRecords) {
            for (int i = recentRecords.size() - 1; i >= Math.max(0, recentRecords.size() - 20); i--) {
                UsageRecord r = recentRecords.get(i);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("time", r.time.toString());
                m.put("endpoint", r.endpoint);
                m.put("inputTokens", r.inputTokens);
                m.put("outputTokens", r.outputTokens);
                m.put("costFen", r.costFen);
                m.put("durationMs", r.durationMs);
                result.add(m);
            }
        }
        return result;
    }

    /** 重置统计 */
    public void reset() {
        totalCalls.set(0);
        totalInputTokens.set(0);
        totalOutputTokens.set(0);
        totalTokens.set(0);
        totalCostFen.set(0);
        recentRecords.clear();
        log.info("用量统计已重置");
    }

    // 内部记录类
    private record UsageRecord(Instant time, String endpoint, long inputTokens, long outputTokens, long costFen, long durationMs) {}
}