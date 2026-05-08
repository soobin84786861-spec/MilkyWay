package com.skku.milkyway.api.traffic.service;

import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.traffic.calculator.TrafficAggregationService;
import com.skku.milkyway.api.traffic.calculator.TrafficNormalizationService;
import com.skku.milkyway.api.traffic.client.TrafficApiClient;
import com.skku.milkyway.api.traffic.config.TrafficApiProperties;
import com.skku.milkyway.api.traffic.domain.DistrictTrafficAggregate;
import com.skku.milkyway.api.traffic.domain.TrafficMeasurement;
import com.skku.milkyway.api.traffic.dto.TrafficHistoryRawDto;
import com.skku.milkyway.api.traffic.mapper.TrafficDistrictMapper;
import com.skku.milkyway.api.traffic.support.TrafficApiException;
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
public class TrafficFacadeService {

    private static final String HISTORY_POINT_ID_FIELD = "volmno";
    private static final String HISTORY_POINT_ID_FALLBACK_FIELD = "spotnum";
    private static final String HISTORY_VOLUME_FIELD = "vol";

    private final TrafficApiClient trafficApiClient;
    private final TrafficApiProperties properties;
    private final TrafficDistrictMapper trafficDistrictMapper;
    private final TrafficAggregationService trafficAggregationService;
    private final TrafficNormalizationService trafficNormalizationService;

    /**
     * 지정한 날짜와 시간 기준으로 자치구별 교통량 집계를 계산한다.
     */
    public List<DistrictTrafficAggregate> getDistrictTrafficAggregates(LocalDate date, Integer hour) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        int targetHour = hour != null ? hour : LocalTime.now().minusHours(1).getHour();

        Map<String, SeoulDistrict> districtBySpotNum = trafficDistrictMapper.getDistrictBySpotNum();
        List<TrafficMeasurement> measurements = fetchMeasurements(districtBySpotNum.keySet(), targetDate, targetHour);
        List<DistrictTrafficAggregate> aggregated = trafficAggregationService.aggregate(districtBySpotNum, measurements);
        return trafficNormalizationService.normalize(aggregated);
    }

    /**
     * 모든 지점의 교통량 이력 데이터를 조회해 측정값 목록으로 변환한다.
     */
    private List<TrafficMeasurement> fetchMeasurements(Iterable<String> pointIds, LocalDate date, int hour) {
        List<TrafficMeasurement> measurements = new ArrayList<>();
        for (String pointId : pointIds) {
            try {
                List<TrafficHistoryRawDto> rawHistories = fetchTrafficHistoryWithFallback(pointId, date, hour);
                measurements.addAll(mapMeasurements(rawHistories));
            } catch (Exception e) {
                log.warn("[Traffic] 교통량 이력 조회 실패 - {}: {}", pointId, e.getMessage());
            }
        }
        return measurements;
    }

    /**
     * 우선 요청 시각으로 조회하고, 데이터가 없을 때만 직전 1시간으로 한 번 더 조회한다.
     */
    private List<TrafficHistoryRawDto> fetchTrafficHistoryWithFallback(String pointId, LocalDate date, int hour) {
        try {
            return trafficApiClient.fetchTrafficHistory(pointId, date, hour);
        } catch (TrafficApiException e) {
            if (!isNoDataException(e)) {
                throw e;
            }

            PreviousTrafficSlot previousSlot = PreviousTrafficSlot.from(date, hour);
            log.info(
                    "[Traffic] 교통량 데이터 없음 - {}: date={}, hour={} -> fallback date={}, hour={}",
                    pointId,
                    date,
                    hour,
                    previousSlot.date(),
                    previousSlot.hour()
            );
            return trafficApiClient.fetchTrafficHistory(pointId, previousSlot.date(), previousSlot.hour());
        }
    }

    private boolean isNoDataException(TrafficApiException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("INFO-200") || message.contains("해당하는 데이터가 없습니다");
    }

    /**
     * 원본 이력 row를 교통량 측정값 도메인으로 변환한다.
     */
    private List<TrafficMeasurement> mapMeasurements(List<TrafficHistoryRawDto> rawHistories) {
        if (rawHistories == null || rawHistories.isEmpty()) {
            return Collections.emptyList();
        }

        List<TrafficMeasurement> result = new ArrayList<>();
        for (TrafficHistoryRawDto rawHistory : rawHistories) {
            Map<String, String> fields = normalizeFields(rawHistory.fields());
            String pointId = getField(fields, HISTORY_POINT_ID_FIELD, HISTORY_POINT_ID_FALLBACK_FIELD);
            if (pointId == null || pointId.isBlank()) {
                continue;
            }

            long volume = parseLong(getField(fields, HISTORY_VOLUME_FIELD, "trafficvolume"));
            if (volume < 0) {
                continue;
            }

            result.add(TrafficMeasurement.builder()
                    .pointId(pointId)
                    .trafficVolume(volume)
                    .build());
        }
        return result;
    }

    /**
     * 원본 필드명을 비교하기 쉬운 형태로 정규화한다.
     */
    private Map<String, String> normalizeFields(Map<String, String> fields) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            normalized.put(normalizeKey(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    /**
     * 여러 후보 필드명 중 실제 값이 있는 필드를 찾아 반환한다.
     */
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
     * 숫자 문자열을 long으로 변환하고 실패하면 -1을 반환한다.
     */
    private long parseLong(String value) {
        try {
            return value != null && !value.isBlank() ? Long.parseLong(value.trim()) : -1L;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * 필드명을 비교용 문자열로 정규화한다.
     */
    private String normalizeKey(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9가-힣]", "");
    }

    private record PreviousTrafficSlot(
            LocalDate date,
            int hour
    ) {
        static PreviousTrafficSlot from(LocalDate date, int hour) {
            if (hour <= 0) {
                return new PreviousTrafficSlot(date.minusDays(1), 23);
            }
            return new PreviousTrafficSlot(date, hour - 1);
        }
    }
}
