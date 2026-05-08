package com.skku.milkyway.api.ai.dto;

import java.util.List;

public record AiRiskAnalysisResponse(
        String summary,
        String comfortMessage,
        String timeAdvice,
        List<String> actionGuides,
        List<String> riskFactors
) {
}
