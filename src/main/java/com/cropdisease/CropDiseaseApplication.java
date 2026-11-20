package com.cropdisease;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CropDisease AI Pro
 * Leaf-photo disease detection, crop doctor, weather disease-risk forecast,
 * spray calculator and farm assistant. Data lives in MySQL ("cropdisease").
 */
@SpringBootApplication
public class CropDiseaseApplication {
    public static void main(String[] args) {
        SpringApplication.run(CropDiseaseApplication.class, args);
    }
}
