package com.skku.milkyway.config;

import com.skku.milkyway.api.risk.service.RiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupWarmupService {

    private final RiskService riskService;

    /** 애플리케이션 시작 직후 위험도 캐시를 미리 채워 첫 요청 지연을 줄인다. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        long startedAt = System.currentTimeMillis();
        log.info("[Warmup] 초기 데이터 워밍업 시작");

        try {
            riskService.getRegions(null);
            log.info("[Warmup] 초기 데이터 워밍업 완료 - elapsed={}ms", System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            log.error("[Warmup] 초기 데이터 워밍업 실패: {}", e.getMessage(), e);
        }
    }
}
