package org.example;

import org.example.ai.agent.impl.CoordinatorAgent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 快速路由纯函数单测（不发任何 AI 请求）。
 */
class CoordinatorRoutingTest {

    @Test
    void weatherOnlyRoutesToWeather() {
        List<CoordinatorAgent.TaskAssignment> result = CoordinatorAgent.quickRoute("北京今天天气怎么样？");
        assertEquals(1, result.size());
        assertEquals("weather-agent", result.get(0).agentName());
    }

    @Test
    void writeTaskRoutesToWriting() {
        List<CoordinatorAgent.TaskAssignment> result = CoordinatorAgent.quickRoute("帮我写一篇关于AI发展的文章");
        assertEquals(1, result.size());
        assertEquals("writing-agent", result.get(0).agentName());
    }

    @Test
    void weatherPlusResearchDelegatesToLlm() {
        // 多关键词组合交给 LLM 分析，不快速路由
        List<CoordinatorAgent.TaskAssignment> result = CoordinatorAgent.quickRoute("上海明天下雨吗？分析一下对出行的影响并写份建议");
        assertTrue(result.isEmpty());
    }

    @Test
    void researchPromptWithoutKeywordsDefersRouting() {
        List<CoordinatorAgent.TaskAssignment> result = CoordinatorAgent.quickRoute("什么是量子纠缠？");
        assertTrue(result.isEmpty());
    }

    @Test
    void writeWithoutWriteKeywordsDefersRouting() {
        List<CoordinatorAgent.TaskAssignment> result = CoordinatorAgent.quickRoute("计算 156*23");
        assertTrue(result.isEmpty());
    }
}