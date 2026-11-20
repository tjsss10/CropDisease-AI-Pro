package com.cropdisease.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Photos (original leaf + AI heatmap) stored inside MySQL, served at /media/{id}. */
@Entity
@Table(name = "media")
public class Media {

    @Id
    @Column(length = 64)
    private String id;

    @Column(length = 40)
    private String contentType;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] data;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Media() { }

    public Media(String id, String contentType, byte[] data) {
        this.id = id;
        this.contentType = contentType;
        this.data = data;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
