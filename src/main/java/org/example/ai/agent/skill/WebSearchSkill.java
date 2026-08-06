package org.example.ai.agent.skill;

import org.example.ai.agent.core.Skill;
import org.example.ai.agent.core.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Web搜索技能 - 模拟搜索，返回结构化结果
 * 实际生产环境可接入真实的搜索API
 */
public class WebSearchSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(WebSearchSkill.class);

    @Override
    public String name() { return "web_search"; }

    @Override
    public String description() {
        return "搜索互联网获取最新信息，支持关键词搜索和结构化结果返回";
    }

    @Override
    public String parametersSchema() {
        return "{ \"query\": \"搜索关键词\", \"count\": \"返回结果数量(可选,默认3)\" }";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String query = (String) params.getOrDefault("query", "");
        if (query.isBlank()) {
            return ToolResult.failure(name(), "搜索关键词为空");
        }

        int count = 3;
        Object countObj = params.get("count");
        if (countObj instanceof Number) {
            count = Math.min(((Number) countObj).intValue(), 5);
        }

        log.info("WebSearchSkill: query={}, count={}", query, count);

        // 模拟搜索结果（基于搜索词生成相关摘要）
        StringBuilder sb = new StringBuilder();
        sb.append("搜索关键词: ").append(query).append("\n\n");

        String[] results = generateMockResults(query, count);
        for (int i = 0; i < results.length; i++) {
            sb.append("结果").append(i + 1).append(": ").append(results[i]).append("\n");
        }

        return ToolResult.success(name(), sb.toString().trim());
    }

    private String[] generateMockResults(String query, int count) {
        // 根据搜索词生成模拟结果
        String[][] topics = {
            {"AI", "人工智能技术近年来快速发展，深度学习、大语言模型等推动了行业变革。"},
            {"天气", "根据气象部门预报，未来一周全国大部分地区气温适宜。"},
            {"经济", "最新经济数据显示，数字经济占GDP比重持续提升。"},
            {"科技", "科技行业持续创新，5G、物联网、云计算等技术加速落地。"},
            {"健康", "健康生活方式日益受到关注，均衡饮食和适量运动是保持健康的关键。"},
            {"教育", "在线教育市场规模持续扩大，个性化学习成为新趋势。"},
            {"编程", "编程语言生态不断演进，Java、Python、Go等语言各有优势。"},
            {"旅游", "国内旅游市场持续复苏，短途周边游成为热门选择。"}
        };

        String[] results = new String[count];
        for (int i = 0; i < count; i++) {
            int idx = Math.floorMod(query.hashCode() + i * 7, topics.length);
            results[i] = "【" + topics[idx][0] + "】" + topics[idx][1];
        }
        return results;
    }
}