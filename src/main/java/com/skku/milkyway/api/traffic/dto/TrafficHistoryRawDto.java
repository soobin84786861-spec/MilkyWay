package com.skku.milkyway.api.traffic.dto;

import java.util.Map;

/**
 * 서울시 교통량 이력 API에서 받은 원본 row 데이터.
 *
 * <p>필드 alias를 유연하게 처리하기 위해 정규화 전 원본 맵으로 유지한다.</p>
 */
public record TrafficHistoryRawDto(
        Map<String, String> fields
) {}
