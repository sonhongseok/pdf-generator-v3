// backend/src/main/java/com/example/pdfgen/dto/CertificateHistoryResponse.java
package com.example.pdfgen.dto;

import com.example.pdfgen.domain.CertificateHistory;
import com.example.pdfgen.domain.CertificateSerialMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class CertificateHistoryResponse {

    private Long id;
    private String certificateNo;
    private LocalDate certificateDate;
    private LocalDate calibrationDate;
    private LocalDate expiryDate;
    private LocalDateTime createdDate;
    private List<String> serialNos;

    public CertificateHistoryResponse() {
    }

    public CertificateHistoryResponse(CertificateHistory history) {
        this.id = history.getId();
        this.certificateNo = history.getCertificateNo();
        this.certificateDate = history.getCertificateDate();
        this.calibrationDate = history.getCalibrationDate();
        this.expiryDate = history.getExpiryDate();
        this.createdDate = history.getCreatedDate();
        
        if (history.getSerialMappings() != null) {
            this.serialNos = history.getSerialMappings().stream()
                    .sorted(java.util.Comparator.comparingInt(CertificateSerialMapping::getPageNumber))
                    .map(CertificateSerialMapping::getSerialNo)
                    .collect(Collectors.toList());
        }
    }

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

    public List<String> getSerialNos() {
        return serialNos;
    }

    public void setSerialNos(List<String> serialNos) {
        this.serialNos = serialNos;
    }
}
