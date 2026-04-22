package com.skku.milkyway.api.traffic.client;

import com.skku.milkyway.api.traffic.config.TrafficApiProperties;
import com.skku.milkyway.api.traffic.dto.TrafficHistoryRawDto;
import com.skku.milkyway.api.traffic.dto.TrafficPointRawDto;
import com.skku.milkyway.api.traffic.support.TrafficApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
/**
 * 서울시 열린데이터 교통량 Open API 호출 전담 클라이언트.
 *
 * <p>지점 정보와 교통량 이력 조회를 담당하며, XML 응답을
 * row 단위 key-value 구조로 풀어 상위 서비스에 전달한다.</p>
 */
public class TrafficApiClient {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final TrafficApiProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 교통량 측정 지점 목록을 전부 조회한다.
     */
    public List<TrafficPointRawDto> fetchTrafficPoints() {
        validateConfiguration(properties.getPointServiceName(), "pointServiceName");
        System.out.println("--- 교통량 지점 정보 API 원본 응답 시작 ---");
        List<Map<String, String>> rows = fetchPagedRows(properties.getPointServiceName(), List.of());
        System.out.println("--- 교통량 지점 정보 API 원본 응답 종료 ---");
        List<TrafficPointRawDto> result = new ArrayList<>(rows.size());
        for (Map<String, String> row : rows) {
            result.add(new TrafficPointRawDto(row));
        }
        return result;
    }

    /**
     * 특정 지점의 날짜/시간 기준 교통량 이력을 조회한다.
     */
    public List<TrafficHistoryRawDto> fetchTrafficHistory(String pointId, LocalDate date, Integer hour) {
        validateConfiguration(properties.getHistoryServiceName(), "historyServiceName");
        List<String> extraSegments = new ArrayList<>();
        extraSegments.add(pointId);
        extraSegments.add(date.format(BASIC_DATE));
        if (hour != null) {
            extraSegments.add(String.format(Locale.ROOT, "%02d", hour));
        }

        System.out.printf("--- 교통량 이력 정보 API 원본 응답 시작 (pointId=%s, date=%s, hour=%s) ---%n",
                pointId, date, hour);
        List<Map<String, String>> rows = fetchPagedRows(properties.getHistoryServiceName(), extraSegments);
        System.out.printf("--- 교통량 이력 정보 API 원본 응답 종료 (pointId=%s, date=%s, hour=%s) ---%n",
                pointId, date, hour);
        List<TrafficHistoryRawDto> result = new ArrayList<>(rows.size());
        for (Map<String, String> row : rows) {
            result.add(new TrafficHistoryRawDto(row));
        }
        return result;
    }

    /**
     * 서울시 Open API의 페이징 규칙에 맞춰 전체 row를 수집한다.
     */
    private List<Map<String, String>> fetchPagedRows(String serviceName, List<String> extraSegments) {
        int startIndex = 1;
        int batchSize = Math.max(1, properties.getBatchSize());
        List<Map<String, String>> allRows = new ArrayList<>();
        Integer totalCount = null;

        while (totalCount == null || startIndex <= totalCount) {
            int endIndex = startIndex + batchSize - 1;
            String url = buildUrl(serviceName, startIndex, endIndex, extraSegments);
            String xml = restTemplate.getForObject(url, String.class);
            if (xml == null || xml.isBlank()) {
                throw new TrafficApiException("교통량 API 응답이 비어 있습니다: " + url);
            }

            System.out.println(xml);

            Document document = parseXml(xml);
            ensureSuccess(document);

            List<Map<String, String>> rows = extractRows(document);
            allRows.addAll(rows);

            if (totalCount == null) {
                totalCount = extractTotalCount(document);
                if (totalCount == null) {
                    totalCount = rows.size();
                }
            }

            if (rows.isEmpty() || rows.size() < batchSize) {
                break;
            }
            startIndex += batchSize;
        }

        log.info("[TrafficAPI] {} 조회 완료 - {}건", serviceName, allRows.size());
        return allRows;
    }

    /**
     * 서울시 열린데이터 URL 규칙에 맞는 호출 주소를 구성한다.
     */
    private String buildUrl(String serviceName, int startIndex, int endIndex, List<String> extraSegments) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.getBaseUrl())
                .pathSegment(
                        properties.getApiKey(),
                        "xml",
                        serviceName,
                        String.valueOf(startIndex),
                        String.valueOf(endIndex)
                );

        for (String segment : extraSegments) {
            builder.pathSegment(segment);
        }

        return builder.build(false).toUriString();
    }

    /**
     * XML 문자열을 DOM 문서로 변환한다.
     */
    private Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new TrafficApiException("교통량 API XML 파싱에 실패했습니다.", e);
        }
    }

    /**
     * 공통 응답 코드가 정상인지 확인한다.
     */
    private void ensureSuccess(Document document) {
        String code = firstTagText(document, "CODE");
        if (code == null) {
            code = firstTagText(document, "code");
        }
        if (code == null || code.isBlank()) {
            return;
        }

        if ("INFO-000".equalsIgnoreCase(code) || "200".equals(code)) {
            return;
        }

        String message = firstTagText(document, "MESSAGE");
        if (message == null) {
            message = firstTagText(document, "message");
        }
        throw new TrafficApiException("교통량 API 오류: " + code + " / " + message);
    }

    /**
     * 응답에 포함된 총 건수를 읽는다.
     */
    private Integer extractTotalCount(Document document) {
        String value = firstTagText(document, "list_total_count");
        if (value == null) value = firstTagText(document, "LIST_TOTAL_COUNT");
        if (value == null) value = firstTagText(document, "totalCount");
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
     * row 또는 item 태그를 찾아 실제 데이터 목록을 추출한다.
     */
    private List<Map<String, String>> extractRows(Document document) {
        List<Map<String, String>> rows = extractElements(document, "row");
        if (!rows.isEmpty()) {
            return rows;
        }
        return extractElements(document, "item");
    }

    /**
     * 특정 태그 이름을 가진 요소 목록을 key-value 맵으로 바꾼다.
     */
    private List<Map<String, String>> extractElements(Document document, String tagName) {
        NodeList nodeList = document.getElementsByTagName(tagName);
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
     * 첫 번째 태그 값을 읽는다.
     */
    private String firstTagText(Document document, String tagName) {
        NodeList nodeList = document.getElementsByTagName(tagName);
        if (nodeList.getLength() == 0) {
            return null;
        }
        return nodeList.item(0).getTextContent();
    }

    /**
     * XML 태그 이름을 비교하기 쉬운 소문자 key로 정규화한다.
     */
    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 필수 설정값이 비어 있으면 호출 전에 즉시 실패시킨다.
     */
    private void validateConfiguration(String serviceName, String propertyName) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new TrafficApiException("seoul.traffic-api.api-key 설정이 비어 있습니다.");
        }
        if (serviceName == null || serviceName.isBlank()) {
            throw new TrafficApiException("seoul.traffic-api." + propertyName + " 설정이 비어 있습니다.");
        }
    }
}
