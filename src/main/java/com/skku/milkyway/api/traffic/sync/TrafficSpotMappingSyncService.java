package com.skku.milkyway.api.traffic.sync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.traffic.config.ReverseGeocodeProperties;
import com.skku.milkyway.api.traffic.config.TrafficApiProperties;
import com.skku.milkyway.api.traffic.domain.GeoCoordinate;
import com.skku.milkyway.api.traffic.dto.TrafficPointRawDto;
import com.skku.milkyway.api.traffic.geo.TrafficCoordinateConverter;
import com.skku.milkyway.api.traffic.geo.TrafficReverseGeocodeService;
import com.skku.milkyway.api.traffic.mapper.TrafficDistrictMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SpotInfo와 좌표 API를 이용해 spot_num -> 자치구 매핑 파일을 실제로 갱신한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficSpotMappingSyncService {

    private static final String SPOT_NUM_FIELD = "spot_num";
    private static final String SPOT_NAME_FIELD = "spot_nm";
    private static final String GRS80TM_X_FIELD = "grs80tm_x";
    private static final String GRS80TM_Y_FIELD = "grs80tm_y";

    private final TrafficApiProperties trafficApiProperties;
    private final ReverseGeocodeProperties reverseGeocodeProperties;
    private final com.skku.milkyway.api.traffic.client.TrafficApiClient trafficApiClient;
    private final TrafficCoordinateConverter trafficCoordinateConverter;
    private final TrafficReverseGeocodeService trafficReverseGeocodeService;
    private final TrafficDistrictMapper trafficDistrictMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, String> syncSpotDistrictMappings() {
        Path mappingPath = Path.of(trafficApiProperties.getMappingFilePath());
        Map<String, String> mappings = readMappings(mappingPath);
        List<TrafficPointRawDto> points = trafficApiClient.fetchTrafficPoints();

        for (TrafficPointRawDto rawPoint : points) {
            String spotNum = rawPoint.fields().get(SPOT_NUM_FIELD);
            String spotName = rawPoint.fields().getOrDefault(SPOT_NAME_FIELD, "");
            if (spotNum == null || spotNum.isBlank()) {
                continue;
            }

            Double x = parseDouble(rawPoint.fields().get(GRS80TM_X_FIELD));
            Double y = parseDouble(rawPoint.fields().get(GRS80TM_Y_FIELD));
            if (x == null || y == null) {
                log.warn("[Traffic] 좌표가 없어 매핑을 건너뜁니다 - {} / {}", spotNum, spotName);
                continue;
            }

            GeoCoordinate coordinate = trafficCoordinateConverter.toWgs84(x, y);
            Optional<SeoulDistrict> district = trafficReverseGeocodeService.resolveDistrict(coordinate);
            if (district.isEmpty()) {
                log.warn("[Traffic] reverse geocoding으로 자치구를 찾지 못했습니다 - {} / {} (lat={}, lon={})",
                        spotNum, spotName, coordinate.latitude(), coordinate.longitude());
                sleepBetweenRequests();
                continue;
            }

            String districtName = district.get().getKoreanName();
            String previous = mappings.put(spotNum, districtName);
            if (previous == null || previous.isBlank()) {
                log.info("[Traffic] 매핑 추가 - {} / {} -> {}", spotNum, spotName, districtName);
            } else if (!previous.equals(districtName)) {
                log.info("[Traffic] 매핑 수정 - {} / {} : {} -> {}", spotNum, spotName, previous, districtName);
            }
            sleepBetweenRequests();
        }

        writeMappings(mappingPath, mappings);
        trafficDistrictMapper.reload();
        return mappings;
    }

    private Map<String, String> readMappings(Path path) {
        try {
            if (!Files.exists(path)) {
                return new LinkedHashMap<>();
            }
            return objectMapper.readValue(path.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("교통량 지점 매핑 파일을 읽을 수 없습니다: " + path, e);
        }
    }

    private void writeMappings(Path path, Map<String, String> mappings) {
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), mappings);
        } catch (IOException e) {
            throw new IllegalStateException("교통량 지점 매핑 파일을 저장할 수 없습니다: " + path, e);
        }
    }

    private void sleepBetweenRequests() {
        if (reverseGeocodeProperties.getRequestDelayMs() <= 0) {
            return;
        }
        try {
            Thread.sleep(reverseGeocodeProperties.getRequestDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("reverse geocoding 대기 중 인터럽트가 발생했습니다.", e);
        }
    }

    private Double parseDouble(String value) {
        try {
            return value != null && !value.isBlank() ? Double.parseDouble(value.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
