// backend/src/test/java/com/example/pdfgen/service/CertificateExcelServiceTest.java
package com.example.pdfgen.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CertificateExcelServiceTest {

    private final CertificateExcelService service = new CertificateExcelService();

    @Test
    @DisplayName("한글 헤더(시리얼번호)가 있는 경우 1행을 건너뛰고 2행부터 정상 추출한다")
    void parseExcelWithKoreanHeaderTest() throws Exception {
        byte[] excelBytes = createExcelBytes(List.of("시리얼번호", "2813C4", "28017F"));
        MockMultipartFile file = new MockMultipartFile("file", "test_korean.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        List<String> serials = service.parseSerialNumbers(file);

        assertThat(serials).containsExactly("2813C4", "28017F");
    }

    @Test
    @DisplayName("모델명 헤더(A10 또는 A10 시리얼)가 있는 경우 1행을 건너뛰고 추출한다")
    void parseExcelWithModelHeaderTest() throws Exception {
        byte[] excelBytes1 = createExcelBytes(List.of("A10", "2813C4", "28017F"));
        MockMultipartFile file1 = new MockMultipartFile("file", "test_a10.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes1);
        assertThat(service.parseSerialNumbers(file1)).containsExactly("2813C4", "28017F");

        byte[] excelBytes2 = createExcelBytes(List.of("A10 시리얼번호", "280C39", "28138D"));
        MockMultipartFile file2 = new MockMultipartFile("file", "test_a10_serial.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes2);
        assertThat(service.parseSerialNumbers(file2)).containsExactly("280C39", "28138D");
    }

    @Test
    @DisplayName("영문 헤더(Serial No)가 있는 경우 1행을 건너뛰고 추출한다")
    void parseExcelWithEnglishHeaderTest() throws Exception {
        byte[] excelBytes = createExcelBytes(List.of("Serial No", "SN-2026-001", "SN-2026-002"));
        MockMultipartFile file = new MockMultipartFile("file", "test_en.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        List<String> serials = service.parseSerialNumbers(file);

        assertThat(serials).containsExactly("SN-2026-001", "SN-2026-002");
    }

    @Test
    @DisplayName("헤더 없이 1행부터 실제 시리얼(2813C4 등)이 시작되는 경우 1행을 누락 없이 포함한다")
    void parseExcelWithoutHeaderTest() throws Exception {
        byte[] excelBytes = createExcelBytes(List.of("2813C4", "28017F", "280C39"));
        MockMultipartFile file = new MockMultipartFile("file", "test_no_header.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        List<String> serials = service.parseSerialNumbers(file);

        assertThat(serials).containsExactly("2813C4", "28017F", "280C39");
    }

    @Test
    @DisplayName("중간에 빈 행이나 공백 셀이 섞여 있어도 유효한 시리얼만 정상 추출한다")
    void parseExcelWithBlankRowsTest() throws Exception {
        byte[] excelBytes = createExcelBytes(List.of("시리얼", "2813C4", "   ", "28017F", ""));
        MockMultipartFile file = new MockMultipartFile("file", "test_blanks.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        List<String> serials = service.parseSerialNumbers(file);

        assertThat(serials).containsExactly("2813C4", "28017F");
    }

    @Test
    @DisplayName("숫자 형식의 시리얼 번호도 지수 표기 없이 문자열로 정상 추출한다")
    void parseExcelWithNumericValuesTest() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Serials");
            Row row0 = sheet.createRow(0);
            row0.createCell(0).setCellValue("시리얼번호");
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue(10001.0);
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue(10002.0);
            workbook.write(bos);

            MockMultipartFile file = new MockMultipartFile("file", "numeric.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
            List<String> serials = service.parseSerialNumbers(file);

            assertThat(serials).containsExactly("10001", "10002");
        }
    }

    @Test
    @DisplayName("빈 파일인 경우 예외가 발생한다")
    void parseEmptyFileTest() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> service.parseSerialNumbers(emptyFile));
    }

    private byte[] createExcelBytes(List<String> values) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            for (int i = 0; i < values.size(); i++) {
                Row row = sheet.createRow(i);
                row.createCell(0).setCellValue(values.get(i));
            }
            workbook.write(bos);
            return bos.toByteArray();
        }
    }
}