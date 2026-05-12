package com.skku.milkyway.api.illumination.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "seoul.illumination-api")
public class IlluminationApiProperties {

    private String apiKey = "";
    private String baseUrl = "http://openapi.seoul.go.kr:8088";
    private String serviceName = "sDoTEnv";
    private int batchSize = 1000;
    private long cacheTtlMs = 60 * 60 * 1000L;
    private int connectTimeoutMs = 5_000;
    private int readTimeoutMs = 20_000;
}
