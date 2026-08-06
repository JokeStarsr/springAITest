package org.example.ai.agent.skill;

import org.example.ai.agent.core.Skill;
import org.example.ai.agent.core.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 文本分析技能 - 统计、摘要、关键词提取等
 */
public class TextAnalysisSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(TextAnalysisSkill.class);

    @Override
    public String name() { return "text_analysis"; }

    @Override
    public String description() {
        return "对文本进行统计分析：字数统计、词频统计、语言检测、情感倾向分析";
    }

    @Override
    public String parametersSchema() {
        return "{ \"text\": \"要分析的文本\", \"operation\": \"word_count|char_count|freq|sentiment\" }";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String text = (String) params.getOrDefault("text", "");
        String op = (String) params.getOrDefault("operation", "word_count");

        if (text.isBlank()) {
            return ToolResult.failure(name(), "文本为空");
        }

        try {
            return switch (op.toLowerCase()) {
                case "word_count" -> ToolResult.success(name(), "字数统计: " + countWords(text) + " 词, " + text.length() + " 字符");
                case "char_count" -> ToolResult.success(name(), "字符统计: 总计 " + text.length() + " 字符, 中文字符 " + countChineseChars(text) + " 个");
                case "freq" -> ToolResult.success(name(), "词频分析:\n" + wordFrequency(text));
                case "sentiment" -> ToolResult.success(name(), "情感倾向: " + analyzeSentiment(text));
                default -> ToolResult.success(name(), "文本分析:\n" + fullAnalysis(text));
            };
        } catch (Exception e) {
            log.error("TextAnalysisSkill 执行失败", e);
            return ToolResult.failure(name(), "分析失败: " + e.getMessage());
        }
    }

    private int countWords(String text) {
        return text.split("[\\s,，。.!！?？;；:：]+").length;
    }

    private int countChineseChars(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                count++;
            }
        }
        return count;
    }

    private String wordFrequency(String text) {
        Map<String, Integer> freq = new java.util.LinkedHashMap<>();
        String[] words = text.split("[\\s,，。.!！?？;；:：\n]+");
        for (String w : words) {
            if (w.length() >= 2) freq.merge(w, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        freq.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(10)
            .forEach(e -> sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("次\n"));
        return sb.toString().trim();
    }

    private String analyzeSentiment(String text) {
        String[] positive = {"好", "棒", "优秀", "喜欢", "开心", "成功", "完美", "满意", "赞", "厉害", "great", "good", "excellent"};
        String[] negative = {"差", "坏", "糟糕", "讨厌", "失败", "失望", "生气", "垃圾", "差劲", "bad", "poor", "terrible"};

        int pos = 0, neg = 0;
        String lower = text.toLowerCase();
        for (String w : positive) { if (lower.contains(w)) pos++; }
        for (String w : negative) { if (lower.contains(w)) neg++; }

        if (pos > neg) return "偏正面 (正面词" + pos + " vs 负面词" + neg + ")";
        if (neg > pos) return "偏负面 (正面词" + pos + " vs 负面词" + neg + ")";
        return "中性 (正面词" + pos + " vs 负面词" + neg + ")";
    }

    private String fullAnalysis(String text) {
        return "字数: " + countWords(text) + " 词\n" +
               "字符: " + text.length() + " (中文 " + countChineseChars(text) + ")\n" +
               "情感: " + analyzeSentiment(text);
    }
}