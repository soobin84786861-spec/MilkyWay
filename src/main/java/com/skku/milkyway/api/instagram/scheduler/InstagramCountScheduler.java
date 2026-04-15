package com.skku.milkyway.api.instagram.scheduler;

import com.skku.milkyway.api.instagram.config.InstagramProperties;
import com.skku.milkyway.api.instagram.dto.InstagramPostDto;
import com.skku.milkyway.api.instagram.service.InstagramCrawlerService;
import com.skku.milkyway.api.instagram.service.InstagramDistrictCountService;
import com.skku.milkyway.api.instagram.store.InstagramCountStore;
import com.skku.milkyway.api.risk.code.SeoulDistrict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstagramCountScheduler {

    private static final String KEYWORD = "러브버그";

    private final InstagramProperties instagramProperties;
    private final InstagramCrawlerService crawlerService;
    private final InstagramDistrictCountService countService;
    private final InstagramCountStore countStore;


    /** 앱 완전 기동 후 1회 즉시 실행 (로그인 완료 후 보장) */
    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        run();
    }

    /**
     * 매 1시간마다 실행 (cron: 정각 기준)
     * 개발 테스트 시 fixedDelay = 60_000 으로 변경
     */
    @Scheduled(cron = "0 0 * * * *")
    public void run() {
        if (!instagramProperties.isEnabled()) {
            log.info("[Instagram 배치] 크롤링 비활성화 상태 (instagram.enabled=false)");
            return;
        }
        log.info("[Instagram 배치] {} 크롤링 시작", KEYWORD);

        List<InstagramPostDto> posts = crawlerService.fetchPosts(KEYWORD);
        log.info("[Instagram 배치] 수집 게시물 수: {}건", posts.size());

        Map<SeoulDistrict, Integer> countMap = countService.countByDistrict(posts);
        countStore.update(countMap);

        log.info("===== 자치구별 러브버그 언급 횟수 =====");
        countMap.entrySet().stream()
            .filter(e -> e.getValue() > 0)
            .sorted(Map.Entry.<SeoulDistrict, Integer>comparingByValue().reversed())
            .forEach(e ->
                    log.info("  {} : {}건", e.getKey().getKoreanName(), e.getValue())
            );

        log.info("========================================");
    }
}