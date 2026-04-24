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
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

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

    public RegionRiskResponse getRegion(SeoulDistrict district) {
        return buildData().stream()
                .filter(region -> region.getDistrictCode().equals(district.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 자치구입니다: " + district));
    }

    public List<RegionRiskResponse> getRegions(RiskLevel riskLevel) {
        List<RegionRiskResponse> data = buildData();
        if (riskLevel == null) {
            return data;
        }
        return data.stream()
                .filter(region -> region.getRiskLevel() == riskLevel)
                .toList();
    }

    private List<RegionRiskResponse> buildData() {
        return Arrays.stream(SeoulDistrict.values())
                .map(this::toResponse)
                .toList();
    }

    private RegionRiskResponse toResponse(SeoulDistrict district) {
        WeatherResponse weather = weatherService.getWeather(district);
        double illumination = illuminationService.getCurrentIllumination(district);
        double weatherIndex = calculateWeatherIndex(weather, illumination);
        double habitatFactor = forestAreaService.getHabitatFactor(district);
        double trafficFactor = toTrafficFactor(trafficService.getCurrentTrafficScore(district));
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
                .weatherIndex(weatherIndex)
                .habitatFactor(habitatFactor)
                .trafficFactor(trafficFactor)
                .riskIndex(riskIndex)
                .build();
    }

    private double calculateWeatherIndex(WeatherResponse weather, double illumination) {
        double weightedScore =
                (TEMP_WEIGHT * temperatureScore(weather.temperature())) +
                (HUMIDITY_WEIGHT * humidityScore(weather.humidity())) +
                (ILLUMINATION_WEIGHT * illuminationScore(illumination)) +
                (WIND_WEIGHT * windScore(toMph(weather.windSpeed())));

        return round(weightedScore / 100.0);
    }

    private double calculateRiskIndex(double weatherIndex, double habitatFactor, double trafficFactor) {
        return round(((weatherIndex + habitatFactor + trafficFactor) / 3.0) * 10.0);
    }

    private int toPercent(double riskIndex) {
        double clamped = Math.max(0.0, Math.min(10.0, riskIndex));
        return (int) Math.round(clamped * 10.0);
    }

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

    private int illuminationScore(double illumination) {
        if (illumination < 1500) {
            return 10;
        }
        if (illumination < 2000) {
            return 100;
        }
        return 60;
    }

    private int windScore(double windSpeedMph) {
        if (windSpeedMph < 5) {
            return 30;
        }
        if (windSpeedMph < 8) {
            return 100;
        }
        return 75;
    }

    private double toTrafficFactor(double trafficScore) {
        return round(Math.max(0.0, Math.min(1.0, trafficScore)));
    }

    private double toMph(double metersPerSecond) {
        return metersPerSecond * 2.23694;
    }

    private double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }
}
