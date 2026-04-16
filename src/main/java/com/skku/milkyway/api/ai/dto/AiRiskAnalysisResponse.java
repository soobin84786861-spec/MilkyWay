package com.skku.milkyway.api.ai.dto;

import java.util.List;

public record AiRiskAnalysisResponse(
        String description,
        List<String> actionGuides
) {}