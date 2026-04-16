package com.skku.milkyway.api.ai.service;

import com.skku.milkyway.api.ai.dto.AiRiskAnalysisResponse;
import com.skku.milkyway.api.risk.code.SeoulDistrict;
import com.skku.milkyway.api.risk.response.RegionRiskResponse;
import com.skku.milkyway.api.risk.service.RiskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AiRiskAnalysisService {

    private static final long CACHE_TTL_MS = 60 * 60 * 1000L;

    private record CachedEntry(AiRiskAnalysisResponse data, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    private final ChatClient chatClient;
    private final RiskService riskService;
    private final ConcurrentHashMap<SeoulDistrict, CachedEntry> cache = new ConcurrentHashMap<>();

    public AiRiskAnalysisService(ChatClient.Builder chatClientBuilder, RiskService riskService) {
        this.chatClient = chatClientBuilder.build();
        this.riskService = riskService;
    }

    public AiRiskAnalysisResponse getAnalysis(SeoulDistrict district) {
        CachedEntry cached = cache.get(district);
        if (cached != null && !cached.isExpired()) {
            log.info("[AI] 캐시 반환 - {}", district);
            return cached.data();
        }

        log.info("[AI] LLM 호출 - {}", district);

        RegionRiskResponse region = riskService.getRegion(district);

        String prompt = String.format("""
                서울 %s의 러브버그(사랑벌레) 위험 분석을 해주세요.

                데이터:
                - 발생 확률: %d%%
                - 위험도 등급: %s
                - 현재 온도: %.1f°C
                - 현재 습도: %.1f%%
                - 인스타그램 언급 횟수: %d건

                반드시 아래 형식으로만 출력하세요. 다른 텍스트는 절대 추가하지 마세요.

                설명: (위험도를 1~2문장으로 설명)
                가이드1: (행동 가이드 1)
                가이드2: (행동 가이드 2)
                가이드3: (행동 가이드 3)
                """,
                district.getKoreanName(),
                region.getRiskPercent(),
                region.getRiskLevel().name(),
                region.getTemperature(),
                region.getHumidity(),
                region.getInstaCnt()
        );

        String raw = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        AiRiskAnalysisResponse result = parseResponse(raw != null ? raw : "");
        cache.put(district, new CachedEntry(result, System.currentTimeMillis()));
        return result;
    }

    private AiRiskAnalysisResponse parseResponse(String raw) {
        String description = "";
        List<String> guides = new ArrayList<>();

        for (String line : raw.split("\n")) {
            line = line.trim();
            if (line.startsWith("설명:")) {
                description = line.substring("설명:".length()).trim();
            } else if (line.startsWith("가이드1:") || line.startsWith("가이드2:") || line.startsWith("가이드3:")) {
                int colon = line.indexOf(':');
                String guide = line.substring(colon + 1).trim();
                if (!guide.isEmpty()) guides.add(guide);
            }
        }

        return new AiRiskAnalysisResponse(description, guides);
    }
}