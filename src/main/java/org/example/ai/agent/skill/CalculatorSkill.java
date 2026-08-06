package org.example.ai.agent.skill;

import org.example.ai.agent.core.Skill;
import org.example.ai.agent.core.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Map;

/**
 * 计算器技能 - 执行数学计算和统计分析
 * 通过 @Tool 注解暴露给 Spring AI，LLM 自动决策何时调用
 */
public class CalculatorSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(CalculatorSkill.class);

    // ====== Skill 接口（兼容旧版） ======

    @Override
    public String name() { return "calculator"; }

    @Override
    public String description() {
        return "执行数学计算，支持表达式求值、求和、平均值、最大值、最小值、排序等统计操作";
    }

    @Override
    public String parametersSchema() {
        return "{ \"expression\": \"数学表达式(如 3+5*2)\", 或 \"operation\": \"sum|avg|max|min|sort\", \"numbers\": \"逗号分隔的数字列表\" }";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            if (params.containsKey("expression")) {
                return ToolResult.success(name(), "计算结果: " + calculate((String) params.get("expression")));
            }
            if (params.containsKey("operation") && params.containsKey("numbers")) {
                String op = (String) params.get("operation");
                String numsStr = (String) params.get("numbers");
                return ToolResult.success(name(), doStatistics(op, numsStr));
            }
            return ToolResult.failure(name(), "参数不足");
        } catch (Exception e) {
            log.error("CalculatorSkill 执行失败", e);
            return ToolResult.failure(name(), "计算失败: " + e.getMessage());
        }
    }

    // ====== Spring AI @Tool 方法（主要入口） ======

    @Tool(name = "calculator",
          description = "执行数学表达式计算，支持加减乘除和括号，例如: 3+5*2、(10+20)*3")
    public String calculate(
            @ToolParam(description = "数学表达式，支持 + - * / ( )，如 3+5*2") String expression) {

        if (expression == null || expression.isBlank()) {
            return "错误: 表达式为空";
        }
        log.info("CalculatorSkill @Tool: expression={}", expression);
        try {
            double result = evaluateExpression(expression);
            return expression + " = " + formatResult(result);
        } catch (Exception e) {
            return "计算失败: " + e.getMessage();
        }
    }

    @Tool(name = "statistics",
          description = "对一组数字进行统计计算：求和(sum)、平均值(avg)、最大值(max)、最小值(min)、排序(sort)")
    public String statistics(
            @ToolParam(description = "操作类型: sum(求和), avg(平均值), max(最大值), min(最小值), sort(排序)") String operation,
            @ToolParam(description = "逗号分隔的数字列表，如 10,20,30,40") String numbers) {

        if (operation == null || numbers == null) {
            return "错误: 参数不足，需要 operation 和 numbers";
        }
        log.info("CalculatorSkill @Tool statistics: op={}, numbers={}", operation, numbers);
        try {
            return doStatistics(operation, numbers);
        } catch (Exception e) {
            return "统计失败: " + e.getMessage();
        }
    }

    // ====== 内部实现 ======

    private String doStatistics(String op, String numsStr) {
        String[] parts = numsStr.split("[,\\s]+");
        double[] nums = new double[parts.length];
        for (int i = 0; i < parts.length; i++) nums[i] = Double.parseDouble(parts[i].trim());

        return switch (op.toLowerCase().trim()) {
            case "sum" -> "求和: " + formatResult(sum(nums));
            case "avg", "average" -> "平均值: " + formatResult(avg(nums));
            case "max" -> "最大值: " + formatResult(max(nums));
            case "min" -> "最小值: " + formatResult(min(nums));
            case "sort" -> "排序: " + sort(nums);
            default -> "未知操作: " + op + "，支持: sum, avg, max, min, sort";
        };
    }

    private double evaluateExpression(String expr) {
        expr = expr.replaceAll("\\s+", "");
        return evaluate(expr, 0, expr.length());
    }

    private double evaluate(String s, int start, int end) {
        double result = 0, current = 0;
        char lastOp = '+';
        for (int i = start; i < end; i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                StringBuilder sb = new StringBuilder();
                while (i < end && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) sb.append(s.charAt(i++));
                i--;
                current = Double.parseDouble(sb.toString());
            } else if (c == '(') {
                int depth = 1, j = i + 1;
                while (j < end && depth > 0) {
                    if (s.charAt(j) == '(') depth++;
                    if (s.charAt(j) == ')') depth--;
                    j++;
                }
                current = evaluate(s, i + 1, j - 1);
                i = j - 1;
            } else if (c == '+' || c == '-' || c == '*' || c == '/') {
                result = applyOp(result, current, lastOp);
                lastOp = c;
                current = 0;
            }
        }
        return applyOp(result, current, lastOp);
    }

    private double applyOp(double a, double b, char op) {
        return switch (op) {
            case '+' -> a + b;
            case '-' -> a - b;
            case '*' -> a * b;
            case '/' -> b != 0 ? a / b : Double.NaN;
            default -> a + b;
        };
    }

    private String formatResult(double v) {
        return (v == Math.floor(v) && !Double.isInfinite(v)) ? String.valueOf((long) v) : String.format("%.2f", v);
    }

    private double sum(double[] nums) { double s = 0; for (double n : nums) s += n; return s; }
    private double avg(double[] nums) { return sum(nums) / nums.length; }
    private double max(double[] nums) { double m = nums[0]; for (double n : nums) if (n > m) m = n; return m; }
    private double min(double[] nums) { double m = nums[0]; for (double n : nums) if (n < m) m = n; return m; }
    private String sort(double[] nums) {
        java.util.Arrays.sort(nums);
        StringBuilder sb = new StringBuilder();
        for (double n : nums) sb.append(formatResult(n)).append(" ");
        return sb.toString().trim();
    }
}
