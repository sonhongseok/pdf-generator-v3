// backend/src/main/java/com/example/pdfgen/repository/JsonDataStore.java
package com.example.pdfgen.repository;

import com.example.pdfgen.domain.CertificateHistory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * JSON 파일을 단일 진입점으로 읽고 쓰는 데이터 저장소.
 *
 * - Spring이 관리하는 ObjectMapper를 주입받아 LocalDate/LocalDateTime 직렬화 설정을 공유합니다.
 * - ReentrantReadWriteLock으로 멀티스레드 동시성 안전 처리
 * - 전체 데이터를 List<CertificateHistory> 형태로 메모리에 올려 관리
 * - 쓰기 시 전체 파일을 덮어씁니다 (소규모 내부 도구에 적합)
 */
@Component
public class JsonDataStore {

    private static final Logger logger = LoggerFactory.getLogger(JsonDataStore.class);

    // Spring Boot가 자동 구성한 ObjectMapper (JavaTimeModule, WRITE_DATES_AS_TIMESTAMPS 설정 포함)
    private final ObjectMapper objectMapper;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @Value("${app.data.path:./data/history.json}")
    private String dataFilePath;

    private File dataFile;

    public JsonDataStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 애플리케이션 기동 시 데이터 파일 및 디렉토리를 준비합니다.
     */
    @PostConstruct
    public void initialize() {
        this.dataFile = new File(dataFilePath).getAbsoluteFile();
        File parentDir = dataFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (parentDir.mkdirs()) {
                logger.info("[JsonDataStore] 데이터 디렉토리 생성 완료: {}", parentDir.getAbsolutePath());
            } else {
                logger.error("[JsonDataStore] 데이터 디렉토리 생성 실패: {}", parentDir.getAbsolutePath());
            }
        }
        if (!dataFile.exists()) {
            try {
                objectMapper.writeValue(dataFile, new ArrayList<>());
                logger.info("[JsonDataStore] 빈 데이터 파일 생성 완료: {}", dataFile.getAbsolutePath());
            } catch (IOException ioException) {
                logger.error("[JsonDataStore] 데이터 파일 초기화 실패: {}", ioException.getMessage());
            }
        }
        logger.info("[JsonDataStore] 데이터 파일 경로: {}", dataFile.getAbsolutePath());
    }

    private void handleCorruptedFile() {
        if (dataFile.exists()) {
            File backupFile = new File(dataFile.getAbsolutePath() + ".corrupted." + System.currentTimeMillis());
            if (dataFile.renameTo(backupFile)) {
                logger.warn("[JsonDataStore] ⚠️ 오염된 JSON 데이터를 백업했습니다: {}", backupFile.getAbsolutePath());
            } else {
                logger.error("[JsonDataStore] 🚨 오염된 파일 백업 실패, 강제 삭제 시도");
                dataFile.delete();
            }
        }
    }

    /**
     * JSON 파일에서 전체 이력 목록을 읽어 반환합니다.
     * 읽기 잠금(Read Lock) 사용 - 동시 다중 읽기 허용.
     */
    public List<CertificateHistory> readAll() {
        lock.readLock().lock();
        try {
            if (!dataFile.exists() || dataFile.length() == 0) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(dataFile, new TypeReference<List<CertificateHistory>>() {});
        } catch (IOException ioException) {
            logger.error("[JsonDataStore] 🚨 데이터 파일 읽기 실패 (파일 오염 의심): {}", ioException.getMessage());
            lock.readLock().unlock(); // 읽기 락 해제 후 쓰기 락 획득 필요
            lock.writeLock().lock();
            try {
                handleCorruptedFile();
            } finally {
                lock.writeLock().unlock();
                lock.readLock().lock(); // 락 상태 원복
            }
            return new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 전체 이력 목록을 JSON 파일에 덮어씁니다.
     * 쓰기 잠금(Write Lock) 사용 - 쓰기 중 다른 읽기/쓰기 차단.
     */
    public void writeAll(List<CertificateHistory> histories) {
        lock.writeLock().lock();
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dataFile, histories);
        } catch (IOException ioException) {
            logger.error("[JsonDataStore] 🚨 데이터 파일 쓰기 실패: {}", ioException.getMessage());
            throw new RuntimeException("저장소 접근 권한이 없거나 디스크 공간이 부족합니다. (상세: " + ioException.getMessage() + ")", ioException);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 동시성 경쟁 조건(TOCTOU) 방지를 위해,
     * 쓰기 잠금(Write Lock)을 얻은 상태에서 파일을 읽고, 수정하고, 다시 쓰는
     * 원자적(Atomic) 업데이트를 수행합니다.
     */
    public void update(java.util.function.Consumer<List<CertificateHistory>> updater) {
        lock.writeLock().lock();
        try {
            List<CertificateHistory> histories;
            if (!dataFile.exists() || dataFile.length() == 0) {
                histories = new ArrayList<>();
            } else {
                try {
                    histories = objectMapper.readValue(dataFile, new TypeReference<List<CertificateHistory>>() {});
                } catch (IOException e) {
                    logger.error("[JsonDataStore] 🚨 원자적 업데이트 중 읽기 실패 (파일 오염). 백업 후 초기화합니다: {}", e.getMessage());
                    handleCorruptedFile();
                    histories = new ArrayList<>();
                }
            }
            
            updater.accept(histories);
            
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dataFile, histories);
        } catch (IOException ioException) {
            logger.error("[JsonDataStore] 🚨 데이터 원자적 쓰기 실패: {}", ioException.getMessage());
            throw new RuntimeException("저장소 접근 권한이 없거나 디스크 공간이 부족합니다. (상세: " + ioException.getMessage() + ")", ioException);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 현재 저장된 데이터의 다음 ID를 계산합니다.
     * 기존 목록이 비어있으면 1, 아니면 최대 ID + 1을 반환합니다.
     */
    public long generateNextId(List<CertificateHistory> existingHistories) {
        return existingHistories.stream()
                .mapToLong(h -> h.getId() != null ? h.getId() : 0L)
                .max()
                .orElse(0L) + 1L;
    }
}
