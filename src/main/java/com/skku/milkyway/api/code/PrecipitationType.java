package com.skku.milkyway.api.code;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 기상청 강수형태(PTY) 코드.
 * JSON 직렬화 시 {@link #getCode()}의 int 값으로 출력한다.
 */
@Getter
@RequiredArgsConstructor
public enum PrecipitationType {

    NONE          (0, "없음"),
    RAIN          (1, "비"),
    RAIN_SNOW     (2, "비/눈"),
    SNOW          (3, "눈"),
    RAIN_DROP     (5, "빗방울"),
    RAIN_DROP_SNOW(6, "빗방울+눈날림"),
    SNOW_DRIFT    (7, "눈날림");

    @JsonValue
    private final int code;
    private final String label;

    public static PrecipitationType fromCode(int code) {
        for (PrecipitationType p : values()) {
            if (p.code == code) return p;
        }
        return NONE; // 알 수 없는 코드는 없음으로 처리
    }
}
