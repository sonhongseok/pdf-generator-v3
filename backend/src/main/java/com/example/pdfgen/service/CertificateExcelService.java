// backend/src/main/java/com/example/pdfgen/service/CertificateExcelService.java
package com.example.pdfgen.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class CertificateExcelService {

    private static final int SERIAL_NO_COLUMN_INDEX = 0;
    private static final int FIRST_SHEET_INDEX = 0;

    private static final Set<String> EXACT_HEADER_KEYWORDS = Set.of(
            "serial",
            "serialno",
            "serialnumber",
            "시리얼",
            "시리얼번호",
            "시리얼넘버",
            "sn",
            "s/n",
            "no",
            "번호",
            "a10",
            "a10시리얼",
            "a10시리얼번호"
    );

    private static final List<String> HEADER_SUFFIXES = List.of(
            "시리얼",
            "시리얼번호",
            "시리얼넘버",
            "serial",
            "serialno",
            "serialnumber"
    );

    public List<String> parseSerialNumbers(MultipartFile excelFile) throws Exception {
        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException("업로드된 엑셀 파일이 비어 있습니다.");
        }

        List<String> serialNumbers = new ArrayList<>();
        DataFormatter dataFormatter = new DataFormatter();

        try (InputStream inputStream = excelFile.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("엑셀 파일에 시트가 존재하지 않습니다.");
            }

            Sheet sheet = workbook.getSheetAt(FIRST_SHEET_INDEX);
            int firstRowNum = sheet.getFirstRowNum();
            int lastRowNum = sheet.getLastRowNum();

            if (firstRowNum < 0 || lastRowNum < 0) {
                throw new IllegalArgumentException("엑셀 시트에 데이터가 존재하지 않습니다.");
            }

            FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
            boolean isFirstValidRow = true;

            for (int rowIndex = firstRowNum; rowIndex <= lastRowNum; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                Cell cell = row.getCell(SERIAL_NO_COLUMN_INDEX);
                if (cell == null) {
                    continue;
                }

                String cellValue = dataFormatter.formatCellValue(cell, formulaEvaluator).trim();
                if (cellValue.isEmpty()) {
                    continue;
                }

                if (isFirstValidRow) {
                    isFirstValidRow = false;
                    if (isHeaderKeyword(cellValue)) {
                        continue;
                    }
                }

                serialNumbers.add(cellValue);
            }
        }

        if (serialNumbers.isEmpty()) {
            throw new IllegalArgumentException("엑셀 파일의 A열에서 유효한 Serial No를 찾을 수 없습니다.");
        }

        return serialNumbers;
    }

    private boolean isHeaderKeyword(String text) {
        String normalized = text.toLowerCase()
                .replaceAll("[^a-z0-9가-힣]", "");
        if (EXACT_HEADER_KEYWORDS.contains(normalized)) {
            return true;
        }
        for (String suffix : HEADER_SUFFIXES) {
            if (normalized.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
