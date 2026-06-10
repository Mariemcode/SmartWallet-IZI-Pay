package com.pfe.clientdashboard.recommendation.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.pfe.clientdashboard.recommendation.entities.RecommendationMetrics;

import java.util.List;

@Repository
public interface RecommendationMetricsRepository
        extends JpaRepository<RecommendationMetrics, Integer> {

    List<RecommendationMetrics> findByEvaluationTypeOrderByF1ScoreDesc(String evaluationType);

    List<RecommendationMetrics> findByProfileNameOrderByComputedAtDesc(String profileName);

    @Query("""
        SELECT AVG(m.f1Score), AVG(m.precisionScore), AVG(m.recallScore)
        FROM RecommendationMetrics m
        WHERE m.evaluationType = :type
    """)
    Object[] getAverageMetrics(@Param("type") String evaluationType);
}
