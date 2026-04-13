package com.skku.milkyway.api.instagram.dto;

import java.time.LocalDateTime;
import java.util.List;

public record InstagramPostDto(
        String caption,
        List<String> hashtags,
        LocalDateTime createdAt
) {
    /** caption + 해시태그를 하나의 문자열로 합쳐 반환 */
    public String fullText() {
        return caption + " " + String.join(" ", hashtags);
    }
}