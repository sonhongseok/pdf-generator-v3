// backend/src/test/java/com/example/pdfgen/service/CertificateExcelServiceEdgeCaseTest.java
package com.example.pdfgen.service;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 엑셀 파싱 서비스 엣지 케이스 단위 테스트.
 * (HSSF .xls 구형 포맷, 수식 셀, 1,000건 대량 파싱, 빈 셀 등)
 */
class CertificateExcelServiceEdgeCaseTest {

    private final CertificateExcelService service = new CertificateExcelService();

    @Test
    @DisplayName("구형 바이너리 엑셀(.xls, HSSF 포맷) 파일도 정상적으로 파싱된다")
    void parseLegacyHssfExcelTest() throws Exception {
        byte[] xlsBytes;
        try (Workbook workbook = new HSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("시리얼번호");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("XLS-001");
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("XLS-002");

            workbook.write(bos);
            xlsBytes = bos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "legacy.xls", "application/vnd.ms-excel", xlsBytes
        );

        List<String> serials = service.parseSerialNumbers(file);

        assertThat(serials).containsExactly("XLS-001", "XLS-002");
    }

    @Test
    @DisplayName("수식(Formula) 셀이 포함된 경우 계산 결과 문자열을 정상 추출한다")
    void parseExcelWithFormulaCellsTest() throws Exception {
        byte[] excelBytes;
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("FormulaSheet");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Serial");

            Row row1 = sheet.createRow(1);
            Cell cell1 = row1.createCell(0);
            cell1.setCellFormula("CONCATENATE(\"CALC-\", \"100\")");

            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(bos);
            excelBytes = bos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "formula.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes
        );

        List<String> serials = service.parseSerialNumbers(file);

        assertThat(serials).hasSize(1);
        assertThat(serials.get(0)).isEqualTo("CALC-100");
    }

    @Test
    @DisplayName("1,000건의 대규모 시리얼 데이터도 고속으로 누락 없이 순서대로 추출한다")
    void parseLargeAmountOfSerialsTest() throws Exception {
        final int TOTAL_ITEMS = 1000;
        List<String> expectedSerials = new ArrayList<>();
        byte[] excelBytes;

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("LargeData");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("시리얼 넘버");

            for (int i = 1; i <= TOTAL_ITEMS; i++) {
                String serialValue = String.format("SN-BULK-%04d", i);
                expectedSerials.add(serialValue);
                Row row = sheet.createRow(i);
                row.createCell(0).setCellValue(serialValue);
            }

            workbook.write(bos);
            excelBytes = bos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "large.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes
        );

        long startTime = System.currentTimeMillis();
        List<String> serials = service.parseSerialNumbers(file);
        long elapsed = System.currentTimeMillis() - startTime;

        assertThat(serials).hasSize(TOTAL_ITEMS);
        assertThat(serials.get(0)).isEqualTo("SN-BULK-0001");
        assertThat(serials.get(TOTAL_ITEMS - 1)).isEqualTo(String.format("SN-BULK-%04d", TOTAL_ITEMS));
        assertThat(serials).isEqualTo(expectedSerials);
        assertThat(elapsed).isLessThan(5000); // 5초 이내 고속 처리
    }

    @Test
    @DisplayName("A열이 모두 비어 있거나 유효한 시리얼이 없을 때 IllegalArgumentException이 발생한다")
    void parseExcelAllBlankThrowsExceptionTest() throws Exception {
        byte[] excelBytes;
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("EmptySheet");
            Row row0 = sheet.createRow(0);
            row0.createCell(0).setCellValue("시리얼번호");
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("   ");
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("");

            workbook.write(bos);
            excelBytes = bos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "all_blanks.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes
        );

        assertThrows(IllegalArgumentException.class, () -> service.parseSerialNumbers(file));
    }
}
