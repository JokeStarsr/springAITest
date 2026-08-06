package org.example.ai.controller;

import org.example.ai.service.UsageTracker;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用量统计控制器 - 提供 token 消耗和费用查询
 */
@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private final UsageTracker usageTracker;

    public UsageController(UsageTracker usageTracker) {
        this.usageTracker = usageTracker;
    }

    /** 获取用量统计摘要 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return usageTracker.getStats();
    }

    /** 重置统计 */
    @PostMapping("/reset")
    public Map<String, String> reset() {
        usageTracker.reset();
        return Map.of("status", "ok", "message", "用量统计已重置");
    }
}