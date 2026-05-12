package com.skku.milkyway.api.illumination.service;

import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.illumination.client.IlluminationApiClient;
import com.skku.milkyway.api.illumination.config.IlluminationApiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IlluminationServiceTest {

    @Test
    void returnsZeroValuesWhenApiFetchFails() {
        IlluminationApiClient client = new IlluminationApiClient(new IlluminationApiProperties()) {
            @Override
            public List<Map<String, String>> fetchRows() {
                throw new IllegalStateException("boom");
            }
        };
        IlluminationService service = new IlluminationService(client);

        Map<SeoulDistrict, Double> illumination = service.getAllCurrentIllumination();

        assertEquals(SeoulDistrict.values().length, illumination.size());
        for (SeoulDistrict district : SeoulDistrict.values()) {
            assertEquals(0.0, illumination.get(district));
        }
    }
}
