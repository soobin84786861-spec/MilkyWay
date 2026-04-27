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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 기상청 초단기예보 API를 통해 서울 자치구별 날씨를 조회하는 서비스.
 */
@Slf4j
@Service
public class WeatherService {

    private static final String BASE_URL = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0";
    private static final long REQUEST_DELAY_MS = 120L;
    private static final WeatherResponse DEFAULT =
            new WeatherResponse(20.0, 60.0, SkyCondition.SUNNY, PrecipitationType.NONE, 0.0);

    private final String apiKey;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WeatherService(@Value("${kma.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 특정 자치구의 현재 날씨를 반환한다.
     */
    public WeatherResponse getWeather(SeoulDistrict district) {
        return getAllWeather().getOrDefault(district, DEFAULT);
    }

    /**
     * 서울 전체 자치구의 현재 날씨를 조회한다.
     */
    public Map<SeoulDistrict, WeatherResponse> getAllWeather() {
        long startedAt = System.currentTimeMillis();
        log.info("[KMA] 서울 전체 날씨 조회 시작 - districts={}", SeoulDistrict.values().length);

        Map<SeoulDistrict, WeatherResponse> weatherByDistrict = new EnumMap<>(SeoulDistrict.class);
        int successCount = 0;
        int fallbackCount = 0;

        for (SeoulDistrict district : SeoulDistrict.values()) {
            try {
                WeatherResponse weather = fetchWeather(district);
                weatherByDistrict.put(district, weather);
                successCount++;
            } catch (Exception e) {
                weatherByDistrict.put(district, DEFAULT);
                fallbackCount++;
                log.warn("[KMA] 날씨 조회 실패 - {}: {}", district.getKoreanName(), e.getMessage());
            }

            sleepQuietly(REQUEST_DELAY_MS);
        }

        logWeatherTable(weatherByDistrict);
        log.info(
                "[KMA] 서울 전체 날씨 조회 완료 - districts={}, success={}, fallback={}, elapsed={}ms",
                weatherByDistrict.size(),
                successCount,
                fallbackCount,
                System.currentTimeMillis() - startedAt
        );
        return Map.copyOf(weatherByDistrict);
    }

    /**
     * 하나의 자치구에 대해 실황/예보를 조합해 날씨 응답을 만든다.
     */
    private WeatherResponse fetchWeather(SeoulDistrict district) throws Exception {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        int nx = district.getNx();
        int ny = district.getNy();

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

        String ncstJson = restTemplate.getForObject(URI.create(ncstUrl), String.class);
        Map<String, String> ncst = parseObservedItems(ncstJson);

        double temperature = parseDouble(ncst.get("T1H"), DEFAULT.temperature());
        double humidity = parseDouble(ncst.get("REH"), DEFAULT.humidity());
        PrecipitationType precipitationType = PrecipitationType.fromCode(parseCode(ncst.get("PTY")));
        double windSpeed = parseDouble(ncst.get("WSD"), DEFAULT.windSpeed());

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

        String fcstJson = restTemplate.getForObject(URI.create(fcstUrl), String.class);
        SkyCondition sky = parseForecastSky(fcstJson);

        return new WeatherResponse(temperature, humidity, sky, precipitationType, windSpeed);
    }

    /**
     * 자치구별 날씨를 표 형태 로그로 출력한다.
     */
    private void logWeatherTable(Map<SeoulDistrict, WeatherResponse> weatherByDistrict) {
        log.info("[KMA] district summary");
        log.info(
                "[KMA] {}",
                String.format(Locale.ROOT, "%-12s | %8s | %8s | %-8s | %-8s | %10s", "district", "temp", "humidity", "sky", "precip", "windSpeed")
        );
        log.info("[KMA] {}", "-------------+----------+----------+----------+----------+------------");

        for (SeoulDistrict district : SeoulDistrict.values()) {
            WeatherResponse weather = weatherByDistrict.getOrDefault(district, DEFAULT);
            log.info(
                    "[KMA] {}",
                    String.format(
                            Locale.ROOT,
                            "%-12s | %8.1f | %8.1f | %-8s | %-8s | %10.1f",
                            district,
                            weather.temperature(),
                            weather.humidity(),
                            weather.sky().name(),
                            weather.precipitationType().name(),
                            weather.windSpeed()
                    )
            );
        }
    }

    /**
     * 실황 응답에서 category -> obsrValue 맵을 추출한다.
     */
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

    /**
     * 예보 응답에서 첫 번째 SKY 값을 읽어 하늘상태 enum으로 변환한다.
     */
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

    /**
     * 숫자형 문자열을 double로 파싱하고, 실패하면 기본값을 반환한다.
     */
    private double parseDouble(String value, double fallback) {
        try {
            return (value != null && !value.isBlank()) ? Double.parseDouble(value) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 정수 코드 문자열을 파싱하고, 실패하면 0을 반환한다.
     */
    private int parseCode(String value) {
        try {
            return (value != null && !value.isBlank()) ? Integer.parseInt(value.trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 여러 API 호출 사이에 짧은 간격을 두어 급격한 요청 폭주를 줄인다.
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
