package com.skku.milkyway.api.traffic.domain;

import lombok.Builder;
import lombok.Getter;

/**
 * 특정 지점의 교통량 측정값을 나타내는 내부 모델.
 */
@Getter
@Builder
public class TrafficMeasurement {
    private final String pointId;
    private final long trafficVolume;
}
