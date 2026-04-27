package com.skku.milkyway.api.illumination.service;

import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.illumination.client.IlluminationApiClient;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IlluminationService {

    private static final String DISTRICT_FIELD = "autonomous_district";
    private static final String ADMINISTRATIVE_DISTRICT_FIELD = "administrative_district";
    private static final String SENSING_TIME_FIELD = "sensing_time";
    private static final String SERIAL_FIELD = "serial";
    private static final String AVG_ILLUMINATION_FIELD = "avg_inte_illu";
    private static final String DATA_NO_FIELD = "data_no";
    private static final DateTimeFormatter[] SENSING_TIME_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

    private final IlluminationApiClient illuminationApiClient;

    /**
     * 특정 자치구의 현재 평균 조도값을 반환한다.
     */
    public double getCurrentIllumination(SeoulDistrict district) {
        return getAllCurrentIllumination().getOrDefault(district, 0.0);
    }

    /**
     * 모든 자치구의 현재 평균 조도값을 반환한다.
     */
    public Map<SeoulDistrict, Double> getAllCurrentIllumination() {
        long startedAt = System.currentTimeMillis();
        log.info("[Illumination] 조도 스냅샷 갱신 시작");

        List<Map<String, String>> rows = illuminationApiClient.fetchRows();
        LocalDateTime latestSensingTime = rows.stream()
                .map(row -> parseSensingTime(row.get(SENSING_TIME_FIELD)))
                .filter(value -> value != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime windowStart = latestSensingTime == null ? null : latestSensingTime.minusHours(1);

        Map<String, SeoulDistrict> districtByAdministrativeDong = buildAdministrativeDistrictMap(rows);
        Map<String, SensorRecord> latestBySensor = new LinkedHashMap<>();

        int skippedByTime = 0;
        int skippedByDistrict = 0;
        int skippedBySerial = 0;
        int skippedByIllumination = 0;
        int insertedCount = 0;
        int replacedCount = 0;
        int keptCount = 0;
        int fallbackByAdministrativeDistrict = 0;

        for (Map<String, String> row : rows) {
            LocalDateTime sensingAt = parseSensingTime(row.get(SENSING_TIME_FIELD));
            if (sensingAt == null) {
                skippedByTime++;
                continue;
            }

            DistrictResolution resolution = resolveDistrict(row, districtByAdministrativeDong);
            SeoulDistrict district = resolution.district();
            if (resolution.usedAdministrativeFallback()) {
                fallbackByAdministrativeDistrict++;
            }
            if (district == null) {
                skippedByDistrict++;
                continue;
            }

            String serial = trim(row.get(SERIAL_FIELD));
            if (serial == null || serial.isBlank()) {
                skippedBySerial++;
                continue;
            }

            double avgIllumination = parseDouble(row.get(AVG_ILLUMINATION_FIELD));
            if (avgIllumination < 0) {
                skippedByIllumination++;
                continue;
            }

            int dataNo = parseInt(row.get(DATA_NO_FIELD));
            SensorRecord candidate = SensorRecord.builder()
                    .district(district)
                    .serial(serial)
                    .sensingAt(sensingAt)
                    .avgIllumination(avgIllumination)
                    .dataNo(dataNo)
                    .build();

            SensorRecord current = latestBySensor.get(serial);
            if (current == null) {
                latestBySensor.put(serial, candidate);
                insertedCount++;
            } else if (isCandidateNewer(candidate, current)) {
                latestBySensor.put(serial, candidate);
                replacedCount++;
            } else {
                keptCount++;
            }
        }

        Map<SeoulDistrict, DoubleSummary> summaries = new EnumMap<>(SeoulDistrict.class);
        for (SensorRecord record : latestBySensor.values()) {
            summaries.computeIfAbsent(record.district(), key -> new DoubleSummary())
                    .add(record.avgIllumination());
        }

        Map<SeoulDistrict, Double> values = new EnumMap<>(SeoulDistrict.class);
        for (SeoulDistrict district : SeoulDistrict.values()) {
            DoubleSummary summary = summaries.get(district);
            values.put(district, summary == null ? 0.0 : summary.average());
        }

        logDistrictTable(summaries);
        log.info(
                "[Illumination] 조도 스냅샷 갱신 완료 - latestSensingTime={}, windowStart={}, districts={}, rows={}, sensors={}, inserted={}, replaced={}, keptExisting={}, skipTime={}, skipDistrict={}, skipSerial={}, skipIllumination={}, fallback={}, elapsed={}ms",
                latestSensingTime,
                windowStart,
                summaries.size(),
                rows.size(),
                latestBySensor.size(),
                insertedCount,
                replacedCount,
                keptCount,
                skippedByTime,
                skippedByDistrict,
                skippedBySerial,
                skippedByIllumination,
                fallbackByAdministrativeDistrict,
                System.currentTimeMillis() - startedAt
        );

        return Map.copyOf(values);
    }

    /**
     * 조도 API의 자치구명을 SeoulDistrict로 변환한다.
     */
    private SeoulDistrict toDistrict(String districtName) {
        if (districtName == null || districtName.isBlank()) {
            return null;
        }

        try {
            return SeoulDistrict.fromIlluminationName(districtName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 자치구명이 정상인 row를 기준으로 행정동 -> 자치구 fallback 맵을 만든다.
     */
    private Map<String, SeoulDistrict> buildAdministrativeDistrictMap(List<Map<String, String>> rows) {
        Map<String, SeoulDistrict> result = new LinkedHashMap<>();

        for (Map<String, String> row : rows) {
            SeoulDistrict district = toDistrict(trim(row.get(DISTRICT_FIELD)));
            if (district == null) {
                continue;
            }

            String administrativeDistrict = normalizeAdministrativeDistrict(trim(row.get(ADMINISTRATIVE_DISTRICT_FIELD)));
            if (administrativeDistrict == null || administrativeDistrict.isBlank()) {
                continue;
            }

            result.putIfAbsent(administrativeDistrict, district);
        }

        return result;
    }

    /**
     * 자치구명을 우선 사용하고, 실패하면 행정동 기준 fallback으로 자치구를 판별한다.
     */
    private DistrictResolution resolveDistrict(
            Map<String, String> row,
            Map<String, SeoulDistrict> districtByAdministrativeDong
    ) {
        SeoulDistrict district = toDistrict(trim(row.get(DISTRICT_FIELD)));
        if (district != null) {
            return new DistrictResolution(district, false);
        }

        String administrativeDistrict = normalizeAdministrativeDistrict(trim(row.get(ADMINISTRATIVE_DISTRICT_FIELD)));
        SeoulDistrict fallbackDistrict = districtByAdministrativeDong.get(administrativeDistrict);
        return new DistrictResolution(fallbackDistrict, fallbackDistrict != null);
    }

    /**
     * 같은 센서 row 두 개를 비교해 후보 row가 더 최신인지 판단한다.
     */
    private boolean isCandidateNewer(SensorRecord candidate, SensorRecord current) {
        if (candidate.sensingAt().isAfter(current.sensingAt())) {
            return true;
        }
        return candidate.sensingAt().isEqual(current.sensingAt()) && candidate.dataNo() > current.dataNo();
    }

    /**
     * 지원하는 sensing_time 포맷을 LocalDateTime으로 변환한다.
     */
    private LocalDateTime parseSensingTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        for (DateTimeFormatter formatter : SENSING_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    /**
     * 행정동 문자열을 fallback 비교용 형식으로 정규화한다.
     */
    private String normalizeAdministrativeDistrict(String value) {
        return value == null ? null : value.replace(" ", "").toLowerCase();
    }

    /**
     * 문자열 값을 안전하게 trim 처리한다.
     */
    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 자치구별 조도 집계 결과를 표 형태 로그로 출력한다.
     */
    private void logDistrictTable(Map<SeoulDistrict, DoubleSummary> summaries) {
        log.info("[Illumination] district summary");
        log.info("[Illumination] {}", String.format(Locale.ROOT, "%-12s | %5s | %16s", "district", "count", "avgIllumination"));
        log.info("[Illumination] {}", "-------------+-------+------------------");
        for (SeoulDistrict district : SeoulDistrict.values()) {
            DoubleSummary summary = summaries.get(district);
            int count = summary == null ? 0 : summary.count();
            double average = summary == null ? 0.0 : summary.average();
            log.info("[Illumination] {}", String.format(Locale.ROOT, "%-12s | %5d | %16.2f", district, count, average));
        }
    }

    /**
     * 조도 문자열 값을 double로 변환하고, 실패하면 -1을 반환한다.
     */
    private double parseDouble(String value) {
        try {
            return value != null && !value.isBlank() ? Double.parseDouble(value.trim()) : -1.0;
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    /**
     * DATA_NO 값을 정수로 변환하고, 실패하면 0을 반환한다.
     */
    private int parseInt(String value) {
        try {
            return value != null && !value.isBlank() ? Integer.parseInt(value.trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Builder
    private record SensorRecord(
            SeoulDistrict district,
            String serial,
            LocalDateTime sensingAt,
            double avgIllumination,
            int dataNo
    ) {
    }

    private record DistrictResolution(
            SeoulDistrict district,
            boolean usedAdministrativeFallback
    ) {
    }

    private static final class DoubleSummary {
        private double sum;
        private int count;

        void add(double value) {
            this.sum += value;
            this.count++;
        }

        double average() {
            return count == 0 ? 0.0 : sum / count;
        }

        int count() {
            return count;
        }
    }
}
