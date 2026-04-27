package com.skku.milkyway.api.forest.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skku.milkyway.api.code.SeoulDistrict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
@Service
public class ForestAreaService {

    private static final String RESOURCE_PATH = "risk/forest-area-by-district.json";

    private final Map<SeoulDistrict, Double> forestAreaByDistrict;
    private final double maxForestArea;

    /** 자치구별 산림면적 JSON을 읽고, 최대 산림면적 값을 함께 계산한다. */
    public ForestAreaService() {
        this.forestAreaByDistrict = loadForestAreaByDistrict();
        this.maxForestArea = forestAreaByDistrict.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
    }

    /** 특정 자치구의 산림면적 비율을 0.1~2.0 범위의 증폭계수로 변환해 반환한다. */
    public double getHabitatFactor(SeoulDistrict district) {
        if (maxForestArea <= 0.0) {
            return 0.1;
        }

        double forestRatio = getHabitatRatio(district);
        return round(0.1 + (forestRatio * 1.9));
    }

    /** 특정 자치구의 산림면적 비율값(0~1)을 반환한다. */
    public double getHabitatRatio(SeoulDistrict district) {
        if (maxForestArea <= 0.0) {
            return 0.0;
        }

        double forestArea = forestAreaByDistrict.getOrDefault(district, 0.0);
        return Math.max(0.0, Math.min(1.0, forestArea / maxForestArea));
    }

    /** 서울시 자치구 중 최대 산림 면적 원본값을 반환한다. */
    public double getMaxForestArea() {
        return maxForestArea;
    }

    /** 리소스 JSON을 읽어 enum 키 기준 자치구 산림면적 맵으로 변환한다. */
    private Map<SeoulDistrict, Double> loadForestAreaByDistrict() {
        try (InputStream inputStream = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Double> raw = objectMapper.readValue(inputStream, new TypeReference<>() {});

            Map<SeoulDistrict, Double> result = new EnumMap<>(SeoulDistrict.class);
            for (SeoulDistrict district : SeoulDistrict.values()) {
                result.put(district, raw.getOrDefault(district.name(), 0.0));
            }
            return Map.copyOf(result);
        } catch (Exception e) {
            log.error("[Forest] 산림면적 JSON 로드 실패: {}", e.getMessage());

            Map<SeoulDistrict, Double> fallback = new EnumMap<>(SeoulDistrict.class);
            for (SeoulDistrict district : SeoulDistrict.values()) {
                fallback.put(district, 0.0);
            }
            return Map.copyOf(fallback);
        }
    }

    /** 계산 결과를 소수점 넷째 자리까지 반올림한다. */
    private double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }
}
