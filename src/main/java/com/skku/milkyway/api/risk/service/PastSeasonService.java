package com.skku.milkyway.api.risk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skku.milkyway.api.code.SeasonGrade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class PastSeasonService {

    private static final String RESOURCE_PATH = "pastData/data.json";

    private final Map<MonthDayKey, SeasonGrade> seasonByMonthDay;

    public PastSeasonService() {
        this.seasonByMonthDay = loadSeasonData();
    }

    public Optional<SeasonGrade> getSeasonGrade(LocalDate date) {
        if (date == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(seasonByMonthDay.get(new MonthDayKey(date.getMonthValue(), date.getDayOfMonth())));
    }

    private Map<MonthDayKey, SeasonGrade> loadSeasonData() {
        try (InputStream inputStream = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(inputStream);
            JsonNode records = root.path("records");

            Map<MonthDayKey, SeasonGrade> result = new HashMap<>();
            if (records.isArray()) {
                for (JsonNode record : records) {
                    int month = record.path("month").asInt();
                    int day = record.path("day").asInt();
                    SeasonGrade seasonGrade = SeasonGrade.fromValue(record.path("season_grade").asText(""));

                    result.put(new MonthDayKey(month, day), seasonGrade);
                }
            }

            log.info("[PastSeason] loaded season data - days={}", result.size());
            return Map.copyOf(result);
        } catch (Exception e) {
            log.error("[PastSeason] failed to load pastData/data.json: {}", e.getMessage());
            return Map.of();
        }
    }

    private record MonthDayKey(int month, int day) {
    }
}
