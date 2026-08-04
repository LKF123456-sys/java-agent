package com.ailearn.tools; // 声明包名，tools包存放AI可调用的工具类

// Jackson 3 迁移：包名由 com.fasterxml.jackson 更换为 tools.jackson（类名与API保持不变）
import tools.jackson.databind.JsonNode; // JSON树节点，用于解析嵌套JSON
import tools.jackson.databind.ObjectMapper; // JSON对象映射器
import lombok.extern.slf4j.Slf4j; // Lombok日志注解
import org.springframework.ai.tool.annotation.Tool; // Spring AI工具注解
import org.springframework.ai.tool.annotation.ToolParam; // Spring AI工具参数注解
import org.springframework.stereotype.Component; // Spring组件注解

import java.net.URI; // URI统一资源标识符
import java.net.URLEncoder; // URL编码工具，处理中文等特殊字符
import java.net.http.HttpClient; // JDK内置HTTP客户端（JDK11+）
import java.net.http.HttpRequest; // HTTP请求构建器
import java.net.http.HttpResponse; // HTTP响应对象
import java.nio.charset.StandardCharsets; // 字符集常量
import java.time.Duration; // 时间段类，用于超时设置
import java.util.HashMap; // 哈希表
import java.util.Map; // Map接口

/**
 * 天气查询工具类
 * 提供给AI Agent调用的天气查询功能，支持实时天气查询和未来3天天气预报。
 * 基于Open-Meteo开放气象API实现，无需API Key即可使用。
 *
 * <p>API调用流程：
 * <ol>
 *   <li>地理编码：调用Open-Meteo Geocoding API将城市名转换为经纬度坐标</li>
 *   <li>天气查询：使用经纬度调用Open-Meteo Forecast API获取天气数据</li>
 *   <li>数据解析：解析JSON响应，将WMO天气代码转换为中文天气描述</li>
 *   <li>格式化输出：将天气数据格式化为易读的中文文本</li>
 * </ol>
 *
 * @author AiLearn Platform
 */
@Slf4j // 自动注入log
@Component // 注册为Spring Bean
public class WeatherTool { // 天气工具类定义

    /**
     * Jackson ObjectMapper实例，用于JSON序列化和反序列化
     */
    private final ObjectMapper objectMapper = new ObjectMapper(); // 创建JSON解析器

    /**
     * HTTP客户端实例，用于调用外部天气API
     * 配置10秒连接超时，防止长时间阻塞
     */
    private final HttpClient httpClient = HttpClient.newBuilder() // 构建HTTP客户端
            .connectTimeout(Duration.ofSeconds(10)) // 连接超时10秒
            .build(); // 构建

    /**
     * 地理编码API URL模板
     * 用于将城市名称转换为经纬度坐标
     * 参数说明：%s - URL编码后的城市名称
     */
    private static final String GEOCODE_URL = "https://geocoding-api.open-meteo.com/v1/search?name=%s&count=1&language=zh&format=json"; // 地理编码URL模板

    /**
     * 天气预报API URL模板
     * 用于获取实时天气和未来3天预报数据
     */
    private static final String WEATHER_URL = "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,apparent_temperature&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=3"; // 天气API URL模板

