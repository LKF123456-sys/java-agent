package com.ailearn.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class WeatherTool {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String GEOCODE_URL = "https://geocoding-api.open-meteo.com/v1/search?name=%s&count=1&language=zh&format=json";
    private static final String WEATHER_URL = "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,apparent_temperature&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=3";

    private static final Map<Integer, String> WEATHER_CODE_MAP = new HashMap<>();
    static {
        WEATHER_CODE_MAP.put(0, "晴天");
        WEATHER_CODE_MAP.put(1, "大部晴朗");
        WEATHER_CODE_MAP.put(2, "局部多云");
        WEATHER_CODE_MAP.put(3, "阴天");
        WEATHER_CODE_MAP.put(45, "雾");
        WEATHER_CODE_MAP.put(48, "雾凇");
        WEATHER_CODE_MAP.put(51, "小毛毛雨");
        WEATHER_CODE_MAP.put(53, "毛毛雨");
        WEATHER_CODE_MAP.put(55, "大毛毛雨");
        WEATHER_CODE_MAP.put(56, "冻毛毛雨");
        WEATHER_CODE_MAP.put(57, "强冻毛毛雨");
        WEATHER_CODE_MAP.put(61, "小雨");
        WEATHER_CODE_MAP.put(63, "中雨");
        WEATHER_CODE_MAP.put(65, "大雨");
        WEATHER_CODE_MAP.put(66, "冻雨");
        WEATHER_CODE_MAP.put(67, "强冻雨");
        WEATHER_CODE_MAP.put(71, "小雪");
        WEATHER_CODE_MAP.put(73, "中雪");
        WEATHER_CODE_MAP.put(75, "大雪");
        WEATHER_CODE_MAP.put(77, "雪粒");
        WEATHER_CODE_MAP.put(80, "小阵雨");
        WEATHER_CODE_MAP.put(81, "阵雨");
        WEATHER_CODE_MAP.put(82, "强阵雨");
        WEATHER_CODE_MAP.put(85, "小阵雪");
        WEATHER_CODE_MAP.put(86, "大阵雪");
        WEATHER_CODE_MAP.put(95, "雷暴");
        WEATHER_CODE_MAP.put(96, "雷暴伴小冰雹");
        WEATHER_CODE_MAP.put(99, "雷暴伴大冰雹");
    }

    private String weatherCodeToText(int code) {
        return WEATHER_CODE_MAP.getOrDefault(code, "未知天气");
    }

    private GeocodeResult geocode(String city) {
        try {
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String url = String.format(GEOCODE_URL, encodedCity);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("地理编码请求失败: status={}, city={}", response.statusCode(), city);
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.get("results");
            if (results == null || !results.isArray() || results.isEmpty()) {
                log.warn("未找到城市: {}", city);
                return null;
            }
            JsonNode first = results.get(0);
            double lat = first.get("latitude").asDouble();
            double lon = first.get("longitude").asDouble();
            String name = first.has("name") ? first.get("name").asText() : city;
            String country = first.has("country") ? first.get("country").asText() : "";
            String admin1 = first.has("admin1") ? first.get("admin1").asText() : "";
            return new GeocodeResult(lat, lon, name, country, admin1);
        } catch (Exception e) {
            log.error("地理编码异常: city={}, error={}", city, e.getMessage(), e);
            return null;
        }
    }

    @Tool(description = "获取指定城市的当前实时天气信息，包括天气状况、温度、湿度、风速、体感温度等。当用户询问今天天气、当前气温、是否下雨、冷不冷等问题时使用此工具。数据来自Open-Meteo实时气象API。")
    public String getWeather(
            @ToolParam(description = "城市名称，必须是中文城市名，如：北京、上海、深圳、成都、广州、杭州、武汉、西安等") String city,
            @ToolParam(description = "温度单位，可选值：celsius（摄氏度，默认）或 fahrenheit（华氏度）", required = false) String unit) {
        log.info("天气查询工具被调用(真实API): city={}, unit={}", city, unit);

        GeocodeResult geo = geocode(city);
        if (geo == null) {
            return String.format("抱歉，未能找到城市「%s」的天气信息，请尝试其他城市名称。", city);
        }

        try {
            String url = String.format(WEATHER_URL, geo.lat, geo.lon);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return String.format("天气数据获取失败（HTTP %d），请稍后重试。", response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode current = root.get("current");

            double temp = current.get("temperature_2m").asDouble();
            int humidity = current.get("relative_humidity_2m").asInt();
            int weatherCode = current.get("weather_code").asInt();
            double windSpeed = current.get("wind_speed_10m").asDouble();
            double apparentTemp = current.get("apparent_temperature").asDouble();
            String tempUnit = unit != null ? unit : "celsius";
            String unitSymbol = "celsius".equals(tempUnit) ? "°C" : "°F";

            if ("fahrenheit".equals(tempUnit)) {
                temp = temp * 9 / 5 + 32;
                apparentTemp = apparentTemp * 9 / 5 + 32;
            }

            String condition = weatherCodeToText(weatherCode);
            String locationDisplay = geo.name;
            if (!geo.admin1.isEmpty() && !geo.admin1.equals(geo.name)) {
                locationDisplay = geo.admin1 + "·" + geo.name;
            }

            String result = String.format(
                    "📍 %s（%s）实时天气：\n" +
                    "🌤 天气状况：%s\n" +
                    "🌡 当前温度：%.1f%s（体感%.1f%s）\n" +
                    "💧 相对湿度：%d%%\n" +
                    "💨 风速：%.1f km/h\n" +
                    "🕐 更新时间：%s\n" +
                    "（数据来源：Open-Meteo 开放气象API）",
                    locationDisplay, geo.country, condition,
                    temp, unitSymbol, apparentTemp, unitSymbol,
                    humidity, windSpeed,
                    current.has("time") ? current.get("time").asText() : "实时"
            );
            log.info("天气查询结果: city={}, condition={}, temp={}{}", city, condition, temp, unitSymbol);
            return result;
        } catch (Exception e) {
            log.error("天气查询异常: city={}, error={}", city, e.getMessage(), e);
            return String.format("天气查询服务暂时不可用（%s），请稍后重试。", e.getMessage());
        }
    }

    @Tool(description = "获取指定城市未来3天的天气预报，包括每天的天气状况、最高/最低温度、降水概率。当用户询问未来天气、明后天天气、天气预报、会不会下雨等问题时使用此工具。数据来自Open-Meteo气象API。")
    public String getWeatherForecast(
            @ToolParam(description = "城市名称，如：北京、上海、深圳、广州、杭州等") String city) {
        log.info("天气预报工具被调用(真实API): city={}", city);

        GeocodeResult geo = geocode(city);
        if (geo == null) {
            return String.format("抱歉，未能找到城市「%s」的天气信息，请尝试其他城市名称。", city);
        }

        try {
            String url = String.format(WEATHER_URL, geo.lat, geo.lon);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return String.format("天气预报获取失败（HTTP %d），请稍后重试。", response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode daily = root.get("daily");

            String locationDisplay = geo.name;
            if (!geo.admin1.isEmpty() && !geo.admin1.equals(geo.name)) {
                locationDisplay = geo.admin1 + "·" + geo.name;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("📍 %s（%s）未来3天天气预报：\n\n", locationDisplay, geo.country));

            String[] dayLabels = {"明天", "后天", "大后天"};
            JsonNode times = daily.get("time");
            JsonNode weatherCodes = daily.get("weather_code");
            JsonNode maxTemps = daily.get("temperature_2m_max");
            JsonNode minTemps = daily.get("temperature_2m_min");
            JsonNode precipProbs = daily.get("precipitation_probability_max");

            for (int i = 1; i < Math.min(times.size(), 4); i++) {
                int code = weatherCodes.get(i).asInt();
                double maxT = maxTemps.get(i).asDouble();
                double minT = minTemps.get(i).asDouble();
                int precipProb = precipProbs.get(i).asInt();
                String cond = weatherCodeToText(code);
                String label = (i - 1) < dayLabels.length ? dayLabels[i - 1] : times.get(i).asText();
                sb.append(String.format("%s（%s）：%s，%.0f°C ~ %.0f°C，降水概率%d%%\n",
                        label, times.get(i).asText().substring(5), cond, minT, maxT, precipProb));
            }

            sb.append("\n（数据来源：Open-Meteo 开放气象API）");
            log.info("天气预报结果: city={}, days={}", city, Math.min(times.size() - 1, 3));
            return sb.toString();
        } catch (Exception e) {
            log.error("天气预报异常: city={}, error={}", city, e.getMessage(), e);
            return String.format("天气预报服务暂时不可用（%s），请稍后重试。", e.getMessage());
        }
    }

    private record GeocodeResult(double lat, double lon, String name, String country, String admin1) {}
}
