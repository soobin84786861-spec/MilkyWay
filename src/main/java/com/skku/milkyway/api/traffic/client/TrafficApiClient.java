package com.skku.milkyway.api.traffic.client;

import com.skku.milkyway.api.traffic.config.TrafficApiProperties;
import com.skku.milkyway.api.traffic.dto.TrafficHistoryRawDto;
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
public class TrafficApiClient {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final TrafficApiProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    public List<TrafficHistoryRawDto> fetchTrafficHistory(String pointId, LocalDate date, Integer hour) {
        validateConfiguration(properties.getHistoryServiceName(), "historyServiceName");

        List<String> extraSegments = new ArrayList<>();
        extraSegments.add(pointId);
        extraSegments.add(date.format(BASIC_DATE));
        if (hour != null) {
            extraSegments.add(String.format(Locale.ROOT, "%02d", hour));
        }

        List<Map<String, String>> rows = fetchPagedRows(properties.getHistoryServiceName(), extraSegments);
        List<TrafficHistoryRawDto> result = new ArrayList<>(rows.size());
        for (Map<String, String> row : rows) {
            result.add(new TrafficHistoryRawDto(row));
        }
        return result;
    }

    private List<Map<String, String>> fetchPagedRows(String serviceName, List<String> extraSegments) {
        int startIndex = 1;
        int batchSize = Math.max(1, properties.getBatchSize());
        Integer totalCount = null;
        List<Map<String, String>> allRows = new ArrayList<>();

        while (totalCount == null || startIndex <= totalCount) {
            int endIndex = startIndex + batchSize - 1;
            String url = buildUrl(serviceName, startIndex, endIndex, extraSegments);
            String xml = restTemplate.getForObject(url, String.class);
            if (xml == null || xml.isBlank()) {
                throw new TrafficApiException("교통량 API 응답이 비어 있습니다: " + url);
            }

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

        return allRows;
    }

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

    private Integer extractTotalCount(Document document) {
        String value = firstTagText(document, "list_total_count");
        if (value == null) {
            value = firstTagText(document, "LIST_TOTAL_COUNT");
        }
        if (value == null) {
            value = firstTagText(document, "totalCount");
        }
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<Map<String, String>> extractRows(Document document) {
        List<Map<String, String>> rows = extractElements(document, "row");
        if (!rows.isEmpty()) {
            return rows;
        }
        return extractElements(document, "item");
    }

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

    private String firstTagText(Document document, String tagName) {
        NodeList nodeList = document.getElementsByTagName(tagName);
        if (nodeList.getLength() == 0) {
            return null;
        }
        return nodeList.item(0).getTextContent();
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private void validateConfiguration(String serviceName, String propertyName) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new TrafficApiException("seoul.traffic-api.api-key 설정이 비어 있습니다.");
        }
        if (serviceName == null || serviceName.isBlank()) {
            throw new TrafficApiException("seoul.traffic-api." + propertyName + " 설정이 비어 있습니다.");
        }
    }
}
