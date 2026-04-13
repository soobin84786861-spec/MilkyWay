package com.skku.milkyway.api.instagram.service;

import com.skku.milkyway.api.instagram.dto.InstagramPostDto;

import java.util.List;

/**
 * 인스타그램 게시물 수집 인터페이스.
 * 현재는 DummyInstagramCrawlerService 가 주입되며,
 * 추후 실제 크롤링 구현체로 교체한다.
 */
public interface InstagramCrawlerService {
    List<InstagramPostDto> fetchPosts(String keyword);
}