package com.skku.milkyway.api.traffic.service;

import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.traffic.client.TrafficApiClient;
import com.skku.milkyway.api.traffic.calculator.TrafficAggregationService;
import com.skku.milkyway.api.traffic.calculator.TrafficNormalizationService;
import com.skku.milkyway.api.traffic.config.TrafficApiProperties;
import com.skku.milkyway.api.traffic.domain.DistrictTrafficAggregate;
import com.skku.milkyway.api.traffic.domain.TrafficMeasurement;
import com.skku.milkyway.api.traffic.domain.TrafficPoint;
import com.skku.milkyway.api.traffic.dto.TrafficHistoryRawDto;
import com.skku.milkyway.api.traffic.dto.TrafficPointRawDto;
import com.skku.milkyway.api.traffic.mapper.TrafficDistrictMapper;
import com.skku.milkyway.api.traffic.store.TrafficSnapshotStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 교통량 유틸리티의 전체 흐름을 조합하는 파사드 서비스.
 *
 * <p>지점 조회, 자치구 매핑, 이력 조회, 평균 집계, 정규화, 캐시 저장을 한 번에 수행한다.</p>
 */
public class TrafficFacadeService {

    private static final String SPOT_NUM_FIELD = "spotnum";
    private static final String SPOT_NAME_FIELD = "spotnm";
    private static final String HISTORY_POINT_ID_FIELD = "volmno";
    private static final String HISTORY_DATE_FIELD = "ymd";
    private static final String HISTORY_HOUR_FIELD = "hour";
    private static final String HISTORY_FLOW_TYPE_FIELD = "inout";
    private static final String HISTORY_LANE_FIELD = "laneno";
    private static final String HISTORY_VOLUME_FIELD = "vol";

    private final TrafficApiClient trafficApiClient;
    private final TrafficApiProperties properties;
    private final TrafficDistrictMapper trafficDistrictMapper;
    private final TrafficAggregationService trafficAggregationService;
    private final TrafficNormalizationService trafficNormalizationService;
    private final TrafficSnapshotStore trafficSnapshotStore;

    /**
     * 특정 날짜/시간의 자치구별 교통량 집계 결과를 반환한다.
     */
    public List<DistrictTrafficAggregate> getDistrictTrafficAggregates(LocalDate date, Integer hour) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        Integer targetHour = hour != null ? hour : getDefaultQueryHour();

        List<DistrictTrafficAggregate> cached = trafficSnapshotStore.get(targetDate, targetHour, properties.getCacheTtlMs());
        if (cached != null) {
            return cached;
        }

        List<TrafficPoint> trafficPoints = mapTrafficPoints(trafficApiClient.fetchTrafficPoints());
        List<TrafficMeasurement> measurements = fetchMeasurements(trafficPoints, targetDate, targetHour);

        List<DistrictTrafficAggregate> aggregated = trafficAggregationService.aggregate(trafficPoints, measurements);
        List<DistrictTrafficAggregate> normalized = trafficNormalizationService.normalize(aggregated);

        trafficSnapshotStore.put(targetDate, targetHour, normalized);

