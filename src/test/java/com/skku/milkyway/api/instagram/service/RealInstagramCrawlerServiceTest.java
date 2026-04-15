package com.skku.milkyway.api.instagram.service;

import com.skku.milkyway.api.instagram.scheduler.InstagramCountScheduler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


/**
 * 실제 Instagram API를 호출하는 통합 테스트.
 * application.properties에 instagram.session-id 가 설정된 환경에서만 실행.
 *
 * 로컬 실행: ./gradlew test -Dgroups=integration
 * CI에서는 제외: ./gradlew test -DexcludedGroups=integration
 */
@Tag("integration")
@SpringBootTest
class RealInstagramCrawlerServiceTest {

    @Autowired
    InstagramCountScheduler instagramCountScheduler;

    @Test
    void 해시태그_크롤링_결과가_비어있지_않아야_한다() {
        instagramCountScheduler.run();
    }
}