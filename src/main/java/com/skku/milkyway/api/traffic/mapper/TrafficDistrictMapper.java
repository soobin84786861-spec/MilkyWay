package com.skku.milkyway.api.traffic.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.traffic.config.TrafficApiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class TrafficDistrictMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TrafficApiProperties trafficApiProperties;
    private volatile Map<String, SeoulDistrict> districtBySpotNum;

    @Autowired
    public TrafficDistrictMapper(TrafficApiProperties trafficApiProperties) {
        this.trafficApiProperties = trafficApiProperties;
        this.districtBySpotNum = loadMappings();
    }

    TrafficDistrictMapper(Map<String, SeoulDistrict> districtBySpotNum) {
        this.trafficApiProperties = null;
        this.districtBySpotNum = Map.copyOf(districtBySpotNum);
    }

    public Map<String, SeoulDistrict> getDistrictBySpotNum() {
        return Map.copyOf(districtBySpotNum);
    }

    private Map<String, SeoulDistrict> loadMappings() {
        if (trafficApiProperties == null) {
            return districtBySpotNum;
        }

        try {
            Map<String, String> rawMappings = objectMapper.readValue(
                    Path.of(trafficApiProperties.getMappingFilePath()).toFile(),
                    new TypeReference<>() {}
            );

            Map<String, SeoulDistrict> result = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : rawMappings.entrySet()) {
                String districtName = entry.getValue();
                if (districtName == null || districtName.isBlank()) {
                    continue;
                }
                result.put(entry.getKey(), SeoulDistrict.fromKoreanName(districtName.trim()));
            }

            log.info("[Traffic] 지점-자치구 매핑 로드 완료 - {}건", result.size());
            return result;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "교통량 지점 매핑 파일을 읽을 수 없습니다: " + trafficApiProperties.getMappingFilePath(),
                    e
            );
        }
    }
}
