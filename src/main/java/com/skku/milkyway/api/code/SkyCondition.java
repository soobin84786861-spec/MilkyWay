package com.skku.milkyway.api.code;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 기상청 하늘상태(SKY) 코드.
 * JSON 직렬화 시 {@link #getCode()}의 int 값으로 출력한다.
 */
@Getter
@RequiredArgsConstructor
public enum SkyCondition {

    SUNNY   (1, "맑음"),
    CLOUDY  (3, "구름많음"),
    OVERCAST(4, "흐림");

    @JsonValue
    private final int code;
    private final String label;

    public static SkyCondition fromCode(int code) {
        for (SkyCondition s : values()) {
            if (s.code == code) return s;
        }
        return SUNNY; // 알 수 없는 코드는 맑음으로 처리
    }
}
