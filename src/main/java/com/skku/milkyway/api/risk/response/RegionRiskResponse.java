package com.skku.milkyway.api.risk.response;

import com.skku.milkyway.api.code.PrecipitationType;
import com.skku.milkyway.api.code.RiskLevel;
import com.skku.milkyway.api.code.SkyCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionRiskResponse {
    private String districtCode;
    private String regionName;
    private double latitude;
    private double longitude;
    private RiskLevel riskLevel;
    private int riskPercent;
    private int instaCnt;
    private double temperature;
    private double humidity;
    private double illumination;
    private SkyCondition sky;
    private PrecipitationType precipitationType;
    private double windSpeed;
    private int temperatureScore;
    private int humidityScore;
    private int illuminationScore;
    private int windScore;
    private double weatherIndex;
    private double habitatFactor;
    private double trafficFactor;
    private double riskIndex;
}
