package com.skku.milkyway.api.traffic.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skku.milkyway.api.traffic.config.TrafficApiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class TrafficDataAvailabilityManualTest {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int SAMPLE_HOUR = 12;
    private static final int MIN_YEAR = 2016;
    private static final int POINT_SAMPLE_SIZE = 8;

    @Autowired
    private TrafficApiProperties trafficApiProperties;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * VolInfo에서 실제 데이터가 존재하는 가장 최근 날짜/시간을 탐색한다.
     *
     * <p>일회용 확인 테스트라서 메인 코드에는 손대지 않고,
     * 테스트 안에서 직접 API를 호출해 결과를 콘솔에 출력한다.</p>
     */
    @Test
    void findLatestAvailableTrafficSnapshot() throws Exception {
        List<String> pointIds = loadMappedPointIds();
        assertFalse(pointIds.isEmpty(), "spot-district-map.json에 매핑된 스팟이 없습니다.");
        SearchHit monthlyHit = findAvailableMonth(pointIds);
        assertNotNull(monthlyHit, "VolInfo에서 데이터가 존재하는 월을 찾지 못했습니다.");

        SearchHit dailyHit = findAvailableDay(monthlyHit.pointId(), monthlyHit.yearMonth());
        assertNotNull(dailyHit, "데이터가 존재하는 일을 찾지 못했습니다.");

        SearchHit hourlyHit = findAvailableHour(dailyHit.pointId(), dailyHit.date());
        assertNotNull(hourlyHit, "데이터가 존재하는 시간을 찾지 못했습니다.");

        System.out.println();
        System.out.println("===== VolInfo 실제 데이터 존재 시점 =====");
        System.out.printf("pointId=%s%n", hourlyHit.pointId());
        System.out.printf("date=%s%n", hourlyHit.date());
        System.out.printf("hour=%02d%n", hourlyHit.hour());
        System.out.println("======================================");
    }

    private SearchHit findAvailableMonth(List<String> pointIds) {
        YearMonth cursor = YearMonth.now();
        YearMonth min = YearMonth.of(MIN_YEAR, 1);

        while (!cursor.isBefore(min)) {
            LocalDate probeDate = cursor.atDay(Math.min(15, cursor.lengthOfMonth()));
            System.out.printf("[coarse] checking month %s with date %s%n", cursor, probeDate);

            for (String pointId : pointIds) {
                ApiProbeResult result = probe(pointId, probeDate, SAMPLE_HOUR);
                if (result.hasRows()) {
                    System.out.printf("[coarse] found data candidate - pointId=%s, date=%s, hour=%02d%n",
                            pointId, probeDate, SAMPLE_HOUR);
                    return SearchHit.forMonth(pointId, cursor);
                }
            }

            cursor = cursor.minusMonths(1);
        }

        return null;
    }

    private SearchHit findAvailableDay(String pointId, YearMonth yearMonth) {
        for (int day = yearMonth.lengthOfMonth(); day >= 1; day--) {
            LocalDate probeDate = yearMonth.atDay(day);
            ApiProbeResult result = probe(pointId, probeDate, SAMPLE_HOUR);
            if (result.hasRows()) {
                System.out.printf("[day] found data candidate - pointId=%s, date=%s, hour=%02d%n",
                        pointId, probeDate, SAMPLE_HOUR);
                return SearchHit.forDay(pointId, probeDate);
            }
        }
        return null;
    }

    private SearchHit findAvailableHour(String pointId, LocalDate date) {
        for (int hour = 23; hour >= 0; hour--) {
            ApiProbeResult result = probe(pointId, date, hour);
            if (result.hasRows()) {
                return SearchHit.forHour(pointId, date, hour);
            }
        }
        return null;
    }

    private ApiProbeResult probe(String pointId, LocalDate date, int hour) {
        String url = UriComponentsBuilder.fromUriString(trafficApiProperties.getBaseUrl())
                .pathSegment(
                        trafficApiProperties.getApiKey(),
                        "xml",
                        trafficApiProperties.getHistoryServiceName(),
                        "1",
                        "5",
                        pointId,
                        date.format(BASIC_DATE),
                        String.format("%02d", hour)
                )
                .build(false)
                .toUriString();

        try {
            String xml = restTemplate.getForObject(url, String.class);
            if (xml == null || xml.isBlank()) {
                return ApiProbeResult.empty("EMPTY_BODY");
            }

            Document document = parseXml(xml);
            String code = firstTagText(document, "CODE");
            if (code == null) {
                code = firstTagText(document, "code");
            }

            int rowCount = document.getElementsByTagName("row").getLength();
            if (rowCount == 0) {
                rowCount = document.getElementsByTagName("item").getLength();
            }

            if (rowCount > 0) {
                return ApiProbeResult.withRows(code, rowCount);
            }
            return ApiProbeResult.empty(code);
        } catch (Exception e) {
            System.out.printf("[probe] failed - pointId=%s, date=%s, hour=%02d, reason=%s%n",
                    pointId, date, hour, e.getMessage());
            return ApiProbeResult.empty("EXCEPTION");
        }
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private String firstTagText(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent();
    }

    private List<String> loadMappedPointIds() throws Exception {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("traffic/spot-district-map.json")) {
            assertNotNull(inputStream, "spot-district-map.json 리소스를 찾지 못했습니다.");

            Map<String, String> mapping = objectMapper.readValue(inputStream, new TypeReference<>() {});
            List<String> pointIds = new ArrayList<>();
            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                String districtName = entry.getValue();
                if (districtName != null && !districtName.isBlank()) {
                    pointIds.add(entry.getKey());
                }
                if (pointIds.size() >= POINT_SAMPLE_SIZE) {
                    break;
                }
            }
            return pointIds;
        }
    }

    private record ApiProbeResult(String code, int rowCount) {
        static ApiProbeResult withRows(String code, int rowCount) {
            return new ApiProbeResult(code, rowCount);
        }

        static ApiProbeResult empty(String code) {
            return new ApiProbeResult(code, 0);
        }

        boolean hasRows() {
            return rowCount > 0;
        }
    }

    private record SearchHit(String pointId, YearMonth yearMonth, LocalDate date, Integer hour) {
        static SearchHit forMonth(String pointId, YearMonth yearMonth) {
            return new SearchHit(pointId, yearMonth, null, null);
        }

        static SearchHit forDay(String pointId, LocalDate date) {
            return new SearchHit(pointId, YearMonth.from(date), date, null);
        }

        static SearchHit forHour(String pointId, LocalDate date, int hour) {
            return new SearchHit(pointId, YearMonth.from(date), date, hour);
        }
    }
}
