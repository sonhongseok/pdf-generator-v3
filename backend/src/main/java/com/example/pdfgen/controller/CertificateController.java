// backend/src/main/java/com/example/pdfgen/controller/CertificateController.java
package com.example.pdfgen.controller;

import com.example.pdfgen.domain.CertificateHistory;
import com.example.pdfgen.domain.CertificateSerialMapping;
import com.example.pdfgen.dto.CertificateHistoryResponse;
import com.example.pdfgen.dto.CertificateRequest;
import com.example.pdfgen.dto.PdfGenerationJob;
import com.example.pdfgen.repository.CertificateHistoryRepository;
import com.example.pdfgen.service.CertificateHistoryService;
import com.example.pdfgen.service.DocxTemplateService;
import com.example.pdfgen.service.MsWordPdfConverter;
import com.example.pdfgen.service.PdfJobService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.ByteArrayOutputStream;

@RestController
@RequestMapping("/api/documents/certificates")
public class CertificateController {

    private final CertificateHistoryRepository certificateHistoryRepository;
    private final CertificateHistoryService certificateHistoryService;
    private final DocxTemplateService docxTemplateService;
    private final MsWordPdfConverter msWordPdfConverter;
    private final PdfJobService pdfJobService;
    // 수정: MS Word COM 단일 인스턴스 보호 및 매크로/광클 동시성 방어를 위한 락
    private final java.util.concurrent.locks.ReentrantLock conversionLock = new java.util.concurrent.locks.ReentrantLock();

    // 생성자 주입
    public CertificateController(CertificateHistoryRepository certificateHistoryRepository,
            CertificateHistoryService certificateHistoryService,
            DocxTemplateService docxTemplateService,
            MsWordPdfConverter msWordPdfConverter,
            PdfJobService pdfJobService) {
        this.certificateHistoryRepository = certificateHistoryRepository;
        this.certificateHistoryService = certificateHistoryService;
        this.docxTemplateService = docxTemplateService;
        this.msWordPdfConverter = msWordPdfConverter;
        this.pdfJobService = pdfJobService;
    }

