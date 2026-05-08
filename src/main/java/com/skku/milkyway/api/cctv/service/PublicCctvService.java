package com.skku.milkyway.api.cctv.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skku.milkyway.api.cctv.response.PublicCctvResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

@Service
public class PublicCctvService {

    private static final String RESOURCE_PATH = "cctv/public-cctv.json";
    private static final String LOCAL_STREAM_URL_TEMPLATE = "/api/cctv/%s/stream";
    private static final String UTIC_BASE_URL = "https://www.utic.go.kr";
    private static final String UTIC_REFERER_URL = UTIC_BASE_URL + "/main/main.do";
    private static final String UTIC_OPEN_DATA_URL_TEMPLATE = "http://www.utic.go.kr/guide/cctvOpenData.do?key=%s";
    private static final String UTIC_INFO_URL_TEMPLATE = UTIC_BASE_URL + "/map/getCctvInfoById.do?cctvId=%s";
    private static final String UTIC_FALLBACK_INFO_URL_TEMPLATE = "http://www.utic.go.kr/map/getCctvInfoById.do?cctvId=%s";
    private static final String UTIC_STREAM_URL_TEMPLATE =
            UTIC_BASE_URL + "/jsp/map/cctvStream.jsp?cctvid=%s&cctvname=%s&kind=%s&cctvip=%s&cctvch=%s&id=%s&cctvpasswd=%s&cctvport=%s%s";
    private static final Pattern ARRAY_PATTERN = Pattern.compile("\\[(.*)]", Pattern.DOTALL);
    private static final Pattern OBJECT_PATTERN = Pattern.compile("\\{([^{}]*)}");
    private static final Pattern FIELD_PATTERN = Pattern.compile("([A-Za-z0-9_]+)\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|[^,}]+)");