    /**
     * WMO天气代码到中文天气描述的映射表
     * 参考Open-Meteo WMO Weather interpretation codes (WW)
     */
    private static final Map<Integer, String> WEATHER_CODE_MAP = new HashMap<>(); // 静态映射表
    static { // 静态初始化块
        // 晴天相关
        WEATHER_CODE_MAP.put(0, "晴天"); // 0=晴天
        WEATHER_CODE_MAP.put(1, "大部晴朗"); // 1=大部晴朗
        WEATHER_CODE_MAP.put(2, "局部多云"); // 2=局部多云
        WEATHER_CODE_MAP.put(3, "阴天"); // 3=阴天
        // 雾相关
        WEATHER_CODE_MAP.put(45, "雾"); // 45=雾
        WEATHER_CODE_MAP.put(48, "雾凇"); // 48=雾凇
        // 毛毛雨相关
        WEATHER_CODE_MAP.put(51, "小毛毛雨"); // 51=小毛毛雨
        WEATHER_CODE_MAP.put(53, "毛毛雨"); // 53=毛毛雨
        WEATHER_CODE_MAP.put(55, "大毛毛雨"); // 55=大毛毛雨
        WEATHER_CODE_MAP.put(56, "冻毛毛雨"); // 56=冻毛毛雨
        WEATHER_CODE_MAP.put(57, "强冻毛毛雨"); // 57=强冻毛毛雨
        // 雨相关
        WEATHER_CODE_MAP.put(61, "小雨"); // 61=小雨
        WEATHER_CODE_MAP.put(63, "中雨"); // 63=中雨
        WEATHER_CODE_MAP.put(65, "大雨"); // 65=大雨
        WEATHER_CODE_MAP.put(66, "冻雨"); // 66=冻雨
        WEATHER_CODE_MAP.put(67, "强冻雨"); // 67=强冻雨
        // 雪相关
        WEATHER_CODE_MAP.put(71, "小雪"); // 71=小雪
        WEATHER_CODE_MAP.put(73, "中雪"); // 73=中雪
        WEATHER_CODE_MAP.put(75, "大雪"); // 75=大雪
        WEATHER_CODE_MAP.put(77, "雪粒"); // 77=雪粒
        // 阵雨相关
        WEATHER_CODE_MAP.put(80, "小阵雨"); // 80=小阵雨
        WEATHER_CODE_MAP.put(81, "阵雨"); // 81=阵雨
        WEATHER_CODE_MAP.put(82, "强阵雨"); // 82=强阵雨
        WEATHER_CODE_MAP.put(85, "小阵雪"); // 85=小阵雪
        WEATHER_CODE_MAP.put(86, "大阵雪"); // 86=大阵雪
        // 雷暴相关
        WEATHER_CODE_MAP.put(95, "雷暴"); // 95=雷暴
        WEATHER_CODE_MAP.put(96, "雷暴伴小冰雹"); // 96=雷暴伴小冰雹
        WEATHER_CODE_MAP.put(99, "雷暴伴大冰雹"); // 99=雷暴伴大冰雹
    }

    /**
     * 将WMO天气代码转换为中文天气描述
     *
     * @param code WMO标准天气代码（0-99）
     * @return String 对应的中文天气描述，未知代码返回"未知天气"
     */
    private String weatherCodeToText(int code) { // 天气代码转中文
        return WEATHER_CODE_MAP.getOrDefault(code, "未知天气"); // 查表，未知返回默认
    }

    /**
     * 地理编码：将城市名称转换为经纬度坐标
     * 调用Open-Meteo Geocoding API进行地名解析
     *
     * @param city 城市名称（中文）
     * @return GeocodeResult 包含纬度、经度、城市名、国家、行政区的结果对象，失败返回null
     */
    private GeocodeResult geocode(String city) { // 私有地理编码方法
        try { // 尝试调用API
            // URL编码城市名称，防止中文和特殊字符导致URL无效
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8); // 中文转%XX编码
            String url = String.format(GEOCODE_URL, encodedCity); // 填充URL模板

            // 构建HTTP GET请求
            HttpRequest request = HttpRequest.newBuilder() // 构建请求
                    .uri(URI.create(url)) // 设置URI
                    .timeout(Duration.ofSeconds(10)) // 10秒超时
                    .GET() // GET方法
                    .build(); // 构建

            // 发送请求并获取响应
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString()); // 发送，响应体转字符串

            // 检查HTTP响应状态码
            if (response.statusCode() != 200) { // 非200
                log.warn("地理编码请求失败: status={}, city={}", response.statusCode(), city); // 警告日志
                return null; // 返回null
            }

            // 解析JSON响应
            JsonNode root = objectMapper.readTree(response.body()); // 解析为JSON树
            JsonNode results = root.get("results"); // 取results数组
            if (results == null || !results.isArray() || results.isEmpty()) { // 无结果
                log.warn("未找到城市: {}", city); // 警告日志
                return null; // 返回null
            }

            // 取第一个匹配结果（API已按相关性排序）
            JsonNode first = results.get(0); // 取第一个
            double lat = first.get("latitude").asDouble(); // 纬度
            double lon = first.get("longitude").asDouble(); // 经度
            String name = first.has("name") ? first.get("name").asText() : city; // 城市名
            String country = first.has("country") ? first.get("country").asText() : ""; // 国家
            String admin1 = first.has("admin1") ? first.get("admin1").asText() : ""; // 行政区

