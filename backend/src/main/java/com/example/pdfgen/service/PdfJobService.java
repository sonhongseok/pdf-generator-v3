// backend/src/main/java/com/example/pdfgen/service/PdfJobService.java
package com.example.pdfgen.service;

import com.example.pdfgen.config.AsyncConfig;
import com.example.pdfgen.dto.PdfGenerationJob;
import com.example.pdfgen.dto.PdfGenerationJob.Status;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PDF 비동기 생성 작업을 관리하는 서비스.
 *
 * - 작업 등록: submitJob()으로 jobId 발급 후 @Async로 백그라운드 실행
 * - 상태 조회: getJob()으로 진행률 및 상태 확인
 * - 파일 수령: takeResultBytes()로 결과 바이트 획득 후 작업 제거
 * - TTL 정리: 30분 경과된 완료/실패 작업은 스케줄러가 자동 삭제
 */
@Service
public class PdfJobService {

    // 완료/실패 후 메모리 보관 시간 (분)
    private static final long JOB_TTL_MINUTES = 30L;

    // 수정: application.yml에서 주입받는 서브배치 크기 (Word 1번으로 처리할 장 수)
    @Value("${app.vbs.batch-size:30}")
    private int batchSize;

    private final Map<String, PdfGenerationJob> jobStore = new ConcurrentHashMap<>();

    private final DocxTemplateService docxTemplateService;
    private final MsWordPdfConverter msWordPdfConverter;
    private final CertificateHistoryService certificateHistoryService;
    // 수정: @Async self-invocation 우회를 위해 ApplicationContext에서 프록시를 직접 꺼내 사용
    private final ApplicationContext applicationContext;

    public PdfJobService(DocxTemplateService docxTemplateService,
                         MsWordPdfConverter msWordPdfConverter,
                         CertificateHistoryService certificateHistoryService,
                         ApplicationContext applicationContext) {
        this.docxTemplateService = docxTemplateService;
        this.msWordPdfConverter = msWordPdfConverter;
        this.certificateHistoryService = certificateHistoryService;
        this.applicationContext = applicationContext;
    }

    /**
     * 새 작업을 등록하고 jobId를 반환합니다.
     * 실제 생성은 @Async 메서드가 백그라운드에서 처리합니다.
     */
        /**
     * 재다운로드 작업을 등록하고 jobId를 반환합니다.
     * 이미 DB에 존재하는 이력이므로 DB 중복 저장을 수행하지 않고 파일 생성만 진행합니다.
     */
    public String submitReDownloadJob(String certificateNo, String certDate, String calDate, String expDate,
                                     List<String> serialNos, List<Integer> sequenceNos, String generateMode) {
        String jobId = UUID.randomUUID().toString();
        PdfGenerationJob job = new PdfGenerationJob(jobId, serialNos.size());
        jobStore.put(jobId, job);
        PdfJobService proxy = applicationContext.getBean(PdfJobService.class);
        proxy.executeReDownloadJob(jobId, certificateNo, new PdfJobRequest(certDate, calDate, expDate, serialNos, sequenceNos, generateMode));
        return jobId;
    }

    /**
     * 백그라운드에서 재다운로드용 PDF/ZIP을 생성합니다. (DB 저장 생략)
     */
    @Async(AsyncConfig.PDF_EXECUTOR)
    public void executeReDownloadJob(String jobId, String certificateNo, PdfJobRequest request) {
        PdfGenerationJob job = jobStore.get(jobId);
        if (job == null) return;

        job.setStatus(Status.RUNNING);

        try {
            final boolean isIndividualMode = "INDIVIDUAL".equals(request.getGenerateMode());
            final String formattedDateStr = request.getCertificateDate().replace("-", "");

            byte[] resultContent;
            if (isIndividualMode) {
                resultContent = generateZip(job, formattedDateStr, request);
            } else {
                resultContent = generateMergedPdf(job, formattedDateStr, request);
            }

            // 결과 보관 (DB 저장은 건너뜀)
            String filename = certificateNo + (isIndividualMode ? ".zip" : ".pdf");
            job.setResultBytes(resultContent);
            job.setResultFilename(filename);
            job.setStatus(Status.DONE);

        } catch (Exception e) {
            job.setErrorMessage("재다운로드 생성 중 오류가 발생했습니다: " + e.getMessage());
            job.setStatus(Status.FAILED);
        }
    }

