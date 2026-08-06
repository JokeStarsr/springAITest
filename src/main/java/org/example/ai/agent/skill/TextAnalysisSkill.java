package org.example.ai.agent.skill;

import org.example.ai.agent.core.Skill;
import org.example.ai.agent.core.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文本分析技能 - 统计、词频、情感分析等
 * 通过 @Tool 注解暴露给 Spring AI，LLM 自动决策何时调用
 */
public class TextAnalysisSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(TextAnalysisSkill.class);

    // ====== Skill 接口（兼容旧版） ======

    @Override
    public String name() { return "text_analysis"; }

    @Override
    public String description() {
        return "对文本进行统计分析：字数统计、词频统计、语言检测、情感倾向分析";
    }

    @Override
    public String parametersSchema() {
        return "{ \"text\": \"要分析的文本\", \"operation\": \"word_count|char_count|freq|sentiment|full\" }";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String text = (String) params.getOrDefault("text", "");
        String op = (String) params.getOrDefault("operation", "full");
        if (text.isBlank()) return ToolResult.failure(name(), "文本为空");
        try {
            return ToolResult.success(name(), analyzeText(text, op));
        } catch (Exception e) {
            log.error("TextAnalysisSkill 执行失败", e);
            return ToolResult.failure(name(), "分析失败: " + e.getMessage());
        }
    }

    // ====== Spring AI @Tool 方法（主要入口） ======

    @Tool(name = "text_analysis",
          description = "对文本进行统计分析：字数统计(word_count)、字符统计(char_count)、词频分析(freq)、情感倾向(sentiment)、完整分析(full)")
    public String analyzeText(
            @ToolParam(description = "要分析的文本内容") String text,
            @ToolParam(description = "分析类型: word_count(字数), char_count(字符), freq(词频), sentiment(情感), full(完整分析)") String operation) {

        if (text == null || text.isBlank()) {
            return "错误: 文本为空";
        }
        String op = (operation != null && !operation.isBlank()) ? operation.toLowerCase().trim() : "full";
        log.info("TextAnalysisSkill @Tool: operation={}, textLen={}", op, text.length());

        return switch (op) {
            case "word_count" -> "字数统计: " + countWords(text) + " 词, " + text.length() + " 字符";
            case "char_count" -> "字符统计: 总计 " + text.length() + " 字符, 中文字符 " + countChineseChars(text) + " 个";
            case "freq" -> "词频分析:\n" + wordFrequency(text);
            case "sentiment" -> "情感倾向: " + analyzeSentiment(text);
            default -> fullAnalysis(text);
        };
    }

    // ====== 内部实现 ======

    private int countWords(String text) {
        return text.split("[\\s,，。.!！?？;；:：]+").length;
    }

    private int countChineseChars(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) count++;
        }
        return count;
    }

    private String wordFrequency(String text) {
        Map<String, Integer> freq = new LinkedHashMap<>();
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
