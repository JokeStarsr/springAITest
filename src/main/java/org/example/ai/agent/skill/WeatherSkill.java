package org.example.ai.agent.skill;

import org.example.ai.agent.core.Skill;
import org.example.ai.agent.core.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 天气查询技能 - 接入 OpenWeatherMap 真实API
 * 通过 @Tool 注解暴露给 Spring AI，LLM 自动决策何时调用
 */
@Component
public class WeatherSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(WeatherSkill.class);
    private final RestClient restClient;
    private final String apiKey;
    private final String baseUrl;

    public WeatherSkill(@Value("${api.weather.key:}") String apiKey,
                        @Value("${api.weather.base-url:https://api.openweathermap.org/data/2.5}") String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    // ====== Skill 接口（兼容旧版） ======

    @Override
    public String name() { return "weather_query"; }

    @Override
    public String description() {
        return "查询指定城市当天或未来5天的天气情况，返回温度、天气状况、湿度、风力等真实气象数据";
    }

    @Override
    public String parametersSchema() {
        return "{ \"city\": \"城市名称\", \"days\": \"查询天数(可选,默认1,最大5)\" }";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String city = (String) params.getOrDefault("city", "");
        String result = queryWeather(city, parseDaysFromParams(params));
        return result.startsWith("错误") ? ToolResult.failure(name(), result)
                                         : ToolResult.success(name(), result);
    }

    // ====== Spring AI @Tool 方法（主要入口） ======

    @Tool(name = "weather_query",
          description = "查询指定城市当天或未来5天的天气情况，返回温度、天气状况、湿度、风力等真实气象数据")
    public String queryWeather(
            @ToolParam(description = "城市名称，支持中英文，如 Beijing、北京、Tokyo") String city,
            @ToolParam(description = "查询天数，默认1天，最大5天") Integer days) {

        if (city == null || city.isBlank()) {
            return "错误: 缺少城市参数";
        }
        if (apiKey.isBlank()) {
            return "错误: 未配置天气API Key。请设置环境变量 WEATHER_API_KEY，免费获取: https://openweathermap.org/api";
        }

        int d = (days != null && days > 1) ? Math.min(days, 5) : 1;
        log.info("WeatherSkill @Tool: city={}, days={}", city, d);

        try {
            return d > 1 ? getForecast(city, d) : getCurrentWeather(city);
        } catch (Exception e) {
            log.error("天气查询失败: {}", e.getMessage());
            return "天气查询失败: " + e.getMessage();
        }
    }

    /** 查询当前天气 */
    @SuppressWarnings("unchecked")
    private String getCurrentWeather(String city) {
        Map<String, Object> data = restClient.get()
            .uri("/weather?q={city}&appid={key}&units=metric&lang=zh_cn", city, apiKey)
            .retrieve()
            .body(Map.class);

        if (data == null) return "未获取到天气数据";

        StringBuilder sb = new StringBuilder();
        String cityName = (String) data.getOrDefault("name", "未知");
        List<Map<String, Object>> weatherList = (List<Map<String, Object>>) data.get("weather");
        Map<String, Object> weather = (weatherList != null && !weatherList.isEmpty()) ? weatherList.get(0) : Map.of();
        Map<String, Object> main = (Map<String, Object>) data.get("main");
        Map<String, Object> wind = (Map<String, Object>) data.get("wind");

        sb.append("城市: ").append(cityName).append("\n");
        sb.append("天气: ").append(weather.getOrDefault("description", "未知")).append("\n");
        if (main != null) {
            sb.append("温度: ").append(main.get("temp")).append("℃ (体感 ")
              .append(main.get("feels_like")).append("℃)\n");
            sb.append("湿度: ").append(main.get("humidity")).append("%\n");
            sb.append("气压: ").append(main.get("pressure")).append("hPa\n");
        }
        if (wind != null) {
            sb.append("风速: ").append(wind.get("speed")).append("m/s\n");
        }
        return sb.toString().trim();
    }

    /** 查询多日预报，按天聚合 */
    @SuppressWarnings("unchecked")
    private String getForecast(String city, int days) {
        Map<String, Object> data = restClient.get()
            .uri("/forecast?q={city}&appid={key}&units=metric&lang=zh_cn&cnt={count}", city, apiKey, days * 8)
            .retrieve()
            .body(Map.class);

        if (data == null) return "未获取到天气预报数据";

        StringBuilder sb = new StringBuilder();
        Map<String, Object> cityObj = (Map<String, Object>) data.get("city");
        String cityName = cityObj != null ? (String) cityObj.get("name") : "未知";
        sb.append("城市: ").append(cityName).append(" 未来").append(days).append("天预报:\n\n");

        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        if (list == null || list.isEmpty()) return sb.append("无预报数据").toString();

        Map<String, Map<String, Object>> dailyMap = new LinkedHashMap<>();
        for (Map<String, Object> item : list) {
            String dtText = (String) item.get("dt_txt");
            if (dtText == null) continue;
            dailyMap.putIfAbsent(dtText.split(" ")[0], item);
        }

        int count = 0;
        for (Map.Entry<String, Map<String, Object>> entry : dailyMap.entrySet()) {
            if (count >= days) break;
            Map<String, Object> item = entry.getValue();
            String date = entry.getKey();
            Map<String, Object> main = (Map<String, Object>) item.get("main");
            List<Map<String, Object>> wList = (List<Map<String, Object>>) item.get("weather");
            Map<String, Object> w = (wList != null && !wList.isEmpty()) ? wList.get(0) : Map.of();

            String label = count == 0 ? "今天" : count == 1 ? "明天" : "第" + (count + 1) + "天";
            sb.append(label).append("(").append(date).append("): ");
            sb.append(w.getOrDefault("description", "未知")).append(", ");
            if (main != null) {
                sb.append("最高").append(main.get("temp_max")).append("℃/最低").append(main.get("temp_min")).append("℃, ");
                sb.append("湿度").append(main.get("humidity")).append("%");
            }
            sb.append("\n");
            count++;
        }
        return sb.toString().trim();
    }

    private int parseDaysFromParams(Map<String, Object> params) {
        Object daysObj = params.get("days");
        if (daysObj instanceof Number) return Math.min(((Number) daysObj).intValue(), 5);
        if (daysObj instanceof String && !((String) daysObj).isBlank()) {
            try { return Math.min(Integer.parseInt((String) daysObj), 5); } catch (NumberFormatException ignored) {}
        }
        return 1;
    }
}
