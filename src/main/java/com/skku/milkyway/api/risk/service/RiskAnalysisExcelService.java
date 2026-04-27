package com.skku.milkyway.api.risk.service;

import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.risk.response.RegionRiskResponse;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class RiskAnalysisExcelService {

    private static final Path OUTPUT_PATH = Paths.get("debug", "risk-analysis", "risk-analysis-snapshot.xlsx");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 위험도 계산에 사용된 자치구별 분석 값을 엑셀 파일로 저장한다.
     */
    public void export(
            LocalDateTime generatedAt,
            List<RegionRiskResponse> regions,
            Map<SeoulDistrict, Double> habitatRatioByDistrict,
            Map<SeoulDistrict, Double> trafficScoreByDistrict
    ) {
        try {
            Files.createDirectories(OUTPUT_PATH.getParent());

            try (XSSFWorkbook workbook = new XSSFWorkbook();
                 OutputStream outputStream = Files.newOutputStream(OUTPUT_PATH)) {
                Sheet sheet = workbook.createSheet("risk-analysis");
                writeHeader(sheet);
                writeRows(sheet, generatedAt, regions, habitatRatioByDistrict, trafficScoreByDistrict);
                autoSizeColumns(sheet, 25);
                workbook.write(outputStream);
            }

            log.info("[Risk] 분석용 엑셀 저장 완료 - path={}", OUTPUT_PATH.toAbsolutePath());
        } catch (IOException e) {
            log.error("[Risk] 분석용 엑셀 저장 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 엑셀 컬럼 헤더를 작성한다.
     */
    private void writeHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] columns = {
                "\uC0DD\uC131\uC2DC\uAC01",
                "\uC790\uCE58\uAD6C\uCF54\uB4DC",
                "\uC790\uCE58\uAD6C\uBA85",
                "\uC704\uD5D8\uB4F1\uAE09",
                "\uC704\uD5D8\uB3C4\uD37C\uC13C\uD2B8",
                "\uC628\uB3C4",
                "\uC628\uB3C4\uC810\uC218",
                "\uC2B5\uB3C4",
                "\uC2B5\uB3C4\uC810\uC218",
                "\uC870\uB3C4",
                "\uC870\uB3C4\uC810\uC218",
                "\uD558\uB298\uC0C1\uD0DC",
                "\uAC15\uC218\uD615\uD0DC",
                "\uD48D\uC18D(mph)",
                "\uD48D\uC18D\uC810\uC218",
                "\uAE30\uC0C1\uC9C0\uC218(W)",
                "\uC0B0\uB9BC\uBE44\uC728",
                "\uC11C\uC2DD\uC9C0\uACC4\uC218(G)",
                "\uAD50\uD1B5\uC810\uC218",
                "\uAD50\uD1B5\uACC4\uC218(V)",
                "\uCD5C\uC885\uC704\uD5D8\uC9C0\uC218(LORR)",
                "\uC778\uC2A4\uD0C0\uAC74\uC218",
                "\uC704\uB3C4",
                "\uACBD\uB3C4",
                "\uBA54\uBAA8"
        };

        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
        }
    }

    /**
     * 자치구별 값을 엑셀 데이터 행으로 작성한다.
     */
    private void writeRows(
            Sheet sheet,
            LocalDateTime generatedAt,
            List<RegionRiskResponse> regions,
            Map<SeoulDistrict, Double> habitatRatioByDistrict,
            Map<SeoulDistrict, Double> trafficScoreByDistrict
    ) {
        int rowIndex = 1;
        String generatedAtText = generatedAt.format(DATE_TIME_FORMATTER);

        for (RegionRiskResponse region : regions) {
            SeoulDistrict district = SeoulDistrict.valueOf(region.getDistrictCode());
            double forestRatio = habitatRatioByDistrict.getOrDefault(district, 0.0);
            double trafficScore = trafficScoreByDistrict.getOrDefault(district, 0.0);

            ExportRow exportRow = ExportRow.builder()
                    .generatedAt(generatedAtText)
                    .districtCode(region.getDistrictCode())
                    .regionName(region.getRegionName())
                    .riskLevel(region.getRiskLevel().name())
                    .riskPercent(region.getRiskPercent())
                    .temperature(region.getTemperature())
                    .temperatureScore(region.getTemperatureScore())
                    .humidity(region.getHumidity())
                    .humidityScore(region.getHumidityScore())
                    .illumination(region.getIllumination())
                    .illuminationScore(region.getIlluminationScore())
                    .sky(region.getSky().name())
                    .precipitationType(region.getPrecipitationType().name())
                    .windSpeed(toMph(region.getWindSpeed()))
                    .windScore(region.getWindScore())
                    .weatherIndex(region.getWeatherIndex())
                    .forestRatio(forestRatio)
                    .habitatFactor(region.getHabitatFactor())
                    .trafficScore(trafficScore)
                    .trafficFactor(region.getTrafficFactor())
                    .riskIndex(region.getRiskIndex())
                    .instaCnt(region.getInstaCnt())
                    .latitude(region.getLatitude())
                    .longitude(region.getLongitude())
                    .memo(buildMemo(forestRatio, trafficScore))
                    .build();

            Row row = sheet.createRow(rowIndex++);
            writeCell(row, 0, exportRow.generatedAt());
            writeCell(row, 1, exportRow.districtCode());
            writeCell(row, 2, exportRow.regionName());
            writeCell(row, 3, exportRow.riskLevel());
            writeCell(row, 4, exportRow.riskPercent());
            writeCell(row, 5, exportRow.temperature());
            writeCell(row, 6, exportRow.temperatureScore());
            writeCell(row, 7, exportRow.humidity());
            writeCell(row, 8, exportRow.humidityScore());
            writeCell(row, 9, exportRow.illumination());
            writeCell(row, 10, exportRow.illuminationScore());
            writeCell(row, 11, exportRow.sky());
            writeCell(row, 12, exportRow.precipitationType());
            writeCell(row, 13, exportRow.windSpeed());
            writeCell(row, 14, exportRow.windScore());
            writeCell(row, 15, exportRow.weatherIndex());
            writeCell(row, 16, exportRow.forestRatio());
            writeCell(row, 17, exportRow.habitatFactor());
            writeCell(row, 18, exportRow.trafficScore());
            writeCell(row, 19, exportRow.trafficFactor());
            writeCell(row, 20, exportRow.riskIndex());
            writeCell(row, 21, exportRow.instaCnt());
            writeCell(row, 22, exportRow.latitude());
            writeCell(row, 23, exportRow.longitude());
            writeCell(row, 24, exportRow.memo());
        }
    }

    /**
     * 원본 비율값을 빠르게 확인하기 위한 메모 문자열을 만든다.
     */
    private String buildMemo(double forestRatio, double trafficScore) {
        return String.format(Locale.ROOT, "forestRatio=%.4f, trafficScore=%.4f", forestRatio, trafficScore);
    }

    /**
     * 풍속을 m/s에서 mph로 변환한다.
     */
    private double toMph(double metersPerSecond) {
        return metersPerSecond * 2.23694;
    }

    /**
     * 문자열 셀 값을 기록한다.
     */
    private void writeCell(Row row, int columnIndex, String value) {
        row.createCell(columnIndex).setCellValue(value);
    }

    /**
     * 정수 셀 값을 기록한다.
     */
    private void writeCell(Row row, int columnIndex, int value) {
        row.createCell(columnIndex).setCellValue(value);
    }

    /**
     * 실수 셀 값을 기록한다.
     */
    private void writeCell(Row row, int columnIndex, double value) {
        row.createCell(columnIndex).setCellValue(value);
    }

    /**
     * 보기 좋게 컬럼 너비를 자동 조정한다.
     */
    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    @Builder
    private record ExportRow(
            String generatedAt,
            String districtCode,
            String regionName,
            String riskLevel,
            int riskPercent,
            double temperature,
            int temperatureScore,
            double humidity,
            int humidityScore,
            double illumination,
            int illuminationScore,
            String sky,
            String precipitationType,
            double windSpeed,
            int windScore,
            double weatherIndex,
            double forestRatio,
            double habitatFactor,
            double trafficScore,
            double trafficFactor,
            double riskIndex,
            int instaCnt,
            double latitude,
            double longitude,
            String memo
    ) {
    }
}
