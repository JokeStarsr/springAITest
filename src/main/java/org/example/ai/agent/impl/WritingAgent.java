package org.example.ai.agent.impl;

import org.example.ai.agent.core.BaseAgent;
import org.example.ai.agent.skill.TextAnalysisSkill;
import org.example.ai.agent.skill.WebSearchSkill;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 写作 Agent - 配备文本分析和搜索技能
 * 可撰写各类文档，分析素材，搜索资料丰富内容
 */
@Component
public class WritingAgent extends BaseAgent {

    public WritingAgent(ChatClient chatClient, TextAnalysisSkill textAnalysisSkill,
                        WebSearchSkill webSearchSkill) {
        super(chatClient, "writing-agent",
            "专业写作助手，可撰写各类文档（报告、方案、文章、邮件、PPT大纲等），善用搜索获取素材，分析文本质量。");
        addSkill(textAnalysisSkill);
        addSkill(webSearchSkill);
    }

    @Override
    public String execute(String userInput) {
        // 从上下文获取写作素材
        if (context != null) {
            String sourceMaterial = context.get("source_material");
            if (sourceMaterial != null) {
                userInput = userInput + "\n[参考素材]\n" + sourceMaterial;
            }
            String targetAudience = context.get("target_audience");
            if (targetAudience != null) {
                userInput = userInput + "\n[目标受众] " + targetAudience;
            }
        }
        return super.execute(userInput);
    }
}