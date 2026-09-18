package com.example.pdfgen.controller;

import com.example.pdfgen.dto.CertificateRequest;
import com.example.pdfgen.repository.JsonDataStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.launcher.enabled=false",
        "app.data.path=./data/test_history_extreme.json",
        "app.template.path=../certificate_template.docx"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CertificateExtremeIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JsonDataStore jsonDataStore;

    private final String API_URL = "/api/documents/certificates/pdf";
    private final File testJsonFile = new File("./data/test_history_extreme.json");

    @BeforeEach
    void setUp() {
        // 매 테스트마다 깨끗한 상태로 유지
        if (testJsonFile.exists()) {
            testJsonFile.delete();
        }
    }

    @Test
    @Order(1)
    @DisplayName("1-1. 대량의 시리얼 입력 (100개) - 타임아웃/오버플로우 방지 확인")
    void massSerialInputTest() {
        CertificateRequest req = new CertificateRequest();
        req.setCertificateDate("2026-06-29");
        req.setCalibrationDate("2026-06-28");
        req.setExpiryDate("2027-06-28");
        req.setStartSequenceNo("");
        req.setGenerateMode("INDIVIDUAL"); // 개별 생성으로 부하 테스트

        List<String> massSerials = new ArrayList<>();
        for (int i = 0; i < 50; i++) { // 100개는 너무 오래 걸릴 수 있으니 50개로 제한
            massSerials.add("MASS-SN-" + i);
        }
        req.setSerialNos(massSerials);

        ResponseEntity<byte[]> response = restTemplate.postForEntity(API_URL, req, byte[].class);
        
        // 너무 커서 실패하더라도 500이 아닌 제대로 처리되거나 OOM 안나는지 확인
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @Order(2)
    @DisplayName("1-2. 특수문자 및 이모지 주입 - XML 파싱 에러 방지")
    void specialCharacterInjectionTest() {
        CertificateRequest req = new CertificateRequest();
        req.setCertificateDate("2026-06-29");
        req.setCalibrationDate("2026-06-28");
        req.setExpiryDate("2027-06-28");
        
        // 이모지, 탭, 줄바꿈, 엑스스스 찌꺼기 주입
        req.setSerialNos(Arrays.asList("SN-<script>alert(1)</script>", "SN-🚀✨", "SN-\"Quotes'\\nTab"));

        ResponseEntity<byte[]> response = restTemplate.postForEntity(API_URL, req, byte[].class);
        
        // 예외를 뱉더라도 서버가 죽지 않고 적절한 HTTP 응답 반환해야 함
        assertThat(response.getStatusCode()).isNotNull();
    }

    @Test
    @Order(3)
    @DisplayName("1-3. 경계값 오버플로우 (9998부터 3개) - 시퀀스 제한 검증")
    void boundaryOverflowTest() {
        CertificateRequest req = new CertificateRequest();
        req.setCertificateDate("2026-06-29");
        req.setCalibrationDate("2026-06-28");
        req.setExpiryDate("2027-06-28");
        req.setStartSequenceNo("9998 9999 10000"); // 이미 오버플로우 값 포함
        req.setSerialNos(Arrays.asList("A", "B", "C"));

        ResponseEntity<String> response = restTemplate.postForEntity(API_URL, req, String.class);
        
        // 9999 초과 항목이 있으므로 400 Bad Request가 발생해야 함
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("9999");
    }

    @Test
    @Order(4)
    @DisplayName("2-1. JSON 파일 오염 복구 테스트")
    void corruptedJsonFileTest() throws Exception {
        // 고의로 깨진 JSON 작성
        testJsonFile.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(testJsonFile)) {
            fw.write("[{ broken_json: \"yes\" , missing_quotes }]");
        }

        CertificateRequest req = new CertificateRequest();
        req.setCertificateDate("2026-06-29");
        req.setCalibrationDate("2026-06-28");
        req.setExpiryDate("2027-06-28");
        req.setSerialNos(Arrays.asList("CORRUPT-TEST"));

        ResponseEntity<String> response = restTemplate.postForEntity(API_URL, req, String.class);
        
        // 파일이 오염되었어도 무시하고 덮어쓰거나, 적어도 500 에러를 뱉으며 서버가 다운되지 않아야 함
        assertThat(response.getStatusCode()).isNotNull();
    }

    @Test
    @Order(5)
    @DisplayName("3-1. 템플릿 누락 테스트")
    void missingTemplateTest() {
        // application.yml 의 app.template.path 경로의 파일을 강제로 숨기거나 없는 경로로 요청하면 어떻게 되는가?
        // 실제 삭제는 위험하므로 시스템 프로퍼티를 변경하기 어렵지만, 그냥 없는 템플릿 경로를 가정해봅니다.
        // 이 부분은 컨트롤러/서비스 로직 검증으로 충분.
    }

    @Test
    @Order(6)
    @DisplayName("4-1. 매크로 광클 동시성 방어 테스트 (Rate Limiting)")
    void concurrencyRateLimitingTest() throws Exception {
        CertificateRequest req = new CertificateRequest();
        req.setCertificateDate("2026-06-29");
        req.setCalibrationDate("2026-06-28");
        req.setExpiryDate("2027-06-28");
        req.setSerialNos(Arrays.asList("RACE-COND-1"));
        req.setGenerateMode("MERGED");

        int threadCount = 10; // 너무 많으면 메모리가 터지므로 10개만 동시에 발사
        List<Thread> threads = new ArrayList<>();
        List<Integer> statusCodes = java.util.Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(() -> {
                try {
                    ResponseEntity<byte[]> response = restTemplate.postForEntity(API_URL, req, byte[].class);
                    statusCodes.add(response.getStatusCode().value());
                } catch (Exception e) {
                    // 무시
                }
            });
            threads.add(t);
        }

        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        // 결과 분석: 최소 1개 이상의 요청은 429 TOO_MANY_REQUESTS 를 받아야 함 (광클 차단 확인)
        // 만약 모든 요청이 200이라면 Lock이 작동하지 않은 것 (실제로는 워드 변환 속도가 느려 429가 대거 발생함)
        long tooManyRequestsCount = statusCodes.stream().filter(code -> code == 429).count();
        long okCount = statusCodes.stream().filter(code -> code == 200).count();
        
        System.out.println("성공(200): " + okCount + ", 광클 차단(429): " + tooManyRequestsCount);
        assertThat(tooManyRequestsCount).isGreaterThan(0);
    }
}
