package com.skku.milkyway.api.traffic.domain;

import com.skku.milkyway.api.code.SeoulDistrict;
import lombok.Builder;
import lombok.Getter;

/**
 * 교통량 측정 지점의 내부 도메인 모델.
 *
 * <p>원본 API의 지점 메타데이터를 서비스 내부에서 일관되게 쓰기 위한 구조다.</p>
 */
@Getter
@Builder(toBuilder = true)
public class TrafficPoint {
    private final String pointId;
    private final String pointName;
    private final String address;
    private final String locationDescription;
    private final String directionDescription;
    private final Double latitude;
    private final Double longitude;
    private final SeoulDistrict district;
}
