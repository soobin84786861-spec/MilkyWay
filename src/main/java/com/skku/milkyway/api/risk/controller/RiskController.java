package com.skku.milkyway.api.risk.controller;

import com.skku.milkyway.api.ai.dto.AiRiskAnalysisResponse;
import com.skku.milkyway.api.ai.service.AiRiskAnalysisService;
import com.skku.milkyway.api.code.RiskLevel;
import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.risk.response.RegionRiskResponse;
import com.skku.milkyway.api.risk.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;
    private final AiRiskAnalysisService aiRiskAnalysisService;

    @GetMapping("/regions")
    public List<RegionRiskResponse> getRegions(@RequestParam(required = false) RiskLevel riskLevel) {
        return riskService.getRegions(riskLevel);
    }

    @GetMapping("/regions/{district}")
    public AiRiskAnalysisResponse getRegionAnalysis(@PathVariable SeoulDistrict district) {
        return aiRiskAnalysisService.getAnalysis(district);
    }
}
