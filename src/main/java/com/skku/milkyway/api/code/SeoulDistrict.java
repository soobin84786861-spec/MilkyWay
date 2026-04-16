package com.skku.milkyway.api.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeoulDistrict {

    GANGNAM      ("강남구",   37.5172, 127.0473, 61, 126),
    GANGDONG     ("강동구",   37.5301, 127.1238, 62, 126),
    GANGBUK      ("강북구",   37.6396, 127.0253, 61, 128),
    GANGSEO      ("강서구",   37.5509, 126.8495, 58, 126),
    GWANAK       ("관악구",   37.4784, 126.9516, 59, 125),
    GWANGJIN     ("광진구",   37.5384, 127.0823, 62, 126),
    GURO         ("구로구",   37.4954, 126.8874, 58, 125),
    GEUMCHEON    ("금천구",   37.4567, 126.8956, 58, 124),
    NOWON        ("노원구",   37.6542, 127.0568, 61, 129),
    DOBONG       ("도봉구",   37.6688, 127.0471, 61, 129),
    DONGDAEMUN   ("동대문구", 37.5744, 127.0398, 61, 127),
    DONGJAK      ("동작구",   37.5124, 126.9396, 59, 126),
    MAPO         ("마포구",   37.5663, 126.9014, 58, 127),
    SEODAEMUN    ("서대문구", 37.5791, 126.9368, 59, 127),
    SEOCHO       ("서초구",   37.4837, 127.0324, 61, 125),
    SEONGDONG    ("성동구",   37.5633, 127.0369, 61, 127),
    SEONGBUK     ("성북구",   37.5894, 127.0168, 60, 127),
    SONGPA       ("송파구",   37.5145, 127.1059, 62, 126),
    YANGCHEON    ("양천구",   37.5170, 126.8665, 58, 126),
    YEONGDEUNGPO ("영등포구", 37.5264, 126.8962, 58, 126),
    YONGSAN      ("용산구",   37.5384, 126.9654, 60, 126),
    EUNPYEONG    ("은평구",   37.6027, 126.9291, 59, 127),
    JONGNO       ("종로구",   37.5735, 126.9788, 60, 127),
    JUNG         ("중구",     37.5641, 126.9979, 60, 127),
    JUNGNANG     ("중랑구",   37.6063, 127.0925, 62, 128);

    private final String koreanName;
    private final double latitude;
    private final double longitude;
    private final int nx;  // 기상청 격자 X 좌표
    private final int ny;  // 기상청 격자 Y 좌표

    public static SeoulDistrict fromKoreanName(String name) {
        for (SeoulDistrict d : values()) {
            if (d.koreanName.equals(name)) return d;
        }
        throw new IllegalArgumentException("알 수 없는 자치구: " + name);
    }
}
