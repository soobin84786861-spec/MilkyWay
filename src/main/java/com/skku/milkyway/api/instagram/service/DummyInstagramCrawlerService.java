package com.skku.milkyway.api.instagram.service;

import com.skku.milkyway.api.instagram.dto.InstagramPostDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** instagram.session-id 미설정 시 기본 활성화 */
@Service
@ConditionalOnMissingBean(InstagramCrawlerService.class)
public class DummyInstagramCrawlerService implements InstagramCrawlerService {

    @Override
    public List<InstagramPostDto> fetchPosts(String keyword) {
        // 추후 실제 인스타그램 크롤링 API 호출로 교체
        return List.of(
                new InstagramPostDto(
                        "오늘 강남구에서 러브버그 엄청 많이 봤어요 조심하세요",
                        List.of("러브버그", "강남구", "벌레주의"),
                        LocalDateTime.now().minusHours(1)
                ),
                new InstagramPostDto(
                        "송파구 올림픽공원 산책하다가 러브버그 때문에 혼났네요",
                        List.of("러브버그", "송파구", "올림픽공원"),
                        LocalDateTime.now().minusHours(2)
                ),
                new InstagramPostDto(
                        "서초구 카페 테라스에 러브버그가 너무 많아서 못 앉겠어요",
                        List.of("러브버그", "서초구", "카페"),
                        LocalDateTime.now().minusHours(2)
                ),
                new InstagramPostDto(
                        "강남구 역삼동 러브버그 출몰 주의하세요!!",
                        List.of("러브버그", "강남구", "역삼동"),
                        LocalDateTime.now().minusHours(3)
                ),
                new InstagramPostDto(
                        "마포구 홍대 앞에 러브버그 많네요",
                        List.of("러브버그", "마포구", "홍대"),
                        LocalDateTime.now().minusHours(3)
                ),
                new InstagramPostDto(
                        "용산구 이태원 거리에도 러브버그 많이 보여요",
                        List.of("러브버그", "용산구", "이태원"),
                        LocalDateTime.now().minusHours(4)
                ),
                new InstagramPostDto(
                        "광진구 건대 근처에도 러브버그 출몰",
                        List.of("러브버그", "광진구", "건대"),
                        LocalDateTime.now().minusHours(4)
                ),
                new InstagramPostDto(
                        "성동구 성수동 카페 거리도 러브버그 조심!",
                        List.of("러브버그", "성동구", "성수동"),
                        LocalDateTime.now().minusHours(5)
                ),
                new InstagramPostDto(
                        "러브버그가 강남구 곳곳에 나타나고 있어요",
                        List.of("러브버그", "강남구"),
                        LocalDateTime.now().minusHours(5)
                ),
                new InstagramPostDto(
                        "종로구 경복궁 주변에도 러브버그 있으니 주의하세요",
                        List.of("러브버그", "종로구", "경복궁"),
                        LocalDateTime.now().minusHours(6)
                )
        );
    }
}
