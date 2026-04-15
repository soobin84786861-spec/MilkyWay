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
    private String username;
    private String password;
    private String userAgent;
    private boolean browserLogin = true;
    private boolean enabled = true;
}