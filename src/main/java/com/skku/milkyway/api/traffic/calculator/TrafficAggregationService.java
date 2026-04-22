package com.skku.milkyway.api.traffic.calculator;

import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.traffic.domain.DistrictTrafficAggregate;
import com.skku.milkyway.api.traffic.domain.TrafficMeasurement;
import com.skku.milkyway.api.traffic.domain.TrafficPoint;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
/**
 * 교통량 지점과 측정값을 자치구 단위로 집계하는 서비스.
 *
 * <p>핵심은 총합이 아니라 유효 지점 수를 분모로 하는
 * 지점당 평균 교통량을 계산하는 것이다.</p>
 */
public class TrafficAggregationService {

    /**
     * 지점 목록과 측정값 목록을 받아 자치구별 교통량 집계를 계산한다.
     */
    public List<DistrictTrafficAggregate> aggregate(
            List<TrafficPoint> points,
            List<TrafficMeasurement> measurements
    ) {
        Map<String, Long> trafficByPoint = measurements.stream()
                .collect(Collectors.groupingBy(
                        TrafficMeasurement::getPointId,
                        LinkedHashMap::new,
                        Collectors.summingLong(TrafficMeasurement::getTrafficVolume)
                ));

        Map<SeoulDistrict, List<TrafficPoint>> pointsByDistrict = points.stream()
                .filter(point -> point.getDistrict() != null)
                .collect(Collectors.groupingBy(
                        TrafficPoint::getDistrict,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<DistrictTrafficAggregate> result = new ArrayList<>();
        for (SeoulDistrict district : SeoulDistrict.values()) {
            List<TrafficPoint> districtPoints = pointsByDistrict.getOrDefault(district, List.of());
            Set<String> validPointIds = districtPoints.stream()
                    .map(TrafficPoint::getPointId)
                    .filter(trafficByPoint::containsKey)
                    .collect(Collectors.toSet());

            long totalTraffic = validPointIds.stream()
                    .mapToLong(pointId -> trafficByPoint.getOrDefault(pointId, 0L))
                    .sum();

            int pointCount = validPointIds.size();
            double avgTrafficPerPoint = pointCount == 0 ? 0.0 : round((double) totalTraffic / pointCount);

            result.add(DistrictTrafficAggregate.builder()
                    .districtName(district.getKoreanName())
                    .pointCount(pointCount)
                    .totalTraffic(totalTraffic)
                    .avgTrafficPerPoint(avgTrafficPerPoint)
                    .normalizedTrafficScore(0.0)
                    .build());
        }

        return result;
    }

    /**
     * 평균값을 비교하기 좋게 소수 둘째 자리까지 반올림한다.
     */
    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
