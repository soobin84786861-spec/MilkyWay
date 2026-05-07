package com.skku.milkyway.api.code;

public enum SeasonGrade {
    A,
    B,
    C;

    public static SeasonGrade fromValue(String value) {
        if (value == null || value.isBlank()) {
            return C;
        }

        return switch (value.trim().toUpperCase()) {
            case "A" -> A;
            case "B" -> B;
            default -> C;
        };
    }
}
