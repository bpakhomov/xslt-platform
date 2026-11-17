package com.example.xsltservice.entity;

import com.fasterxml.jackson.annotation.JsonRawValue;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversion")
public class Conversion {
    @Column(name = "id")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversion_time")
    private double conversionTime;

    @Column(name = "conversion_datetime")
    private LocalDateTime conversionDateTime;

    @Column(name = "json_value", columnDefinition = "TEXT")
    @JsonRawValue
    private String jsonValue;

    @Column(name = "xml_tags_count")
    private int xmlTagsCount;

    @Column(name = "json_keys_count")
    private int  jsonKeysCount;

    public Conversion() {
    }

    public Conversion(double conversionTime, LocalDateTime conversionDateTime, String jsonValue, int xmlTagsCount, int jsonKeysCount) {
        this.conversionTime = conversionTime;
        this.conversionDateTime = conversionDateTime;
        this.jsonValue = jsonValue;
        this.xmlTagsCount = xmlTagsCount;
        this.jsonKeysCount = jsonKeysCount;
    }

    public void setConversionTime(double conversionTime) {
        this.conversionTime = conversionTime;
    }

    public void setConversionDateTime(LocalDateTime conversionDateTime) {
        this.conversionDateTime = conversionDateTime;
    }

    public void setJsonValue(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    public void setXmlTagsCount(int xmlTagsCount) {
        this.xmlTagsCount = xmlTagsCount;
    }

    public void setJsonKeysCount(int jsonKeysCount) {
        this.jsonKeysCount = jsonKeysCount;
    }

    public Long getId() {
        return id;
    }

    public double getConversionTime() {
        return conversionTime;
    }

    public LocalDateTime getConversionDateTime() {
        return conversionDateTime;
    }

    public String getJsonValue() {
        return jsonValue;
    }

    public int getXmlTagsCount() {
        return xmlTagsCount;
    }

    public int getJsonKeysCount() {
        return jsonKeysCount;
    }
}
