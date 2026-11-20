package com.cropdisease.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A saved leaf scan (only for signed-in farmers; guest scans are never stored). */
@Entity
@Table(name = "scans", indexes = @Index(name = "idx_scans_user", columnList = "userId,createdAt"))
public class Scan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(length = 30)
    private String cropCode;

    @Column(length = 60)
    private String diseaseCode;          // null when healthy

    @Column(length = 80)
    private String diagnosis;            // "Healthy leaf" or disease name

    @Column(length = 20)
    private String status;               // HEALTHY, DISEASED, UNCERTAIN

    private double confidence;           // 0..1
    private double severityPct;          // % of leaf area affected
    private int healthScore;             // 0..100

    @Column(length = 20)
    private String stage;                // Trace, Mild, Moderate, Severe, Critical

    @Column(length = 120)
    private String imagePath;

    @Column(length = 120)
    private String heatmapPath;

    @JsonIgnore
    @Column(columnDefinition = "MEDIUMTEXT")
    private String resultJson;           // full analysis, re-sent when the scan is reopened

    @Column(length = 500)
    private String notes;

    @Column(length = 80)
    private String fieldName;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCropCode() { return cropCode; }
    public void setCropCode(String cropCode) { this.cropCode = cropCode; }
    public String getDiseaseCode() { return diseaseCode; }
    public void setDiseaseCode(String diseaseCode) { this.diseaseCode = diseaseCode; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public double getSeverityPct() { return severityPct; }
    public void setSeverityPct(double severityPct) { this.severityPct = severityPct; }
    public int getHealthScore() { return healthScore; }
    public void setHealthScore(int healthScore) { this.healthScore = healthScore; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public String getHeatmapPath() { return heatmapPath; }
    public void setHeatmapPath(String heatmapPath) { this.heatmapPath = heatmapPath; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
