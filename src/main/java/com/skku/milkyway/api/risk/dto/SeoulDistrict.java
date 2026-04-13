package com.skku.milkyway.api.risk.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeoulDistrict {

    GANGNAM  ("강남구",  37.5172, 127.0473),
    GANGDONG ("강동구",  37.5301, 127.1238),
    GANGBUK  ("강북구",  37.6396, 127.0253),
    GANGSEO  ("강서구",  37.5509, 126.8495),
    GWANAK   ("관악구",  37.4784, 126.9516),
    GWANGJIN ("광진구",  37.5384, 127.0823),
    GURO     ("구로구",  37.4954, 126.8874),
    GEUMCHEON("금천구",  37.4567, 126.8956),
    NOWON    ("노원구",  37.6542, 127.0568),
    DOBONG   ("도봉구",  37.6688, 127.0471),
    DONGDAEMUN("동대문구", 37.5744, 127.0398),
    DONGJAK  ("동작구",  37.5124, 126.9396),
    MAPO     ("마포구",  37.5663, 126.9014),
    SEODAEMUN("서대문구", 37.5791, 126.9368),
    SEOCHO   ("서초구",  37.4837, 127.0324),
    SEONGDONG("성동구",  37.5633, 127.0369),
    SEONGBUK ("성북구",  37.5894, 127.0168),
    SONGPA   ("송파구",  37.5145, 127.1059),
    YANGCHEON("양천구",  37.5170, 126.8665),
    YEONGDEUNGPO("영등포구", 37.5264, 126.8962),
    YONGSAN  ("용산구",  37.5384, 126.9654),
    EUNPYEONG("은평구",  37.6027, 126.9291),
    JONGNO   ("종로구",  37.5735, 126.9788),
    JUNG     ("중구",    37.5641, 126.9979),
    JUNGNANG ("중랑구",  37.6063, 127.0925);

    private final String koreanName;
    private final double latitude;
    private final double longitude;

    /** 한글 이름으로 Enum 조회 */
    public static SeoulDistrict fromKoreanName(String name) {
        for (SeoulDistrict d : values()) {
            if (d.koreanName.equals(name)) return d;
        }
        throw new IllegalArgumentException("알 수 없는 자치구: " + name);
    }
}