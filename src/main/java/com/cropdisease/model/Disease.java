package com.cropdisease.model;

import jakarta.persistence.*;

/**
 * One disease (or disorder) of one crop, with farmer-friendly advice and the
 * visual "signature" the leaf analyzer compares a photo against.
 * List fields (symptoms, treatments ...) are stored as text separated by "|".
 */
@Entity
@Table(name = "diseases")
public class Disease {

    @Id
    @Column(length = 60)
    private String code;                 // e.g. "tomato-early-blight"

    @Column(nullable = false, length = 30)
    private String cropCode;             // "general" = can affect any crop

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 120)
    private String pathogen;

    @Column(length = 20)
    private String type;                 // Fungal, Bacterial, Viral, Oomycete, Nutrient

    @Column(length = 20)
    private String severity;             // Low, Moderate, High, Critical

    @Column(length = 30)
    private String yieldLoss;

    @Column(length = 600)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String symptoms;

    @Column(columnDefinition = "TEXT")
    private String organicTreatment;

    @Column(columnDefinition = "TEXT")
    private String chemicalTreatment;

    @Column(columnDefinition = "TEXT")
    private String prevention;

    @Column(length = 300)
    private String favourableConditions;

    @Column(length = 300)
    private String symptomTags;          // comma list used by the symptom checker

    // Visual signature: share of each lesion colour (0..1) + spread pattern
    private double sigYellow;
    private double sigBrown;
    private double sigDark;
    private double sigWhite;
    private double sigRust;

    @Column(length = 20)
    private String pattern;              // SPOTS, BLOTCH, MARGIN, DIFFUSE, POWDER, PUSTULES

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCropCode() { return cropCode; }
    public void setCropCode(String cropCode) { this.cropCode = cropCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPathogen() { return pathogen; }
    public void setPathogen(String pathogen) { this.pathogen = pathogen; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getYieldLoss() { return yieldLoss; }
    public void setYieldLoss(String yieldLoss) { this.yieldLoss = yieldLoss; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }
    public String getOrganicTreatment() { return organicTreatment; }
    public void setOrganicTreatment(String organicTreatment) { this.organicTreatment = organicTreatment; }
    public String getChemicalTreatment() { return chemicalTreatment; }
    public void setChemicalTreatment(String chemicalTreatment) { this.chemicalTreatment = chemicalTreatment; }
    public String getPrevention() { return prevention; }
    public void setPrevention(String prevention) { this.prevention = prevention; }
    public String getFavourableConditions() { return favourableConditions; }
    public void setFavourableConditions(String favourableConditions) { this.favourableConditions = favourableConditions; }
    public String getSymptomTags() { return symptomTags; }
    public void setSymptomTags(String symptomTags) { this.symptomTags = symptomTags; }
    public double getSigYellow() { return sigYellow; }
    public void setSigYellow(double sigYellow) { this.sigYellow = sigYellow; }
    public double getSigBrown() { return sigBrown; }
    public void setSigBrown(double sigBrown) { this.sigBrown = sigBrown; }
    public double getSigDark() { return sigDark; }
    public void setSigDark(double sigDark) { this.sigDark = sigDark; }
    public double getSigWhite() { return sigWhite; }
    public void setSigWhite(double sigWhite) { this.sigWhite = sigWhite; }
    public double getSigRust() { return sigRust; }
    public void setSigRust(double sigRust) { this.sigRust = sigRust; }
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
}
