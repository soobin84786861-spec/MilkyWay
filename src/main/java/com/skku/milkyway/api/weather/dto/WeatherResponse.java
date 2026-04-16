package com.skku.milkyway.api.weather.dto;

import com.skku.milkyway.api.code.PrecipitationType;
import com.skku.milkyway.api.code.SkyCondition;

/**
 * 기상청 단기예보 API 응답 데이터.
 *
 * @param temperature       기온 (℃) — 초단기실황 T1H
 * @param humidity          습도 (%) — 초단기실황 REH
 * @param sky               하늘상태 — 초단기예보 SKY
 * @param precipitationType 강수형태 — 초단기실황 PTY
 * @param windSpeed         풍속 (m/s) — 초단기실황 WSD
 */
public record WeatherResponse(
        double temperature,
        double humidity,
        SkyCondition sky,
        PrecipitationType precipitationType,
        double windSpeed
) {}
