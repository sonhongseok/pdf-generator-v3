package com.example.pdfgen.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

// 실제 템플릿 파일이 c:\work\pdf-generator\ 아래에 존재하므로,
// 테스트 환경에서도 해당 경로를 정확히 가리키도록 프로퍼티를 강제 지정합니다.
@SpringBootTest(classes = DocxTemplateService.class)
@TestPropertySource(properties = {
        "app.template.path=../certificate_template.docx"
})
class DocxTemplateServiceTest {

    @Autowired
    private DocxTemplateService docxTemplateService;

    @Test
    @DisplayName("외부 템플릿 파일을 읽어와 변수 치환 후 정상적으로 byte 배열을 반환한다")
    void fillTemplateTest() {
        // given
        Map<String, String> variables = new HashMap<>();
        variables.put("cno", "OP202605210001");
        variables.put("sno", "SN-TEST-123");
        variables.put("cedate", "2026-05-21");
        variables.put("pdate", "2026-05-20");
        variables.put("edate", "2027-05-20");

        // when
        byte[] docxBytes = assertDoesNotThrow(() -> docxTemplateService.fillTemplate(variables));

        // then
        assertThat(docxBytes).isNotNull();
        assertThat(docxBytes.length).isGreaterThan(0);
        // docx 파일의 매직 넘버(Zip 파일 시그니처: PK..) 50 4B 03 04 확인
        assertThat(docxBytes[0]).isEqualTo((byte) 0x50);
        assertThat(docxBytes[1]).isEqualTo((byte) 0x4B);
    }
}
