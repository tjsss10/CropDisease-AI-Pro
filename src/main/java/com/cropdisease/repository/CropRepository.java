package com.cropdisease.repository;

import com.cropdisease.model.Crop;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CropRepository extends JpaRepository<Crop, String> {
}
