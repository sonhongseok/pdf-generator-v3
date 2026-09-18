// backend/src/main/java/com/example/pdfgen/controller/DashboardController.java
package com.example.pdfgen.controller;

import com.example.pdfgen.domain.CertificateHistory;
import com.example.pdfgen.repository.CertificateHistoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 발급 현황 대시보드 통계 API 컨트롤러.
 * GET /api/dashboard/stats 단일 엔드포인트로 프런트엔드에 필요한 모든 통계를 반환합니다.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final int RECENT_MONTHS_COUNT = 6;
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("M월");

    private final CertificateHistoryRepository certificateHistoryRepository;

    public DashboardController(CertificateHistoryRepository certificateHistoryRepository) {
        this.certificateHistoryRepository = certificateHistoryRepository;
    }

    /**
     * 대시보드에 필요한 전체 통계를 반환합니다.
     * - 요약 카드: 총 발급 건수, 총 시리얼 수, 이번달 발급 건수, 오늘 발급 건수
     * - 월별 발급 건수 차트 데이터 (최근 6개월)
     * - 월별 시리얼 수 차트 데이터 (최근 6개월)
     */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStats> getStats() {
        List<CertificateHistory> allHistories = certificateHistoryRepository.findAllByOrderByCreatedDateDesc();

        SummaryCards summary = buildSummaryCards(allHistories);
        List<MonthlyData> monthlyData = buildMonthlyData(allHistories);

        return ResponseEntity.ok(new DashboardStats(summary, monthlyData));
    }

    private SummaryCards buildSummaryCards(List<CertificateHistory> histories) {
        long totalIssuances = histories.size();
        long totalSerials = histories.stream()
                .mapToLong(h -> h.getSerialMappings().size())
                .sum();

        LocalDate today = LocalDate.now();
        String thisMonth = today.format(MONTH_FORMATTER);

        long thisMonthIssuances = histories.stream()
                .filter(h -> h.getCertificateDate() != null
                        && h.getCertificateDate().format(MONTH_FORMATTER).equals(thisMonth))
                .count();

        long todayIssuances = histories.stream()
                .filter(h -> today.equals(h.getCertificateDate()))
                .count();

        return new SummaryCards(totalIssuances, totalSerials, thisMonthIssuances, todayIssuances);
    }

    private List<MonthlyData> buildMonthlyData(List<CertificateHistory> histories) {
        LocalDate today = LocalDate.now();
        List<String> monthKeys = new ArrayList<>();
        for (int i = RECENT_MONTHS_COUNT - 1; i >= 0; i--) {
            monthKeys.add(today.minusMonths(i).format(MONTH_FORMATTER));
        }

        Map<String, List<CertificateHistory>> groupedByMonth = histories.stream()
                .filter(h -> h.getCertificateDate() != null)
                .collect(Collectors.groupingBy(
                        h -> h.getCertificateDate().format(MONTH_FORMATTER)
                ));

        return monthKeys.stream().map(monthKey -> {
            List<CertificateHistory> monthHistories = groupedByMonth.getOrDefault(monthKey, List.of());
            long issuanceCount = monthHistories.size();
            long serialCount = monthHistories.stream()
                    .mapToLong(h -> h.getSerialMappings().size())
                    .sum();

            LocalDate monthDate = LocalDate.parse(monthKey + "-01");
            String label = monthDate.format(DISPLAY_FORMATTER);

            return new MonthlyData(monthKey, label, issuanceCount, serialCount);
        }).collect(Collectors.toList());
    }

    // ─── 응답 DTO ────────────────────────────────────────────────────────

    public record DashboardStats(SummaryCards summary, List<MonthlyData> monthlyData) {}

    public record SummaryCards(
            long totalIssuances,
            long totalSerials,
            long thisMonthIssuances,
            long todayIssuances
    ) {}

    public record MonthlyData(
            String monthKey,
            String label,
            long issuanceCount,
            long serialCount
    ) {}
}
