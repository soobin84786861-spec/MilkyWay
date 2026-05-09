package com.skku.milkyway.api.traffic.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.traffic.config.TrafficApiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class TrafficDistrictMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TrafficApiProperties trafficApiProperties;
    private final ResourceLoader resourceLoader;
    private volatile Map<String, SeoulDistrict> districtBySpotNum;

    @Autowired
    public TrafficDistrictMapper(TrafficApiProperties trafficApiProperties, ResourceLoader resourceLoader) {
        this.trafficApiProperties = trafficApiProperties;
        this.resourceLoader = resourceLoader;
        this.districtBySpotNum = loadMappings();
    }

    TrafficDistrictMapper(Map<String, SeoulDistrict> districtBySpotNum) {
        this.trafficApiProperties = null;
        this.resourceLoader = null;
        this.districtBySpotNum = Map.copyOf(districtBySpotNum);
    }

    public Map<String, SeoulDistrict> getDistrictBySpotNum() {
        return Map.copyOf(districtBySpotNum);
    }

    private Map<String, SeoulDistrict> loadMappings() {
        if (trafficApiProperties == null) {
            return districtBySpotNum;
        }

        String mappingFilePath = trafficApiProperties.getMappingFilePath();
        try {
            Resource resource = resourceLoader.getResource(mappingFilePath);

            if (!resource.exists())
                throw new IllegalStateException("매핑 파일이 존재하지 않습니다: " + mappingFilePath);

            try (InputStream inputStream = resource.getInputStream()) {

                Map<String, String> rawMappings = objectMapper.readValue(inputStream, new TypeReference<Map<String, String>>() {});

                Map<String, SeoulDistrict> result = new LinkedHashMap<>();

                for (Map.Entry<String, String> entry : rawMappings.entrySet()) {

                    String districtName = entry.getValue();

                    if (districtName == null || districtName.isBlank()) {
                        continue;
                    }

                    result.put(
                            entry.getKey(),
                            SeoulDistrict.fromKoreanName(districtName.trim())
                    );
                }

                log.info("[Traffic] 지점-자치구 매핑 로드 완료 - {}건", result.size());

                return Map.copyOf(result);
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "교통량 지점 매핑 파일을 읽을 수 없습니다: " + mappingFilePath,
                    e
            );
        }
    }
}