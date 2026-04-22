package com.skku.milkyway.api.ai.service;

import com.skku.milkyway.api.ai.dto.AiRiskAnalysisResponse;
import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.risk.response.RegionRiskResponse;
import com.skku.milkyway.api.risk.service.RiskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.stringtemplate.v4.ST;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AiRiskAnalysisService {

    private static final long CACHE_TTL_MS = 60 * 60 * 1000L;
    private static final String SYSTEM_PROMPT_PATH = "prompts/ai-risk-system.st";
    private static final String USER_PROMPT_PATH = "prompts/ai-risk-user.st";
    private static final List<TimeAdviceContext> TIME_ADVICE_CONTEXTS = List.of(
            new TimeAdviceContext(5, 10, "출근·등교 이동 시간대", "대중교통 대기, 도보 이동, 횡단보도 대기처럼 야외 체류가 잦은 시간대"),
            new TimeAdviceContext(10, 17, "주간 야외활동 시간대", "공원, 산책로, 골목길, 캠퍼스처럼 야외 이동과 체류가 이어지는 시간대"),
            new TimeAdviceContext(17, 22, "퇴근·저녁 야외활동 시간대", "조명 주변 체류와 저녁 산책, 귀가 동선 노출이 늘어나는 시간대"),
            new TimeAdviceContext(22, 24, "야간 실내관리 시간대", "귀가 후 환기, 창문 개방, 실내 유입 관리가 중요한 시간대"),
            new TimeAdviceContext(0, 5, "야간 실내관리 시간대", "귀가 후 환기, 창문 개방, 실내 유입 관리가 중요한 시간대")
    );

    private record CachedEntry(AiRiskAnalysisResponse data, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    private record TimeAdviceContext(int startHourInclusive, int endHourExclusive, String label, String seed) {
        boolean matches(int hour) {
            return hour >= startHourInclusive && hour < endHourExclusive;
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
        TimeAdviceContext timeAdviceContext = resolveTimeAdviceContext(LocalTime.now());
        String systemPrompt = renderTemplate(SYSTEM_PROMPT_PATH, Map.of(
                "jsonSchema", """
                        {
                          "type": "object",
                          "properties": {
                            "description": { "type": "string" },
                            "comfortMessage": { "type": "string" },
                            "timeAdvice": { "type": "string" },
                            "actionGuides": {
                              "type": "array",
                              "items": { "type": "string" },
                              "minItems": 3,
                              "maxItems": 3
                            }
                          },
                          "required": ["description", "comfortMessage", "timeAdvice", "actionGuides"],
                          "additionalProperties": false
                        }
                        """
        ));
        String userPrompt = renderTemplate(USER_PROMPT_PATH, Map.of(
                "district", district.getKoreanName(),
                "riskPercent", region.getRiskPercent(),
                "riskLevel", region.getRiskLevel().getKoreanName(),
                "temperature", region.getTemperature(),
                "humidity", region.getHumidity(),
                "instaCnt", region.getInstaCnt(),
                "timeSlotLabel", timeAdviceContext.label(),
                "timeAdviceSeed", timeAdviceContext.seed()
        ));

        AiRiskAnalysisResponse result;
        try {
            result = chatClient.prompt()
                    .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(AiRiskAnalysisResponse.class);
        } catch (RuntimeException e) {
            log.error("[AI] 구조화 응답 변환 실패 - {}", district, e);
            result = fallbackResponse();
        }

        cache.put(district, new CachedEntry(result, System.currentTimeMillis()));
        return result;
    }

    private TimeAdviceContext resolveTimeAdviceContext(LocalTime currentTime) {
        int hour = currentTime.getHour();
        return TIME_ADVICE_CONTEXTS.stream()
                .filter(context -> context.matches(hour))
                .findFirst()
                .orElseGet(() -> TIME_ADVICE_CONTEXTS.stream()
                        .min(Comparator.comparingInt(TimeAdviceContext::startHourInclusive))
                        .orElseThrow());
    }

    private String renderTemplate(String path, Map<String, Object> attributes) {
        try {
            String template = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);

            ST st = new ST(template, '<', '>');
            attributes.forEach(st::add);
            return st.render();
        } catch (IOException e) {
            throw new IllegalStateException("프롬프트 템플릿을 불러올 수 없습니다: " + path, e);
        }
    }

    private AiRiskAnalysisResponse fallbackResponse() {
        return new AiRiskAnalysisResponse(
                "현재 위험도 데이터를 바탕으로 러브버그 노출 가능성이 있어 주의가 필요합니다.",
                "오늘도 너무 걱정하지 말고, 필요한 만큼만 가볍게 대비해보세요.",
                "현재 시간대에는 야외 이동 동선과 실내 유입 가능성을 함께 살펴보세요.",
                List.of(
                        "외출 전 방충망과 창문 틈새를 먼저 확인하세요.",
                        "야외 이동 중에는 조명 주변에 오래 머무르지 마세요.",
                        "귀가 후에는 옷과 소지품을 털어 실내 유입을 줄이세요."
                )
        );
    }
}
