package com.skku.milkyway.api.instagram.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skku.milkyway.api.instagram.config.InstagramProperties;
import com.skku.milkyway.api.instagram.dto.InstagramPostDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 실제 Instagram 크롤링 서비스.
 * application.properties 에 instagram.session-id 가 설정된 경우에만 활성화되며,
 * DummyInstagramCrawlerService 보다 우선 적용된다.
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "instagram.session-id", matchIfMissing = false)
public class RealInstagramCrawlerService implements InstagramCrawlerService {

    private static final String BASE_URL = "https://www.instagram.com/explore/tags/%s/?__a=1&__d=dis";
    private static final String X_IG_APP_ID = "936619743392459";

    private final InstagramProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // ──────────────────────────────────────────────────────────────────────────
    // Instagram 응답 파싱용 내부 레코드 (Jackson)
    // ──────────────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record InstagramResponse(Graphql graphql) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Graphql(Hashtag hashtag) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Hashtag(
            @JsonProperty("edge_hashtag_to_media") EdgeMedia recentMedia,
            @JsonProperty("edge_hashtag_to_top_posts") EdgeMedia topMedia
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EdgeMedia(List<Edge> edges) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Edge(Node node) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Node(
            @JsonProperty("edge_media_to_caption") EdgeCaption edgeMediaToCaption,
            @JsonProperty("taken_at_timestamp") long takenAtTimestamp
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EdgeCaption(List<CaptionEdge> edges) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CaptionEdge(CaptionNode node) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CaptionNode(String text) {}

    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public List<InstagramPostDto> fetchPosts(String keyword) {
        String tagName = keyword.replace("#", "");
        String url = String.format(BASE_URL, URLEncoder.encode(tagName, StandardCharsets.UTF_8));

        log.info("[Instagram] 크롤링 요청 → {}", url);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("Cookie", buildCookie())
                    .header("User-Agent", properties.getUserAgent())
                    .header("X-CSRFToken", properties.getCsrfToken())
                    .header("X-IG-App-ID", X_IG_APP_ID)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", "https://www.instagram.com/")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("[Instagram] 응답 코드 {} — 세션 쿠키를 확인하세요", response.statusCode());
                return Collections.emptyList();
            }

            return parse(response.body());

        } catch (Exception e) {
            log.error("[Instagram] 크롤링 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<InstagramPostDto> parse(String json) throws Exception {
        InstagramResponse root = objectMapper.readValue(json, InstagramResponse.class);

        if (root.graphql() == null || root.graphql().hashtag() == null) {
            log.warn("[Instagram] 응답 파싱 실패 — 구조가 예상과 다릅니다");
            return Collections.emptyList();
        }

        Hashtag hashtag = root.graphql().hashtag();

        // 최신 게시물 + 인기 게시물 합산
        List<Edge> edges = mergeEdges(hashtag.recentMedia(), hashtag.topMedia());

        return edges.stream()
                .map(this::toDto)
                .filter(dto -> !dto.caption().isBlank())
                .toList();
    }

    private List<Edge> mergeEdges(EdgeMedia recent, EdgeMedia top) {
        List<Edge> result = new java.util.ArrayList<>();
        if (recent != null && recent.edges() != null) result.addAll(recent.edges());
        if (top != null && top.edges() != null) result.addAll(top.edges());
        return result;
    }

    private InstagramPostDto toDto(Edge edge) {
        Node node = edge.node();

        // caption 텍스트 추출
        String captionText = "";
        if (node.edgeMediaToCaption() != null
                && node.edgeMediaToCaption().edges() != null
                && !node.edgeMediaToCaption().edges().isEmpty()) {
            CaptionNode captionNode = node.edgeMediaToCaption().edges().get(0).node();
            if (captionNode != null && captionNode.text() != null) {
                captionText = captionNode.text();
            }
        }

        // caption 에서 해시태그 추출 (#으로 시작하는 단어)
        List<String> hashtags = Arrays.stream(captionText.split("\\s+"))
                .filter(w -> w.startsWith("#"))
                .map(w -> w.replaceAll("[^#\\w가-힣]", ""))
                .toList();

        // 타임스탬프 변환
        LocalDateTime createdAt = node.takenAtTimestamp() > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochSecond(node.takenAtTimestamp()), ZoneId.of("Asia/Seoul"))
                : LocalDateTime.now();

        return new InstagramPostDto(captionText, hashtags, createdAt);
    }

    private String buildCookie() {
        return "sessionid=" + properties.getSessionId()
                + "; csrftoken=" + properties.getCsrfToken();
    }
}