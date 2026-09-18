// backend/src/main/java/com/example/pdfgen/domain/CertificateHistory.java
package com.example.pdfgen.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 성적서 발급 이력 도메인 모델.
 * JPA 어노테이션 없는 순수 POJO - Jackson으로 JSON 직렬화/역직렬화됩니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CertificateHistory {

    private Long id;
    private String certificateNo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate certificateDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate calibrationDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdDate;

    private List<CertificateSerialMapping> serialMappings = new ArrayList<>();

    public CertificateHistory() {
        this.createdDate = LocalDateTime.now();
    }

    public CertificateHistory(String certificateNo, LocalDate certificateDate,
                               LocalDate calibrationDate, LocalDate expiryDate) {
        this.certificateNo = certificateNo;
        this.certificateDate = certificateDate;
        this.calibrationDate = calibrationDate;
        this.expiryDate = expiryDate;
        this.createdDate = LocalDateTime.now();
    }

    public void addSerialMapping(CertificateSerialMapping serialMapping) {
        this.serialMappings.add(serialMapping);
    }

    // Getter and Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCertificateNo() {
        return certificateNo;
    }

    public void setCertificateNo(String certificateNo) {
        this.certificateNo = certificateNo;
    }

    public LocalDate getCertificateDate() {
        return certificateDate;
    }

    public void setCertificateDate(LocalDate certificateDate) {
        this.certificateDate = certificateDate;
    }

    public LocalDate getCalibrationDate() {
        return calibrationDate;
    }

    public void setCalibrationDate(LocalDate calibrationDate) {
        this.calibrationDate = calibrationDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public List<CertificateSerialMapping> getSerialMappings() {
        return serialMappings;
    }

    public void setSerialMappings(List<CertificateSerialMapping> serialMappings) {
        this.serialMappings = serialMappings;
    }
}
