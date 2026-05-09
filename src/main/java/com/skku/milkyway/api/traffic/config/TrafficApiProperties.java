package com.skku.milkyway.api.traffic.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "seoul.traffic-api")
public class TrafficApiProperties {

    private String apiKey = "";
    private String baseUrl = "http://openapi.seoul.go.kr:8088";
    private String historyServiceName = "";
    private int batchSize = 1000;
    private long cacheTtlMs = 60 * 60 * 1000L;
    private String mappingFilePath = "classpath:traffic/spot-district-map.json";
}
