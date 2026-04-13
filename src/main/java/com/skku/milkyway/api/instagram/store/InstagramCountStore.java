package com.skku.milkyway.api.instagram.store;

import com.skku.milkyway.api.risk.code.SeoulDistrict;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 인스타그램 자치구별 언급 횟수를 메모리에 보관하는 스토어.
 * 스케줄러가 갱신하고, RiskService 가 읽어 API 응답에 포함한다.
 */
@Component
public class InstagramCountStore {

    private volatile Map<SeoulDistrict, Integer> countMap = new EnumMap<>(SeoulDistrict.class);

    /** 스케줄러 실행 후 전체 카운트 맵을 교체한다. */
    public void update(Map<SeoulDistrict, Integer> newMap) {
        this.countMap = new EnumMap<>(newMap);
    }

    /** 특정 자치구의 언급 횟수를 반환한다. 데이터 없으면 0. */
    public int getCount(SeoulDistrict district) {
        return countMap.getOrDefault(district, 0);
    }
}
