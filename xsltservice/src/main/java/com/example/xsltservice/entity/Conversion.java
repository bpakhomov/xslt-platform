package com.example.xsltservice.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversion")
public class Conversion {
    @Column(name = "id")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversion_type")
    private ConversionType conversionType;

    @Column(name = "conversion_time")
    private double conversionTime;

    @Column(name = "conversion_datetime")
    private LocalDateTime conversionDateTime;

    @Column(name = "source_file_key")
    private String sourceFileKey;

    @Column(name = "result_file_key")
    private String resultFileKey;

    @Column(name = "input_elements_count")
    private Integer inputElementsCount;

    @Column(name = "output_elements_count")
    private Integer outputElementsCount;

    @Column(name = "source_content", columnDefinition = "TEXT")
    private String sourceContent;

    @Column(name = "result_content", columnDefinition = "TEXT")
    private String resultContent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public Conversion() {
    }

    public Conversion(
            ConversionType conversionType,
            double conversionTime,
            LocalDateTime conversionDateTime,
            String sourceFileKey,
            String resultFileKey,
            Integer inputElementsCount,
            Integer outputElementsCount
    ) {
        this(
                conversionType,
                conversionTime,
                conversionDateTime,
                sourceFileKey,
                resultFileKey,
                inputElementsCount,
                outputElementsCount,
                null,
                null
        );
    }

    public Conversion(
            ConversionType conversionType,
            double conversionTime,
            LocalDateTime conversionDateTime,
            String sourceFileKey,
            String resultFileKey,
            Integer inputElementsCount,
            Integer outputElementsCount,
            String sourceContent,
            String resultContent
    ) {
        this(
                conversionType,
                conversionTime,
                conversionDateTime,
                sourceFileKey,
                resultFileKey,
                inputElementsCount,
                outputElementsCount,
                sourceContent,
                resultContent,
                null
        );
    }

    public Conversion(
            ConversionType conversionType,
            double conversionTime,
            LocalDateTime conversionDateTime,
            String sourceFileKey,
            String resultFileKey,
            Integer inputElementsCount,
            Integer outputElementsCount,
            String sourceContent,
            String resultContent,
            User user
    ) {
        this.conversionType = conversionType;
        this.conversionTime = conversionTime;
        this.conversionDateTime = conversionDateTime;
        this.sourceFileKey = sourceFileKey;
        this.resultFileKey = resultFileKey;
        this.inputElementsCount = inputElementsCount;
        this.outputElementsCount = outputElementsCount;
        this.sourceContent = sourceContent;
        this.resultContent = resultContent;
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public ConversionType getConversionType() {
        return conversionType;
    }

    public void setConversionType(ConversionType conversionType) {
        this.conversionType = conversionType;
    }

    public double getConversionTime() {
        return conversionTime;
    }

    public void setConversionTime(double conversionTime) {
        this.conversionTime = conversionTime;
    }

    public LocalDateTime getConversionDateTime() {
        return conversionDateTime;
    }

    public void setConversionDateTime(LocalDateTime conversionDateTime) {
        this.conversionDateTime = conversionDateTime;
    }

    public String getSourceFileKey() {
        return sourceFileKey;
    }

    public void setSourceFileKey(String sourceFileKey) {
        this.sourceFileKey = sourceFileKey;
    }

    public String getResultFileKey() {
        return resultFileKey;
    }

    public void setResultFileKey(String resultFileKey) {
        this.resultFileKey = resultFileKey;
    }

    public Integer getInputElementsCount() {
        return inputElementsCount;
    }

    public void setInputElementsCount(Integer inputElementsCount) {
        this.inputElementsCount = inputElementsCount;
    }

    public Integer getOutputElementsCount() {
        return outputElementsCount;
    }

    public void setOutputElementsCount(Integer outputElementsCount) {
        this.outputElementsCount = outputElementsCount;
    }

    public String getSourceContent() {
        return sourceContent;
    }

    public void setSourceContent(String sourceContent) {
        this.sourceContent = sourceContent;
    }

    public String getResultContent() {
        return resultContent;
    }

    public void setResultContent(String resultContent) {
        this.resultContent = resultContent;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
