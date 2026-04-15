package com.skku.milkyway.api.risk.service;

import com.skku.milkyway.api.risk.code.RiskLevel;
import com.skku.milkyway.api.risk.code.SeoulDistrict;
import com.skku.milkyway.api.risk.response.RegionRiskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.skku.milkyway.api.risk.code.RiskLevel.*;
import static com.skku.milkyway.api.risk.code.SeoulDistrict.*;

@Service
@RequiredArgsConstructor
public class RiskService {

    private RegionRiskResponse of(SeoulDistrict district, RiskLevel level, int percent) {
        return new RegionRiskResponse(
                district.getKoreanName(),
                district.getLatitude(),
                district.getLongitude(),
                level,
                percent,
                0
        );
    }

    private List<RegionRiskResponse> buildData() {
        return List.of(
                of(GANGNAM,       CRITICAL, 89),
                of(SONGPA,        CRITICAL, 84),
                of(SEOCHO,        DANGER,   76),
                of(GWANGJIN,      DANGER,   73),
                of(YONGSAN,       DANGER,   71),
                of(SEONGDONG,     DANGER,   68),
                of(MAPO,          DANGER,   65),
                of(JONGNO,        CAUTION,  52),
                of(JUNG,          CAUTION,  48),
                of(DONGDAEMUN,    CAUTION,  45),
                of(SEONGBUK,      CAUTION,  42),
                of(EUNPYEONG,     CAUTION,  38),
                of(SEODAEMUN,     CAUTION,  35),
                of(YEONGDEUNGPO,  CAUTION,  32),
                of(GURO,          CAUTION,  30),
                of(GEUMCHEON,     SAFE,     23),
                of(GANGBUK,       SAFE,     22),
                of(GANGDONG,      SAFE,     21),
                of(NOWON,         SAFE,     20),
                of(YANGCHEON,     SAFE,     19),
                of(DOBONG,        SAFE,     18),
                of(DONGJAK,       SAFE,     17),
                of(GANGSEO,       SAFE,     16),
                of(JUNGNANG,      SAFE,     24),
                of(GWANAK,        SAFE,     14)
        );
    }

    public List<RegionRiskResponse> getRegions(RiskLevel riskLevel) {
        List<RegionRiskResponse> data = buildData();
        if (riskLevel == null) {
            return data;
        }
        return data.stream()
                .filter(r -> r.getRiskLevel() == riskLevel)
                .toList();
    }
}