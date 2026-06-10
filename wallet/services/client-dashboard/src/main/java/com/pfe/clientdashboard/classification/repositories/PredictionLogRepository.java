package com.pfe.clientdashboard.classification.repositories;


import com.pfe.clientdashboard.classification.entities.PredictionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PredictionLogRepository extends JpaRepository<PredictionLogEntity, UUID> {
}
