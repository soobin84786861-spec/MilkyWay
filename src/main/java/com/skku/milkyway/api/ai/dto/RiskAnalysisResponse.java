package com.skku.milkyway.api.ai.dto;

public record RiskAnalysisResponse(
        String mainRiskFactors,
        String predictionBasis
) {}