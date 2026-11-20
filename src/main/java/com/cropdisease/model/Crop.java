package com.cropdisease.model;

import jakarta.persistence.*;

@Entity
@Table(name = "crops")
public class Crop {

    @Id
    @Column(length = 30)
    private String code;            // e.g. "tomato"

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 80)
    private String scientificName;

    @Column(length = 16)
    private String emoji;

    @Column(length = 40)
    private String season;

    @Column(length = 300)
    private String description;

    public Crop() { }

    public Crop(String code, String name, String scientificName, String emoji, String season, String description) {
        this.code = code;
        this.name = name;
        this.scientificName = scientificName;
        this.emoji = emoji;
        this.season = season;
        this.description = description;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getScientificName() { return scientificName; }
    public void setScientificName(String scientificName) { this.scientificName = scientificName; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
