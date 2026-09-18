// backend/src/main/java/com/example/pdfgen/service/LibreOfficePdfConverter.java
package com.example.pdfgen.service;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class MsWordPdfConverter {

    // documents4j 변환 타임아웃 60초 설정 (개별 변환용)
    private static final long CONVERSION_TIMEOUT_SECONDS = 60L;
    // 배치 변환 타임아웃: 파일당 15초 기준, 최소 120초
    private static final long BATCH_TIMEOUT_SECONDS_PER_FILE = 15L;
    private static final long BATCH_TIMEOUT_MINIMUM_SECONDS = 120L;

    // application.yml에서 VBS 스크립트 경로를 주입받음 (절대/상대 경로 모두 지원)
    @org.springframework.beans.factory.annotation.Value("${app.vbs.script-path:./docx2pdf.vbs}")
    private String vbsScriptPath;

    // 배치 변환용 VBS 스크립트 경로
    @org.springframework.beans.factory.annotation.Value("${app.vbs.batch-script-path:./docx2pdf_batch.vbs}")
    private String batchVbsScriptPath;

    /**
     * 하나의 docx 바이트 배열을 PDF 바이트 배열로 변환.
     * documents4j 라이브러리를 통해 MS Word COM 자동화를 사용.
     * (PC에 Microsoft Word가 설치되어 있어야 합니다.)
     */
    public byte[] convertToPdf(byte[] docxContent) throws Exception {
        // 1. 임시 디렉토리 및 파일 경로 생성
        Path tempDir = Files.createTempDirectory("pdfgen");
        String uuid = UUID.randomUUID().toString();
        Path docxPath = tempDir.resolve(uuid + ".docx");
        Path pdfPath = tempDir.resolve(uuid + ".pdf");

        try {
            // 2. docx 바이트를 임시 파일로 저장
            Files.write(docxPath, docxContent);

            // 3. 커스텀 VBScript를 호출하여 PDF 변환 (documents4j 버그 우회)
            // @Value로 주입된 경로 기준으로 절대경로를 계산하여 JVM 실행 디렉토리와 무관하게 동작
            File vbsScript = new File(vbsScriptPath).getCanonicalFile();
            if (!vbsScript.exists()) {
                throw new RuntimeException("변환 스크립트를 찾을 수 없습니다: " + vbsScript.getAbsolutePath());
            }

            ProcessBuilder pb = new ProcessBuilder(
                    "cscript.exe",
                    "//nologo",
                    vbsScript.getAbsolutePath(),
                    docxPath.toAbsolutePath().toString(),
                    pdfPath.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 최대 60초 대기
            boolean finished = process.waitFor(CONVERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("MS Word PDF 변환 시간이 초과되었습니다.");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                // 스크립트 에러 내용 읽기
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder errorMsg = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    errorMsg.append(line).append(" ");
                }
                throw new RuntimeException("MS Word 변환 스크립트 에러 (코드 " + exitCode + "): " + errorMsg.toString());
            }

            if (!Files.exists(pdfPath) || Files.size(pdfPath) == 0) {
                throw new RuntimeException("변환된 PDF 파일이 생성되지 않았습니다.");
            }

            // 4. 생성된 PDF 바이트 배열로 읽어 반환
            return Files.readAllBytes(pdfPath);

        } finally {
            // 5. 임시 파일 정리
            Files.deleteIfExists(docxPath);
            Files.deleteIfExists(pdfPath);
            Files.deleteIfExists(tempDir);
        }
    }

    /**
     * 여러 DOCX 바이트 배열을 한 번에 PDF로 배치 변환합니다.
     * Word를 단 1번만 실행하여 N개를 순서대로 변환한 뒤 종료합니다.
     * 개별 변환 대비 Word 초기화 오버헤드를 제거하여 대폭 빠릅니다.
     */
    public List<byte[]> convertBatch(List<byte[]> docxBytesList) throws Exception {
        if (docxBytesList == null || docxBytesList.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        Path tempDir = Files.createTempDirectory("pdfgen_batch");

        try {
            // 1. DOCX 파일 전체를 임시 디렉토리에 기록
            List<Path> docxPaths = new java.util.ArrayList<>();
            List<Path> pdfPaths  = new java.util.ArrayList<>();

            for (int i = 0; i < docxBytesList.size(); i++) {
                String index    = String.format("%04d", i);
                Path   docxPath = tempDir.resolve(index + ".docx");
                Path   pdfPath  = tempDir.resolve(index + ".pdf");
                Files.write(docxPath, docxBytesList.get(i));
                docxPaths.add(docxPath);
                pdfPaths.add(pdfPath);
            }

            // 2. 매니페스트 파일 생성 (탭으로 구분된 입력경로\t출력경로)
            Path manifestPath = tempDir.resolve("manifest.txt");
            StringBuilder manifestContent = new StringBuilder();
            for (int i = 0; i < docxPaths.size(); i++) {
                manifestContent.append(docxPaths.get(i).toAbsolutePath());
                manifestContent.append("\t");
                manifestContent.append(pdfPaths.get(i).toAbsolutePath());
                manifestContent.append("\n");
            }
            Files.writeString(manifestPath, manifestContent.toString(), java.nio.charset.StandardCharsets.UTF_8);

            // 3. 배치 VBS 스크립트 실행 (Word 1번 실행으로 N개 일괄 변환)
            File batchVbsScript = new File(batchVbsScriptPath).getCanonicalFile();
            if (!batchVbsScript.exists()) {
                throw new RuntimeException("배치 변환 스크립트를 찾을 수 없습니다: " + batchVbsScript.getAbsolutePath());
            }

            ProcessBuilder pb = new ProcessBuilder(
                    "cscript.exe",
                    "//nologo",
                    batchVbsScript.getAbsolutePath(),
                    manifestPath.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 타임아웃: 파일 수 × 15초, 최소 120초
            long timeoutSeconds = Math.max(
                    BATCH_TIMEOUT_MINIMUM_SECONDS,
                    docxBytesList.size() * BATCH_TIMEOUT_SECONDS_PER_FILE
            );
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException(
                        "배치 PDF 변환 시간이 초과되었습니다. (" + docxBytesList.size() + "개 파일, 제한: " + timeoutSeconds + "초)"
                );
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder errorMsg = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    errorMsg.append(line).append(" ");
                }
                throw new RuntimeException("배치 변환 스크립트 에러 (코드 " + exitCode + "): " + errorMsg);
            }

            // 4. 생성된 PDF 파일들을 바이트 배열로 읽어 반환
            List<byte[]> resultList = new java.util.ArrayList<>();
            for (Path pdfPath : pdfPaths) {
                if (!Files.exists(pdfPath) || Files.size(pdfPath) == 0) {
                    throw new RuntimeException("변환된 PDF 파일이 생성되지 않았습니다: " + pdfPath.getFileName());
                }
                resultList.add(Files.readAllBytes(pdfPath));
            }
            return resultList;

        } finally {
            // 5. 임시 디렉토리 전체 정리
            try {
                if (Files.exists(tempDir)) {
                    Files.walk(tempDir)
                            .sorted(java.util.Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(java.io.File::delete);
                }
            } catch (Exception cleanupEx) {
                // 정리 실패는 무시 (로그만 남김)
            }
        }
    }

    /**
     * 여러 개의 PDF 바이트 배열을 하나의 PDF로 병합합니다. (Apache PDFBox 사용)
     */
    public byte[] mergePdfs(List<byte[]> pdfList) throws Exception {
        if (pdfList == null || pdfList.isEmpty()) {
            throw new IllegalArgumentException("병합할 PDF가 없습니다.");
        }
        if (pdfList.size() == 1) {
            return pdfList.get(0);
        }

        PDFMergerUtility mergerUtility = new PDFMergerUtility();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mergerUtility.setDestinationStream(out);

        for (byte[] pdfBytes : pdfList) {
            mergerUtility.addSource(new ByteArrayInputStream(pdfBytes));
        }

        mergerUtility.mergeDocuments(org.apache.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly());
        return out.toByteArray();
    }
}
