package org.example.ai.agent.impl;

import org.example.ai.agent.core.AgentContext;
import org.example.ai.agent.core.BaseAgent;
import org.example.ai.agent.skill.WebSearchSkill;
import org.example.ai.agent.skill.TextAnalysisSkill;
import org.example.ai.agent.skill.CalculatorSkill;
import org.example.ai.service.UsageTracker;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 研究 Agent - 配备搜索、分析和计算技能
 * 可进行深度研究：搜索信息 → 分析文本 → 数据计算 → 综合报告
 */
@Component
public class ResearchAgent extends BaseAgent {

    public ResearchAgent(ChatClient chatClient, UsageTracker usageTracker, WebSearchSkill webSearchSkill,
                         TextAnalysisSkill textAnalysisSkill, CalculatorSkill calculatorSkill) {
        super(chatClient, usageTracker, "research-agent",
            "深度研究分析专家，可搜索互联网获取信息，分析文本数据，进行计算统计，产出一份结构化研究报告。");
        addSkill(webSearchSkill);
        addSkill(textAnalysisSkill);
        addSkill(calculatorSkill);
    }

    @Override
    public String execute(String userInput, AgentContext context) {
        String existingResearch = context != null ? context.get("research_data") : null;
        if (existingResearch != null) {
            userInput = userInput + "\n[已有研究数据]\n" + existingResearch;
        }
        return super.execute(userInput, context);
    }
}