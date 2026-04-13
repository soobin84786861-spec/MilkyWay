package com.skku.milkyway.api.instagram.service;

import com.skku.milkyway.api.instagram.dto.InstagramPostDto;
import com.skku.milkyway.api.risk.code.SeoulDistrict;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class InstagramDistrictCountService {

    /**
     * 게시물 목록에서 자치구별 언급 횟수를 집계한다.
     * caption + hashtags 전체 텍스트에 자치구 한글명이 포함되면 count +1
     */
    public Map<SeoulDistrict, Integer> countByDistrict(List<InstagramPostDto> posts) {
        Map<SeoulDistrict, Integer> countMap = new EnumMap<>(SeoulDistrict.class);

        for (SeoulDistrict district : SeoulDistrict.values()) {
            countMap.put(district, 0);
        }

        for (InstagramPostDto post : posts) {
            String fullText = post.fullText();
            for (SeoulDistrict district : SeoulDistrict.values()) {
                if (fullText.contains(district.getKoreanName())) {
                    countMap.merge(district, 1, Integer::sum);
                }
            }
        }

        return countMap;
    }
}