package com.skku.milkyway.api.risk.controller;

import com.skku.milkyway.api.risk.code.RiskLevel;
import com.skku.milkyway.api.risk.response.RegionRiskResponse;
import com.skku.milkyway.api.risk.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;

    @GetMapping("/regions")
    public List<RegionRiskResponse> getRegions(
            @RequestParam(required = false) RiskLevel riskLevel
    ) {
        return riskService.getRegions(riskLevel);
    }
}