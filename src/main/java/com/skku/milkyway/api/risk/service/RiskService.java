package com.skku.milkyway.api.risk.service;

import com.skku.milkyway.api.code.PrecipitationType;
import com.skku.milkyway.api.code.RiskLevel;
import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.risk.response.RegionRiskResponse;
import com.skku.milkyway.api.weather.dto.WeatherResponse;
import com.skku.milkyway.api.weather.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RiskService {

    private final WeatherService weatherService;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public RegionRiskResponse getRegion(SeoulDistrict district) {
        return buildData().stream()
                .filter(r -> r.getDistrictCode().equals(district.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 자치구: " + district));
    }

    public List<RegionRiskResponse> getRegions(RiskLevel riskLevel) {
        List<RegionRiskResponse> data = buildData();
        if (riskLevel == null) return data;
        return data.stream()
                .filter(r -> r.getRiskLevel() == riskLevel)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------

    private List<RegionRiskResponse> buildData() {
        return Arrays.stream(SeoulDistrict.values())
                .map(this::of)
                .toList();
    }

    private RegionRiskResponse of(SeoulDistrict district) {
        WeatherResponse w = weatherService.getWeather(district);
        int instaCnt = 0; // TODO: InstagramCountStore 연동
        int percent = calculatePercent(w, instaCnt);
        RiskLevel level = toRiskLevel(percent);

        return RegionRiskResponse.builder()
                .districtCode(district.name())
                .regionName(district.getKoreanName())
                .latitude(district.getLatitude())
                .longitude(district.getLongitude())
                .riskLevel(level)
                .riskPercent(percent)
                .instaCnt(instaCnt)
                .temperature(w.temperature())
                .humidity(w.humidity())
                .sky(w.sky())
                .precipitationType(w.precipitationType())
                .windSpeed(w.windSpeed())
                .build();
    }

    // -------------------------------------------------------------------------
    // Risk calculation
    // -------------------------------------------------------------------------

    /**
     * 러브버그 발생 위험 퍼센트를 계산한다 (0~100).
     *
     * <pre>
     * 점수 구성
     *   기온 (최대 40점): 26~30°C 최적
     *   습도 (최대 30점): 70~85% 최적
     *   인스타 언급 수 (최대 30점): 직접 목격 근거
     *   강수형태 패널티 (최대 -25점): 비/눈은 활동 억제
     *   풍속 패널티 (최대 -10점): 강풍은 활동 억제
     * </pre>
     */
    private int calculatePercent(WeatherResponse w, int instaCnt) {
        int score = tempScore(w.temperature())
                + humidityScore(w.humidity())
                + instaScore(instaCnt)
                + precipPenalty(w.precipitationType())
                + windPenalty(w.windSpeed());
        return Math.max(0, Math.min(100, score));
    }

    private RiskLevel toRiskLevel(int percent) {

        // 추후에 이 부분 지울 것
        percent = (int) (Math.random() * 100);

        if (percent >= 75) return RiskLevel.CRITICAL;
        if (percent >= 50) return RiskLevel.DANGER;
        if (percent >= 25) return RiskLevel.CAUTION;
        return RiskLevel.SAFE;
    }

    /** 기온 점수 (0~40). 러브버그 활동 최적 온도: 26~30°C */
    private int tempScore(double temp) {
        if (temp < 18) return 0;
        if (temp < 22) return 10;
        if (temp < 26) return 25;
        if (temp < 30) return 40;
        if (temp < 34) return 30;
        return 18; // 34°C 이상 — 너무 더워서 활동 감소
    }

    /** 습도 점수 (0~30). 러브버그 활동 최적 습도: 70~85% */
    private int humidityScore(double humidity) {
        if (humidity < 40) return 0;
        if (humidity < 55) return 8;
        if (humidity < 70) return 20;
        if (humidity < 85) return 30;
        return 20; // 85% 이상 — 과습도로 약간 감소
    }

    /** 인스타그램 언급 수 점수 (0~30). 직접 목격 근거 */
    private int instaScore(int instaCnt) {
        if (instaCnt <= 0)  return 0;
        if (instaCnt <= 5)  return 10;
        if (instaCnt <= 15) return 20;
        if (instaCnt <= 30) return 25;
        return 30;
    }

    /** 강수형태 패널티. 비·눈은 러브버그 활동을 억제 */
    private int precipPenalty(PrecipitationType pty) {
        return switch (pty) {
            case RAIN_DROP        -> -8;
            case RAIN_DROP_SNOW   -> -12;
            case RAIN, RAIN_SNOW  -> -18;
            case SNOW, SNOW_DRIFT -> -25;
            default               -> 0;
        };
    }

    /** 풍속 패널티. 강풍은 러브버그 비행을 억제 */
    private int windPenalty(double wsd) {
        if (wsd > 6) return -10;
        if (wsd > 3) return -5;
        return 0;
    }
}
