package org.example.ai.service.agent;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai.model.AgentResponse;
import org.example.ai.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WeatherAgent implements IAgent {

    private final ChatClient chatClient;
    private final WeatherService weatherService;
    private static final Logger log = LoggerFactory.getLogger(WeatherAgent.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern DAYS_PATTERN = Pattern.compile(
        "(未来|接下来|今后|后面|最近|近)\\s*(\\d+|[零一二三四五六七八九十廿卅百千万]+)\\s*天|(\\d+|[零一二三四五六七八九十廿卅百千万]+)\\s*天(?:的)?(?:天气|预报)?"
    );

    private static final Map<String, Integer> CN_NUM = Map.ofEntries(
        Map.entry("零", 0), Map.entry("一", 1), Map.entry("二", 2), Map.entry("两", 2),
        Map.entry("三", 3), Map.entry("四", 4), Map.entry("五", 5), Map.entry("六", 6),
        Map.entry("七", 7), Map.entry("八", 8), Map.entry("九", 9), Map.entry("十", 10),
        Map.entry("廿", 20), Map.entry("卅", 30), Map.entry("百", 100), Map.entry("千", 1000),
        Map.entry("万", 10000)
    );

    public WeatherAgent(ChatClient chatClient, WeatherService weatherService) {
        this.chatClient = chatClient;
        this.weatherService = weatherService;
    }

    @Override
    public String execute(String input) {
        log.info("WeatherAgent开始处理: {}", input);

        String city = extractCity(input);

        if (city == null || city.isEmpty()) {
            try {
                return objectMapper.writeValueAsString(
                        new AgentResponse("请告诉我您想查询哪个城市的天气")
                );
            } catch (Exception e) {
                log.error("序列化失败", e);
                return "{\"content\":\"请告诉我您想查询哪个城市的天气\",\"type\":\"text\"}";
            }
        }

        int forecastDays = extractDays(input);

        String weatherInfo;
        String systemPrompt;

        if (forecastDays > 1) {
            weatherInfo = weatherService.getWeatherForecast(city, forecastDays);
            systemPrompt = "你是一个天气助手，请根据以下未来" + forecastDays + "天的天气预报，生成一份简洁友好的逐日天气总结，每行一天，包含温度、天气状况和出行建议";
        } else {
            weatherInfo = weatherService.getWeather(city);
            systemPrompt = "你是一个天气助手，请根据天气信息生成友好的回复";
        }

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user("城市: " + city + "\n天气信息:\n" + weatherInfo)
                .call()
                .content();

        log.info("WeatherAgent处理完成");

        try {
            return objectMapper.writeValueAsString(new AgentResponse(response));
        } catch (Exception e) {
            log.error("序列化失败", e);
            return String.format("{\"content\":\"%s\",\"type\":\"text\"}",
                    response.replace("\"", "\\\""));
        }
    }

    private int extractDays(String input) {
        Matcher m = DAYS_PATTERN.matcher(input);
        if (m.find()) {
            String daysStr = m.group(2) != null ? m.group(2) : m.group(3);
            if (daysStr != null) {
                try {
                    int days = Integer.parseInt(daysStr);
                    return Math.min(days, 30);
                } catch (NumberFormatException e) {
                    // 中文数字转阿拉伯数字
                    int days = parseChineseNumber(daysStr);
                    if (days > 0) return Math.min(days, 30);
                }
            }
        }
        return 1;
    }

    private int parseChineseNumber(String s) {
        // 单字映射
        Integer val = CN_NUM.get(s);
        if (val != null) return val;

        // 复合数字：如 "二十三"、"三十五"
        int result = 0;
        int current = 0;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            Integer n = CN_NUM.get(ch);
            if (n == null) continue;
            if (n >= 10) {
                if (current == 0) current = 1;
                result += current * n;
                current = 0;
            } else {
                current = n;
            }
        }
        result += current;
        return result > 0 ? result : 0;
    }

    @Override
    public String getName() {
        return "weather-agent";
    }

    @Override
    public String getDescription() {
        return "天气查询Agent，支持单日查询和未来N天天气预报";
    }

    private String extractCity(String input) {
        String[] cities = {
            "北京", "上海", "广州", "深圳", "杭州", "成都", "重庆", "武汉",
            "南京", "天津", "西安", "长沙", "郑州", "济南", "青岛", "大连",
            "厦门", "福州", "昆明", "贵阳", "南宁", "海口", "三亚", "拉萨",
            "乌鲁木齐", "呼和浩特", "哈尔滨", "长春", "沈阳", "石家庄", "合肥", "南昌", "太原"
        };
        for (String city : cities) {
            if (input.contains(city)) {
                return city;
            }
        }
        return null;
    }
}