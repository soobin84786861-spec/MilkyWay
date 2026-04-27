package com.skku.milkyway.api.risk.service;

import com.skku.milkyway.api.code.RiskLevel;
import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.forest.service.ForestAreaService;
import com.skku.milkyway.api.illumination.service.IlluminationService;
import com.skku.milkyway.api.risk.response.RegionRiskResponse;
import com.skku.milkyway.api.traffic.service.TrafficService;
import com.skku.milkyway.api.weather.dto.WeatherResponse;
import com.skku.milkyway.api.weather.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskService {

    private static final double TEMP_WEIGHT = 0.37;
    private static final double HUMIDITY_WEIGHT = 0.28;
    private static final double ILLUMINATION_WEIGHT = 0.27;
    private static final double WIND_WEIGHT = 0.04;

    private final WeatherService weatherService;
    private final IlluminationService illuminationService;
    private final TrafficService trafficService;
    private final ForestAreaService forestAreaService;
    private final RiskAnalysisExcelService riskAnalysisExcelService;

    private volatile RiskSnapshot riskSnapshot = RiskSnapshot.empty();

    /**
     * 특정 자치구의 최신 위험도 계산 결과를 반환한다.
     */
    public RegionRiskResponse getRegion(SeoulDistrict district) {
        return getCachedData().stream()
                .filter(region -> region.getDistrictCode().equals(district.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 자치구입니다: " + district));
    }

    /**
     * 전체 자치구 위험도 목록을 만들고, 필요하면 위험 등급으로 필터링한다.
     */
    public List<RegionRiskResponse> getRegions(RiskLevel riskLevel) {
        List<RegionRiskResponse> data = getCachedData();
        if (riskLevel == null) {
            return data;
        }
        return data.stream()
                .filter(region -> region.getRiskLevel() == riskLevel)
                .toList();
    }

    /**
     * 전체 위험도 계산 결과를 캐시에 보관하고, 만료 시에만 다시 계산한다.
     */
    private List<RegionRiskResponse> getCachedData() {
        if (riskSnapshot.isExpired()) {
            synchronized (this) {
                if (riskSnapshot.isExpired()) {
                    riskSnapshot = new RiskSnapshot(buildData(), LocalDateTime.now());
                }
            }
        }
        return riskSnapshot.data();
    }

    /**
     * 서울 25개 자치구에 대한 최신 위험도 응답을 생성한다.
     */
    private List<RegionRiskResponse> buildData() {
        long startedAt = System.currentTimeMillis();
        log.info("[Risk] 전체 자치구 위험도 계산 시작 - districts={}", SeoulDistrict.values().length);

        Map<SeoulDistrict, WeatherResponse> weatherByDistrict = weatherService.getAllWeather();
        Map<SeoulDistrict, Double> illuminationByDistrict = illuminationService.getAllCurrentIllumination();
        Map<SeoulDistrict, Double> trafficAverageByDistrict = trafficService.getAllCurrentAverageTraffic();
        Map<SeoulDistrict, Double> trafficScoreByDistrict = trafficService.getAllCurrentTrafficScores();
        Map<SeoulDistrict, Double> habitatRatioByDistrict = Arrays.stream(SeoulDistrict.values())
                .collect(java.util.stream.Collectors.toMap(
                        district -> district,
                        forestAreaService::getHabitatRatio,
                        (left, right) -> left,
                        () -> new java.util.EnumMap<>(SeoulDistrict.class)
                ));

        List<RegionRiskResponse> result = Arrays.stream(SeoulDistrict.values())
                .map(district -> toResponse(
                        district,
                        weatherByDistrict.getOrDefault(district, defaultWeather()),
                        illuminationByDistrict.getOrDefault(district, 0.0),
                        trafficScoreByDistrict.getOrDefault(district, 0.0)
                ))
                .toList();

        riskAnalysisExcelService.export(
                LocalDateTime.now(),
                result,
                habitatRatioByDistrict,
                trafficScoreByDistrict,
                forestAreaService.getMaxForestArea(),
                trafficAverageByDistrict
        );

        log.info("[Risk] 전체 자치구 위험도 계산 완료 - elapsed={}ms", System.currentTimeMillis() - startedAt);
        return result;
    }

    /**
     * 하나의 자치구에 대해 W, G, V, LORR를 계산하고 API 응답 객체로 변환한다.
     */
    private RegionRiskResponse toResponse(
            SeoulDistrict district,
            WeatherResponse weather,
            double illumination,
            double trafficScore
    ) {
        int temperatureScore = temperatureScore(weather.temperature());
        int humidityScore = humidityScore(weather.humidity());
        int illuminationScore = illuminationScore(illumination);
        int windScore = windScore(toMph(weather.windSpeed()));
        double weatherIndex = calculateWeatherIndex(weather, illumination);
        double habitatFactor = forestAreaService.getHabitatFactor(district);
        double trafficFactor = toTrafficFactor(trafficScore);

        double riskIndex = calculateRiskIndex(weatherIndex, habitatFactor, trafficFactor);
        int riskPercent = toPercent(riskIndex);

        return RegionRiskResponse.builder()
                .districtCode(district.name())
                .regionName(district.getKoreanName())
                .latitude(district.getLatitude())
                .longitude(district.getLongitude())
                .riskLevel(toRiskLevel(riskPercent))
                .riskPercent(riskPercent)
                .instaCnt(0)
                .temperature(weather.temperature())
                .humidity(weather.humidity())
                .illumination(illumination)
                .sky(weather.sky())
                .precipitationType(weather.precipitationType())
                .windSpeed(weather.windSpeed())
                .temperatureScore(temperatureScore)
                .humidityScore(humidityScore)
                .illuminationScore(illuminationScore)
                .windScore(windScore)
                .weatherIndex(weatherIndex)
                .habitatFactor(habitatFactor)
                .trafficFactor(trafficFactor)
                .riskIndex(riskIndex)
                .build();
    }

    /**
     * 기온, 습도, 조도, 풍속 점수에 가중치를 적용해 W 값을 계산한다.
     */
    private double calculateWeatherIndex(WeatherResponse weather, double illumination) {
        double weightedScore =
                (TEMP_WEIGHT * temperatureScore(weather.temperature())) +
                (HUMIDITY_WEIGHT * humidityScore(weather.humidity())) +
                (ILLUMINATION_WEIGHT * illuminationScore(illumination)) +
                (WIND_WEIGHT * windScore(toMph(weather.windSpeed())));

        return round(weightedScore / 100.0);
    }

    /**
     * 최종 공식에 따라 W, G, V의 평균값에 10을 곱해 LORR를 계산한다.
     */
    private double calculateRiskIndex(double weatherIndex, double habitatFactor, double trafficFactor) {
        return round(((weatherIndex + habitatFactor + trafficFactor) / 3.0) * 10.0);
    }

    /**
     * 0~10 범위의 LORR를 기존 API 응답용 0~100 퍼센트로 환산한다.
     */
    private int toPercent(double riskIndex) {
        double clamped = Math.max(0.0, Math.min(10.0, riskIndex));
        return (int) Math.round(clamped * 10.0);
    }

    /**
     * 환산된 퍼센트 값을 기존 위험 등급 체계로 변환한다.
     */
    private RiskLevel toRiskLevel(int percent) {
        if (percent >= 75) {
            return RiskLevel.CRITICAL;
        }
        if (percent >= 50) {
            return RiskLevel.DANGER;
        }
        if (percent >= 25) {
            return RiskLevel.CAUTION;
        }
        return RiskLevel.SAFE;
    }

    /**
     * 기온 점수표를 그대로 적용한다.
     */
    private int temperatureScore(double temperature) {
        if (temperature <= 19) {
            return 10;
        }
        if (temperature <= 24) {
            return 60;
        }
        if (temperature <= 34) {
            return 100;
        }
        return 40;
    }

    /**
     * 습도 점수표를 그대로 적용한다.
     */
    private int humidityScore(double humidity) {
        if (humidity <= 40) {
            return 20;
        }
        if (humidity <= 65) {
            return 50;
        }
        if (humidity <= 85) {
            return 100;
        }
        return 70;
    }

    /**
     * lux 기준 조도 구간을 점수로 변환한다.
     */
    private int illuminationScore(double illumination) {
        if (illumination < 1500) {
            return 10;
        }
        if (illumination < 2000) {
            return 100;
        }
        return 60;
    }

    /**
     * m/s를 mph로 환산한 뒤 풍속 점수표를 적용한다.
     */
    private int windScore(double windSpeedMph) {
        if (windSpeedMph < 5) {
            return 30;
        }
        if (windSpeedMph < 8) {
            return 100;
        }
        return 75;
    }

    /**
     * 교통량 비율값을 기획서 기준의 증폭 계수로 변환한다.
     */
    private double toTrafficFactor(double trafficScore) {
        double trafficRatio = Math.max(0.0, Math.min(1.0, trafficScore));
        return round(0.5 + (trafficRatio * 1.5));
    }

    /**
     * 기상청 풍속 단위인 m/s를 mph로 변환한다.
     */
    private double toMph(double metersPerSecond) {
        return metersPerSecond * 2.23694;
    }

    /**
     * 계산 결과를 소수점 넷째 자리까지 반올림한다.
     */
    private double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }

    /**
     * 원천 날씨 조회 실패 시 사용하는 기본값을 반환한다.
     */
    private WeatherResponse defaultWeather() {
        return new WeatherResponse(20.0, 60.0, com.skku.milkyway.api.code.SkyCondition.SUNNY, com.skku.milkyway.api.code.PrecipitationType.NONE, 0.0);
    }

    private record RiskSnapshot(List<RegionRiskResponse> data, LocalDateTime updatedAt) {
        static RiskSnapshot empty() {
            return new RiskSnapshot(List.of(), null);
        }

        boolean isExpired() {
            if (updatedAt == null || data.isEmpty()) {
                return true;
            }
            long ageMs = ChronoUnit.MILLIS.between(updatedAt, LocalDateTime.now());
            return ageMs > 30 * 60 * 1000L;
        }
    }
}
