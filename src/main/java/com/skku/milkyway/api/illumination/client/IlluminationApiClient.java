package com.skku.milkyway.api.illumination.client;

import com.skku.milkyway.api.illumination.config.IlluminationApiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
public class IlluminationApiClient {

    private static final String SENSING_TIME_FIELD = "sensing_time";
    private static final DateTimeFormatter[] SENSING_TIME_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

    private final IlluminationApiProperties properties;
    private final RestTemplate restTemplate;

    public IlluminationApiClient(IlluminationApiProperties properties) {
        this.properties = properties;
        this.restTemplate = buildRestTemplate(properties);
    }

    /**
     * 최근 1시간 window에 해당하는 조도 row를 조회한다.
     */
    public List<Map<String, String>> fetchRows() {
        validateConfiguration();
        long startedAt = System.currentTimeMillis();
        log.info("[IlluminationAPI] 조도 API 조회 시작 - serviceName={}", properties.getServiceName());

        int startIndex = 1;
        int batchSize = Math.max(1, properties.getBatchSize());
        Integer totalCount = null;
        LocalDateTime latestSensingTime = null;
        LocalDateTime windowStart = null;
        List<Map<String, String>> windowRows = new ArrayList<>();

        while (totalCount == null || startIndex <= totalCount) {
            int endIndex = startIndex + batchSize - 1;
            String url = buildUrl(startIndex, endIndex);
            String xml = fetchXml(url, startIndex, endIndex, totalCount);
            if (xml == null || xml.isBlank()) {
                throw new IllegalStateException("조도 API 응답이 비어 있습니다.");
            }

            Document document = parseXml(xml);
            ensureSuccess(document);

            List<Map<String, String>> rows = extractRows(document);
            if (totalCount == null) {
                totalCount = extractTotalCount(document);
                if (totalCount == null) {
                    totalCount = rows.size();
                }

                latestSensingTime = rows.stream()
                        .map(row -> parseSensingTime(row.get(SENSING_TIME_FIELD)))
                        .filter(value -> value != null)
                        .max(LocalDateTime::compareTo)
                        .orElse(null);
                windowStart = latestSensingTime == null ? null : latestSensingTime.minusHours(1);
            }

            List<Map<String, String>> currentWindowRows = filterRowsWithinWindow(rows, windowStart, latestSensingTime);
            windowRows.addAll(currentWindowRows);

            if (latestSensingTime != null && currentWindowRows.isEmpty()) {
                break;
            }
            if (rows.isEmpty() || rows.size() < batchSize) {
                break;
            }

            startIndex += batchSize;
        }

        log.info(
                "[IlluminationAPI] 조도 API 조회 완료 - serviceName={}, rows={}, latestSensingTime={}, windowStart={}, elapsed={}ms",
                properties.getServiceName(),
                windowRows.size(),
                latestSensingTime,
                windowStart,
                System.currentTimeMillis() - startedAt
        );
        return windowRows;
    }

    private String fetchXml(String url, int startIndex, int endIndex, Integer totalCount) {
        log.info(
                "[IlluminationAPI] 조도 API 배치 조회 - serviceName={}, startIndex={}, endIndex={}, totalCount={}",
                properties.getServiceName(),
                startIndex,
                endIndex,
                totalCount
        );
        try {
            return restTemplate.getForObject(url, String.class);
        } catch (RestClientException e) {
            throw new IllegalStateException(
                    "조도 API 호출 실패 - serviceName=%s, startIndex=%d, endIndex=%d, url=%s"
                            .formatted(properties.getServiceName(), startIndex, endIndex, url),
                    e
            );
        }
    }

    private RestTemplate buildRestTemplate(IlluminationApiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(1, properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Math.max(1, properties.getReadTimeoutMs()));
        return new RestTemplate(requestFactory);
    }

