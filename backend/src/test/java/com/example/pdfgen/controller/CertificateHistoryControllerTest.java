// backend/src/test/java/com/example/pdfgen/controller/CertificateHistoryControllerTest.java
package com.example.pdfgen.controller;

import com.example.pdfgen.domain.CertificateHistory;
import com.example.pdfgen.domain.CertificateSerialMapping;
import com.example.pdfgen.repository.CertificateHistoryRepository;
import com.example.pdfgen.service.CertificateHistoryService;
import com.example.pdfgen.service.DocxTemplateService;
import com.example.pdfgen.service.MsWordPdfConverter;
import com.example.pdfgen.service.PdfJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 발급이력 조회 및 재다운로드 엔드포인트 전용 MockMvc 테스트.
 */
@WebMvcTest(CertificateController.class)
class CertificateHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    @DisplayName("발급 이력 목록 조회 시 정상 데이터와 시리얼 번호 목록이 최신순으로 반환된다")
    void getCertificateHistoriesSuccessTest() throws Exception {
        CertificateHistory history1 = new CertificateHistory("OP202609170001",
                LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 16), LocalDate.of(2027, 9, 16));
        history1.setId(1L);
        history1.setCreatedDate(LocalDateTime.of(2026, 9, 17, 10, 0, 0));
        history1.addSerialMapping(new CertificateSerialMapping("SN-001", 1, 1));
        history1.addSerialMapping(new CertificateSerialMapping("SN-002", 2, 2));

        Mockito.when(historyRepository.findAllByOrderByCreatedDateDesc())
                .thenReturn(Collections.singletonList(history1));

        mockMvc.perform(get("/api/documents/certificates")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].certificateNo").value("OP202609170001"))
                .andExpect(jsonPath("$[0].serialNos.length()").value(2))
                .andExpect(jsonPath("$[0].serialNos[0]").value("SN-001"))
                .andExpect(jsonPath("$[0].serialNos[1]").value("SN-002"));
    }

    @Test
    @DisplayName("발급 이력이 없을 때 빈 배열(200 OK)을 반환한다")
    void getCertificateHistoriesEmptyTest() throws Exception {
        Mockito.when(historyRepository.findAllByOrderByCreatedDateDesc())
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/documents/certificates")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("발급 이력 재다운로드 비동기 요청 시 jobId가 정상 반환된다")
    void requestAsyncReDownloadSuccessTest() throws Exception {
        CertificateHistory history = new CertificateHistory("OP202609170001",
                LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 16), LocalDate.of(2027, 9, 16));
        history.setId(1L);
        history.addSerialMapping(new CertificateSerialMapping("SN-001", 1, 1));

        Mockito.when(historyRepository.findById(1L)).thenReturn(Optional.of(history));
        Mockito.when(pdfJobService.submitReDownloadJob(
                anyString(), anyString(), anyString(), anyString(), anyList(), anyList(), anyString()))
                .thenReturn("job-test-123");

        mockMvc.perform(post("/api/documents/certificates/1/download/request?mode=MERGED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-test-123"));
    }

    @Test
    @DisplayName("존재하지 않는 이력 ID로 재다운로드 요청 시 404 에러를 반환한다")
    void requestAsyncReDownloadNotFoundTest() throws Exception {
        Mockito.when(historyRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/documents/certificates/999/download/request?mode=MERGED"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("해당 발급 이력을 찾을 수 없습니다."));
    }
}
