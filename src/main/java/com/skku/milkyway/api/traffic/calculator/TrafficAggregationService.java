package com.skku.milkyway.api.traffic.calculator;

import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.traffic.domain.DistrictTrafficAggregate;
import com.skku.milkyway.api.traffic.domain.TrafficMeasurement;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TrafficAggregationService {

    public List<DistrictTrafficAggregate> aggregate(
            Map<String, SeoulDistrict> districtBySpotNum,
            List<TrafficMeasurement> measurements
    ) {
        Map<String, Long> trafficByPoint = measurements.stream()
                .collect(Collectors.groupingBy(
                        TrafficMeasurement::getPointId,
                        LinkedHashMap::new,
                        Collectors.summingLong(TrafficMeasurement::getTrafficVolume)
                ));

        Map<SeoulDistrict, Set<String>> pointIdsByDistrict = new EnumMap<>(SeoulDistrict.class);
        for (Map.Entry<String, SeoulDistrict> entry : districtBySpotNum.entrySet()) {
            pointIdsByDistrict.computeIfAbsent(entry.getValue(), key -> new java.util.LinkedHashSet<>())
                    .add(entry.getKey());
        }

        List<DistrictTrafficAggregate> result = new ArrayList<>();
        for (SeoulDistrict district : SeoulDistrict.values()) {
            Set<String> districtPointIds = pointIdsByDistrict.getOrDefault(district, Set.of());
            Set<String> validPointIds = districtPointIds.stream()
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

    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
