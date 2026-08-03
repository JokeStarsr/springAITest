package org.example.ai.service.agent;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.controller.AgentController;
import org.example.ai.model.AgentResponse;
import org.example.ai.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

//@Slf4j
@Component
public class WeatherAgent implements IAgent {

    private final ChatClient chatClient;
    private final WeatherService weatherService;
    private static final Logger log = LoggerFactory.getLogger(WeatherAgent.class); // 手动定义
    private final ObjectMapper objectMapper = new ObjectMapper();
    public WeatherAgent(ChatClient chatClient, WeatherService weatherService) {
        this.chatClient = chatClient;
        this.weatherService = weatherService;
    }

    @Override
    public String execute(String input) {
        log.info("WeatherAgent开始处理: {}", input);

        // 1. 从用户输入中提取城市信息
        String city = extractCity(input);

        if (city == null || city.isEmpty()) {
            // ✅ 返回 JSON 格式
            try {
                return objectMapper.writeValueAsString(
                        new AgentResponse("请告诉我您想查询哪个城市的天气")
                );
            } catch (Exception e) {
                log.error("序列化失败", e);
                return "{\"content\":\"请告诉我您想查询哪个城市的天气\",\"type\":\"text\"}";
            }
        }

        // 2. 调用天气服务
        String weatherInfo = weatherService.getWeather(city);

        // 3. 使用LLM生成友好的回复
        String response = chatClient.prompt()
                .system("你是一个天气助手，请根据天气信息生成友好的回复")
                .user("城市: " + city + "\n天气信息: " + weatherInfo)
                .call()
                .content();

        log.info("WeatherAgent处理完成");

        // ✅ 返回 JSON 格式
        try {
            return objectMapper.writeValueAsString(new AgentResponse(response));
        } catch (Exception e) {
            log.error("序列化失败", e);
            return String.format("{\"content\":\"%s\",\"type\":\"text\"}",
                    response.replace("\"", "\\\""));
        }
    }

    @Override
    public String getName() {
        return "weather-agent";
    }

    @Override
    public String getDescription() {
        return "天气查询Agent，可以查询指定城市的天气信息";
    }

    /**
     * 从输入中提取城市名称
     */
    private String extractCity(String input) {
        // 简单的关键词提取
        String[] cities = {"北京", "上海", "广州", "深圳", "杭州", "成都", "重庆", "武汉"};
        for (String city : cities) {
            if (input.contains(city)) {
                return city;
            }
        }
        return null;
    }
}
