package org.example.ai.agent.skill;

import org.example.ai.agent.core.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Web搜索技能 - 接入 Brave Search 真实搜索API
 * 通过 @Tool 注解暴露给 Spring AI，LLM 自动决策何时调用
 */
@Component
public class WebSearchSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(WebSearchSkill.class);
    private final RestClient restClient;
    private final String apiKey;
    private final String baseUrl;

    public WebSearchSkill(@Value("${api.search.key:}") String apiKey,
                          @Value("${api.search.base-url:https://api.search.brave.com/res/v1/web/search}") String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public String name() { return "web_search"; }

    @Override
    public String description() {
        return "搜索互联网获取最新信息，返回标题、URL和摘要。基于Brave搜索引擎，覆盖全球网页。";
    }

    // ====== Spring AI @Tool 方法（主要入口） ======

    @Tool(name = "web_search",
          description = "搜索互联网获取最新信息，返回标题、URL和摘要。基于Brave搜索引擎，覆盖全球网页。")
    public String searchWeb(
            @ToolParam(description = "搜索关键词，支持中英文") String query,
            @ToolParam(description = "返回结果数量，默认3条，最大10条") Integer count) {

        if (query == null || query.isBlank()) {
            return "错误: 搜索关键词为空";
        }
        if (apiKey.isBlank()) {
            return "未配置搜索API Key。请设置环境变量 SEARCH_API_KEY，免费获取: https://brave.com/search/api/";
        }

        int c = (count != null) ? Math.min(count, 10) : 3;
        log.info("WebSearchSkill @Tool: query={}, count={}", query, c);

        try {
            return searchBrave(query, c);
        } catch (Exception e) {
            log.error("搜索失败: {}", e.getMessage(), e);
            return "搜索失败: " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private String searchBrave(String query, int count) {
        Map<String, Object> data = restClient.get()
            .uri(baseUrl + "?q={query}&count={count}", query, count)
            .header("Accept", "application/json")
            .header("Accept-Encoding", "gzip")
            .header("X-Subscription-Token", apiKey)
            .retrieve()
            .body(Map.class);

        if (data == null) return "搜索服务返回空数据";

        Map<String, Object> web = (Map<String, Object>) data.get("web");
        if (web == null) return "搜索服务返回格式异常";

        List<Map<String, Object>> results = (List<Map<String, Object>>) web.get("results");
        if (results == null || results.isEmpty()) {
            return "搜索关键词: " + query + "\n\n未找到相关结果。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("搜索关键词: ").append(query).append("\n");
        sb.append("共找到约 ").append(web.getOrDefault("total_results", "N/A")).append(" 条结果\n\n");

        int idx = 1;
        for (Map<String, Object> result : results) {
            sb.append("结果").append(idx).append(": ").append(result.getOrDefault("title", "无标题")).append("\n");
            sb.append("链接: ").append(result.getOrDefault("url", "")).append("\n");
            sb.append("摘要: ").append(result.getOrDefault("description", "无描述")).append("\n\n");
            idx++;
        }
        return sb.toString().trim();
    }
}
