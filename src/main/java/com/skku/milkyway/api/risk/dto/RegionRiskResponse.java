package com.skku.milkyway.api.risk.dto;

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
}