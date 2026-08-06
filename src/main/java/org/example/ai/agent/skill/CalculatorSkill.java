package org.example.ai.agent.skill;

import org.example.ai.agent.core.Skill;
import org.example.ai.agent.core.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 计算器技能 - 执行数学计算和统计分析
 */
public class CalculatorSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(CalculatorSkill.class);

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
            // 表达式求值
            if (params.containsKey("expression")) {
                String expr = (String) params.get("expression");
                double result = evaluateExpression(expr);
                return ToolResult.success(name(), "计算结果: " + expr + " = " + result);
            }
            // 统计操作
            if (params.containsKey("operation") && params.containsKey("numbers")) {
                String op = (String) params.get("operation");
                String numsStr = (String) params.get("numbers");
                String[] parts = numsStr.split("[,\\s]+");
                double[] nums = new double[parts.length];
                for (int i = 0; i < parts.length; i++) nums[i] = Double.parseDouble(parts[i].trim());

                return switch (op.toLowerCase()) {
                    case "sum" -> ToolResult.success(name(), "求和: " + sum(nums));
                    case "avg", "average" -> ToolResult.success(name(), "平均值: " + avg(nums));
                    case "max" -> ToolResult.success(name(), "最大值: " + max(nums));
                    case "min" -> ToolResult.success(name(), "最小值: " + min(nums));
                    case "sort" -> ToolResult.success(name(), "排序: " + sort(nums));
                    default -> ToolResult.failure(name(), "未知操作: " + op);
                };
            }
            return ToolResult.failure(name(), "参数不足，需要 expression 或 operation+numbers");
        } catch (Exception e) {
            log.error("CalculatorSkill 执行失败", e);
            return ToolResult.failure(name(), "计算失败: " + e.getMessage());
        }
    }

    private double evaluateExpression(String expr) {
        // 简单计算器：支持 + - * / ( )
        expr = expr.replaceAll("\\s+", "");
        return evaluate(expr, 0, expr.length());
    }

    private double evaluate(String s, int start, int end) {
        double result = 0;
        double current = 0;
        char lastOp = '+';
        for (int i = start; i < end; i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                StringBuilder sb = new StringBuilder();
                while (i < end && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
                    sb.append(s.charAt(i++));
                }
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
            } else {
                if (c == '+' || c == '-' || c == '*' || c == '/') {
                    result = applyOp(result, current, lastOp);
                    lastOp = c;
                    current = 0;
                }
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

    private String sum(double[] nums) {
        double s = 0; for (double n : nums) s += n;
        return String.format("%.2f", s);
    }
    private String avg(double[] nums) {
        double s = 0; for (double n : nums) s += n;
        return String.format("%.2f", s / nums.length);
    }
    private String max(double[] nums) {
        double m = nums[0]; for (double n : nums) if (n > m) m = n;
        return String.format("%.2f", m);
    }
    private String min(double[] nums) {
        double m = nums[0]; for (double n : nums) if (n < m) m = n;
        return String.format("%.2f", m);
    }
    private String sort(double[] nums) {
        java.util.Arrays.sort(nums);
        StringBuilder sb = new StringBuilder();
        for (double n : nums) sb.append(String.format("%.2f", n)).append(" ");
        return sb.toString().trim();
    }
}