    public String submitJob(PdfJobRequest request) {
        String jobId = UUID.randomUUID().toString();
        PdfGenerationJob job = new PdfGenerationJob(jobId, request.getSerialNos().size());
        jobStore.put(jobId, job);
        // 수정: self-invocation 방지 → ApplicationContext로 Spring 프록시를 꺼내 호출
        //       this.executeJob() 직접 호출 시 @Async AOP가 우회되어 동기 실행되는 버그 수정
        PdfJobService proxy = applicationContext.getBean(PdfJobService.class);
        proxy.executeJob(jobId, request);
        return jobId;
    }

    /**
     * jobId에 해당하는 작업 상태를 반환합니다.
     * 존재하지 않으면 null을 반환합니다.
     */
    public PdfGenerationJob getJob(String jobId) {
        return jobStore.get(jobId);
    }

    /**
     * 완료된 작업의 결과 바이트를 가져오고 jobStore에서 제거합니다.
     * 다운로드 후 즉시 메모리에서 정리됩니다.
     */
    public PdfGenerationJob takeCompletedJob(String jobId) {
        PdfGenerationJob job = jobStore.get(jobId);
        if (job != null && job.getStatus() == Status.DONE) {
            jobStore.remove(jobId);
        }
        return job;
    }

    /**
     * 백그라운드에서 PDF를 순차 생성합니다.
     * MS Word COM 충돌 방지를 위해 단일 스레드 Executor를 사용합니다.
     */
    @Async(AsyncConfig.PDF_EXECUTOR)
    public void executeJob(String jobId, PdfJobRequest request) {
        PdfGenerationJob job = jobStore.get(jobId);
        if (job == null) return;

        job.setStatus(Status.RUNNING);

        try {
            final boolean isIndividualMode = "INDIVIDUAL".equals(request.getGenerateMode());
            final String formattedDateStr = request.getCertificateDate().replace("-", "");

            byte[] resultContent;
            if (isIndividualMode) {
                resultContent = generateZip(job, formattedDateStr, request);
            } else {
                resultContent = generateMergedPdf(job, formattedDateStr, request);
            }

            // PDF 생성 성공 후 DB 저장
            String baseCertificateNo = String.format("OP%s%04d",
                    formattedDateStr, request.getSequenceNos().get(0));

            certificateHistoryService.saveToDatabase(
                    baseCertificateNo,
                    java.time.LocalDate.parse(request.getCertificateDate()),
                    java.time.LocalDate.parse(request.getCalibrationDate()),
                    java.time.LocalDate.parse(request.getExpiryDate()),
                    request.getSerialNos(),
                    request.getSequenceNos()
            );

            // 결과 보관
            String filename = baseCertificateNo + (isIndividualMode ? ".zip" : ".pdf");
            job.setResultBytes(resultContent);
            job.setResultFilename(filename);
            job.setStatus(Status.DONE);

        } catch (Exception e) {
            job.setErrorMessage("PDF 생성 중 오류가 발생했습니다: " + e.getMessage());
            job.setStatus(Status.FAILED);
        }
    }

    /**
     * 통합 PDF 생성 (서브배치 단위로 Word를 실행하여 일괄 변환)
     */
    private byte[] generateMergedPdf(PdfGenerationJob job, String formattedDateStr,
                                     PdfJobRequest request) throws Exception {
        List<String>  serialNos   = request.getSerialNos();
        List<Integer> sequenceNos = request.getSequenceNos();
        int total = serialNos.size();
        List<byte[]> allPdfPages = new java.util.ArrayList<>();

        // 서브배치 단위로 처리: Word가 batchSize당 1번만 실행되어 초기화 오버헤드를 대폭 줄임
        for (int batchStart = 0; batchStart < total; batchStart += batchSize) {
            int batchEnd = Math.min(batchStart + batchSize, total);

            // 1. 이번 배치의 DOCX 생성 (빠름, Java측)
            List<byte[]> batchDocxList = new java.util.ArrayList<>();
            for (int i = batchStart; i < batchEnd; i++) {
                String pageCertNo = String.format("OP%s%04d", formattedDateStr, sequenceNos.get(i));
                Map<String, String> variables = buildVariables(pageCertNo, request, serialNos.get(i));
                batchDocxList.add(docxTemplateService.fillTemplate(variables));
            }

            // 2. Word 1번 실행으로 이번 배치 일괄 변환
            List<byte[]> batchPdfList = msWordPdfConverter.convertBatch(batchDocxList);
            allPdfPages.addAll(batchPdfList);

            // 3. 진행률 업데이트
            job.setCompletedCount(batchEnd);
        }

        return msWordPdfConverter.mergePdfs(allPdfPages);
    }

