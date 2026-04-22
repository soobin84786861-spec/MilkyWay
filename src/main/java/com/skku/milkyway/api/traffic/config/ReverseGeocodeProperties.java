package com.skku.milkyway.api.traffic.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "reverse-geocode")
public class ReverseGeocodeProperties {

    /**
     * Reverse geocoding API endpoint.
     */
    private String baseUrl = "https://nominatim.openstreetmap.org/reverse";

    /**
     * 외부 geocoding API 호출 시 보낼 User-Agent.
     */
    private String userAgent = "MilkyWayTrafficMapper/1.0";

    /**
     * Nominatim 권장 식별용 이메일.
     */
    private String email = "";

    /**
     * 외부 geocoding API 과호출을 피하기 위한 요청 간 대기시간(ms).
     */
    private long requestDelayMs = 1100L;
}
