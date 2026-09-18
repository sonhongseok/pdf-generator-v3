// backend/src/main/java/com/example/pdfgen/dto/CertificateRequest.java
package com.example.pdfgen.dto;

import java.util.List;

public class CertificateRequest {

    private String certificateDate;
    private String calibrationDate;
    private String expiryDate;
    private List<String> serialNos;
    // 선택 입력: 시퀀스 시작 번호 (미입력 시 기본 로직 사용)
    private String startSequenceNo;
    // 생성 방식: "MERGED"(기본값, 통합 PDF) 또는 "INDIVIDUAL"(개별 PDF ZIP)
    private String generateMode;

    // 기본 생성자
    public CertificateRequest() {
    }

    // 편의 생성자
    public CertificateRequest(String certificateDate, String calibrationDate, String expiryDate, List<String> serialNos) {
        this.certificateDate = certificateDate;
        this.calibrationDate = calibrationDate;
        this.expiryDate = expiryDate;
        this.serialNos = serialNos;
    }

    // Getter and Setter
    public String getCertificateDate() {
        return certificateDate;
    }

    public void setCertificateDate(String certificateDate) {
        this.certificateDate = certificateDate;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getCalibrationDate() {
        return calibrationDate;
    }

    public void setCalibrationDate(String calibrationDate) {
        this.calibrationDate = calibrationDate;
    }

    public List<String> getSerialNos() {
        return serialNos;
    }

    public void setSerialNos(List<String> serialNos) {
        this.serialNos = serialNos;
    }

    public String getStartSequenceNo() {
        return startSequenceNo;
    }

    public void setStartSequenceNo(String startSequenceNo) {
        this.startSequenceNo = startSequenceNo;
    }

    public String getGenerateMode() {
        return generateMode;
    }

    public void setGenerateMode(String generateMode) {
        this.generateMode = generateMode;
    }
}
