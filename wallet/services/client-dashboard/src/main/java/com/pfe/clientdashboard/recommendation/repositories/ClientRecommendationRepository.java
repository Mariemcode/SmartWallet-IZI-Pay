package com.pfe.clientdashboard.recommendation.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.pfe.clientdashboard.recommendation.entities.ClientRecommendation;

import java.util.List;
import java.util.Optional;

/**
 * ★ FIX clientId String : tous les types UUID → String pour cohérence avec
 * Client.id (qui peut être un identifiant non-UUID).
 */
@Repository
public interface ClientRecommendationRepository
        extends JpaRepository<ClientRecommendation, Long> {

    List<ClientRecommendation> findByClientIdOrderByGeneratedAtDesc(String clientId);

    Page<ClientRecommendation> findByClientId(String clientId, Pageable pageable);

    @Query("""
        SELECT cr FROM ClientRecommendation cr
        WHERE cr.clientId = :clientId
          AND cr.status IN ('PENDING','SENT')
        ORDER BY cr.personalScore DESC
    """)
    List<ClientRecommendation> findActiveForClient(@Param("clientId") String clientId);

    long countByClientIdAndStatus(String clientId, ClientRecommendation.ClientRecoStatus status);

    Optional<ClientRecommendation> findByClientIdAndRecommendationId(String clientId, Long recommendationId);

    List<ClientRecommendation> findByClientIdAndStatusIn(
            String clientId,
            List<ClientRecommendation.ClientRecoStatus> statuses
    );

    List<ClientRecommendation> findByClientId(String clientId);

    /** Toutes les ClientRecommendation issues d'une Recommendation donnée. */
    List<ClientRecommendation> findByRecommendationId(Long recommendationId);

    /** Liste des distinct clientIds qui ont reçu cette recommandation (pour stats). */
    @Query("SELECT DISTINCT cr.clientId FROM ClientRecommendation cr WHERE cr.recommendationId = :recommendationId")
    List<String> findDistinctClientIdsByRecommendationId(@Param("recommendationId") Long recommendationId);
}
