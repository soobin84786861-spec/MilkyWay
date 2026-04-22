package com.skku.milkyway.api.traffic.store;

import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.traffic.domain.DistrictTrafficAggregate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 현재 시각 기준 자치구별 교통량 평균 스냅샷을 메모리에 보관한다.
 */
@Component
public class TrafficCurrentSnapshotStore {

    private volatile LocalDateTime updatedAt;
    private volatile List<DistrictTrafficAggregate> aggregates = List.of();
    private volatile Map<SeoulDistrict, Double> averageTrafficByDistrict = Map.of();

    public synchronized void update(List<DistrictTrafficAggregate> aggregates) {
        this.aggregates = List.copyOf(aggregates);

        Map<SeoulDistrict, Double> averages = new EnumMap<>(SeoulDistrict.class);
        for (DistrictTrafficAggregate aggregate : aggregates) {
            averages.put(
                    SeoulDistrict.fromKoreanName(aggregate.getDistrictName()),
                    aggregate.getAvgTrafficPerPoint()
            );
        }
        this.averageTrafficByDistrict = Map.copyOf(averages);
        this.updatedAt = LocalDateTime.now();
    }

    public List<DistrictTrafficAggregate> getAggregates() {
        return aggregates;
    }

    public Map<SeoulDistrict, Double> getAverageTrafficByDistrict() {
        return averageTrafficByDistrict;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isEmpty() {
        return updatedAt == null || averageTrafficByDistrict.isEmpty();
    }
}