    /**
     * 개별 PDF ZIP 생성 (서브배치 단위로 Word를 실행하여 일괄 변환)
     */
    private byte[] generateZip(PdfGenerationJob job, String formattedDateStr,
                                PdfJobRequest request) throws Exception {
        List<String>  serialNos   = request.getSerialNos();
        List<Integer> sequenceNos = request.getSequenceNos();
        int total = serialNos.size();

        try (java.io.ByteArrayOutputStream zipBaos = new java.io.ByteArrayOutputStream();
             java.util.zip.ZipOutputStream  zipOut  = new java.util.zip.ZipOutputStream(zipBaos)) {

            // 서브배치 단위로 처리
            for (int batchStart = 0; batchStart < total; batchStart += batchSize) {
                int batchEnd = Math.min(batchStart + batchSize, total);

                // 1. 이번 배치의 DOCX 생성
                List<byte[]> batchDocxList    = new java.util.ArrayList<>();
                List<String> batchSerialNames = new java.util.ArrayList<>();
                for (int i = batchStart; i < batchEnd; i++) {
                    String pageCertNo = String.format("OP%s%04d", formattedDateStr, sequenceNos.get(i));
                    Map<String, String> variables = buildVariables(pageCertNo, request, serialNos.get(i));
                    batchDocxList.add(docxTemplateService.fillTemplate(variables));
                    batchSerialNames.add(serialNos.get(i));
                }

                // 2. Word 1번 실행으로 이번 배치 일괄 변환
                List<byte[]> batchPdfList = msWordPdfConverter.convertBatch(batchDocxList);

                // 3. ZIP 항목 추가 (파일명: 시리얼번호.pdf)
                for (int j = 0; j < batchPdfList.size(); j++) {
                    java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(batchSerialNames.get(j) + ".pdf");
                    zipOut.putNextEntry(zipEntry);
                    zipOut.write(batchPdfList.get(j));
                    zipOut.closeEntry();
                }

                // 4. 진행률 업데이트
                job.setCompletedCount(batchEnd);
            }

            zipOut.finish();
            return zipBaos.toByteArray();
        }
    }

    /**
     * 템플릿 치환 변수 맵 생성
     */
    private Map<String, String> buildVariables(String certNo, PdfJobRequest request, String serialNo) {
        Map<String, String> variables = new java.util.HashMap<>();
        variables.put("cno", certNo);
        variables.put("cedate", request.getCertificateDate().replace("-", "/"));
        variables.put("pdate", request.getCalibrationDate().replace("-", "/"));
        variables.put("edate", request.getExpiryDate().replace("-", "/"));
        variables.put("sno", serialNo);
        return variables;
    }

    /**
     * 30분 이상 경과한 완료/실패 작업을 메모리에서 정리합니다. (5분마다 실행)
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void cleanupExpiredJobs() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(JOB_TTL_MINUTES);
        jobStore.entrySet().removeIf(entry -> {
            PdfGenerationJob job = entry.getValue();
            boolean isTerminal = job.getStatus() == Status.DONE || job.getStatus() == Status.FAILED;
            return isTerminal && job.getCreatedAt().isBefore(threshold);
        });
    }

    // ── 내부 요청 파라미터 객체 ──────────────────────────────────────────────

    /**
     * executeJob() 호출 시 필요한 모든 파라미터를 묶는 내부 클래스
     */
    public static class PdfJobRequest {
        private final String certificateDate;
        private final String calibrationDate;
        private final String expiryDate;
        private final List<String> serialNos;
        private final List<Integer> sequenceNos;
        private final String generateMode;

        public PdfJobRequest(String certificateDate, String calibrationDate, String expiryDate,
                             List<String> serialNos, List<Integer> sequenceNos, String generateMode) {
            this.certificateDate = certificateDate;
            this.calibrationDate = calibrationDate;
            this.expiryDate = expiryDate;
            this.serialNos = serialNos;
            this.sequenceNos = sequenceNos;
            this.generateMode = generateMode;
        }

        public String getCertificateDate() { return certificateDate; }
        public String getCalibrationDate() { return calibrationDate; }
        public String getExpiryDate() { return expiryDate; }
        public List<String> getSerialNos() { return serialNos; }
        public List<Integer> getSequenceNos() { return sequenceNos; }
        public String getGenerateMode() { return generateMode; }
    }
}
