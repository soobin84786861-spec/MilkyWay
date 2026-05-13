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
    private static final List<LifeStageContext> LIFE_STAGE_CONTEXTS = List.of(
            new LifeStageContext(1, 6, "유충기",
                    "현재는 러브버그가 땅속 또는 서식 환경에서 자라는 시기다. 성충 출몰은 보통 6월 중순 이후부터 시작되므로, 환경 조건은 유리해도 실제 대량 출몰 시점은 아직 아닐 수 있다."),
            new LifeStageContext(6, 8, "성충 활동기",
                    "현재는 러브버그 성충이 본격적으로 이동하고 관측되는 시기다. 기온, 습도, 조도 조건에 따라 실제 출몰 위험이 높아질 수 있다."),
            new LifeStageContext(9, 12, "비활동기",
                    "현재는 러브버그 성충 활동이 거의 종료된 시기다. 일부 환경 요인이 있더라도 실제 출몰 가능성은 낮다.")
    );
    private static final List<TimeAdviceContext> TIME_ADVICE_CONTEXTS = List.of(
            new TimeAdviceContext(5, 10, "출근·등교 이동 시간대", "버스정류장, 횡단보도, 보행로처럼 야외 체류가 있는 시간대"),
            new TimeAdviceContext(10, 17, "주간 야외활동 시간대", "공원, 산책로, 주택가 골목 이동처럼 야외 체류가 이어지는 시간대"),
            new TimeAdviceContext(17, 22, "일과 이후 야외활동 시간대", "조명 주변 체류 또는 귀가 이동이 늘어나는 시간대"),
            new TimeAdviceContext(22, 24, "야간 실내관리 시간대", "귀가 후 환기, 창문 개방, 실내 유입 관리가 중요해지는 시간대"),
            new TimeAdviceContext(0, 5, "야간 실내관리 시간대", "귀가 후 환기, 창문 개방, 실내 유입 관리가 중요해지는 시간대")
    );

    private record CachedEntry(AiRiskAnalysisResponse data, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    private record LifeStageContext(int startMonthInclusive, int endMonthInclusive, String label, String context) {
        boolean matches(int month) {
            return month >= startMonthInclusive && month <= endMonthInclusive;
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
        LifeStageContext lifeStageContext = resolveLifeStageContext(java.time.LocalDate.now().getMonthValue());
        String systemPrompt = renderTemplate(SYSTEM_PROMPT_PATH, Map.of(
                "jsonSchema", """
                        {
                          "type": "object",
                          "properties": {
                            "summary": { "type": "string" },
                            "comfortMessage": { "type": "string" },
                            "timeAdvice": { "type": "string" },
                            "actionGuides": {
                              "type": "array",
                              "items": { "type": "string" },
                              "minItems": 3,
                              "maxItems": 3
                            },
                            "riskFactors": {
                              "type": "array",
                              "items": { "type": "string" },
                              "minItems": 3,
                              "maxItems": 3
                            }
                          },
                          "required": ["summary", "comfortMessage", "timeAdvice", "actionGuides", "riskFactors"],
                          "additionalProperties": false
                        }
                        """
        ));
        String userPrompt = renderTemplate(USER_PROMPT_PATH, Map.ofEntries(
                Map.entry("district", district.getKoreanName()),
                Map.entry("riskPercent", region.getRiskPercent()),
                Map.entry("riskLevel", region.getRiskLevel().getKoreanName()),
                Map.entry("temperature", region.getTemperature()),
                Map.entry("humidity", region.getHumidity()),
                Map.entry("illumination", region.getIllumination()),
                Map.entry("windSpeedMph", round(toMph(region.getWindSpeed()))),
                Map.entry("weatherIndex", region.getWeatherIndex()),
                Map.entry("habitatFactor", region.getHabitatFactor()),
                Map.entry("trafficFactor", region.getTrafficFactor()),
                Map.entry("riskIndex", region.getRiskIndex()),
                Map.entry("instaCnt", region.getInstaCnt()),
                Map.entry("timeSlotLabel", timeAdviceContext.label()),
                Map.entry("timeAdviceSeed", timeAdviceContext.seed()),
                Map.entry("currentMonth", java.time.LocalDate.now().getMonthValue()),
                Map.entry("lifeStage", lifeStageContext.label()),
                Map.entry("lifeStageContext", lifeStageContext.context())
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
            result = fallbackResponse(region);
        }

        cache.put(district, new CachedEntry(result, System.currentTimeMillis()));
        return result;
    }

    private LifeStageContext resolveLifeStageContext(int month) {
        return LIFE_STAGE_CONTEXTS.stream()
                .filter(context -> context.matches(month))
                .findFirst()
                .orElse(LIFE_STAGE_CONTEXTS.get(0));
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

    private AiRiskAnalysisResponse fallbackResponse(RegionRiskResponse region) {
        return new AiRiskAnalysisResponse(
                "현재 " + region.getRegionName() + "은 러브버그 출몰 가능성이 있어 주의가 필요한 상태입니다.",
                "너무 걱정하지 말고, 필요한 만큼만 대비해도 충분합니다.",
                "현재 시간대에는 야외 조명 주변 체류와 실내 유입 가능성을 함께 확인해보는 것이 좋습니다.",
                List.of(
                        "창문과 방충망 상태를 먼저 확인해보세요.",
                        "밝은 조명 주변 야외 체류 시간을 줄여보세요.",
                        "귀가 후 옷이나 차량 표면을 가볍게 털어주세요."
                ),
                List.of(
                        "현재 기온과 습도 조건이 러브버그 활동 가능성에 영향을 줍니다.",
                        "조도와 풍속 조건이 러브버그 이동과 유입 요인으로 반영됩니다.",
                        "서식지 계수와 교통 계수가 지역 위험도에 함께 반영됩니다."
                )
        );
    }

    private double toMph(double metersPerSecond) {
        return metersPerSecond * 2.23694;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
