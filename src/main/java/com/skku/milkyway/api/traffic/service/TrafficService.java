package com.skku.milkyway.api.traffic.service;

import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.traffic.domain.DistrictTrafficAggregate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 자치구별 현재 평균 교통량을 제공하는 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficService {

    private final TrafficFacadeService trafficFacadeService;

    /**
     * 특정 자치구의 현재 평균 교통량을 반환한다.
     */
    public double getCurrentAverageTraffic(SeoulDistrict district) {
        return getAllCurrentAverageTraffic().getOrDefault(district, 0.0);
    }

    /**
     * 특정 자치구의 현재 교통량 정규화 점수를 반환한다.
     */
    public double getCurrentTrafficScore(SeoulDistrict district) {
        return getAllCurrentTrafficScores().getOrDefault(district, 0.0);
    }

    /**
     * 모든 자치구의 현재 평균 교통량을 반환한다.
     */
    public Map<SeoulDistrict, Double> getAllCurrentAverageTraffic() {
        List<DistrictTrafficAggregate> aggregates = refreshCurrentTrafficSnapshot();
        Map<SeoulDistrict, Double> averageTrafficByDistrict = new EnumMap<>(SeoulDistrict.class);
        for (DistrictTrafficAggregate aggregate : aggregates) {
            SeoulDistrict district = SeoulDistrict.fromKoreanName(aggregate.getDistrictName());
            averageTrafficByDistrict.put(district, aggregate.getAvgTrafficPerPoint());
        }
        for (SeoulDistrict district : SeoulDistrict.values()) {
            averageTrafficByDistrict.putIfAbsent(district, 0.0);
        }
        return Map.copyOf(averageTrafficByDistrict);
    }

    /**
     * 모든 자치구의 현재 교통량 정규화 점수를 반환한다.
     */
    public Map<SeoulDistrict, Double> getAllCurrentTrafficScores() {
        Map<SeoulDistrict, Double> trafficScoreByDistrict = new EnumMap<>(SeoulDistrict.class);
        List<DistrictTrafficAggregate> aggregates = refreshCurrentTrafficSnapshot();
        for (DistrictTrafficAggregate aggregate : aggregates) {
            SeoulDistrict district = SeoulDistrict.fromKoreanName(aggregate.getDistrictName());
            trafficScoreByDistrict.put(district, aggregate.getNormalizedTrafficScore());
        }
        for (SeoulDistrict district : SeoulDistrict.values()) {
            trafficScoreByDistrict.putIfAbsent(district, 0.0);
        }
        return Map.copyOf(trafficScoreByDistrict);
    }

    /**
     * 자치구별 평균 교통량들의 전체 평균값을 반환한다.
     */
    public double getOverallAverageTraffic() {
        return getAllCurrentAverageTraffic().values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    /**
     * 현재 시각 기준 직전 1시간 데이터를 사용해 교통량 스냅샷을 계산한다.
     */
    public List<DistrictTrafficAggregate> refreshCurrentTrafficSnapshot() {
        long startedAt = System.currentTimeMillis();
        LocalDate today = LocalDate.now();
        int currentHour = LocalTime.now().minusHours(1).getHour();
        log.info("[Traffic] 현재 시각 교통량 스냅샷 갱신 시작 - date={}, hour={}", today, currentHour);

        List<DistrictTrafficAggregate> aggregates = trafficFacadeService.getDistrictTrafficAggregates(today, currentHour);
        logTrafficTable(aggregates);

        log.info(
                "[Traffic] 현재 시각 교통량 스냅샷 갱신 완료 - date={}, hour={}, districts={}, elapsed={}ms",
                today,
                currentHour,
                aggregates.size(),
                System.currentTimeMillis() - startedAt
        );
        return aggregates;
    }

    /**
     * 자치구별 교통량 집계 결과를 표 형태 로그로 출력한다.
     */
    private void logTrafficTable(List<DistrictTrafficAggregate> aggregates) {
        Map<String, DistrictTrafficAggregate> aggregateByDistrictName = new LinkedHashMap<>();
        for (DistrictTrafficAggregate aggregate : aggregates) {
            aggregateByDistrictName.put(aggregate.getDistrictName(), aggregate);
        }

        log.info("[Traffic] district summary");
        log.info("[Traffic] {}", String.format(Locale.ROOT, "%-12s | %5s | %14s", "district", "count", "avgTraffic"));
        log.info("[Traffic] {}", "-------------+-------+----------------");

        for (SeoulDistrict district : SeoulDistrict.values()) {
            DistrictTrafficAggregate aggregate = aggregateByDistrictName.get(district.getKoreanName());
            int count = aggregate == null ? 0 : aggregate.getPointCount();
            double average = aggregate == null ? 0.0 : aggregate.getAvgTrafficPerPoint();
            String row = String.format(Locale.ROOT, "%-12s | %5d | %14.2f", district, count, average);
            log.info("[Traffic] {}", row);
        }
    }
}
