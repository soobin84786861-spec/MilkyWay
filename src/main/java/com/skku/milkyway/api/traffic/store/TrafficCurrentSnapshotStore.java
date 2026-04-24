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
    private volatile Map<SeoulDistrict, Double> trafficScoreByDistrict = Map.of();

    /** 최신 집계 결과를 현재 스냅샷으로 교체하고, 자치구별 평균/정규화 맵을 함께 갱신한다. */
    public synchronized void update(List<DistrictTrafficAggregate> aggregates) {
        this.aggregates = List.copyOf(aggregates);

        Map<SeoulDistrict, Double> averages = new EnumMap<>(SeoulDistrict.class);
        Map<SeoulDistrict, Double> scores = new EnumMap<>(SeoulDistrict.class);
        for (DistrictTrafficAggregate aggregate : aggregates) {
            SeoulDistrict district = SeoulDistrict.fromKoreanName(aggregate.getDistrictName());
            averages.put(district, aggregate.getAvgTrafficPerPoint());
            scores.put(district, aggregate.getNormalizedTrafficScore());
        }
        this.averageTrafficByDistrict = Map.copyOf(averages);
        this.trafficScoreByDistrict = Map.copyOf(scores);
        this.updatedAt = LocalDateTime.now();
    }

    /** 현재 메모리에 보관 중인 자치구별 교통량 집계 목록을 반환한다. */
    public List<DistrictTrafficAggregate> getAggregates() {
        return aggregates;
    }

    /** 자치구별 지점당 평균 교통량 값을 반환한다. */
    public Map<SeoulDistrict, Double> getAverageTrafficByDistrict() {
        return averageTrafficByDistrict;
    }

    /** 자치구별 교통량 정규화 점수(V 계산용)를 반환한다. */
    public Map<SeoulDistrict, Double> getTrafficScoreByDistrict() {
        return trafficScoreByDistrict;
    }

    /** 현재 스냅샷이 마지막으로 갱신된 시각을 반환한다. */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** 아직 유효한 현재 스냅샷이 없는지 확인한다. */
    public boolean isEmpty() {
        return updatedAt == null || averageTrafficByDistrict.isEmpty();
    }
}
