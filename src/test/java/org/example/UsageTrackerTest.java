package org.example;

import org.example.ai.service.UsageTracker;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用量统计单测：汇总、精确 token 记录与重置。
 */
class UsageTrackerTest {

    @Test
    void aggregatesCallsAndTokens() {
        UsageTracker tracker = new UsageTracker();
        tracker.recordWithUsage("/chat", "你好", "你好呀", 100, 10, 20);
        tracker.recordWithUsage("/chat/context", "a", "b", 200, 30, 40);

        Map<String, Object> stats = tracker.getStats();
        assertEquals(2L, stats.get("totalCalls"));
        assertEquals(40L, stats.get("totalInputTokens"));
        assertEquals(60L, stats.get("totalOutputTokens"));
        assertEquals(100L, stats.get("totalTokens"));
    }

    @Test
    void negativeTokensFallBackToEstimate() {
        UsageTracker tracker = new UsageTracker();
        tracker.recordWithUsage("/chat", "你好", "你好呀", 100, -1, -1);
        Map<String, Object> stats = tracker.getStats();
        // 估算：字符数 / 3 → 至少 1
        assertTrue((Long) stats.get("totalTokens") >= 2);
    }

    @Test
    void resetClearsEverything() {
        UsageTracker tracker = new UsageTracker();
        tracker.record("/chat", "abc", "def", 50);
        tracker.reset();
        Map<String, Object> stats = tracker.getStats();
        assertEquals(0L, stats.get("totalCalls"));
        assertEquals("0.0000", stats.get("totalCostYuan"));
    }

    @Test
    void recentRecordsCappedAtTwentyForDisplay() {
        UsageTracker tracker = new UsageTracker();
        for (int i = 0; i < 50; i++) {
            tracker.record("/chat", "m" + i, "r" + i, 10);
        }
        assertEquals(20, ((java.util.List<?>) tracker.getStats().get("recentRecords")).size());
    }
}