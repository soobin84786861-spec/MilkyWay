package com.skku.milkyway.api.ai.service;

import com.skku.milkyway.api.ai.dto.WeatherResponse;
import com.skku.milkyway.api.risk.code.SeoulDistrict;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 자치구별 기상 데이터를 제공하는 서비스.
 * 현재는 더미 데이터를 반환하며, 추후 실제 기상 API로 교체 예정.
 */
@Service
public class WeatherService {

    private static final WeatherResponse DEFAULT = new WeatherResponse(25.0, 60.0);

    private static final Map<SeoulDistrict, WeatherResponse> DUMMY = Map.ofEntries(
            Map.entry(SeoulDistrict.GANGNAM,       new WeatherResponse(31, 78)),
            Map.entry(SeoulDistrict.SONGPA,        new WeatherResponse(29, 74)),
            Map.entry(SeoulDistrict.SEOCHO,        new WeatherResponse(28, 71)),
            Map.entry(SeoulDistrict.GWANGJIN,      new WeatherResponse(28, 69)),
            Map.entry(SeoulDistrict.YONGSAN,       new WeatherResponse(27, 68)),
            Map.entry(SeoulDistrict.SEONGDONG,     new WeatherResponse(27, 67)),
            Map.entry(SeoulDistrict.MAPO,          new WeatherResponse(27, 66)),
            Map.entry(SeoulDistrict.JONGNO,        new WeatherResponse(26, 62)),
            Map.entry(SeoulDistrict.JUNG,          new WeatherResponse(26, 61)),
            Map.entry(SeoulDistrict.DONGDAEMUN,    new WeatherResponse(25, 60)),
            Map.entry(SeoulDistrict.SEONGBUK,      new WeatherResponse(25, 59)),
            Map.entry(SeoulDistrict.EUNPYEONG,     new WeatherResponse(25, 57)),
            Map.entry(SeoulDistrict.SEODAEMUN,     new WeatherResponse(24, 56)),
            Map.entry(SeoulDistrict.YEONGDEUNGPO,  new WeatherResponse(24, 55)),
            Map.entry(SeoulDistrict.GURO,          new WeatherResponse(24, 54)),
            Map.entry(SeoulDistrict.GEUMCHEON,     new WeatherResponse(23, 51)),
            Map.entry(SeoulDistrict.GANGBUK,       new WeatherResponse(23, 50)),
            Map.entry(SeoulDistrict.GANGDONG,      new WeatherResponse(23, 50)),
            Map.entry(SeoulDistrict.NOWON,         new WeatherResponse(23, 50)),
            Map.entry(SeoulDistrict.YANGCHEON,     new WeatherResponse(23, 49)),
            Map.entry(SeoulDistrict.DOBONG,        new WeatherResponse(22, 49)),
            Map.entry(SeoulDistrict.DONGJAK,       new WeatherResponse(23, 48)),
            Map.entry(SeoulDistrict.GANGSEO,       new WeatherResponse(22, 48)),
            Map.entry(SeoulDistrict.JUNGNANG,      new WeatherResponse(24, 51)),
            Map.entry(SeoulDistrict.GWANAK,        new WeatherResponse(22, 47))
    );

    public WeatherResponse getWeather(SeoulDistrict district) {
        return DUMMY.getOrDefault(district, DEFAULT);
    }
}