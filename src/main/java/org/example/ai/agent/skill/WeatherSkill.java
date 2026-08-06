package org.example.ai.agent.skill;

import org.example.ai.agent.core.Skill;
import org.example.ai.agent.core.ToolResult;
import org.example.ai.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 天气查询技能 - 支持单日查询和未来N天预报
 */
public class WeatherSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(WeatherSkill.class);
    private final WeatherService weatherService;

    private static final Pattern DAYS_PATTERN = Pattern.compile(
        "(\\d+|[零一二三四五六七八九十廿卅百千万]+)\\s*天|(未来|接下来|今后|后面|最近|近)\\s*(\\d+|[零一二三四五六七八九十廿卅百千万]+)\\s*天"
    );

    public WeatherSkill(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @Override
    public String name() { return "weather_query"; }

    @Override
    public String description() {
        return "查询指定城市当天或未来N天的天气情况，返回温度、天气状况、湿度、风力等";
    }

    @Override
    public String parametersSchema() {
        return "{ \"city\": \"城市名称\", \"days\": \"查询天数(可选,默认1)\" }";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String city = (String) params.getOrDefault("city", "");
        if (city.isBlank()) {
            return ToolResult.failure(name(), "缺少城市参数");
        }

        int days = parseDays(params);
        log.info("WeatherSkill: city={}, days={}", city, days);

        try {
            String result;
            if (days > 1) {
                result = weatherService.getWeatherForecast(city, days);
            } else {
                result = weatherService.getWeather(city);
            }
            return ToolResult.success(name(), city + "天气信息:\n" + result);
        } catch (Exception e) {
            return ToolResult.failure(name(), "查询失败: " + e.getMessage());
        }
    }

    private int parseDays(Map<String, Object> params) {
        Object daysObj = params.get("days");
        if (daysObj instanceof Number) {
            return Math.min(((Number) daysObj).intValue(), 30);
        }
        if (daysObj instanceof String && !((String) daysObj).isBlank()) {
            String s = (String) daysObj;
            try { return Math.min(Integer.parseInt(s), 30); }
            catch (NumberFormatException e) { return parseChineseNumber(s); }
        }
        // 尝试从 city 参数中提取天数
        String city = (String) params.getOrDefault("city", "");
        Matcher m = DAYS_PATTERN.matcher(city);
        if (m.find()) {
            String ds = m.group(1) != null ? m.group(1) : m.group(3);
            if (ds != null) {
                try { return Math.min(Integer.parseInt(ds), 30); }
                catch (NumberFormatException e) { return parseChineseNumber(ds); }
            }
        }
        return 1;
    }

    private int parseChineseNumber(String s) {
        Map<String, Integer> map = Map.ofEntries(
            Map.entry("零",0),Map.entry("一",1),Map.entry("二",2),Map.entry("两",2),
            Map.entry("三",3),Map.entry("四",4),Map.entry("五",5),Map.entry("六",6),
            Map.entry("七",7),Map.entry("八",8),Map.entry("九",9),Map.entry("十",10)
        );
        Integer val = map.get(s);
        if (val != null) return val;
        int result = 0, current = 0;
        for (int i = 0; i < s.length(); i++) {
            Integer n = map.get(String.valueOf(s.charAt(i)));
            if (n == null) continue;
            if (n >= 10) { result += (current == 0 ? 1 : current) * n; current = 0; }
            else current = n;
        }
        result += current;
        return result > 0 ? result : 1;
    }
}