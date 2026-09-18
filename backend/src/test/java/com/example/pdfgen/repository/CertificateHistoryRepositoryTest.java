// backend/src/test/java/com/example/pdfgen/repository/CertificateHistoryRepositoryTest.java
package com.example.pdfgen.repository;

import com.example.pdfgen.domain.CertificateHistory;
import com.example.pdfgen.domain.CertificateSerialMapping;
import com.example.pdfgen.dto.CertificateHistoryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CertificateHistoryRepository (JSON 파일 기반) 단위 테스트.
 * @TempDir를 사용해 테스트마다 독립적인 임시 폴더를 사용하므로 부작용이 없습니다.
 */
class CertificateHistoryRepositoryTest {

    @TempDir
    Path tempDir;

    private CertificateHistoryRepository historyRepository;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        JsonDataStore dataStore = new JsonDataStore(objectMapper);
        java.lang.reflect.Field field = JsonDataStore.class.getDeclaredField("dataFilePath");
        field.setAccessible(true);
        field.set(dataStore, tempDir.resolve("history.json").toString());
        dataStore.initialize();

        historyRepository = new CertificateHistoryRepository(dataStore);
    }

    @Test
    @DisplayName("성적서 이력 및 시리얼 매핑이 정상적으로 저장되고 조회된다")
    void saveAndFindTest() {
        CertificateHistory history = new CertificateHistory();
        history.setCertificateNo("OP202605210001");
        history.setCertificateDate(LocalDate.of(2026, 5, 21));
        history.setCalibrationDate(LocalDate.of(2026, 5, 20));
        history.setExpiryDate(LocalDate.of(2027, 5, 20));

        CertificateSerialMapping mapping1 = new CertificateSerialMapping("SN001", 1, 1);
        CertificateSerialMapping mapping2 = new CertificateSerialMapping("SN002", 2, 2);
        history.addSerialMapping(mapping1);
        history.addSerialMapping(mapping2);

        CertificateHistory savedHistory = historyRepository.save(history);

        assertThat(savedHistory.getId()).isNotNull();
        assertThat(savedHistory.getSerialMappings()).hasSize(2);
        assertThat(savedHistory.getSerialMappings().get(0).getSerialNo()).isEqualTo("SN001");

        Optional<CertificateHistory> found = historyRepository.findById(savedHistory.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getCertificateNo()).isEqualTo("OP202605210001");
    }

    @Test
    @DisplayName("발행일, 교정일, 만료일로 성적서를 정확히 조회할 수 있다")
    void findByDatesTest() {
        LocalDate certDate = LocalDate.of(2026, 5, 21);
        LocalDate calDate = LocalDate.of(2026, 5, 20);
        LocalDate expDate = LocalDate.of(2027, 5, 20);

        CertificateHistory history1 = new CertificateHistory("OP202605210001", certDate, calDate, expDate);
        CertificateHistory history2 = new CertificateHistory("OP202605210002", certDate, calDate, expDate);
        CertificateHistory history3 = new CertificateHistory("OP202605220001",
                LocalDate.of(2026, 5, 22), calDate, expDate);

        historyRepository.save(history1);
        historyRepository.save(history2);
        historyRepository.save(history3);

        List<CertificateHistory> foundList =
                historyRepository.findByCertificateDateAndCalibrationDateAndExpiryDate(certDate, calDate, expDate);

        assertThat(foundList).hasSize(2);
        assertThat(foundList).extracting("certificateNo")
                .containsExactlyInAnyOrder("OP202605210001", "OP202605210002");
    }

    @Test
    @DisplayName("중복된 Certificate NO 존재 여부를 정확히 판별한다")
    void existsByCertificateNoTest() {
        CertificateHistory history = new CertificateHistory("OP202605210001",
                LocalDate.of(2026, 5, 21), LocalDate.of(2026, 5, 20), LocalDate.of(2027, 5, 20));
        historyRepository.save(history);

        assertThat(historyRepository.existsByCertificateNo("OP202605210001")).isTrue();
        assertThat(historyRepository.existsByCertificateNo("OP202605210999")).isFalse();
    }

    @Test
    @DisplayName("전체 이력을 최신순으로 조회한다")
    void findAllOrderByCreatedDateDescTest() {
        CertificateHistory history1 = new CertificateHistory("OP202605210001",
                LocalDate.of(2026, 5, 21), LocalDate.of(2026, 5, 20), LocalDate.of(2027, 5, 20));
        history1.setCreatedDate(LocalDateTime.of(2026, 5, 21, 10, 0, 0));

        CertificateHistory history2 = new CertificateHistory("OP202605220001",
                LocalDate.of(2026, 5, 22), LocalDate.of(2026, 5, 20), LocalDate.of(2027, 5, 20));
        history2.setCreatedDate(LocalDateTime.of(2026, 5, 21, 11, 0, 0));

        historyRepository.save(history1);
        historyRepository.save(history2);

        List<CertificateHistory> allHistories = historyRepository.findAllByOrderByCreatedDateDesc();

        assertThat(allHistories).hasSize(2);
        assertThat(allHistories.get(0).getCertificateNo()).isEqualTo("OP202605220001");
    }

    @Test
    @DisplayName("createdDate가 null인 레코드가 섞여 있어도 NPE 없이 안전하게 최하단으로 정렬된다")
    void findAllWithNullCreatedDateTest() {
        CertificateHistory validHistory = new CertificateHistory("OP202605210001",
                LocalDate.of(2026, 5, 21), LocalDate.of(2026, 5, 20), LocalDate.of(2027, 5, 20));
        validHistory.setCreatedDate(LocalDateTime.of(2026, 5, 21, 10, 0, 0));

        CertificateHistory nullDateHistory = new CertificateHistory("OP202605210002",
                LocalDate.of(2026, 5, 21), LocalDate.of(2026, 5, 20), LocalDate.of(2027, 5, 20));
        nullDateHistory.setCreatedDate(null);

        historyRepository.save(validHistory);
        historyRepository.save(nullDateHistory);

        List<CertificateHistory> allHistories = historyRepository.findAllByOrderByCreatedDateDesc();

        assertThat(allHistories).hasSize(2);
        assertThat(allHistories.get(0).getCertificateNo()).isEqualTo("OP202605210001");
        assertThat(allHistories.get(1).getCertificateNo()).isEqualTo("OP202605210002");
    }

    @Test
    @DisplayName("대규모 200건 시리얼 매핑 이력의 조회 및 DTO 변환 시 순서가 정확히 보장된다")
    void largeSerialMappingResponseMappingTest() {
        CertificateHistory history = new CertificateHistory("OP202609170001",
                LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 16), LocalDate.of(2027, 9, 16));
        history.setCreatedDate(LocalDateTime.of(2026, 9, 17, 9, 30, 0));

        List<CertificateSerialMapping> mappings = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            mappings.add(new CertificateSerialMapping(String.format("SN-%04d", i), i, i));
        }
        history.setSerialMappings(mappings);
        historyRepository.save(history);

        List<CertificateHistoryResponse> responses = historyRepository.findAllByOrderByCreatedDateDesc()
                .stream()
                .map(CertificateHistoryResponse::new)
                .collect(Collectors.toList());

        assertThat(responses).hasSize(1);
        CertificateHistoryResponse response = responses.get(0);
        assertThat(response.getSerialNos()).hasSize(200);
        assertThat(response.getSerialNos().get(0)).isEqualTo("SN-0001");
        assertThat(response.getSerialNos().get(199)).isEqualTo("SN-0200");
    }
}