    /**
     * 성적서 PDF 생성 및 다운로드 API 엔드포인트
     * 입력값 유효성 검증 -> PDF 생성 성공 시에만 DB 저장 순서로 처리하여
     * PDF 생성 실패 시 DB에 데이터가 쌓이는 버그를 원천 차단합니다.
     */
    @PostMapping("/pdf")
    public ResponseEntity<?> generateAndDownloadCertificate(@RequestBody CertificateRequest request) {
        // 1. 필수 입력값 존재 여부 및 유효성 검사
        if (request.getCertificateDate() == null || request.getCertificateDate().trim().isEmpty()) {
            return buildErrorResponse("Certificate Date는 필수 선택 항목입니다.", HttpStatus.BAD_REQUEST);
        }
        if (request.getExpiryDate() == null || request.getExpiryDate().trim().isEmpty()) {
            return buildErrorResponse("Expiry Date는 필수 선택 항목입니다.", HttpStatus.BAD_REQUEST);
        }
        if (request.getSerialNos() == null || request.getSerialNos().isEmpty()) {
            return buildErrorResponse("Serial NO는 최소 하나 이상 입력되어야 합니다.", HttpStatus.BAD_REQUEST);
        }

        Set<String> uniqueSerials = new HashSet<>(request.getSerialNos());
        if (uniqueSerials.size() != request.getSerialNos().size()) {
            return buildErrorResponse("입력된 Serial No 중에 중복된 값이 있습니다. 중복 없이 입력해 주세요.", HttpStatus.BAD_REQUEST);
        }

        // 시작 시퀀스 파싱 및 검사
        String inputStartSeq = request.getStartSequenceNo();
        if (inputStartSeq != null && !inputStartSeq.trim().isEmpty()) {
            String[] parts = inputStartSeq.trim().split("\\s+");
            if (parts.length > 1 && parts.length != request.getSerialNos().size()) {
                return buildErrorResponse("여러 개의 Start No를 입력한 경우, Serial No 개수와 정확히 일치해야 합니다.", HttpStatus.BAD_REQUEST);
            }
            // 각 Start No 항목의 4자리 숫자(0001~9999) 형식 유효성 검사
            final String FOUR_DIGIT_PATTERN = "^\\d{4}$";
            for (String part : parts) {
                if (!part.matches(FOUR_DIGIT_PATTERN)) {
                    return buildErrorResponse(
                            "Start No는 0001~9999 범위의 4자리 숫자여야 합니다. 잘못된 값: '" + part + "'",
                            HttpStatus.BAD_REQUEST);
                }
                int parsed = Integer.parseInt(part);
                if (parsed < 1 || parsed > 9999) {
                    return buildErrorResponse(
                            "Start No는 0001~9999 범위의 4자리 숫자여야 합니다. 잘못된 값: '" + part + "'",
                            HttpStatus.BAD_REQUEST);
                }
            }
        }

        List<Integer> sequenceNos = resolveSequenceNos(inputStartSeq, request.getSerialNos().size());
        for (int seq : sequenceNos) {
            if (seq > 9999) {
                return buildErrorResponse("Certificate NO 시퀀스는 9999를 초과할 수 없습니다.", HttpStatus.BAD_REQUEST);
            }
        }

        // 2. 날짜 파싱 및 검사
        LocalDate certDate;
        LocalDate calDate;
        LocalDate expDate;
        try {
            certDate = LocalDate.parse(request.getCertificateDate());
            calDate = LocalDate.parse(request.getCalibrationDate());
            expDate = LocalDate.parse(request.getExpiryDate());
        } catch (DateTimeParseException e) {
            return buildErrorResponse("날짜 포맷이 올바르지 않습니다. YYYY-MM-DD 형식이어야 합니다.", HttpStatus.BAD_REQUEST);
        }

        // 날짜 논리 오류 검사 (만료일이 발행일보다 빠른지 여부)
        if (certDate.isAfter(expDate)) {
            return buildErrorResponse("만료일(Expiry Date)은 발행일(Certificate Date)보다 빠를 수 없습니다.",
                    HttpStatus.BAD_REQUEST);
        }

        // 3. 4가지 입력값 완전 중복 발급 검사 (날짜 3종 + 시리얼 번호 목록 모두 동일 시 차단)
        Set<String> requestedSerialSet = new HashSet<>(request.getSerialNos());
        List<CertificateHistory> sameDateHistories = certificateHistoryRepository
                .findByCertificateDateAndCalibrationDateAndExpiryDate(certDate, calDate, expDate);

        boolean isExactDuplicate = sameDateHistories.stream().anyMatch(history -> {
            Set<String> savedSerialSet = history.getSerialMappings().stream()
                    .map(CertificateSerialMapping::getSerialNo)
                    .collect(Collectors.toSet());
            return savedSerialSet.equals(requestedSerialSet);
        });

        if (isExactDuplicate) {
            return buildErrorResponse(
                    "동일한 발행일, 교정일, 만료일, 시리얼 번호로 이미 발급된 성적서가 존재합니다. 입력 정보를 다시 확인해 주세요.",
                    HttpStatus.BAD_REQUEST);
        }

        // 4. Certificate NO 조합용 날짜 문자열
        String formattedDateStr = request.getCertificateDate().replace("-", "");

        // 5. 생성 방식 결정 (기본값 통합 PDF)
        final boolean isIndividualMode = "INDIVIDUAL".equals(request.getGenerateMode());

        // 수정: MS Word COM 단일 인스턴스 보호 및 동시 요청(광클) 차단을 위한 tryLock 적용
        if (!conversionLock.tryLock()) {
            return buildErrorResponse(
                    "현재 다른 성적서 변환 작업이 진행 중입니다. 잠시 후 다시 시도해 주세요.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        try {
            // 6. Word 템플릿 치환 및 PDF 변환
            byte[] responseContent;
            try {
                if (isIndividualMode) {
                    responseContent = generateIndividualPdfsAsZip(formattedDateStr, sequenceNos,
                            request.getCertificateDate(), request.getCalibrationDate(),
                            request.getExpiryDate(), request.getSerialNos());
                } else {
                    responseContent = generatePdfBytes(formattedDateStr, sequenceNos, request.getCertificateDate(),
                            request.getCalibrationDate(), request.getExpiryDate(), request.getSerialNos());
                }
            } catch (Exception e) {
                e.printStackTrace();
                return buildErrorResponse("서버 내부 오류로 PDF 생성을 진행할 수 없습니다: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }

            // 7. PDF 생성 성공 시 DB에 발급 이력 저장 (사용자용 생성 성공 시 확인용)
            // 첫 번째 시퀀스 번호를 대표 인증번호로 사용
            String baseCertificateNo = String.format("OP%s%04d", formattedDateStr, sequenceNos.get(0));
            certificateHistoryService.saveToDatabase(baseCertificateNo, certDate, calDate, expDate, request.getSerialNos(), sequenceNos);

            // 8. 생성 방식에 따라 PDF 또는 ZIP 응답 반환
            if (isIndividualMode) {
                return buildZipResponse(responseContent, baseCertificateNo + ".zip");
            }
            return buildPdfResponse(responseContent, baseCertificateNo + ".pdf");
        } finally {
            // 수정: 작업 완료 후 동시성 락 해제
            conversionLock.unlock();
        }
    }

    // ===== 비동기 PDF 생성 API (방향 B) ==========================================

    /**
     * [비동기] PDF 생성 작업 등록 API
     * 유효성 검증 후 백그라운드 작업 큐에 등록하고 jobId를 즉시 반환합니다.
     * 클라이언트는 jobId로 상태를 폴링하며, 완료 시 download 엔드포인트로 파일을 수령합니다.
     */
    @PostMapping("/pdf/request")
    public ResponseEntity<?> requestAsyncPdfGeneration(@RequestBody CertificateRequest request) {
        // 1. 필수 입력값 유효성 검사 (기존 동기 API와 동일)
        if (request.getCertificateDate() == null || request.getCertificateDate().trim().isEmpty()) {
            return buildErrorResponse("Certificate Date는 필수 선택 항목입니다.", HttpStatus.BAD_REQUEST);
        }
        if (request.getExpiryDate() == null || request.getExpiryDate().trim().isEmpty()) {
            return buildErrorResponse("Expiry Date는 필수 선택 항목입니다.", HttpStatus.BAD_REQUEST);
        }
        if (request.getSerialNos() == null || request.getSerialNos().isEmpty()) {
            return buildErrorResponse("Serial NO는 최소 하나 이상 입력되어야 합니다.", HttpStatus.BAD_REQUEST);
        }

        Set<String> uniqueSerials = new HashSet<>(request.getSerialNos());
        if (uniqueSerials.size() != request.getSerialNos().size()) {
            return buildErrorResponse("입력된 Serial No 중에 중복된 값이 있습니다. 중복 없이 입력해 주세요.", HttpStatus.BAD_REQUEST);
        }

        String inputStartSeq = request.getStartSequenceNo();
        if (inputStartSeq != null && !inputStartSeq.trim().isEmpty()) {
            String[] parts = inputStartSeq.trim().split("\\s+");
            if (parts.length > 1 && parts.length != request.getSerialNos().size()) {
                return buildErrorResponse("여러 개의 Start No를 입력한 경우, Serial No 개수와 정확히 일치해야 합니다.", HttpStatus.BAD_REQUEST);
            }
            final String FOUR_DIGIT_PATTERN = "^\\d{4}$";
            for (String part : parts) {
                if (!part.matches(FOUR_DIGIT_PATTERN)) {
                    return buildErrorResponse(
                            "Start No는 0001~9999 범위의 4자리 숫자여야 합니다. 잘못된 값: '" + part + "'",
                            HttpStatus.BAD_REQUEST);
                }
                int parsed = Integer.parseInt(part);
                if (parsed < 1 || parsed > 9999) {
                    return buildErrorResponse(
                            "Start No는 0001~9999 범위의 4자리 숫자여야 합니다. 잘못된 값: '" + part + "'",
                            HttpStatus.BAD_REQUEST);
                }
            }
        }

        List<Integer> sequenceNos = resolveSequenceNos(inputStartSeq, request.getSerialNos().size());
        for (int seq : sequenceNos) {
            if (seq > 9999) {
                return buildErrorResponse("Certificate NO 시퀀스는 9999를 초과할 수 없습니다.", HttpStatus.BAD_REQUEST);
            }
        }

        // 2. 날짜 파싱 및 검사
        LocalDate certDate;
        LocalDate calDate;
        LocalDate expDate;
        try {
            certDate = LocalDate.parse(request.getCertificateDate());
            calDate = LocalDate.parse(request.getCalibrationDate());
            expDate = LocalDate.parse(request.getExpiryDate());
        } catch (DateTimeParseException e) {
            return buildErrorResponse("날짜 포맷이 올바르지 않습니다. YYYY-MM-DD 형식이어야 합니다.", HttpStatus.BAD_REQUEST);
        }

        if (certDate.isAfter(expDate)) {
            return buildErrorResponse("만료일(Expiry Date)은 발행일(Certificate Date)보다 빠를 수 없습니다.",
                    HttpStatus.BAD_REQUEST);
        }

        // 3. 완전 중복 발급 검사
        Set<String> requestedSerialSet = new HashSet<>(request.getSerialNos());
        List<CertificateHistory> sameDateHistories = certificateHistoryRepository
                .findByCertificateDateAndCalibrationDateAndExpiryDate(certDate, calDate, expDate);

        boolean isExactDuplicate = sameDateHistories.stream().anyMatch(history -> {
            Set<String> savedSerialSet = history.getSerialMappings().stream()
                    .map(CertificateSerialMapping::getSerialNo)
                    .collect(Collectors.toSet());
            return savedSerialSet.equals(requestedSerialSet);
        });

        if (isExactDuplicate) {
            return buildErrorResponse(
                    "동일한 발행일, 교정일, 만료일, 시리얼 번호로 이미 발급된 성적서가 존재합니다. 입력 정보를 다시 확인해 주세요.",
                    HttpStatus.BAD_REQUEST);
        }

        // 4. 비동기 작업 등록 후 jobId 즉시 반환
        PdfJobService.PdfJobRequest jobRequest = new PdfJobService.PdfJobRequest(
                request.getCertificateDate(),
                request.getCalibrationDate(),
                request.getExpiryDate(),
                request.getSerialNos(),
                sequenceNos,
                request.getGenerateMode()
        );

        String jobId = pdfJobService.submitJob(jobRequest);

        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("jobId", jobId);
        return ResponseEntity.ok(responseBody);
    }

    /**
     * [비동기] PDF 생성 작업 상태 조회 API
     * 클라이언트가 2초 간격으로 폴링하여 진행률을 확인합니다.
     */
    @GetMapping("/pdf/status/{jobId}")
    public ResponseEntity<?> getJobStatus(@PathVariable String jobId) {
        PdfGenerationJob job = pdfJobService.getJob(jobId);
        if (job == null) {
            return buildErrorResponse("존재하지 않거나 만료된 작업입니다: " + jobId, HttpStatus.NOT_FOUND);
        }

        Map<String, Object> statusBody = new HashMap<>();
        statusBody.put("jobId", job.getJobId());
        statusBody.put("status", job.getStatus().name());
        statusBody.put("progressPercent", job.getProgressPercent());
        statusBody.put("completedCount", job.getCompletedCount());
        statusBody.put("totalCount", job.getTotalCount());
        if (job.getStatus() == PdfGenerationJob.Status.FAILED) {
            statusBody.put("errorMessage", job.getErrorMessage());
        }

        return ResponseEntity.ok(statusBody);
    }

    /**
     * [비동기] 생성된 PDF/ZIP 파일 다운로드 API
     * DONE 상태인 작업의 결과 파일을 반환하고 jobStore에서 즉시 제거합니다.
     */
    @GetMapping("/pdf/download/{jobId}")
    public ResponseEntity<?> downloadCompletedJob(@PathVariable String jobId) {
        PdfGenerationJob job = pdfJobService.takeCompletedJob(jobId);
        if (job == null) {
            return buildErrorResponse("존재하지 않거나 만료된 작업입니다: " + jobId, HttpStatus.NOT_FOUND);
        }
        if (job.getStatus() != PdfGenerationJob.Status.DONE) {
            return buildErrorResponse("아직 생성이 완료되지 않은 작업입니다. 상태: " + job.getStatus().name(), HttpStatus.CONFLICT);
        }

        String filename = job.getResultFilename();
        if (filename != null && filename.endsWith(".zip")) {
            return buildZipResponse(job.getResultBytes(), filename);
        }
        return buildPdfResponse(job.getResultBytes(), filename);
    }

    /**
     * 발급 이력 전체 조회 API
     */
    @GetMapping
    public ResponseEntity<List<CertificateHistoryResponse>> getCertificateHistories() {
        List<CertificateHistoryResponse> responses = certificateHistoryRepository.findAllByOrderByCreatedDateDesc()
                .stream()
                .map(CertificateHistoryResponse::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

        /**
     * [비동기] 특정 성적서 재다운로드 작업 등록 API
     * 백그라운드 큐에 작업을 등록하고 jobId를 즉시 반환합니다.
     * 클라이언트는 jobId로 상태를 폴링하여 실시간 진행률을 확인합니다.
     */
    @PostMapping("/{id}/download/request")
    public ResponseEntity<?> requestAsyncReDownloadCertificate(@PathVariable Long id, @RequestParam(defaultValue = "MERGED") String mode) {
        Optional<CertificateHistory> historyOpt = certificateHistoryRepository.findById(id);
        if (historyOpt.isEmpty()) {
            return buildErrorResponse("해당 발급 이력을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        CertificateHistory history = historyOpt.get();
        List<String> serialNos = history.getSerialMappings().stream()
                .sorted(java.util.Comparator.comparingInt(CertificateSerialMapping::getPageNumber))
                .map(CertificateSerialMapping::getSerialNo)
                .collect(Collectors.toList());

        String savedCertNo = history.getCertificateNo();
        final java.util.regex.Pattern CERT_NO_PATTERN = java.util.regex.Pattern.compile("^OP\\d{8}(\\d{4})$");
        java.util.regex.Matcher certNoMatcher = CERT_NO_PATTERN.matcher(savedCertNo);
        if (!certNoMatcher.matches()) {
            return buildErrorResponse(
                    "저장된 발급 번호 형식이 올바르지 않아 재다운로드할 수 없습니다: " + savedCertNo,
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        int originalStartSequenceNo = Integer.parseInt(certNoMatcher.group(1));

        List<Integer> sequenceNos = history.getSerialMappings().stream()
                .sorted(java.util.Comparator.comparingInt(CertificateSerialMapping::getPageNumber))
                .map(m -> m.getSequenceNo() != null ? m.getSequenceNo() : (originalStartSequenceNo + m.getPageNumber() - 1))
                .collect(Collectors.toList());

        String jobId = pdfJobService.submitReDownloadJob(
                history.getCertificateNo(),
                history.getCertificateDate().toString(),
                history.getCalibrationDate().toString(),
                history.getExpiryDate().toString(),
                serialNos,
                sequenceNos,
                mode
        );

        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("jobId", jobId);
        return ResponseEntity.ok(responseBody);
    }

/**
     * 특정 성적서 재다운로드 API
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<?> reDownloadCertificate(@PathVariable Long id, @RequestParam(defaultValue = "MERGED") String mode) {
        Optional<CertificateHistory> historyOpt = certificateHistoryRepository.findById(id);
        if (historyOpt.isEmpty()) {
            return buildErrorResponse("해당 발급 이력을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        CertificateHistory history = historyOpt.get();
        List<String> serialNos = history.getSerialMappings().stream()
                .sorted(java.util.Comparator.comparingInt(CertificateSerialMapping::getPageNumber))
                .map(CertificateSerialMapping::getSerialNo)
                .collect(Collectors.toList());

        String formattedDateStr = history.getCertificateDate().toString().replace("-", "");

        // DB에 저장된 대표 인증번호에서 원본 시작 시퀀스 번호 복원 (과거 데이터용)
        // 정규식으로 포맷(OP + 8자리 날짜 + 4자리 시퀀스)을 검증하여 파싱 오류 방지
        // 예: "OP202605290050" -> 50
        String savedCertNo = history.getCertificateNo();
        final java.util.regex.Pattern CERT_NO_PATTERN = java.util.regex.Pattern.compile("^OP\\d{8}(\\d{4})$");
        java.util.regex.Matcher certNoMatcher = CERT_NO_PATTERN.matcher(savedCertNo);
        if (!certNoMatcher.matches()) {
            return buildErrorResponse(
                    "저장된 발급 번호 형식이 올바르지 않아 재다운로드할 수 없습니다: " + savedCertNo,
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        int originalStartSequenceNo = Integer.parseInt(certNoMatcher.group(1));

        List<Integer> sequenceNos = history.getSerialMappings().stream()
                .sorted(java.util.Comparator.comparingInt(CertificateSerialMapping::getPageNumber))
                .map(m -> m.getSequenceNo() != null ? m.getSequenceNo() : (originalStartSequenceNo + m.getPageNumber() - 1))
                .collect(Collectors.toList());

        boolean isIndividualMode = "INDIVIDUAL".equals(mode);
        byte[] finalContent;
        try {
            if (isIndividualMode) {
                finalContent = generateIndividualPdfsAsZip(formattedDateStr, sequenceNos,
                        history.getCertificateDate().toString(),
                        history.getCalibrationDate().toString(),
                        history.getExpiryDate().toString(),
                        serialNos);
                return buildZipResponse(finalContent, history.getCertificateNo() + ".zip");
            } else {
                finalContent = generatePdfBytes(formattedDateStr, sequenceNos,
                        history.getCertificateDate().toString(),
                        history.getCalibrationDate().toString(),
                        history.getExpiryDate().toString(),
                        serialNos);
                return buildPdfResponse(finalContent, history.getCertificateNo() + ".pdf");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return buildErrorResponse("서버 내부 오류로 생성을 진행할 수 없습니다: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 공통 PDF 응답 빌더
     */
    private ResponseEntity<byte[]> buildPdfResponse(byte[] pdfContent, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        headers.setContentLength(pdfContent.length);
        return new ResponseEntity<>(pdfContent, headers, HttpStatus.OK);
    }

    /**
     * 공통 ZIP 응답 빌더
     */
    private ResponseEntity<byte[]> buildZipResponse(byte[] zipContent, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        headers.setContentLength(zipContent.length);
        return new ResponseEntity<>(zipContent, headers, HttpStatus.OK);
    }

    /**
     * 사용자 입력값을 파싱하여 시작 시퀀스 번호 리스트를 반환합니다.
     * - 입력이 비어있으면 1부터 순차 증가하는 리스트를 반환합니다.
     * - 단일 유효값이면 해당 값부터 순차 증가하는 리스트를 반환합니다.
     * - 다중 유효값이면 순서대로 각각 매핑된 리스트를 반환합니다.
     * 유효하지 않은 값은 컨트롤러 상단에서 사전에 차단되므로 이 메서드에서는 파싱만 수행합니다.
     */
    private List<Integer> resolveSequenceNos(String input, int serialCount) {
        List<Integer> result = new ArrayList<>();
        final int DEFAULT_START = 1;
        final String FOUR_DIGIT_PATTERN = "^\\d{4}$";

        if (input == null || input.trim().isEmpty()) {
            for (int i = 0; i < serialCount; i++) {
                result.add(DEFAULT_START + i);
            }
            return result;
        }

        String[] parts = input.trim().split("\\s+");
        if (parts.length == 1) {
            // 단일 입력: 해당 값부터 순차 증가 (유효성은 상단에서 보장됨)
            int start = parts[0].matches(FOUR_DIGIT_PATTERN)
                    ? Integer.parseInt(parts[0])
                    : DEFAULT_START;
            for (int i = 0; i < serialCount; i++) {
                result.add(start + i);
            }
            return result;
        }

        // 다중 입력: 각 값을 순서대로 매핑 (유효성은 상단에서 보장됨)
        // 이 시점에서는 모든 part가 이미 4자리 숫자로 검증된 상태이므로 안전하게 파싱합니다.
        for (String part : parts) {
            result.add(Integer.parseInt(part));
        }
        return result;
    }

    /**
     * PDF 바이트 생성 공통 로직
     */
    private byte[] generatePdfBytes(String formattedDateStr, List<Integer> sequenceNos, String certDateStr, String calDateStr, String expDateStr, List<String> serialNos) throws Exception {
        List<byte[]> pdfPages = new ArrayList<>();
        for (int i = 0; i < serialNos.size(); i++) {
            String serialNo = serialNos.get(i);
            int seqNo = sequenceNos.get(i);

            // 해당 시리얼의 시퀀스 번호 적용
            String pageCertNo = String.format("OP%s%04d", formattedDateStr, seqNo);

            Map<String, String> variables = new HashMap<>();
            variables.put("cno", pageCertNo);
            variables.put("cedate", certDateStr.replace("-", "/"));
            variables.put("pdate", calDateStr.replace("-", "/"));
            variables.put("edate", expDateStr.replace("-", "/"));
            variables.put("sno", serialNo);

            // 템플릿 치환하여 .docx 바이트 배열 생성
            byte[] docxBytes = docxTemplateService.fillTemplate(variables);
            // MS Word로 PDF 변환
            byte[] pdfBytes = msWordPdfConverter.convertToPdf(docxBytes);

            pdfPages.add(pdfBytes);
        }

        // 병합
        return msWordPdfConverter.mergePdfs(pdfPages);
    }

    /**
     * 개별 PDF 생성 및 ZIP 패키징
     * 각 시리얼 번호마다 PDF를 1장씩 생성하고, 하나의 ZIP 파일로 묶어 반환합니다.
     * 파일명은 {시리얼번호}.pdf 형식으로 지정됩니다.
     */
    private byte[] generateIndividualPdfsAsZip(String formattedDateStr, List<Integer> sequenceNos,
            String certDateStr, String calDateStr, String expDateStr, List<String> serialNos) throws Exception {
        try (ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
             ZipOutputStream zipOut = new ZipOutputStream(zipBaos)) {

            for (int i = 0; i < serialNos.size(); i++) {
                String serialNo = serialNos.get(i);
                int seqNo = sequenceNos.get(i);

                String pageCertNo = String.format("OP%s%04d", formattedDateStr, seqNo);

                Map<String, String> variables = new HashMap<>();
                variables.put("cno", pageCertNo);
                variables.put("cedate", certDateStr.replace("-", "/"));
                variables.put("pdate", calDateStr.replace("-", "/"));
                variables.put("edate", expDateStr.replace("-", "/"));
                variables.put("sno", serialNo);

                byte[] docxBytes = docxTemplateService.fillTemplate(variables);
                byte[] pdfBytes = msWordPdfConverter.convertToPdf(docxBytes);

                // ZIP 엔트리 추가: 파일명은 시리얼번호.pdf
                ZipEntry zipEntry = new ZipEntry(serialNo + ".pdf");
                zipOut.putNextEntry(zipEntry);
                zipOut.write(pdfBytes);
                zipOut.closeEntry();
            }

            zipOut.finish();
            return zipBaos.toByteArray();
        }
    }

    /**
     * 공통 오류 JSON 포맷 빌더
     */
    private ResponseEntity<Map<String, String>> buildErrorResponse(String message, HttpStatus status) {
        Map<String, String> errorResponseBody = new HashMap<>();
        errorResponseBody.put("message", message);
        return new ResponseEntity<>(errorResponseBody, status);
    }
}