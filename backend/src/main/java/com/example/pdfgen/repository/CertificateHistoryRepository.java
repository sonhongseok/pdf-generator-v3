// backend/src/main/java/com/example/pdfgen/repository/CertificateHistoryRepository.java
package com.example.pdfgen.repository;

import com.example.pdfgen.domain.CertificateHistory;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JSON 파일 기반 CertificateHistory 레포지토리.
 *
 * 기존 JpaRepository 메서드 시그니처를 동일하게 유지하여
 * 컨트롤러/서비스 레이어의 변경을 최소화합니다.
 */
@Repository
public class CertificateHistoryRepository {

    private final JsonDataStore jsonDataStore;

    public CertificateHistoryRepository(JsonDataStore jsonDataStore) {
        this.jsonDataStore = jsonDataStore;
    }

    /**
     * 최종 Certificate NO의 중복 등록 여부를 체크합니다.
     */
    public boolean existsByCertificateNo(String certificateNo) {
        return jsonDataStore.readAll().stream()
                .anyMatch(history -> certificateNo.equals(history.getCertificateNo()));
    }

    /**
     * 특정 날짜(접두사)로 발급된 성적서 중 가장 마지막(사전 순 최대) 이력을 반환합니다.
     * 시리얼 개수에 따른 다음 발급 번호 계산에 사용됩니다.
     */
    public Optional<CertificateHistory> findTopByCertificateNoStartingWithOrderByCertificateNoDesc(String prefix) {
        return jsonDataStore.readAll().stream()
                .filter(history -> history.getCertificateNo() != null
                        && history.getCertificateNo().startsWith(prefix))
                .max(Comparator.comparing(CertificateHistory::getCertificateNo));
    }

    /**
     * 발행일 + 교정일 + 만료일이 모두 동일한 발급 이력 목록을 반환합니다.
     * 4가지 입력값 완전 중복 여부 검사에 사용됩니다.
     */
    public List<CertificateHistory> findByCertificateDateAndCalibrationDateAndExpiryDate(
            LocalDate certificateDate, LocalDate calibrationDate, LocalDate expiryDate) {
        return jsonDataStore.readAll().stream()
                .filter(history -> certificateDate.equals(history.getCertificateDate())
                        && calibrationDate.equals(history.getCalibrationDate())
                        && expiryDate.equals(history.getExpiryDate()))
                .collect(Collectors.toList());
    }

    /**
     * 모든 발급 이력을 최신순(createdDate 내림차순, null은 최하단)으로 반환합니다.
     */
    public List<CertificateHistory> findAllByOrderByCreatedDateDesc() {
        return jsonDataStore.readAll().stream()
                .sorted(Comparator.comparing(CertificateHistory::getCreatedDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    /**
     * ID로 특정 발급 이력을 조회합니다.
     */
    public Optional<CertificateHistory> findById(Long id) {
        return jsonDataStore.readAll().stream()
                .filter(history -> id.equals(history.getId()))
                .findFirst();
    }

    /**
     * 발급 이력을 저장합니다.
     * - ID가 없으면 신규 저장 (자동 ID 채번)
     * - ID가 있으면 기존 데이터 업데이트
     */
    public CertificateHistory save(CertificateHistory history) {
        jsonDataStore.update(histories -> {
            if (history.getId() == null) {
                // 신규 저장: 다음 ID 채번 후 추가
                long nextId = jsonDataStore.generateNextId(histories);
                history.setId(nextId);
                histories.add(history);
            } else {
                // 업데이트: 기존 항목 교체
                boolean updated = false;
                for (int i = 0; i < histories.size(); i++) {
                    if (history.getId().equals(histories.get(i).getId())) {
                        histories.set(i, history);
                        updated = true;
                        break;
                    }
                }
                if (!updated) {
                    histories.add(history);
                }
            }
        });

        return history;
    }
}
