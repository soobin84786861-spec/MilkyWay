package com.skku.milkyway.api.traffic.domain;

import lombok.Builder;
import lombok.Getter;

/**
 * 자치구 단위 교통량 집계 결과.
 *
 * <p>러브버그 위험도 계산에 사용할 도심 유입 계수(V)의 기초 데이터다.</p>
 */
@Getter
@Builder(toBuilder = true)
public class DistrictTrafficAggregate {
    private final String districtName;
    private final int pointCount;
    private final long totalTraffic;
    private final double avgTrafficPerPoint;
    private final double normalizedTrafficScore;
}
