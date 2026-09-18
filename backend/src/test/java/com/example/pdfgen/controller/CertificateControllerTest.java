// backend/src/test/java/com/example/pdfgen/controller/CertificateControllerTest.java
package com.example.pdfgen.controller;

import com.example.pdfgen.domain.CertificateHistory;
import com.example.pdfgen.domain.CertificateSerialMapping;
import com.example.pdfgen.dto.CertificateRequest;
import com.example.pdfgen.repository.CertificateHistoryRepository;
import com.example.pdfgen.service.CertificateHistoryService;
import com.example.pdfgen.service.DocxTemplateService;
import com.example.pdfgen.service.MsWordPdfConverter;
import com.example.pdfgen.service.PdfJobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CertificateController.class)
class CertificateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CertificateHistoryRepository historyRepository;

    @MockBean
    private CertificateHistoryService certificateHistoryService;

    @MockBean
    private DocxTemplateService docxTemplateService;

    @MockBean
    private MsWordPdfConverter pdfConverter;

    @MockBean
    private PdfJobService pdfJobService;

    @Test
    @DisplayName("필수 파라미터 누락 시 400 Bad Request 에러 반환")
    void missingParameterTest() throws Exception {
        // given: 날짜 누락
        CertificateRequest request = new CertificateRequest();
        request.setExpiryDate("2027-05-20");
        request.setSerialNos(Collections.singletonList("SN001"));

        // when & then
        mockMvc.perform(post("/api/documents/certificates/pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Certificate Date는 필수 선택 항목입니다."));
    }

    @Test
    @DisplayName("만료일이 발행일보다 과거인 경우 논리 오류 400 에러 반환")
    void expiryDateBeforeCertificateDateTest() throws Exception {
        // given
        CertificateRequest request = new CertificateRequest();
        request.setCertificateDate("2026-05-21");
        request.setCalibrationDate("2026-05-20");
        request.setExpiryDate("2025-05-21"); // 만료일이 과거
        request.setSerialNos(Collections.singletonList("SN001"));

        Mockito.when(historyRepository.findByCertificateDateAndCalibrationDateAndExpiryDate(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // when & then
        mockMvc.perform(post("/api/documents/certificates/pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("만료일(Expiry Date)은 발행일(Certificate Date)보다 빠를 수 없습니다."));
    }

    @Test
    @DisplayName("완전히 동일한 날짜와 시리얼 정보로 중복 발급 시도 시 400 에러 차단")
    void exactDuplicateBlockTest() throws Exception {
        // given
        CertificateRequest request = new CertificateRequest();
        request.setCertificateDate("2026-05-21");
        request.setCalibrationDate("2026-05-20");
        request.setExpiryDate("2027-05-21");
        request.setSerialNos(Arrays.asList("SN001", "SN002"));

        // 기존에 동일한 시리얼 세트를 가진 이력이 존재함
        CertificateHistory existingHistory = new CertificateHistory();
        existingHistory.addSerialMapping(new CertificateSerialMapping("SN001", 1, 1));
        existingHistory.addSerialMapping(new CertificateSerialMapping("SN002", 2, 2));

        Mockito.when(historyRepository.findByCertificateDateAndCalibrationDateAndExpiryDate(
                LocalDate.of(2026, 5, 21), LocalDate.of(2026, 5, 20), LocalDate.of(2027, 5, 21)
        )).thenReturn(Collections.singletonList(existingHistory));

        // when & then
        mockMvc.perform(post("/api/documents/certificates/pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("이미 발급된 성적서가 존재합니다")));
    }

    @Test
    @DisplayName("정상 입력 시 PDF 변환 후 바이너리를 반환하고 저장한다")
    void generatePdfSuccessTest() throws Exception {
        // given
        CertificateRequest request = new CertificateRequest();
        request.setCertificateDate("2026-05-21");
        request.setCalibrationDate("2026-05-20");
        request.setExpiryDate("2027-05-21");
        request.setSerialNos(Collections.singletonList("SN-NEW"));

        Mockito.when(historyRepository.findByCertificateDateAndCalibrationDateAndExpiryDate(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        byte[] fakePdfBytes = new byte[]{1, 2, 3, 4};
        Mockito.when(docxTemplateService.fillTemplate(anyMap())).thenReturn(new byte[]{5, 6});
        Mockito.when(pdfConverter.convertToPdf(any())).thenReturn(fakePdfBytes);
        Mockito.when(pdfConverter.mergePdfs(any())).thenReturn(fakePdfBytes);

        // when & then
        mockMvc.perform(post("/api/documents/certificates/pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().exists("Content-Disposition"));

        // saveToDatabase가 1회 호출되었는지 검증
        Mockito.verify(certificateHistoryService, Mockito.times(1))
                .saveToDatabase(anyString(), any(), any(), any(), anyList(), anyList());
    }
}