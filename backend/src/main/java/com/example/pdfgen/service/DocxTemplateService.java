// c:/work/pdf-generator-v3/backend/src/main/java/com/example/pdfgen/service/DocxTemplateService.java
package com.example.pdfgen.service;

import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.Configure;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@Service
public class DocxTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(DocxTemplateService.class);
    private static final String BLACK_COLOR = "000000";
    private static final int ZERO_MARGIN_VALUE = 0;

    @Value("${app.template.path:./certificate_template.docx}")
    private String templatePath;

    public byte[] fillTemplate(Map<String, String> variables) throws Exception {
        File templateFile = resolveTemplateFile();
        if (!templateFile.exists() || !templateFile.isFile()) {
            throw new RuntimeException("Word 템플릿 파일을 찾을 수 없습니다: " + templateFile.getAbsolutePath());
        }

        try (InputStream inputStream = new FileInputStream(templateFile);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Configure config = Configure.builder().buildGramer("{", "}").build();
            XWPFTemplate template = XWPFTemplate.compile(inputStream, config).render(variables);
            XWPFDocument document = template.getXWPFDocument();

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                forceBlackColor(paragraph.getRuns());
            }

            for (XWPFTable table : document.getTables()) {
                applyStyleRecursively(table);
            }

            template.write(outputStream);
            template.close();

            return outputStream.toByteArray();
        }
    }

    private File resolveTemplateFile() {
        File primaryFile = new File(templatePath);
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
                logger.info("[DocxTemplateService] 템플릿 대체 경로 탐색 성공: {}", fallbackFile.getAbsolutePath());
                return fallbackFile;
            }
        }

        return primaryFile;
    }

    private void applyStyleRecursively(XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);

                CTTcPr tcPr = cell.getCTTc().getTcPr() != null
                        ? cell.getCTTc().getTcPr()
                        : cell.getCTTc().addNewTcPr();

                CTTcMar tcMar = tcPr.getTcMar() != null
                        ? tcPr.getTcMar()
                        : tcPr.addNewTcMar();

                setMargin(tcMar.isSetTop() ? tcMar.getTop() : tcMar.addNewTop(), ZERO_MARGIN_VALUE);
                setMargin(tcMar.isSetBottom() ? tcMar.getBottom() : tcMar.addNewBottom(), ZERO_MARGIN_VALUE);
                setMargin(tcMar.isSetLeft() ? tcMar.getLeft() : tcMar.addNewLeft(), ZERO_MARGIN_VALUE);
                setMargin(tcMar.isSetRight() ? tcMar.getRight() : tcMar.addNewRight(), ZERO_MARGIN_VALUE);

                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    paragraph.setAlignment(ParagraphAlignment.CENTER);
                    paragraph.setSpacingAfter(ZERO_MARGIN_VALUE);
                    paragraph.setSpacingBefore(ZERO_MARGIN_VALUE);
                    forceBlackColor(paragraph.getRuns());
                }

                for (XWPFTable nestedTable : cell.getTables()) {
                    applyStyleRecursively(nestedTable);
                }
            }
        }
    }

    private void forceBlackColor(List<XWPFRun> runs) {
        for (XWPFRun run : runs) {
            run.setColor(BLACK_COLOR);
        }
    }

    private void setMargin(CTTblWidth margin, int value) {
        margin.setW(BigInteger.valueOf(value));
        margin.setType(STTblWidth.DXA);
    }
}