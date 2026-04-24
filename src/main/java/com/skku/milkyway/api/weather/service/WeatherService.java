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
import java.util.Map;

/**
 * 기상청 초단기예보 API를 통해 서울 자치구별 날씨를 제공하는 서비스.
 *
 * <p>개별 자치구를 호출할 때마다 API를 때리는 대신, 서울 전체 자치구 날씨 스냅샷을
 * 한 번에 구성하고 일정 시간 동안 재사용한다.</p>
 */
@Slf4j
@Service
public class WeatherService {

    private static final String BASE_URL =
            "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0";
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;
    private static final long REQUEST_DELAY_MS = 120L;
    private static final WeatherResponse DEFAULT =
            new WeatherResponse(20.0, 60.0, SkyCondition.SUNNY, PrecipitationType.NONE, 0.0);

    private final String apiKey;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private volatile WeatherSnapshot weatherSnapshot = WeatherSnapshot.empty();

    public WeatherService(@Value("${kma.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /** 특정 자치구의 최신 날씨를 서울 전체 스냅샷 캐시에서 반환한다. */
    public WeatherResponse getWeather(SeoulDistrict district) {
        ensureSnapshotLoaded();
        return weatherSnapshot.values().getOrDefault(district, DEFAULT);
    }

    /** 캐시가 비었거나 만료된 경우에만 서울 전체 날씨 스냅샷을 다시 구성한다. */
    private void ensureSnapshotLoaded() {
        if (weatherSnapshot.isExpired()) {
            synchronized (this) {
                if (weatherSnapshot.isExpired()) {
                    weatherSnapshot = loadSnapshot();
                }
            }
        }
    }

    /** 서울 25개 자치구의 날씨를 순차적으로 조회해 하나의 스냅샷으로 만든다. */
    private WeatherSnapshot loadSnapshot() {
        long startedAt = System.currentTimeMillis();
        log.info("[KMA] 서울 전체 날씨 스냅샷 갱신 시작 - districts={}", SeoulDistrict.values().length);

        Map<SeoulDistrict, WeatherResponse> previousValues = new EnumMap<>(SeoulDistrict.class);
        previousValues.putAll(weatherSnapshot.values());
        Map<SeoulDistrict, WeatherResponse> nextValues = new EnumMap<>(SeoulDistrict.class);

        for (SeoulDistrict district : SeoulDistrict.values()) {
            try {
                WeatherResponse weather = fetchWeather(district);
                nextValues.put(district, weather);
                log.info(
                        "[KMA] 날씨 조회 완료 - {} (기온={}°C, 습도={}%, 하늘={}, 강수={}, 풍속={}m/s)",
                        district.getKoreanName(),
                        weather.temperature(),
                        weather.humidity(),
                        weather.sky().getLabel(),
                        weather.precipitationType().getLabel(),
                        weather.windSpeed()
                );
            } catch (Exception e) {
                WeatherResponse fallback = previousValues.getOrDefault(district, DEFAULT);
                nextValues.put(district, fallback);
                log.error("[KMA] 날씨 조회 실패 - {}: {}", district.getKoreanName(), e.getMessage());
                log.info("[KMA] 이전 성공값 유지 - {}", district.name());
            }

            sleepQuietly(REQUEST_DELAY_MS);
        }

        log.info(
                "[KMA] 서울 전체 날씨 스냅샷 갱신 완료 - districts={}, elapsed={}ms",
                nextValues.size(),
                System.currentTimeMillis() - startedAt
        );
        return new WeatherSnapshot(Map.copyOf(nextValues), System.currentTimeMillis());
    }

    /** 하나의 자치구에 대해 실황/예보를 조합해 날씨 응답을 만든다. */
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
        PrecipitationType pty = PrecipitationType.fromCode(parseCode(ncst.get("PTY")));
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

        return new WeatherResponse(temperature, humidity, sky, pty, windSpeed);
    }

    /** 실황 응답에서 category -> obsrValue 맵을 추출한다. */
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

    /** 예보 응답에서 첫 번째 SKY 값을 읽어 하늘상태 enum으로 변환한다. */
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

    /** 숫자형 문자열을 double로 파싱하고, 실패하면 기본값을 반환한다. */
    private double parseDouble(String value, double fallback) {
        try {
            return (value != null && !value.isBlank()) ? Double.parseDouble(value) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 정수 코드 문자열을 파싱하고, 실패하면 0을 반환한다. */
    private int parseCode(String value) {
        try {
            return (value != null && !value.isBlank()) ? Integer.parseInt(value.trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 외부 API 호출 사이에 짧은 간격을 둬 급격한 요청 폭주를 줄인다. */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record WeatherSnapshot(Map<SeoulDistrict, WeatherResponse> values, long timestamp) {
        static WeatherSnapshot empty() {
            return new WeatherSnapshot(Map.of(), 0L);
        }

        boolean isExpired() {
            return values.isEmpty() || System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
}
