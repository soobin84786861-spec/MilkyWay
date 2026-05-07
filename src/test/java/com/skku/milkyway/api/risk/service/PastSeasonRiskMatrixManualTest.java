package com.skku.milkyway.api.risk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skku.milkyway.api.code.RiskLevel;
import com.skku.milkyway.api.code.SeasonGrade;
import com.skku.milkyway.api.risk.response.RegionRiskResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@SpringBootTest
class PastSeasonRiskMatrixManualTest {

    private static final Path OUTPUT_PATH = Paths.get("debug", "risk-analysis", "past-season-risk-matrix.xlsx");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private RiskService riskService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exportPastSeasonRiskMatrix() throws Exception {
        List<RegionRiskResponse> regions = new ArrayList<>(riskService.getRegions((RiskLevel) null));
        regions.sort(Comparator.comparing(RegionRiskResponse::getRegionName));

        List<SeasonDayRecord> seasonDays = loadSeasonDays();

        Files.createDirectories(OUTPUT_PATH.getParent());
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             OutputStream outputStream = Files.newOutputStream(OUTPUT_PATH)) {

            Sheet sheet = workbook.createSheet("risk-matrix");
            writeSummary(sheet, regions.size(), seasonDays.size());
            writeHeader(sheet, seasonDays, 3);
            writeRows(sheet, regions, seasonDays, 4);
            autoSizeColumns(sheet, seasonDays.size() + 4);
            workbook.write(outputStream);
        }

        System.out.println("saved: " + OUTPUT_PATH.toAbsolutePath());
    }

    private List<SeasonDayRecord> loadSeasonDays() throws Exception {
        try (InputStream inputStream = new ClassPathResource("pastData/data.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            JsonNode records = root.path("records");
            List<SeasonDayRecord> result = new ArrayList<>();

            for (JsonNode record : records) {
                int month = record.path("month").asInt();
                int day = record.path("day").asInt();
                SeasonGrade seasonGrade = SeasonGrade.fromValue(record.path("season_grade").asText(""));
                result.add(new SeasonDayRecord(month, day, seasonGrade));
            }

            result.sort(Comparator
                    .comparingInt(SeasonDayRecord::month)
                    .thenComparingInt(SeasonDayRecord::day));
            return result;
        }
    }

    private void writeSummary(Sheet sheet, int districtCount, int dayCount) {
        Row row0 = sheet.createRow(0);
        row0.createCell(0).setCellValue("생성시각");
        row0.createCell(1).setCellValue(LocalDateTime.now().format(DATE_TIME_FORMATTER));

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("정책");
        row1.createCell(1).setCellValue("A=baseRisk, B=baseRisk*0.8, C=baseRisk*0.2");

        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("대상");
        row2.createCell(1).setCellValue(String.format("districts=%d, days=%d", districtCount, dayCount));
    }

    private void writeHeader(Sheet sheet, List<SeasonDayRecord> seasonDays, int rowIndex) {
        Row header = sheet.createRow(rowIndex);
        header.createCell(0).setCellValue("자치구코드");
        header.createCell(1).setCellValue("자치구명");
        header.createCell(2).setCellValue("현재위험도");

        int columnIndex = 3;
        for (SeasonDayRecord seasonDay : seasonDays) {
            header.createCell(columnIndex++)
                    .setCellValue(String.format("%d.%d (%s)", seasonDay.month(), seasonDay.day(), seasonDay.seasonGrade().name()));
        }
    }

    private void writeRows(Sheet sheet, List<RegionRiskResponse> regions, List<SeasonDayRecord> seasonDays, int startRowIndex) {
        int rowIndex = startRowIndex;

        for (RegionRiskResponse region : regions) {
            Row row = sheet.createRow(rowIndex++);
            int baseRisk = region.getBaseRiskPercent();

            row.createCell(0).setCellValue(region.getDistrictCode());
            row.createCell(1).setCellValue(region.getRegionName());
            row.createCell(2).setCellValue(baseRisk);

            int columnIndex = 3;
            for (SeasonDayRecord seasonDay : seasonDays) {
                row.createCell(columnIndex++).setCellValue(applySeasonPolicy(baseRisk, seasonDay.seasonGrade()));
            }
        }
    }

    private int applySeasonPolicy(int baseRisk, SeasonGrade seasonGrade) {
        return switch (seasonGrade) {
            case A -> baseRisk;
            case B -> scaleRisk(baseRisk, 0.8);
            case C -> scaleRisk(baseRisk, 0.2);
        };
    }

    private int scaleRisk(int baseRisk, double factor) {
        return (int) Math.round(baseRisk * factor);
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private record SeasonDayRecord(int month, int day, SeasonGrade seasonGrade) {
    }
}
