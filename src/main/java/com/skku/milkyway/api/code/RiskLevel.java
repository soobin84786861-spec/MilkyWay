package com.skku.milkyway.api.code;

public enum RiskLevel {
    SAFE("안전"),
    CAUTION("주의"),
    DANGER("위험"),
    CRITICAL("매우 위험");

    private final String koreanName;

    RiskLevel(String koreanName) {
        this.koreanName = koreanName;
    }

    public String getKoreanName() {
        return koreanName;
    }
}
