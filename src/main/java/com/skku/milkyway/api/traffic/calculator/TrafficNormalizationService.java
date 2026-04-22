package com.skku.milkyway.api.traffic.calculator;

import com.skku.milkyway.api.traffic.domain.DistrictTrafficAggregate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
/**
 * 자치구별 평균 교통량을 0~1 범위 점수로 정규화하는 서비스.
 *
 * <p>정규화 기준은 항상 총합이 아니라 지점당 평균 교통량이다.</p>
 */
public class TrafficNormalizationService {

    /**
     * 자치구별 평균 교통량을 min-max 방식으로 정규화한다.
     */
    public List<DistrictTrafficAggregate> normalize(List<DistrictTrafficAggregate> aggregates) {
        double min = aggregates.stream()
                .filter(it -> it.getPointCount() > 0)
                .mapToDouble(DistrictTrafficAggregate::getAvgTrafficPerPoint)
                .min()
                .orElse(0.0);

        double max = aggregates.stream()
                .filter(it -> it.getPointCount() > 0)
                .mapToDouble(DistrictTrafficAggregate::getAvgTrafficPerPoint)
                .max()
                .orElse(0.0);

        List<DistrictTrafficAggregate> normalized = new ArrayList<>(aggregates.size());
        for (DistrictTrafficAggregate aggregate : aggregates) {
            normalized.add(aggregate.toBuilder()
                    .normalizedTrafficScore(normalizeValue(aggregate, min, max))
                    .build());
        }
        return normalized;
    }

    /**
     * 단일 자치구 값을 정규화한다.
     *
     * <p>모든 자치구 값이 같으면 중립값 0.5를 사용한다.</p>
     */
    private double normalizeValue(DistrictTrafficAggregate aggregate, double min, double max) {
        if (aggregate.getPointCount() == 0) {
            return 0.0;
        }
        if (Double.compare(max, min) == 0) {
            return 0.5;
        }
        double value = (aggregate.getAvgTrafficPerPoint() - min) / (max - min);
        return round(Math.max(0.0, Math.min(1.0, value)));
    }

    /**
     * 비교용 점수가 지나치게 긴 소수로 남지 않도록 4자리에서 반올림한다.
     */
    private double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }
}
