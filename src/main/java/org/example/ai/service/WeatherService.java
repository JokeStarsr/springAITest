package org.example.ai.service;

import org.example.ai.model.WeatherResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class WeatherService {
    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private static final String[] CONDITIONS = {"晴", "多云", "阴", "小雨", "阵雨", "雷阵雨", "晴转多云", "多云转阴"};
    private static final String[] WIND_DIRECTIONS = {"东风", "东南风", "南风", "西南风", "西风", "西北风", "北风", "东北风"};

    /**
     * 模拟单日天气
     */
    public String getWeather(String city) {
        log.info("查询{}的天气", city);
        return generateDayWeather(city, LocalDate.now());
    }

    /**
     * 模拟未来N天天气预报（实际项目应调用真实天气API）
     */
    public String getWeatherForecast(String city, int days) {
        log.info("查询{}未来{}天天气", city, days);
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM月dd日");
        LocalDate today = LocalDate.now();

        for (int i = 0; i < days; i++) {
            LocalDate date = today.plusDays(i);
            String label = (i == 0) ? "今天" : (i == 1 ? "明天" : "后天");
            if (i > 2) label = date.format(fmt);
            sb.append(label).append("：").append(generateDayWeather(city, date)).append("\n");
        }
        return sb.toString().trim();
    }

    private String generateDayWeather(String city, LocalDate date) {
        String condition = CONDITIONS[Math.abs((city.hashCode() + (int) date.toEpochDay()) % CONDITIONS.length)];
        int temp = 18 + Math.abs((city.hashCode() + (int) date.toEpochDay()) % 18);
        int humidity = 40 + Math.abs((city.hashCode() + (int) date.toEpochDay() * 7) % 50);
        String wind = WIND_DIRECTIONS[Math.abs((city.hashCode() + (int) date.toEpochDay() * 3) % WIND_DIRECTIONS.length)];
        int windLevel = 1 + Math.abs((city.hashCode() + (int) date.toEpochDay() * 5) % 5);
        return String.format("%s，%d℃，湿度%d%%，%s%d级", condition, temp, humidity, wind, windLevel);
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