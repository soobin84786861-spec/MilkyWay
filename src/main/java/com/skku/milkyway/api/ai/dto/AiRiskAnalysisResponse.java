package com.skku.milkyway.api.ai.dto;

import java.util.List;

public record AiRiskAnalysisResponse(
        String summary,
        String comfortMessage,
        String timeAdvice,
        List<String> actionGuides,
        List<String> riskFactors,
        BasedOn basedOn
) {
    public record BasedOn(
            int riskPercent,
            double temperature,
            double humidity,
            double illumination,
            double windSpeedMph,
            double weatherIndex,
            double habitatFactor,
            double trafficFactor,
            double riskIndex
    ) {
    }
}