            return new GeocodeResult(lat, lon, name, country, admin1); // 返回结果对象
        } catch (Exception e) { // 异常
            log.error("地理编码异常: city={}, error={}", city, e.getMessage(), e); // 错误日志
            return null; // 返回null
        }
    }

    /**
     * 获取指定城市的当前实时天气信息
     * 包括天气状况、温度、湿度、风速、体感温度等实时数据。
     *
     * @param city 城市名称，必须是中文城市名，如：北京、上海、深圳等
     * @param unit 温度单位，可选值：celsius（摄氏度，默认）或 fahrenheit（华氏度）
     * @return String 格式化的实时天气信息
     */
    @Tool(description = "获取指定城市的当前实时天气信息，包括天气状况、温度、湿度、风速、体感温度等。当用户询问今天天气、当前气温、是否下雨、冷不冷等问题时使用此工具。数据来自Open-Meteo实时气象API。") // 工具说明
    public String getWeather( // 实时天气工具方法
            @ToolParam(description = "城市名称，必须是中文城市名，如：北京、上海、深圳、成都、广州、杭州、武汉、西安等") String city, // 城市参数
            @ToolParam(description = "温度单位，可选值：celsius（摄氏度，默认）或 fahrenheit（华氏度）", required = false) String unit) { // 单位参数（可选）
        log.info("天气查询工具被调用(真实API): city={}, unit={}", city, unit); // 业务日志

        // 第一步：地理编码获取经纬度
        GeocodeResult geo = geocode(city); // 城市名转经纬度
        if (geo == null) { // 地理编码失败
            return String.format("抱歉，未能找到城市「%s」的天气信息，请尝试其他城市名称。", city); // 返回提示
        }

        try { // 尝试获取天气
            // 第二步：构建天气API请求URL
            String url = String.format(WEATHER_URL, geo.lat, geo.lon); // 用经纬度填URL
            HttpRequest request = HttpRequest.newBuilder() // 构建请求
                    .uri(URI.create(url)) // URI
                    .timeout(Duration.ofSeconds(10)) // 超时
                    .GET() // GET
                    .build(); // 构建

            // 发送HTTP请求获取天气数据
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString()); // 发送
            if (response.statusCode() != 200) { // 非200
                return String.format("天气数据获取失败（HTTP %d），请稍后重试。", response.statusCode()); // 返回错误
            }

            // 第三步：解析JSON响应
            JsonNode root = objectMapper.readTree(response.body()); // 解析JSON
            JsonNode current = root.get("current"); // 取current对象

            // 提取当前天气数据字段
            double temp = current.get("temperature_2m").asDouble(); // 温度
            int humidity = current.get("relative_humidity_2m").asInt(); // 湿度
            int weatherCode = current.get("weather_code").asInt(); // 天气代码
            double windSpeed = current.get("wind_speed_10m").asDouble(); // 风速
            double apparentTemp = current.get("apparent_temperature").asDouble(); // 体感温度

            // 温度单位处理
            String tempUnit = unit != null ? unit : "celsius"; // 默认摄氏
            String unitSymbol = "celsius".equals(tempUnit) ? "°C" : "°F"; // 单位符号

            // 如果需要华氏度，进行温度转换：华氏度 = 摄氏度 × 9/5 + 32
            if ("fahrenheit".equals(tempUnit)) { // 华氏
                temp = temp * 9 / 5 + 32; // 温度转换
                apparentTemp = apparentTemp * 9 / 5 + 32; // 体感转换
            }

            // 第四步：天气代码转换为中文描述
            String condition = weatherCodeToText(weatherCode); // 代码转中文

            // 构建地点显示名称（优先显示行政区+城市名）
            String locationDisplay = geo.name; // 默认城市名
            if (!geo.admin1.isEmpty() && !geo.admin1.equals(geo.name)) { // 有不同行政区
                locationDisplay = geo.admin1 + "·" + geo.name; // 行政区·城市
            }

            // 第五步：格式化输出结果
            String result = String.format( // 拼装结果字符串
                    "📍 %s（%s）实时天气：\n" +
                    "🌤 天气状况：%s\n" +
                    "🌡 当前温度：%.1f%s（体感%.1f%s）\n" +
                    "💧 相对湿度：%d%%\n" +
                    "💨 风速：%.1f km/h\n" +
                    "🕐 更新时间：%s\n" +
                    "（数据来源：Open-Meteo 开放气象API）",
                    locationDisplay, geo.country, condition, // 占位1-3
                    temp, unitSymbol, apparentTemp, unitSymbol, // 占位4-7
                    humidity, windSpeed, // 占位8-9
                    current.has("time") ? current.get("time").asText() : "实时" // 占位10：更新时间
            );
            log.info("天气查询结果: city={}, condition={}, temp={}{}", city, condition, temp, unitSymbol); // 业务日志
            return result; // 返回结果
        } catch (Exception e) { // 异常
            log.error("天气查询异常: city={}, error={}", city, e.getMessage(), e); // 错误日志
            return String.format("天气查询服务暂时不可用（%s），请稍后重试。", e.getMessage()); // 返回错误提示
        }
    }

    /**
     * 获取指定城市未来3天的天气预报
     * 包括每天的天气状况、最高/最低温度、降水概率。
     *
     * @param city 城市名称，如：北京、上海、深圳、广州、杭州等
     * @return String 格式化的未来3天天气预报信息
     */
    @Tool(description = "获取指定城市未来3天的天气预报，包括每天的天气状况、最高/最低温度、降水概率。当用户询问未来天气、明后天天气、天气预报、会不会下雨等问题时使用此工具。数据来自Open-Meteo气象API。") // 工具说明
    public String getWeatherForecast( // 天气预报工具方法
            @ToolParam(description = "城市名称，如：北京、上海、深圳、广州、杭州等") String city) { // 城市参数
        log.info("天气预报工具被调用(真实API): city={}", city); // 业务日志

        // 第一步：地理编码获取经纬度
        GeocodeResult geo = geocode(city); // 城市转经纬度
        if (geo == null) { // 失败
            return String.format("抱歉，未能找到城市「%s」的天气信息，请尝试其他城市名称。", city); // 返回提示
        }

        try { // 尝试
            // 第二步：构建并发送天气API请求（与实时天气使用同一API，已包含daily参数）
            String url = String.format(WEATHER_URL, geo.lat, geo.lon); // 填URL
            HttpRequest request = HttpRequest.newBuilder() // 构建请求
                    .uri(URI.create(url)) // URI
                    .timeout(Duration.ofSeconds(10)) // 超时
                    .GET() // GET
                    .build(); // 构建

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString()); // 发送
            if (response.statusCode() != 200) { // 非200
                return String.format("天气预报获取失败（HTTP %d），请稍后重试。", response.statusCode()); // 返回错误
            }

            // 第三步：解析JSON响应获取daily数据
            JsonNode root = objectMapper.readTree(response.body()); // 解析JSON
            JsonNode daily = root.get("daily"); // 取daily对象

            // 构建地点显示名称
            String locationDisplay = geo.name; // 默认城市名
            if (!geo.admin1.isEmpty() && !geo.admin1.equals(geo.name)) { // 有不同行政区
                locationDisplay = geo.admin1 + "·" + geo.name; // 行政区·城市
            }

            // 第四步：构建预报文本
            StringBuilder sb = new StringBuilder(); // 文本构建器
            sb.append(String.format("📍 %s（%s）未来3天天气预报：\n\n", locationDisplay, geo.country)); // 头部

            // 日期标签：明天、后天、大后天
            String[] dayLabels = {"明天", "后天", "大后天"}; // 中文标签数组

            // 提取每日预报数组
            JsonNode times = daily.get("time");                    // 日期数组
            JsonNode weatherCodes = daily.get("weather_code");     // 天气代码数组
            JsonNode maxTemps = daily.get("temperature_2m_max");   // 最高温度数组
            JsonNode minTemps = daily.get("temperature_2m_min");   // 最低温度数组
            JsonNode precipProbs = daily.get("precipitation_probability_max"); // 降水概率数组

            // 遍历未来3天数据（索引0是今天，从1开始）
            for (int i = 1; i < Math.min(times.size(), 4); i++) { // 从1到3
                int code = weatherCodes.get(i).asInt(); // 天气代码
                double maxT = maxTemps.get(i).asDouble(); // 最高温
                double minT = minTemps.get(i).asDouble(); // 最低温
                int precipProb = precipProbs.get(i).asInt(); // 降水概率
                String cond = weatherCodeToText(code); // 代码转中文
                // 使用中文标签，超出标签范围则显示日期
                String label = (i - 1) < dayLabels.length ? dayLabels[i - 1] : times.get(i).asText(); // 选标签
                // 日期格式：从"2024-01-15"截取"01-15"
                sb.append(String.format("%s（%s）：%s，%.0f°C ~ %.0f°C，降水概率%d%%\n", // 拼一行
                        label, times.get(i).asText().substring(5), cond, minT, maxT, precipProb)); // 参数
            }

            sb.append("\n（数据来源：Open-Meteo 开放气象API）"); // 尾部
            log.info("天气预报结果: city={}, days={}", city, Math.min(times.size() - 1, 3)); // 业务日志
            return sb.toString(); // 返回预报文本
        } catch (Exception e) { // 异常
            log.error("天气预报异常: city={}, error={}", city, e.getMessage(), e); // 错误日志
            return String.format("天气预报服务暂时不可用（%s），请稍后重试。", e.getMessage()); // 返回错误
        }
    }

    /**
     * 地理编码结果记录类
     * 用于封装地理编码API返回的位置信息
     *
     * @param lat     纬度坐标
     * @param lon     经度坐标
     * @param name    城市/地点名称
     * @param country 国家名称
     * @param admin1  一级行政区（省/州）名称
     */
    private record GeocodeResult(double lat, double lon, String name, String country, String admin1) {} // 不可变记录类
} // WeatherTool类结束
