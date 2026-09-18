// backend/src/test/java/com/example/pdfgen/controller/CertificateExcelControllerTest.java
package com.example.pdfgen.controller;

import com.example.pdfgen.service.CertificateExcelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CertificateExcelController.class)
class CertificateExcelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CertificateExcelService certificateExcelService;

    @Test
    @DisplayName("정상 엑셀 파일 업로드 시 Serial No 목록과 개수를 반환한다")
    void parseExcelSuccessTest() throws Exception {
        Mockito.when(certificateExcelService.parseSerialNumbers(any()))
                .thenReturn(List.of("SN-001", "SN-002", "SN-003"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "serials.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy content".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/certificates/parse-excel").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.serialNos[0]").value("SN-001"))
                .andExpect(jsonPath("$.serialNos[1]").value("SN-002"))
                .andExpect(jsonPath("$.serialNos[2]").value("SN-003"));
    }

    @Test
    @DisplayName("별칭 엔드포인트(/pdf/parse-excel)로 요청 시에도 동일하게 성공 응답을 반환한다")
    void parseExcelAliasEndpointTest() throws Exception {
        Mockito.when(certificateExcelService.parseSerialNumbers(any()))
                .thenReturn(List.of("SN-ALIAS"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/certificates/pdf/parse-excel").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.serialNos[0]").value("SN-ALIAS"));
    }

    @Test
    @DisplayName("대문자 확장자(.XLSX) 파일도 정상적으로 업로드 처리된다")
    void parseUpperExtensionSuccessTest() throws Exception {
        Mockito.when(certificateExcelService.parseSerialNumbers(any()))
                .thenReturn(List.of("SN-UPPER"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "TEST_UPPER.XLSX", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy content".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/certificates/parse-excel").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.serialNos[0]").value("SN-UPPER"));
    }

    @Test
    @DisplayName("확장자가 xlsx 또는 xls가 아닌 경우 400 에러를 반환한다")
    void parseInvalidExtensionTest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "content".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/certificates/parse-excel").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("엑셀 파일(.xlsx, .xls)만 업로드 가능합니다."));
    }

    @Test
    @DisplayName("빈 파일이 전달된 경우 400 에러를 반환한다")
    void parseEmptyFileTest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/documents/certificates/parse-excel").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("업로드할 엑셀 파일을 선택해 주세요."));
    }

    @Test
    @DisplayName("서비스에서 IllegalArgumentException 발생 시 400 에러를 반환한다")
    void parseServiceIllegalArgumentExceptionTest() throws Exception {
        Mockito.when(certificateExcelService.parseSerialNumbers(any()))
                .thenThrow(new IllegalArgumentException("엑셀 파일의 A열에서 유효한 Serial No를 찾을 수 없습니다."));

        MockMultipartFile file = new MockMultipartFile(
                "file", "no_serials.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/certificates/parse-excel").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("엑셀 파일의 A열에서 유효한 Serial No를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("파일 손상 등 예기치 않은 오류 발생 시 500 에러를 반환한다")
    void parseServiceGenericExceptionTest() throws Exception {
        Mockito.when(certificateExcelService.parseSerialNumbers(any()))
                .thenThrow(new RuntimeException("POIFS damaged"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "corrupted.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/certificates/parse-excel").file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("엑셀 파일 처리 중 오류가 발생했습니다")));
    }
}
