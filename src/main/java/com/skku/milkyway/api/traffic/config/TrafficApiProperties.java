package com.skku.milkyway.api.traffic.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "seoul.traffic-api")
/**
 * 서울시 교통량 Open API 연동에 필요한 설정값 모음.
 *
 * <p>실제 서비스명은 열린데이터 Open API 환경마다 다를 수 있어
 * 코드에 하드코딩하지 않고 설정으로 분리한다.</p>
 */
public class TrafficApiProperties {

    /**
     * 서울시 열린데이터 Open API 인증키
     */
    private String apiKey = "";

    /**
     * 서울 열린데이터 Open API 기본 URL
     * 예: http://openapi.seoul.go.kr:8088
     */
    private String baseUrl = "http://openapi.seoul.go.kr:8088";

    /**
     * 교통량 지점 정보 서비스명
     */
    private String pointServiceName = "";

    /**
     * 교통량 이력 정보 서비스명
     */
    private String historyServiceName = "";

    /**
     * 한 번에 요청할 최대 row 수
     */
    private int batchSize = 1000;

    /**
     * 자치구 교통량 집계 캐시 TTL
     */
    private long cacheTtlMs = 60 * 60 * 1000L;

    /**
     * spot_num -> 자치구 매핑 파일 경로
     */
    private String mappingFilePath = "src/main/resources/traffic/spot-district-map.json";
}
