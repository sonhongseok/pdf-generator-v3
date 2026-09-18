package com.example.pdfgen;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;

@Disabled("최초 뼈대 생성용 코드이므로, 사용자의 템플릿을 덮어쓰지 않도록 비활성화함")
public class CreateTemplateTest {
    
    @Test
    public void generateTemplate() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph p1 = document.createParagraph();
            XWPFRun r1 = p1.createRun();
            r1.setText("Calibration Certificate");
            r1.setBold(true);
            r1.setFontSize(24);

            XWPFParagraph p2 = document.createParagraph();
            XWPFRun r2 = p2.createRun();
            r2.setText("Certificate NO: {{CERT_NO}}");
            r2.addBreak();
            r2.setText("Certificate Date: {{CERT_DATE}}");
            r2.addBreak();
            r2.setText("Calibration Date: {{CAL_DATE}}");
            r2.addBreak();
            r2.setText("Expiry Date: {{EXP_DATE}}");
            r2.addBreak();
            r2.setText("Serial NO: {{SERIAL_NO}}");
            
            try (FileOutputStream out = new FileOutputStream("src/main/resources/templates/certificate_template.docx")) {
                document.write(out);
            }
        }
        System.out.println("Template created successfully.");
    }
}
