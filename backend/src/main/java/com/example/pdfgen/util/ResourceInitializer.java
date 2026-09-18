// c:/work/pdf-generator-v3/backend/src/main/java/com/example/pdfgen/util/ResourceInitializer.java
package com.example.pdfgen.util;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class ResourceInitializer {

    private static final Logger logger = LoggerFactory.getLogger(ResourceInitializer.class);

    @Value("${app.template.path:./certificate_template.docx}")
    private String templatePath;

    @Value("${app.vbs.script-path:./docx2pdf.vbs}")
    private String vbsScriptPath;

    @Value("${app.vbs.batch-script-path:./docx2pdf_batch.vbs}")
    private String batchVbsScriptPath;

    @PostConstruct
    public void initializeResources() {
        checkExternalFile(templatePath, "Word 템플릿 파일");
        checkExternalFile(vbsScriptPath, "PDF 단일 변환 VBScript");
        checkExternalFile(batchVbsScriptPath, "PDF 배치 변환 VBScript");
    }

    private void checkExternalFile(String path, String description) {
        File file = resolveFile(path);
        if (!file.exists()) {
            logger.warn("[ResourceInitializer] {} 파일이 존재하지 않습니다: {}", description, file.getAbsolutePath());
            logger.warn("[ResourceInitializer] PDF 생성 시 오류가 발생할 수 있습니다. 해당 파일을 올바른 위치에 배치해 주세요.");
        } else {
            logger.info("[ResourceInitializer] {} 확인 완료: {}", description, file.getAbsolutePath());
        }
    }

    private File resolveFile(String configuredPath) {
        File primaryFile = new File(configuredPath);
        if (primaryFile.exists() && primaryFile.isFile()) {
            return primaryFile;
        }

        String fileName = primaryFile.getName();
        String[] candidatePaths = {
            "./" + fileName,
            "../" + fileName,
            "../../" + fileName,
            "backend/" + fileName
        };

        for (String candidate : candidatePaths) {
            File fallbackFile = new File(candidate);
            if (fallbackFile.exists() && fallbackFile.isFile()) {
                return fallbackFile;
            }
        }

        return primaryFile;
    }
}