package com.skku.milkyway.api.ai.dto;

public record WeatherResponse(
        double temperature,
        double humidity
) {}