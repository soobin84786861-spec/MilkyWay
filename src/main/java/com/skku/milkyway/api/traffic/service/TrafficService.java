package com.skku.milkyway.api.traffic.service;

import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.traffic.domain.DistrictTrafficAggregate;
import com.skku.milkyway.api.traffic.store.TrafficCurrentSnapshotStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 현재 시각 기준 자치구별 평균 통행량을 제공하는 서비스.
 *
 * <p>교통량 집계 결과를 1시간 동안 메모리에 캐시하고,
 * 캐시가 비었거나 만료되었을 때만 다시 계산한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficService {

    private static final long CURRENT_TRAFFIC_CACHE_TTL_MS = 60 * 60 * 1000L;

    private final TrafficFacadeService trafficFacadeService;
    private final TrafficCurrentSnapshotStore trafficCurrentSnapshotStore;

    /**
     * 특정 자치구의 현재 평균 통행량을 반환한다.
     */
    public double getCurrentAverageTraffic(SeoulDistrict district) {
        ensureCurrentSnapshotLoaded();
        return trafficCurrentSnapshotStore.getAverageTrafficByDistrict().getOrDefault(district, 0.0);
    }

    /** 특정 자치구의 현재 교통량 정규화 점수(V 계산용)를 반환한다. */
    public double getCurrentTrafficScore(SeoulDistrict district) {
        ensureCurrentSnapshotLoaded();
        return trafficCurrentSnapshotStore.getTrafficScoreByDistrict().getOrDefault(district, 0.0);
    }

    /**
     * 모든 자치구의 현재 평균 통행량을 반환한다.
     */
    public Map<SeoulDistrict, Double> getAllCurrentAverageTraffic() {
        ensureCurrentSnapshotLoaded();
        Map<SeoulDistrict, Double> result = new EnumMap<>(SeoulDistrict.class);
        Map<SeoulDistrict, Double> averageTrafficByDistrict = trafficCurrentSnapshotStore.getAverageTrafficByDistrict();
        for (SeoulDistrict district : SeoulDistrict.values()) {
            result.put(district, averageTrafficByDistrict.getOrDefault(district, 0.0));
        }
        return result;
    }

    /**
     * 모든 자치구 평균 통행량의 전체 평균값을 반환한다.
     */
    public double getOverallAverageTraffic() {
        ensureCurrentSnapshotLoaded();
        return getAllCurrentAverageTraffic().values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    /**
     * 현재 시각 기준 교통량 스냅샷을 다시 계산해 저장한다.
     */
    public List<DistrictTrafficAggregate> refreshCurrentTrafficSnapshot() {
        long startedAt = System.currentTimeMillis();
        LocalDate today = LocalDate.now();
        int currentHour = LocalTime.now().minusHours(1).getHour();
        log.info("[Traffic] 현재 시각 교통량 스냅샷 갱신 시작 - date={}, hour={}", today, currentHour);

        List<DistrictTrafficAggregate> aggregates = trafficFacadeService.getDistrictTrafficAggregates(today, currentHour);
        trafficCurrentSnapshotStore.update(aggregates);

        log.info(
                "[Traffic] 현재 시각 교통량 스냅샷 갱신 완료 - date={}, hour={}, districts={}, elapsed={}ms",
                today,
                currentHour,
                aggregates.size(),
                System.currentTimeMillis() - startedAt
        );
        return aggregates;
    }

    /** 현재 스냅샷이 비었거나 만료되었으면 다시 집계한다. */
    private void ensureCurrentSnapshotLoaded() {
        if (trafficCurrentSnapshotStore.isEmpty() || isExpired(trafficCurrentSnapshotStore.getUpdatedAt())) {
            refreshCurrentTrafficSnapshot();
        }
    }

    /** 마지막 갱신 시각이 1시간 TTL을 넘었는지 확인한다. */
    private boolean isExpired(LocalDateTime updatedAt) {
        if (updatedAt == null) {
            return true;
        }
        long ageMs = ChronoUnit.MILLIS.between(updatedAt, LocalDateTime.now());
        return ageMs > CURRENT_TRAFFIC_CACHE_TTL_MS;
    }
}
