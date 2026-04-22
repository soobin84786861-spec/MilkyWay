package com.skku.milkyway.api.traffic.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.traffic.config.ReverseGeocodeProperties;
import com.skku.milkyway.api.traffic.domain.GeoCoordinate;
import com.skku.milkyway.api.traffic.support.TrafficApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;

/**
 * 좌표를 행정구역 정보로 reverse geocoding하고 서울 자치구명을 추출한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficReverseGeocodeService {

    private final ReverseGeocodeProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<SeoulDistrict> resolveDistrict(GeoCoordinate coordinate) {
        String url = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .queryParam("format", "jsonv2")
                .queryParam("lat", coordinate.latitude())
                .queryParam("lon", coordinate.longitude())
                .queryParam("addressdetails", 1)
                .queryParamIfPresent("email",
                        properties.getEmail() == null || properties.getEmail().isBlank()
                                ? Optional.empty()
                                : Optional.of(properties.getEmail()))
                .build(false)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, properties.getUserAgent());
        headers.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE));

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            return extractDistrict(body);
        } catch (Exception e) {
            throw new TrafficApiException("좌표 reverse geocoding에 실패했습니다: " + coordinate, e);
        }
    }

    private Optional<SeoulDistrict> extractDistrict(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode address = root.path("address");
        if (address.isMissingNode() || !address.isObject()) {
            return Optional.empty();
        }

        Iterator<JsonNode> values = address.elements();
        while (values.hasNext()) {
            JsonNode value = values.next();
            if (!value.isTextual()) {
                continue;
            }
            String districtName = normalize(value.asText());
            for (SeoulDistrict district : SeoulDistrict.values()) {
                if (districtName.contains(normalize(district.getKoreanName()))) {
                    return Optional.of(district);
                }
            }
        }

        String displayName = normalize(root.path("display_name").asText(""));
        for (SeoulDistrict district : SeoulDistrict.values()) {
            if (displayName.contains(normalize(district.getKoreanName()))) {
                return Optional.of(district);
            }
        }
        return Optional.empty();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