        return normalized;
    }

    private int getDefaultQueryHour() {
        return LocalTime.now().minusHours(1).getHour();
    }

    /**
     * 원본 지점 row를 내부 도메인 모델로 바꾸고 자치구를 매핑한다.
     */
    private List<TrafficPoint> mapTrafficPoints(List<TrafficPointRawDto> rawPoints) {
        List<TrafficPoint> points = new ArrayList<>();
        for (TrafficPointRawDto rawPoint : rawPoints) {
            Map<String, String> fields = normalizeFields(rawPoint.fields());
            String pointId = fields.get(SPOT_NUM_FIELD);
            if (pointId == null || pointId.isBlank()) {
                continue;
            }

            TrafficPoint basePoint = TrafficPoint.builder()
                    .pointId(pointId)
                    .pointName(defaultIfBlank(fields.get(SPOT_NAME_FIELD), pointId))
                    .address(null)
                    .locationDescription(null)
                    .directionDescription(null)
                    .latitude(null)
                    .longitude(null)
                    .district(null)
                    .build();

            try {
                SeoulDistrict district = trafficDistrictMapper.mapDistrict(basePoint);
                points.add(basePoint.toBuilder().district(district).build());
            } catch (IllegalArgumentException e) {
                log.warn("[Traffic] 자치구 매핑 실패 - {} / {}", basePoint.getPointId(), basePoint.getPointName());
            }
        }
        return points;
    }

    /**
     * 지점별 교통량 이력을 조회해 내부 측정값 목록으로 평탄화한다.
     */
    private List<TrafficMeasurement> fetchMeasurements(List<TrafficPoint> points, LocalDate date, Integer hour) {
        List<TrafficMeasurement> measurements = new ArrayList<>();
        for (TrafficPoint point : points) {
            try {
                List<TrafficHistoryRawDto> rawHistories = trafficApiClient.fetchTrafficHistory(point.getPointId(), date, hour);
                measurements.addAll(mapMeasurements(rawHistories));
            } catch (Exception e) {
                log.warn("[Traffic] 교통량 이력 조회 실패 - {} / {}: {}", point.getPointId(), point.getPointName(), e.getMessage());
            }
        }
        return measurements;
    }

    /**
     * 원본 이력 row를 내부 측정값 모델로 변환한다.
     */
    private List<TrafficMeasurement> mapMeasurements(List<TrafficHistoryRawDto> rawHistories) {
        if (rawHistories == null || rawHistories.isEmpty()) {
            return Collections.emptyList();
        }

        List<TrafficMeasurement> result = new ArrayList<>();
        for (TrafficHistoryRawDto rawHistory : rawHistories) {
            Map<String, String> fields = normalizeFields(rawHistory.fields());
            String pointId = readHistoryPointId(fields);
            if (pointId == null || pointId.isBlank()) {
                continue;
            }

            long volume = parseLong(readHistoryVolume(fields));
            if (volume < 0) {
                continue;
            }

            result.add(TrafficMeasurement.builder()
                    .pointId(pointId)
                    .measureDate(readHistoryDate(fields))
                    .measureHour(readHistoryHour(fields))
                    .flowType(readHistoryFlowType(fields))
                    .laneNumber(readHistoryLane(fields))
                    .trafficVolume(volume)
                    .build());
        }
        return result;
    }

    /**
     * alias 비교를 쉽게 하기 위해 원본 필드명을 정규화한다.
     */
    private Map<String, String> normalizeFields(Map<String, String> fields) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            normalized.put(normalizeKey(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    /**
     * 후보 alias 중 첫 번째로 매칭되는 값을 꺼낸다.
     */
    private String readHistoryPointId(Map<String, String> fields) {
        return getField(fields, HISTORY_POINT_ID_FIELD, SPOT_NUM_FIELD);
    }

    private String readHistoryDate(Map<String, String> fields) {
        return getField(fields, HISTORY_DATE_FIELD, "date");
    }

    private String readHistoryHour(Map<String, String> fields) {
        return getField(fields, HISTORY_HOUR_FIELD, "time");
    }

    private String readHistoryFlowType(Map<String, String> fields) {
        return getField(fields, HISTORY_FLOW_TYPE_FIELD, "inoutcd");
    }

    private String readHistoryLane(Map<String, String> fields) {
        return getField(fields, HISTORY_LANE_FIELD, "lane");
    }

    private String readHistoryVolume(Map<String, String> fields) {
        return getField(fields, HISTORY_VOLUME_FIELD, "trafficvolume");
    }

    private String getField(Map<String, String> fields, String... candidates) {
        for (String candidate : candidates) {
            String value = fields.get(normalizeKey(candidate));
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 문자열 숫자를 안전하게 long으로 변환한다.
     */
    private long parseLong(String value) {
        try {
            return value != null && !value.isBlank() ? Long.parseLong(value.trim()) : -1L;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * alias 비교용 key 정규화 규칙.
     */
    private String normalizeKey(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9가-힣]", "");
    }
}
