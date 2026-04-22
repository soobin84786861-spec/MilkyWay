package com.skku.milkyway.api.traffic.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.traffic.config.TrafficApiProperties;
import com.skku.milkyway.api.traffic.domain.TrafficPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
/**
 * 교통량 지점을 서울 자치구로 매핑하는 유틸리티.
 *
 * <p>지점번호(spot_num)별 자치구 매핑 파일을 기준으로 결정한다.
 * 교통량 지점은 비교적 고정된 기준점이므로, 런타임 추론보다
 * 검증된 매핑 테이블을 참조하는 편이 더 안정적이다.</p>
 */
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

    /**
     * 지점번호 기준으로 자치구를 결정한다.
     */
    public SeoulDistrict mapDistrict(TrafficPoint point) {
        SeoulDistrict district = districtBySpotNum.get(point.getPointId());
        if (district != null) {
            return district;
        }

        throw new IllegalArgumentException("매핑 파일에 없는 지점입니다: " + point.getPointId() + " / " + point.getPointName());
    }

    /**
     * 매핑 파일이 수정된 뒤 메모리 캐시를 다시 읽는다.
     */
    public void reload() {
        this.districtBySpotNum = loadMappings();
    }

    /**
     * 리소스 파일에서 spot_num -> 자치구명 매핑을 읽어 enum 맵으로 변환한다.
     */
    private Map<String, SeoulDistrict> loadMappings() {
        if (trafficApiProperties == null) {
            return districtBySpotNum;
        }
        try {
            Map<String, String> rawMappings = objectMapper.readValue(
                    java.nio.file.Path.of(trafficApiProperties.getMappingFilePath()).toFile(),
                    new TypeReference<>() {}
            );

            Map<String, SeoulDistrict> result = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : rawMappings.entrySet()) {
                String spotNum = entry.getKey();
                String districtName = entry.getValue();
                if (districtName == null || districtName.isBlank()) {
                    continue;
                }
                result.put(spotNum, SeoulDistrict.fromKoreanName(districtName.trim()));
            }
            log.info("[Traffic] 지점-자치구 매핑 로드 완료 - {}건", result.size());
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("교통량 지점 매핑 파일을 읽을 수 없습니다: " + trafficApiProperties.getMappingFilePath(), e);
        }
    }
}
