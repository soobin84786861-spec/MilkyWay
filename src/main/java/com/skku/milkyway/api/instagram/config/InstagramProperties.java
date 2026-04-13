package com.skku.milkyway.api.instagram.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "instagram")
public class InstagramProperties {
    private String sessionId;
    private String csrfToken;
    private String userAgent;
}