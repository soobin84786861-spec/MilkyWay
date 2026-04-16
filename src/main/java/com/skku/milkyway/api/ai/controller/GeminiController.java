package com.skku.milkyway.api.ai.controller;

import com.skku.milkyway.api.ai.dto.AiRiskAnalysisResponse;
import com.skku.milkyway.api.ai.service.AiRiskAnalysisService;
import com.skku.milkyway.api.risk.code.SeoulDistrict;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class GeminiController {

    private final AiRiskAnalysisService aiRiskAnalysisService;

    @GetMapping("/risk-analysis")
    public AiRiskAnalysisResponse getRiskAnalysis(@RequestParam SeoulDistrict district) {
        return aiRiskAnalysisService.getAnalysis(district);
    }
}