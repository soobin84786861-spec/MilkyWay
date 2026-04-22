package com.skku.milkyway.api.traffic.dto;

import java.util.Map;

/**
 * 서울시 교통량 지점 정보 API에서 받은 원본 row 데이터.
 *
 * <p>필드명이 운영 환경에 따라 조금씩 다를 수 있어
 * 우선 key-value 맵 형태로 보관한 뒤 내부 도메인으로 변환한다.</p>
 */
public record TrafficPointRawDto(
        Map<String, String> fields
) {}