    private String sanitizeXml(String xml) {
        if (xml == null || xml.isBlank()) {
            return xml;
        }

        return xml.replaceAll("&(?!amp;|lt;|gt;|quot;|apos;|#\\d+;|#x[0-9A-Fa-f]+;)", "&amp;");
    }

    /**
     * 최신 1시간 window 안에 포함되는 row만 남긴다.
     */
    private List<Map<String, String>> filterRowsWithinWindow(
            List<Map<String, String>> rows,
            LocalDateTime windowStart,
            LocalDateTime latestSensingTime
    ) {
        if (windowStart == null || latestSensingTime == null) {
            return rows;
        }

        return rows.stream()
                .filter(row -> {
                    LocalDateTime sensingTime = parseSensingTime(row.get(SENSING_TIME_FIELD));
                    return sensingTime != null
                            && !sensingTime.isBefore(windowStart)
                            && !sensingTime.isAfter(latestSensingTime);
                })
                .toList();
    }

    /**
     * 지원하는 sensing_time 포맷을 LocalDateTime으로 변환한다.
     */
    private LocalDateTime parseSensingTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        for (DateTimeFormatter formatter : SENSING_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    /**
     * 서울 Open API 형식에 맞는 조회 URL을 만든다.
     */
    private String buildUrl(int startIndex, int endIndex) {
        return UriComponentsBuilder
                .fromUriString(properties.getBaseUrl())
                .pathSegment(
                        properties.getApiKey(),
                        "xml",
                        properties.getServiceName(),
                        String.valueOf(startIndex),
                        String.valueOf(endIndex)
                )
                .build(false)
                .toUriString();
    }

    /**
     * XML 응답을 DOM 문서로 변환한다.
     */
    private Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new SilentErrorHandler());
            return builder.parse(new InputSource(new StringReader(sanitizeXml(xml))));
        } catch (Exception e) {
            throw new IllegalStateException("조도 API XML 파싱에 실패했습니다.", e);
        }
    }

    /**
     * API 응답 코드가 정상인지 확인한다.
     */
    private void ensureSuccess(Document document) {
        String code = firstTagText(document, "CODE");
        if (code == null || code.isBlank()) {
            return;
        }
        if ("INFO-000".equalsIgnoreCase(code) || "200".equals(code)) {
            return;
        }

        String message = firstTagText(document, "MESSAGE");
        throw new IllegalStateException("조도 API 오류: " + code + " / " + message);
    }

    /**
     * 응답에서 전체 row 수를 읽는다.
     */
    private Integer extractTotalCount(Document document) {
        String value = firstTagText(document, "list_total_count");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * XML row를 key-value 맵으로 변환한다.
     */
    private List<Map<String, String>> extractRows(Document document) {
        NodeList nodeList = document.getElementsByTagName("row");
        List<Map<String, String>> result = new ArrayList<>();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }

            Map<String, String> fields = new LinkedHashMap<>();
            NodeList children = element.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child instanceof Element childElement) {
                    fields.put(normalizeKey(childElement.getTagName()), childElement.getTextContent().trim());
                }
            }
            result.add(fields);
        }

        return result;
    }

    /**
     * 지정한 태그의 첫 번째 텍스트 값을 반환한다.
     */
    private String firstTagText(Document document, String tagName) {
        NodeList nodeList = document.getElementsByTagName(tagName);
        if (nodeList.getLength() == 0) {
            return null;
        }
        return nodeList.item(0).getTextContent();
    }

    /**
     * XML 태그명을 소문자 key로 정규화한다.
     */
    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * API 호출에 필요한 필수 설정값이 비어 있지 않은지 검사한다.
     */
    private void validateConfiguration() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("seoul.illumination-api.api-key 설정이 비어 있습니다.");
        }
        if (properties.getServiceName() == null || properties.getServiceName().isBlank()) {
            throw new IllegalStateException("seoul.illumination-api.service-name 설정이 비어 있습니다.");
        }
    }
    private static final class SilentErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException exception) throws SAXParseException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXParseException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXParseException {
            throw exception;
        }
    }
}
