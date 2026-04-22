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
public class TrafficFacadeService {

    private static final String HISTORY_POINT_ID_FIELD = "volmno";
    private static final String HISTORY_POINT_ID_FALLBACK_FIELD = "spotnum";
    private static final String HISTORY_VOLUME_FIELD = "vol";

    private final TrafficApiClient trafficApiClient;
    private final TrafficApiProperties properties;
    private final TrafficDistrictMapper trafficDistrictMapper;
    private final TrafficAggregationService trafficAggregationService;
    private final TrafficNormalizationService trafficNormalizationService;
    private final TrafficSnapshotStore trafficSnapshotStore;

    public List<DistrictTrafficAggregate> getDistrictTrafficAggregates(LocalDate date, Integer hour) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        int targetHour = hour != null ? hour : LocalTime.now().minusHours(1).getHour();

        List<DistrictTrafficAggregate> cached = trafficSnapshotStore.get(targetDate, targetHour, properties.getCacheTtlMs());
        if (cached != null) {
            return cached;
        }

        Map<String, SeoulDistrict> districtBySpotNum = trafficDistrictMapper.getDistrictBySpotNum();
        List<TrafficMeasurement> measurements = fetchMeasurements(districtBySpotNum.keySet(), targetDate, targetHour);
        List<DistrictTrafficAggregate> aggregated = trafficAggregationService.aggregate(districtBySpotNum, measurements);
        List<DistrictTrafficAggregate> normalized = trafficNormalizationService.normalize(aggregated);

        trafficSnapshotStore.put(targetDate, targetHour, normalized);
        return normalized;
    }

    private List<TrafficMeasurement> fetchMeasurements(Iterable<String> pointIds, LocalDate date, int hour) {
        List<TrafficMeasurement> measurements = new ArrayList<>();
        for (String pointId : pointIds) {
            try {
                List<TrafficHistoryRawDto> rawHistories = trafficApiClient.fetchTrafficHistory(pointId, date, hour);
                measurements.addAll(mapMeasurements(rawHistories));
            } catch (Exception e) {
                log.warn("[Traffic] 교통량 이력 조회 실패 - {}: {}", pointId, e.getMessage());
            }
        }
        return measurements;
    }

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

    private Map<String, String> normalizeFields(Map<String, String> fields) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            normalized.put(normalizeKey(entry.getKey()), entry.getValue());
        }
        return normalized;
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

    private long parseLong(String value) {
        try {
            return value != null && !value.isBlank() ? Long.parseLong(value.trim()) : -1L;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9가-힣]", "");
    }
}
