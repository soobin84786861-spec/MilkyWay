package com.skku.milkyway.api.instagram.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.RequestOptions;
import com.skku.milkyway.api.instagram.config.InstagramProperties;
import com.skku.milkyway.api.instagram.dto.InstagramPostDto;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Playwright를 사용한 Instagram 해시태그 크롤링 서비스.
 *
 * 세션 관리:
 *  - 최초 실행: 브라우저를 열어 로그인 → 세션을 {@code ~/.milkyway/ig-session.json}에 저장
 *  - 이후 실행: 저장된 세션으로 API 호출 (브라우저 불필요)
 *  - 세션 만료: 세션 파일 삭제 후 재시작하면 재로그인
 *
 * 크롤링:
 *  - 엔드포인트: {@code /api/v1/tags/web_info/?tag_name=}
 *  - top.next_max_id 커서로 페이지네이션
 *  - more_available=false 또는 MAX_PAGES 도달 시 종료
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramCrawlerServiceImpl implements InstagramCrawlerService {

    private static final String TAG_API =
            "https://www.instagram.com/api/v1/tags/web_info/?tag_name=%s";
    private static final String TAG_API_PAGED =
            "https://www.instagram.com/api/v1/tags/web_info/?tag_name=%s&max_id=%s";
    private static final String X_IG_APP_ID  = "936619743392459";
    private static final int    MAX_PAGES     = 500;
    private static final long   PAGE_DELAY_MS = 800L;
    private static final Path   SESSION_FILE  = Paths.get(System.getProperty("user.home"), ".milkyway", "ig-session.json");

    private final InstagramProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Playwright playwright;
    /** 마지막 재로그인 시각 — 직후 재호출 시 재로그인 루프 방지 */
    private volatile Instant lastReloginAt = Instant.EPOCH;
    private static final Duration RELOGIN_COOLDOWN = Duration.ofMinutes(30);

    // ──────────────────────────────────────────────────────────────────────────
    // API 응답 파싱 레코드
    // ──────────────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TagWebInfoResponse(Data data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(@JsonProperty("top") SectionGroup top) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SectionGroup(
            List<Section> sections,
            @JsonProperty("next_max_id")   String  nextMaxId,
            @JsonProperty("more_available") Boolean moreAvailable
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Section(@JsonProperty("layout_content") LayoutContent layoutContent) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LayoutContent(List<MediaWrapper> medias) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MediaWrapper(Media media) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Media(
            Caption caption,
            @JsonProperty("taken_at") long takenAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Caption(String text) {}

    // ──────────────────────────────────────────────────────────────────────────
    // 생명주기
    // ──────────────────────────────────────────────────────────────────────────

    @PostConstruct
    public void initialize() {
        playwright = Playwright.create();
        if (Files.exists(SESSION_FILE)) {
            log.info("[Instagram] 저장된 세션 로드: {}", SESSION_FILE);
        } else {
            browserLoginOrWarn();
        }
    }

    @PreDestroy
    public void destroy() {
        if (playwright != null) playwright.close();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 로그인
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * browserLogin=true(로컬)이면 Playwright 브라우저 로그인, false(서버)이면 경고만 출력.
     */
    private void browserLoginOrWarn() {
        if (!properties.isBrowserLogin()) {
            log.error("[Instagram] 세션 파일 없음. 로컬에서 로그인 후 세션 파일을 업로드하세요: {}", SESSION_FILE);
            log.error("[Instagram] 로컬: instagram.browser-login=true 로 설정 후 앱 실행 → 세션 생성");
            log.error("[Instagram]  EC2: scp ~/.milkyway/ig-session.json ec2-user@<IP>:~/.milkyway/");
            return;
        }
        loginAndSaveSession();
    }

    /**
     * checkpoint_required 응답 수신 시 브라우저로 인증 URL을 열어 사용자가 직접 완료하도록 유도.
     * 완료 후 갱신된 세션을 저장한다. browser-login=false 환경(EC2)에서는 경고만 출력.
     */
    private void resolveCheckpoint(String responseBody) {
        if (!properties.isBrowserLogin()) {
            log.error("[Instagram] checkpoint 필요 — browser-login=false 환경에서는 자동 처리 불가.");
            log.error("[Instagram] 로컬에서 재로그인 후 세션 파일을 EC2에 재업로드하세요: {}", SESSION_FILE);
            return;
        }
        try {
            String checkpointUrl = objectMapper.readTree(responseBody).path("checkpoint_url").asText();
            log.warn("[Instagram] checkpoint 인증 필요 — 브라우저에서 완료해주세요 (최대 5분 대기)");
            log.warn("[Instagram] checkpoint URL: {}", checkpointUrl);

            String sessionJson = Files.exists(SESSION_FILE) ? Files.readString(SESSION_FILE) : null;

            try (Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(false).setSlowMo(80))) {

                Browser.NewContextOptions ctxOpts = new Browser.NewContextOptions()
                        .setUserAgent(properties.getUserAgent());
                if (sessionJson != null) ctxOpts.setStorageState(sessionJson);

                BrowserContext ctx = browser.newContext(ctxOpts);
                Page page = ctx.newPage();
                page.navigate(checkpointUrl);

                // 사용자가 인증 완료하면 /challenge/ URL에서 벗어남
                page.waitForURL(
                        url -> !url.contains("/challenge/"),
                        new Page.WaitForURLOptions().setTimeout(300_000));
                page.waitForTimeout(2_000);

                Files.createDirectories(SESSION_FILE.getParent());
                Files.writeString(SESSION_FILE, ctx.storageState());
                log.info("[Instagram] checkpoint 완료 — 세션 저장. 다음 크롤링부터 정상 동작합니다.");
            }
        } catch (Exception e) {
            log.error("[Instagram] checkpoint 처리 실패: {}", e.getMessage());
        }
    }

    private void loginAndSaveSession() {
        log.info("[Instagram] 브라우저 로그인 시작");
        log.info("[Instagram] checkpoint(보안 인증)가 뜨면 브라우저에서 직접 완료해주세요.");

        try (Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(false).setSlowMo(80))) {

            BrowserContext ctx = browser.newContext(
                    new Browser.NewContextOptions()
                            .setUserAgent(properties.getUserAgent())
                            .setViewportSize(390, 844));
            Page page = ctx.newPage();

            page.navigate("https://www.instagram.com/accounts/login/");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            dismissOverlays(page);

            Locator usernameInput = page.locator("input:not([type='password'])").first();
            usernameInput.waitFor(new Locator.WaitForOptions().setTimeout(10_000));
            usernameInput.fill(properties.getUsername());
            page.waitForTimeout(600);

            page.locator("input[type='password']").fill(properties.getPassword());
            page.waitForTimeout(600);
            page.locator("input[type='password']").press("Enter");

            page.waitForURL(
                    url -> url.startsWith("https://www.instagram.com/")
                            && !url.contains("/accounts/login")
                            && !url.contains("/auth_platform"),
                    new Page.WaitForURLOptions().setTimeout(180_000));

            dismissOverlays(page);

            Files.createDirectories(SESSION_FILE.getParent());
            Files.writeString(SESSION_FILE, ctx.storageState());
            log.info("[Instagram] 로그인 성공, 세션 저장: {}", SESSION_FILE);

        } catch (Exception e) {
            log.error("[Instagram] 로그인 실패: {}", e.getMessage());
        }
    }

    private void dismissOverlays(Page page) {
        for (String text : List.of(
                "Allow all cookies", "모두 허용", "Accept All",
                "Not Now", "나중에", "저장 안 함", "지금은 안 함")) {
            try {
                Locator btn = page.locator("text=" + text).first();
                if (btn.isVisible()) {
                    btn.click();
                    page.waitForTimeout(400);
                }
            } catch (Exception ignored) {}
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 크롤링
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public List<InstagramPostDto> fetchPosts(String keyword) {
        if (!Files.exists(SESSION_FILE)) {
            log.warn("[Instagram] 세션 없음 — 재시작 후 로그인하세요.");
            return Collections.emptyList();
        }

        String tag       = URLEncoder.encode(keyword.replace("#", ""), StandardCharsets.UTF_8);
        String nextMaxId = null;
        String prevMaxId = null;
        int    pageNum   = 0;
        List<MediaWrapper> collected = new ArrayList<>();

        APIRequestContext req;
        try {
            String sessionJson = Files.readString(SESSION_FILE);
            req = playwright.request().newContext(
                    new APIRequest.NewContextOptions()
                            .setStorageState(sessionJson)
                            .setUserAgent(properties.getUserAgent())
                            .setExtraHTTPHeaders(Map.of(
                                    "X-IG-App-ID",      X_IG_APP_ID,
                                    "X-Requested-With", "XMLHttpRequest",
                                    "Referer",          "https://www.instagram.com/",
                                    "Accept",           "*/*"
                            )));
        } catch (Exception e) {
            log.error("[Instagram] 세션 파일 읽기 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
        try {
            do {
                String url = (nextMaxId == null)
                        ? String.format(TAG_API, tag)
                        : String.format(TAG_API_PAGED, tag, nextMaxId);

                APIResponse response = req.get(url, RequestOptions.create()
                        .setTimeout(Duration.ofSeconds(15).toMillis()));

                if (!response.ok()) {
                    String errBody = response.text();
                    log.warn("[Instagram] 응답 코드 {} (page {}) — body: {}",
                            response.status(), pageNum + 1,
                            errBody.length() > 300 ? errBody.substring(0, 300) : errBody);
                    if (response.status() == 400 && errBody.contains("checkpoint_required")) {
                        resolveCheckpoint(errBody);
                    }
                    break;
                }

                String body = response.text();
                if (body.startsWith("<")) {
                    log.warn("[Instagram] HTML 응답 수신 (세션 만료 또는 계정 제한). 수집된 {}건 반환.", collected.size());
                    boolean cooledDown = Duration.between(lastReloginAt, Instant.now()).compareTo(RELOGIN_COOLDOWN) > 0;
                    if (cooledDown) {
                        Files.deleteIfExists(SESSION_FILE);
                        browserLoginOrWarn();
                        lastReloginAt = Instant.now();
                    } else {
                        log.warn("[Instagram] 재로그인 쿨다운 중 — {}분 후 재시도", RELOGIN_COOLDOWN.toMinutes());
                    }
                    break;
                }

                TagWebInfoResponse root = objectMapper.readValue(body, TagWebInfoResponse.class);
                if (root.data() == null || root.data().top() == null) break;

                SectionGroup top = root.data().top();
                collectMedias(top, collected);

                prevMaxId = nextMaxId;
                nextMaxId = hasMore(top) ? top.nextMaxId() : null;
                pageNum++;

                log.info("[Instagram] page={} 누적={}건 nextMaxId={}", pageNum, collected.size(), nextMaxId);

                // 커서가 전진하지 않으면 페이지네이션 종료 (무한 루프 방지)
                if (nextMaxId != null && nextMaxId.equals(prevMaxId)) {
                    log.info("[Instagram] nextMaxId 반복 감지 — 페이지네이션 종료");
                    break;
                }

                if (nextMaxId != null) Thread.sleep(PAGE_DELAY_MS);

            } while (nextMaxId != null && pageNum < MAX_PAGES);

        } catch (Exception e) {
            log.error("[Instagram] 크롤링 실패: {}", e.getMessage());
        } finally {
            req.dispose();
        }

        log.info("[Instagram] 총 {}페이지, {}건 수집", pageNum, collected.size());
        return collected.stream()
                .map(this::toDto)
                .filter(dto -> !dto.caption().isBlank())
                .toList();
    }

    private boolean hasMore(SectionGroup group) {
        return group.nextMaxId() != null && !group.nextMaxId().isBlank();
    }

    private void collectMedias(SectionGroup group, List<MediaWrapper> target) {
        if (group.sections() == null) return;
        for (Section section : group.sections()) {
            if (section.layoutContent() != null && section.layoutContent().medias() != null) {
                target.addAll(section.layoutContent().medias());
            }
        }
    }

    private InstagramPostDto toDto(MediaWrapper wrapper) {
        Media  media = wrapper.media();
        String text  = (media.caption() != null && media.caption().text() != null)
                ? media.caption().text() : "";

        List<String> hashtags = Arrays.stream(text.split("\\s+"))
                .filter(w -> w.startsWith("#"))
                .map(w -> w.replaceAll("[^#\\w가-힣]", ""))
                .toList();

        LocalDateTime createdAt = media.takenAt() > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochSecond(media.takenAt()), ZoneId.of("Asia/Seoul"))
                : LocalDateTime.now();

        return new InstagramPostDto(text, hashtags, createdAt);
    }
}