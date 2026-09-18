// backend/src/main/java/com/example/pdfgen/controller/CertificateExcelController.java
package com.example.pdfgen.controller;

import com.example.pdfgen.service.CertificateExcelService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents/certificates")
public class CertificateExcelController {

    private final CertificateExcelService certificateExcelService;

    public CertificateExcelController(CertificateExcelService certificateExcelService) {
        this.certificateExcelService = certificateExcelService;
    }

    @PostMapping({"/parse-excel", "/pdf/parse-excel"})
    public ResponseEntity<?> parseExcelSerialNumbers(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "업로드할 엑셀 파일을 선택해 주세요.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || (!originalFilename.toLowerCase().endsWith(".xlsx") && !originalFilename.toLowerCase().endsWith(".xls"))) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "엑셀 파일(.xlsx, .xls)만 업로드 가능합니다.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        try {
            List<String> serialNumbers = certificateExcelService.parseSerialNumbers(file);
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("success", true);
            successResponse.put("serialNos", serialNumbers);
            successResponse.put("count", serialNumbers.size());
            return ResponseEntity.ok(successResponse);
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "엑셀 파일 처리 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}