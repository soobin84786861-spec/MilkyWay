package com.skku.milkyway.api.traffic.calculator;

import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.traffic.domain.DistrictTrafficAggregate;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
/**
 * 자치구 교통량 정규화 결과를 위험도 계산용 계수 맵으로 변환하는 서비스.
 */
public class TrafficCoefficientService {

    /**
     * 자치구명을 enum으로 다시 매핑해 위험도 계산에 바로 쓸 수 있는 맵을 만든다.
     */
    public Map<SeoulDistrict, Double> toDistrictCoefficientMap(List<DistrictTrafficAggregate> aggregates) {
        Map<SeoulDistrict, Double> result = new EnumMap<>(SeoulDistrict.class);
        for (DistrictTrafficAggregate aggregate : aggregates) {
            SeoulDistrict district = SeoulDistrict.fromKoreanName(aggregate.getDistrictName());
            result.put(district, aggregate.getNormalizedTrafficScore());
        }
        return result;
    }
}