    private static final String PROXY_INJECT_HEAD = "<base href=\"" + UTIC_BASE_URL + "/\">";
    private static final String PROXY_INJECT_CSS = """
            <style>
            .hd, video ~ .hd, .shad_cctv > .hd, .shad-cctv > .hd,
            [class="hd"], [class~="hd"] { display: none !important; }
            </style>
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final RestTemplate sslTrustingRestTemplate = buildSslTrustingRestTemplate();
    private final String openDataKey;

    public PublicCctvService(@Value("${utic.cctv-open-data.key:}") String openDataKey) {
        this.openDataKey = openDataKey == null ? "" : openDataKey.trim();
    }

    private static RestTemplate buildSslTrustingRestTemplate() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String t) {}
                public void checkServerTrusted(X509Certificate[] c, String t) {}
            }}, new SecureRandom());
            var sf = ctx.getSocketFactory();

            var factory = new SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(HttpURLConnection conn, String method) throws IOException {
                    if (conn instanceof HttpsURLConnection httpsConn) {
                        httpsConn.setSSLSocketFactory(sf);
                        httpsConn.setHostnameVerifier((h, s) -> true);
                    }
                    super.prepareConnection(conn, method);
                }
            };
            return new RestTemplate(factory);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build SSL-trusting RestTemplate", e);
        }
    }

    public List<PublicCctvResponse> getAll() {
        return loadItems().stream()
                .map(item -> PublicCctvResponse.builder()
                        .name(item.name())
                        .latitude(item.latitude())
                        .longitude(item.longitude())
                        .cctvId(item.cctvId())
                        .streamUrl(buildLocalStreamUrl(item.cctvId()))
                        .build())
                .toList();
    }

    public String proxyStreamPage(String cctvId, ViewBounds viewBounds) {
        String streamUrl = resolveStreamUrl(cctvId, viewBounds);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.REFERER, UTIC_REFERER_URL);
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0");
        headers.setAccept(List.of(MediaType.TEXT_HTML, MediaType.ALL));

        String html = sslTrustingRestTemplate.exchange(
                URI.create(streamUrl),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        ).getBody();

        if (html == null || html.isBlank()) {
            return "<html><body></body></html>";
        }

        // inject <base href> right after <head> so relative URLs resolve against utic.go.kr
        html = html.replaceFirst("(?i)(<head[^>]*>)", "$1" + PROXY_INJECT_HEAD);
        // inject CSS to hide HD element before </head>
        html = html.replaceFirst("(?i)(</head>)", PROXY_INJECT_CSS + "$1");

        return html;
    }

    public String resolveStreamUrl(String cctvId, ViewBounds viewBounds) {
        PublicCctvItem item = findItemById(cctvId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown CCTV id: " + cctvId));

        try {
            UticCctvInfo info = fetchUticCctvInfo(cctvId);
            return buildUticStreamUrl(info, item, viewBounds);
        } catch (RestClientException | IllegalStateException e) {
            throw new IllegalStateException("Failed to resolve UTIC stream url for " + cctvId, e);
        }
    }

    private List<PublicCctvItem> loadItems() {
        List<PublicCctvItem> openDataItems = loadItemsFromOpenData();
        if (!openDataItems.isEmpty()) {
            return openDataItems;
        }

        try (InputStream inputStream = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            PublicCctvResource resource = objectMapper.readValue(inputStream, PublicCctvResource.class);
            return resource.items();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load CCTV sample data", e);
        }
    }

    private Optional<PublicCctvItem> findItemById(String cctvId) {
        return loadItems().stream()
                .filter(item -> item.cctvId().equals(cctvId))
                .findFirst();
    }

    private List<PublicCctvItem> loadItemsFromOpenData() {
        if (!hasText(openDataKey) || openDataKey.startsWith("YOUR_")) {
            return List.of();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0");
            headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.ALL));

            String body = restTemplate.exchange(
                    URI.create(buildOpenDataUrl()),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            ).getBody();

            if (!hasText(body)) {
                return List.of();
            }

            return parseOpenDataItems(body).stream()
                    .filter(this::isSeoulMovieCctv)
                    .map(this::toPublicCctvItem)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildLocalStreamUrl(String cctvId) {
        return LOCAL_STREAM_URL_TEMPLATE.formatted(cctvId);
    }

    private String buildOpenDataUrl() {
        String normalizedKey = openDataKey.contains("%")
                ? URLDecoder.decode(openDataKey, StandardCharsets.UTF_8)
                : openDataKey;
        return UTIC_OPEN_DATA_URL_TEMPLATE.formatted(encodeQueryValue(normalizedKey));
    }

    private UticCctvInfo fetchUticCctvInfo(String cctvId) {
        Optional<PublicCctvItem> localItem = findItemById(cctvId);
        if (localItem.isPresent() && localItem.get().openDataBacked()) {
            return new UticCctvInfo(
                    localItem.get().cctvId(),
                    localItem.get().name(),
                    localItem.get().cctvIp(),
                    localItem.get().port(),
                    localItem.get().channel(),
                    localItem.get().remoteId(),
                    localItem.get().password(),
                    localItem.get().kind()
            );
        }

        Exception lastException = null;

        for (String url : List.of(
                UTIC_FALLBACK_INFO_URL_TEMPLATE.formatted(cctvId),
                UTIC_INFO_URL_TEMPLATE.formatted(cctvId)
        )) {
            try {
                UticCctvInfo info = requestUticCctvInfo(url);
                if (hasPlayableFields(info)) {
                    return info;
                }
            } catch (Exception e) {
                lastException = e;
            }
        }

        if (lastException != null) {
            throw new IllegalStateException("UTIC metadata request failed for " + cctvId, lastException);
        }

        throw new IllegalStateException("UTIC metadata is missing required fields for " + cctvId);
    }

    private UticCctvInfo requestUticCctvInfo(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.REFERER, UTIC_REFERER_URL);
        headers.set("X-Requested-With", "XMLHttpRequest");
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));

        String body = restTemplate.exchange(
                URI.create(url),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        ).getBody();

        if (!hasText(body)) {
            return null;
        }

        try {
            return objectMapper.readValue(body, UticCctvInfo.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse UTIC CCTV response", e);
        }
    }

    private boolean hasPlayableFields(UticCctvInfo info) {
        return info != null
                && hasText(info.cctvId())
                && hasText(resolveKind(info.cctvId(), info.kind()))
                && hasText(info.id());
    }

    private String buildUticStreamUrl(UticCctvInfo info, PublicCctvItem item, ViewBounds viewBounds) {
        String resolvedName = hasText(info.cctvName()) ? info.cctvName() : item.name();
        return UTIC_STREAM_URL_TEMPLATE.formatted(
                encodeQueryValue(info.cctvId()),
                doubleEncodeQueryValue(resolvedName),
                encodeQueryValue(resolveKind(info.cctvId(), info.kind())),
                encodeQueryValue(orUndefined(info.cctvIp())),
                encodeQueryValue(orUndefined(info.channel())),
                encodeQueryValue(encodeCctvIdValue(info.id(), info.cctvId())),
                encodeQueryValue(orUndefined(info.password())),
                encodeQueryValue(orUndefined(info.port())),
                buildBoundsQuery(viewBounds)
        );
    }

    private String resolveKind(String cctvId, String kind) {
        if (cctvId == null || cctvId.length() < 3) {
            return kind;
        }

        String prefix = cctvId.substring(0, 3);
        return switch (prefix) {
            case "L01" -> "Seoul";
            case "L02" -> "N";
            case "L03" -> "O";
            case "L04" -> "P";
            case "L08" -> "d";
            default -> kind;
        };
    }

    private String encodeCctvIdValue(String value, String cctvId) {
        if (!hasText(value)) {
            return "undefined";
        }

        String replaced = value.replace("+", "%2B");
        if (cctvId != null && (cctvId.startsWith("E60") || cctvId.startsWith("E62") || cctvId.startsWith("E63"))) {
            return encodeQueryValue(replaced);
        }
        return replaced;
    }

    private String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String doubleEncodeQueryValue(String value) {
        return encodeQueryValue(encodeQueryValue(value));
    }

    private String buildBoundsQuery(ViewBounds viewBounds) {
        if (viewBounds == null || !viewBounds.isComplete()) {
            return "";
        }

        return "&minX=%s&minY=%s&maxX=%s&maxY=%s".formatted(
                trimDouble(viewBounds.minX()),
                trimDouble(viewBounds.minY()),
                trimDouble(viewBounds.maxX()),
                trimDouble(viewBounds.maxY())
        );
    }

    private String trimDouble(Double value) {
        return value == null ? "" : Double.toString(value);
    }

    private String orUndefined(String value) {
        return hasText(value) ? value : "undefined";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<UticOpenDataItem> parseOpenDataItems(String rawBody) throws IOException {
        String body = rawBody.trim();
        if (body.startsWith("<")) {
            return parseXmlOpenDataItems(body);
        }

        try {
            return parseTreeOpenDataItems(objectMapper.readTree(body));
        } catch (Exception ignored) {
            return parseJavascriptStyleItems(body);
        }
    }

    private List<UticOpenDataItem> parseTreeOpenDataItems(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }

        if (node.isArray()) {
            return readJsonArrayItems(node);
        }

        if (node.isObject()) {
            for (String fieldName : List.of("data", "list", "items", "rows", "result")) {
                var child = node.get(fieldName);
                if (child != null && child.isArray()) {
                    return readJsonArrayItems(child);
                }
            }
        }

        return List.of();
    }

    private List<UticOpenDataItem> readJsonArrayItems(com.fasterxml.jackson.databind.JsonNode arrayNode) {
        List<UticOpenDataItem> items = new ArrayList<>();
        for (var node : arrayNode) {
            if (!node.isObject()) {
                continue;
            }

            Map<String, String> values = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> {
                values.put(entry.getKey(), entry.getValue().isNull() ? "" : entry.getValue().asText());
            });
            items.add(UticOpenDataItem.from(values));
        }
        return items;
    }

    private List<UticOpenDataItem> parseJavascriptStyleItems(String body) {
        Matcher arrayMatcher = ARRAY_PATTERN.matcher(body);
        if (!arrayMatcher.find()) {
            return List.of();
        }

        String arrayBody = arrayMatcher.group(1);
        Matcher objectMatcher = OBJECT_PATTERN.matcher(arrayBody);
        List<UticOpenDataItem> items = new ArrayList<>();

        while (objectMatcher.find()) {
            String objectBody = objectMatcher.group(1);
            Matcher fieldMatcher = FIELD_PATTERN.matcher(objectBody);
            Map<String, String> values = new LinkedHashMap<>();
            while (fieldMatcher.find()) {
                values.put(fieldMatcher.group(1), normalizeJavascriptValue(fieldMatcher.group(2)));
            }
            if (!values.isEmpty()) {
                items.add(UticOpenDataItem.from(values));
            }
        }

        return items;
    }

    private List<UticOpenDataItem> parseXmlOpenDataItems(String body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new StringReader("")));
            var document = builder.parse(new java.io.ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
            var itemNodes = document.getElementsByTagName("item");
            if (itemNodes.getLength() == 0) {
                itemNodes = document.getElementsByTagName("data");
            }

            List<UticOpenDataItem> items = new ArrayList<>();
            for (int index = 0; index < itemNodes.getLength(); index++) {
                var node = itemNodes.item(index);
                var childNodes = node.getChildNodes();
                Map<String, String> values = new LinkedHashMap<>();
                for (int childIndex = 0; childIndex < childNodes.getLength(); childIndex++) {
                    var child = childNodes.item(childIndex);
                    if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        values.put(child.getNodeName(), child.getTextContent());
                    }
                }
                if (!values.isEmpty()) {
                    items.add(UticOpenDataItem.from(values));
                }
            }
            return items;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String normalizeJavascriptValue(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return value
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\\\", "\\");
    }

    private boolean isSeoulMovieCctv(UticOpenDataItem item) {
        return hasText(item.cctvId())
                && item.cctvId().startsWith("L01")
                && parseDouble(item.xCoord()) > 0
                && parseDouble(item.yCoord()) > 0
                && !"N".equalsIgnoreCase(trimToEmpty(item.movie()));
    }

    private PublicCctvItem toPublicCctvItem(UticOpenDataItem item) {
        return new PublicCctvItem(
                item.cctvName(),
                parseDouble(item.yCoord()),
                parseDouble(item.xCoord()),
                item.cctvId(),
                item.cctvIp(),
                item.port(),
                item.channel(),
                item.id(),
                item.password(),
                item.kind(),
                true
        );
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PublicCctvResource(List<PublicCctvItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PublicCctvItem(
            String name,
            double latitude,
            double longitude,
            String cctvId,
            String cctvIp,
            String port,
            String channel,
            String remoteId,
            String password,
            String kind,
            boolean openDataBacked
    ) {
        PublicCctvItem(
                String name,
                double latitude,
                double longitude,
                String cctvId
        ) {
            this(name, latitude, longitude, cctvId, null, null, null, null, null, null, false);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UticOpenDataItem(
            String CCTVID,
            String CCTVNAME,
            String XCOORD,
            String YCOORD,
            String CCTVIP,
            String PORT,
            String CH,
            String ID,
            String PASSWD,
            String KIND,
            String MOVIE
    ) {
        static UticOpenDataItem from(Map<String, String> values) {
            Map<String, String> normalized = new LinkedHashMap<>();
            values.forEach((key, value) -> normalized.put(key.toUpperCase(Locale.ROOT), value));

            return new UticOpenDataItem(
                    normalized.get("CCTVID"),
                    normalized.get("CCTVNAME"),
                    normalized.get("XCOORD"),
                    normalized.get("YCOORD"),
                    normalized.get("CCTVIP"),
                    normalized.get("PORT"),
                    normalized.get("CH"),
                    normalized.get("ID"),
                    normalized.get("PASSWD"),
                    normalized.get("KIND"),
                    normalized.get("MOVIE")
            );
        }

        String cctvId() { return CCTVID; }
        String cctvName() { return CCTVNAME; }
        String xCoord() { return XCOORD; }
        String yCoord() { return YCOORD; }
        String cctvIp() { return CCTVIP; }
        String port() { return PORT; }
        String channel() { return CH; }
        String id() { return ID; }
        String password() { return PASSWD; }
        String kind() { return KIND; }
        String movie() { return MOVIE; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UticCctvInfo(
            String CCTVID,
            String CCTVNAME,
            String CCTVIP,
            String PORT,
            String CH,
            String ID,
            String PASSWD,
            String KIND
    ) {
        String cctvId() {
            return CCTVID;
        }

        String cctvName() {
            return CCTVNAME;
        }

        String cctvIp() {
            return CCTVIP;
        }

        String port() {
            return PORT;
        }

        String channel() {
            return CH;
        }

        String id() {
            return ID;
        }

        String password() {
            return PASSWD;
        }

        String kind() {
            return KIND;
        }
    }

    public record ViewBounds(
            Double minX,
            Double minY,
            Double maxX,
            Double maxY
    ) {
        boolean isComplete() {
            return minX != null && minY != null && maxX != null && maxY != null;
        }
    }
}
