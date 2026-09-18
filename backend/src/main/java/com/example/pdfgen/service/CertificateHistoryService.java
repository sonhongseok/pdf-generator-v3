// backend/src/main/java/com/example/pdfgen/service/CertificateHistoryService.java
package com.example.pdfgen.service;

import com.example.pdfgen.domain.CertificateHistory;
import com.example.pdfgen.domain.CertificateSerialMapping;
import com.example.pdfgen.repository.CertificateHistoryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class CertificateHistoryService {

    private final CertificateHistoryRepository certificateHistoryRepository;

    public CertificateHistoryService(CertificateHistoryRepository certificateHistoryRepository) {
        this.certificateHistoryRepository = certificateHistoryRepository;
    }

    /**
     * 파일 저장 전용 메서드 (PDF 생성 성공 후에만 호출됨).
     * JSON 파일 기반이므로 @Transactional 어노테이션은 불필요합니다.
     */
    public void saveToDatabase(String finalCertificateNo, LocalDate certDate, LocalDate calDate,
                               LocalDate expDate, List<String> serialNos, List<Integer> sequenceNos) {
        CertificateHistory history = new CertificateHistory(finalCertificateNo, certDate, calDate, expDate);

        for (int i = 0; i < serialNos.size(); i++) {
            String serialNo = serialNos.get(i);
            int seqNo = sequenceNos.get(i);
            int pageNum = i + 1; // 1-indexed page number
            CertificateSerialMapping mapping = new CertificateSerialMapping(serialNo, pageNum, seqNo);
            history.addSerialMapping(mapping);
        }

        certificateHistoryRepository.save(history);
    }
}
