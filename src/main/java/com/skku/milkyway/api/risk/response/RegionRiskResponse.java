package com.skku.milkyway.api.risk.response;

import com.skku.milkyway.api.risk.code.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RegionRiskResponse {
    private String regionName;
    private double latitude;
    private double longitude;
    private RiskLevel riskLevel;
    private int riskPercent;
    private int instaCnt;
}