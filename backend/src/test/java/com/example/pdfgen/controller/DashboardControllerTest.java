// backend/src/test/java/com/example/pdfgen/controller/DashboardControllerTest.java
package com.example.pdfgen.controller;

import com.example.pdfgen.domain.CertificateHistory;
import com.example.pdfgen.domain.CertificateSerialMapping;
import com.example.pdfgen.repository.CertificateHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
public class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CertificateHistoryRepository certificateHistoryRepository;

    @Test
    @DisplayName("이력이 없는 초기 상태일 때 모든 통계 수치는 0이며 6개월 월별 데이터는 정상 반환된다")
    void getStats_emptyData_returnsZeroSummaryAndSixMonths() throws Exception {
        given(certificateHistoryRepository.findAllByOrderByCreatedDateDesc())
                .willReturn(Collections.emptyList());

        mockMvc.perform(get("/api/dashboard/stats")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalIssuances", is(0)))
                .andExpect(jsonPath("$.summary.totalSerials", is(0)))
                .andExpect(jsonPath("$.summary.thisMonthIssuances", is(0)))
                .andExpect(jsonPath("$.summary.todayIssuances", is(0)))
                .andExpect(jsonPath("$.monthlyData", hasSize(6)))
                .andExpect(jsonPath("$.monthlyData[5].issuanceCount", is(0)))
                .andExpect(jsonPath("$.monthlyData[5].serialCount", is(0)));
    }

    @Test
    @DisplayName("이력이 존재할 때 요약 카드 수치와 월별 통계가 정확하게 집계된다")
    void getStats_withData_calculatesCorrectStatistics() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate lastMonth = today.minusMonths(1);

        CertificateHistory todayHistory = new CertificateHistory(
                "OP202609220001", today, today, today.plusYears(1)
        );
        todayHistory.setId(1L);
        todayHistory.addSerialMapping(new CertificateSerialMapping("SN-01", 1, 1));
        todayHistory.addSerialMapping(new CertificateSerialMapping("SN-02", 2, 2));

        CertificateHistory lastMonthHistory = new CertificateHistory(
                "OP202608220001", lastMonth, lastMonth, lastMonth.plusYears(1)
        );
        lastMonthHistory.setId(2L);
        lastMonthHistory.setCreatedDate(LocalDateTime.now().minusMonths(1));
        lastMonthHistory.addSerialMapping(new CertificateSerialMapping("SN-03", 1, 1));

        given(certificateHistoryRepository.findAllByOrderByCreatedDateDesc())
                .willReturn(List.of(todayHistory, lastMonthHistory));

        mockMvc.perform(get("/api/dashboard/stats")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalIssuances", is(2)))
                .andExpect(jsonPath("$.summary.totalSerials", is(3)))
                .andExpect(jsonPath("$.summary.thisMonthIssuances", is(1)))
                .andExpect(jsonPath("$.summary.todayIssuances", is(1)))
                .andExpect(jsonPath("$.monthlyData", hasSize(6)))
                .andExpect(jsonPath("$.monthlyData[5].issuanceCount", is(1)))
                .andExpect(jsonPath("$.monthlyData[5].serialCount", is(2)))
                .andExpect(jsonPath("$.monthlyData[4].issuanceCount", is(1)))
                .andExpect(jsonPath("$.monthlyData[4].serialCount", is(1)));
    }
}
