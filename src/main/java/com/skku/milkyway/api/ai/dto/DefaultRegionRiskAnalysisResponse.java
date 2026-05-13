package com.skku.milkyway.api.ai.dto;

import com.skku.milkyway.api.code.RiskLevel;

public record DefaultRegionRiskAnalysisResponse(
        String districtCode,
        String regionName,
        RiskLevel riskLevel,
        int riskPercent,
        EvidenceData evidenceData,
        String summary,
        String comfortMessage,
        String timeAdvice
) {
    public record EvidenceData(
            double temperature,
            double humidity,
            double illumination,
            int sky,
            int precipitationType,
            double windSpeed
    ) {
    }
}
