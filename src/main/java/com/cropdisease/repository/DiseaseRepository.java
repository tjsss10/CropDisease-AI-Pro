package com.cropdisease.repository;

import com.cropdisease.model.Disease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DiseaseRepository extends JpaRepository<Disease, String> {
    List<Disease> findByCropCodeOrderByName(String cropCode);
    List<Disease> findByCropCodeIn(Collection<String> cropCodes);
}
