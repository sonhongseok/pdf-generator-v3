// backend/src/main/java/com/example/pdfgen/dto/PdfGenerationJob.java
package com.example.pdfgen.dto;

import java.time.LocalDateTime;

/**
 * PDF 생성 비동기 작업의 상태를 담는 인메모리 객체.
 * PdfJobService의 ConcurrentHashMap에 jobId를 키로 보관됩니다.
 */
public class PdfGenerationJob {

    public enum Status {
        PENDING,   // 대기 중 (큐에 등록됨)
        RUNNING,   // 생성 중
        DONE,      // 완료 (파일 다운로드 가능)
        FAILED     // 실패
    }

    private final String jobId;
    private volatile Status status;
    private volatile int totalCount;
    private volatile int completedCount;
    private volatile String errorMessage;
    private volatile byte[] resultBytes;
    private volatile String resultFilename;
    private final LocalDateTime createdAt;

    public PdfGenerationJob(String jobId, int totalCount) {
        this.jobId = jobId;
        this.totalCount = totalCount;
        this.status = Status.PENDING;
        this.completedCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    // 진행률 퍼센트 (0~100)
    public int getProgressPercent() {
        if (totalCount == 0) return 0;
        return (int) ((completedCount * 100L) / totalCount);
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    public String getJobId() { return jobId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getCompletedCount() { return completedCount; }
    public void setCompletedCount(int completedCount) { this.completedCount = completedCount; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public byte[] getResultBytes() { return resultBytes; }
    public void setResultBytes(byte[] resultBytes) { this.resultBytes = resultBytes; }

    public String getResultFilename() { return resultFilename; }
    public void setResultFilename(String resultFilename) { this.resultFilename = resultFilename; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
