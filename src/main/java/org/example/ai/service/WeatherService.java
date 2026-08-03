package org.example.ai.service;

import org.example.ai.model.WeatherResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.service.agent.WritingAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

//@Slf4j
@Service
public class WeatherService {
    private static final Logger log = LoggerFactory.getLogger(WeatherService.class); // 手动定义

    /**
     * 模拟天气查询（实际项目中应调用真实API）
     */
    public String getWeather(String city) {
        log.info("查询{}的天气", city);

        // 模拟数据
        return switch (city) {
            case "北京" -> "晴，25℃，湿度60%，空气质量良好";
            case "上海" -> "多云，28℃，湿度70%，东南风3级";
            case "广州" -> "阵雨，30℃，湿度80%，注意防雨";
            case "深圳" -> "雷阵雨，29℃，湿度85%，注意安全";
            case "杭州" -> "小雨，22℃，湿度75%，适合室内活动";
            case "成都" -> "阴，20℃，湿度65%，空气质量优";
            case "重庆" -> "雾，18℃，湿度90%，能见度较低";
            case "武汉" -> "晴，26℃，湿度68%，微风";
            default -> "暂无" + city + "的天气数据";
        };
    }

    /**
     * 获取详细天气信息（返回对象）
     */
    public WeatherResponse getWeatherDetail(String city) {
        WeatherResponse response = new WeatherResponse();
        response.setCity(city);
        response.setTemperature("25℃");
        response.setCondition("晴");
        response.setHumidity("60%");
        return response;
    }
}
