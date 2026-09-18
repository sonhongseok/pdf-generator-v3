// backend/src/main/java/com/example/pdfgen/domain/CertificateSerialMapping.java
package com.example.pdfgen.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 성적서-시리얼 매핑 도메인 모델.
 * JPA 어노테이션 없는 순수 POJO - JSON 직렬화/역직렬화됩니다.
 * 순환 참조 방지를 위해 CertificateHistory 역참조 필드를 제거합니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CertificateSerialMapping {

    private Long id;
    private String serialNo;
    private Integer pageNumber;
    private Integer sequenceNo;

    public CertificateSerialMapping() {
    }

    public CertificateSerialMapping(String serialNo, Integer pageNumber, Integer sequenceNo) {
        this.serialNo = serialNo;
        this.pageNumber = pageNumber;
        this.sequenceNo = sequenceNo;
    }

    // Getter and Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public Integer getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(Integer sequenceNo) {
        this.sequenceNo = sequenceNo;
    }
}
