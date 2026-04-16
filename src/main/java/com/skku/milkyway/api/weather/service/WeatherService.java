package com.skku.milkyway.api.weather.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skku.milkyway.api.code.PrecipitationType;
import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.code.SkyCondition;
import com.skku.milkyway.api.weather.dto.WeatherResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 기상청 단기예보 Open API (VilageFcstInfoService_2.0) 를 통해
 * 서울 자치구별 실시간 기상 데이터를 제공하는 서비스.
 *
 * <ul>
 *   <li>초단기실황(getUltraSrtNcst): 기온·습도·강수형태·풍속</li>
 *   <li>초단기예보(getUltraSrtFcst): 하늘상태</li>
 * </ul>
 *
 * 조회 결과는 30분간 인메모리 캐시에 보관한다.
 */
@Slf4j
@Service
public class WeatherService {

    private static final String BASE_URL =
            "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0";
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L; // 30분

    private static final WeatherResponse DEFAULT =
            new WeatherResponse(20.0, 60.0, SkyCondition.SUNNY, PrecipitationType.NONE, 0.0);

    private final String apiKey;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private record CachedWeather(WeatherResponse data, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    private final ConcurrentHashMap<SeoulDistrict, CachedWeather> cache =
            new ConcurrentHashMap<>();

    public WeatherService(@Value("${kma.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public WeatherResponse getWeather(SeoulDistrict district) {
        CachedWeather cached = cache.get(district);
        if (cached != null && !cached.isExpired()) {
            return cached.data();
        }
        try {
            WeatherResponse result = fetchWeather(district);
            cache.put(district, new CachedWeather(result, System.currentTimeMillis()));
            log.info("[KMA] 날씨 조회 완료 - {} (기온={}°C, 습도={}%, 하늘={}, 강수={}, 풍속={}m/s)",
                    district.getKoreanName(),
                    result.temperature(), result.humidity(),
                    result.sky().getLabel(),
                    result.precipitationType().getLabel(),
                    result.windSpeed());
            return result;
        } catch (Exception e) {
            log.error("[KMA] 날씨 조회 실패 - {}: {}", district.getKoreanName(), e.getMessage());
            return DEFAULT;
        }
    }

    // -----------------------------------------------------------------------

    private WeatherResponse fetchWeather(SeoulDistrict district) throws Exception {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        int nx = district.getNx();
        int ny = district.getNy();

        // 초단기실황: 매시 정시 기준, :10 이후 호출 가능
        ZonedDateTime ncstBase = now.getMinute() >= 10
                ? now.truncatedTo(ChronoUnit.HOURS)
                : now.minusHours(1).truncatedTo(ChronoUnit.HOURS);

        String ncstDate = ncstBase.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String ncstTime = ncstBase.format(DateTimeFormatter.ofPattern("HHmm"));

        String ncstUrl = BASE_URL + "/getUltraSrtNcst"
                + "?serviceKey=" + apiKey
                + "&numOfRows=10&pageNo=1&dataType=JSON"
                + "&base_date=" + ncstDate
                + "&base_time=" + ncstTime
                + "&nx=" + nx + "&ny=" + ny;

        log.debug("[KMA] 초단기실황 요청 - {} nx={} ny={} base={}{}",
                district.getKoreanName(), nx, ny, ncstDate, ncstTime);

        String ncstJson = restTemplate.getForObject(URI.create(ncstUrl), String.class);
        Map<String, String> ncst = parseObservedItems(ncstJson);

        double temperature          = parseDouble(ncst.get("T1H"), DEFAULT.temperature());
        double humidity             = parseDouble(ncst.get("REH"), DEFAULT.humidity());
        PrecipitationType pty       = PrecipitationType.fromCode(parseCode(ncst.get("PTY")));
        double windSpeed            = parseDouble(ncst.get("WSD"), DEFAULT.windSpeed());

        // 초단기예보: 매시 :30 기준, :45 이후 호출 가능 → SKY 추출
        ZonedDateTime fcstBase = now.getMinute() >= 45
                ? now.truncatedTo(ChronoUnit.HOURS).plusMinutes(30)
                : now.minusHours(1).truncatedTo(ChronoUnit.HOURS).plusMinutes(30);

        String fcstDate = fcstBase.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fcstTime = fcstBase.format(DateTimeFormatter.ofPattern("HHmm"));

        String fcstUrl = BASE_URL + "/getUltraSrtFcst"
                + "?serviceKey=" + apiKey
                + "&numOfRows=60&pageNo=1&dataType=JSON"
                + "&base_date=" + fcstDate
                + "&base_time=" + fcstTime
                + "&nx=" + nx + "&ny=" + ny;

        log.debug("[KMA] 초단기예보 요청 - {} nx={} ny={} base={}{}",
                district.getKoreanName(), nx, ny, fcstDate, fcstTime);

        String fcstJson = restTemplate.getForObject(URI.create(fcstUrl), String.class);
        SkyCondition sky = parseForecastSky(fcstJson);

        return new WeatherResponse(temperature, humidity, sky, pty, windSpeed);
    }

    /** 초단기실황 응답에서 category → obsrValue 맵 구성 */
    private Map<String, String> parseObservedItems(String json) throws Exception {
        JsonNode items = objectMapper.readTree(json)
                .path("response").path("body").path("items").path("item");
        Map<String, String> result = new HashMap<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                result.put(item.path("category").asText(), item.path("obsrValue").asText());
            }
        }
        return result;
    }

    /** 초단기예보 응답에서 첫 번째 SKY 항목을 {@link SkyCondition}으로 반환 */
    private SkyCondition parseForecastSky(String json) throws Exception {
        JsonNode items = objectMapper.readTree(json)
                .path("response").path("body").path("items").path("item");
        if (items.isArray()) {
            for (JsonNode item : items) {
                if ("SKY".equals(item.path("category").asText())) {
                    return SkyCondition.fromCode(parseCode(item.path("fcstValue").asText()));
                }
            }
        }
        return DEFAULT.sky();
    }

    private double parseDouble(String value, double fallback) {
        try {
            return (value != null && !value.isBlank()) ? Double.parseDouble(value) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 정수 코드 파싱 (실패 시 0 반환) */
    private int parseCode(String value) {
        try {
            return (value != null && !value.isBlank()) ? Integer.parseInt(value.trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
