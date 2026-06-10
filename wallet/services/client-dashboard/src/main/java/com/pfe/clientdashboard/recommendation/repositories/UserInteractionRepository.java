package com.pfe.clientdashboard.recommendation.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.pfe.clientdashboard.recommendation.entities.UserInteraction;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ★ FIX clientId String + nouvelle méthode pour le scheduler
 * (curseur efficace au lieu de findAll().stream().filter).
 */
@Repository
public interface UserInteractionRepository extends JpaRepository<UserInteraction, Long> {

    List<UserInteraction> findByClientIdOrderByRecordedAtDesc(String clientId);

    List<UserInteraction> findByOfferCode(String offerCode);

    @Query("""
        SELECT ui.action, COUNT(ui)
        FROM UserInteraction ui
        WHERE ui.offerCode = :offerCode
        GROUP BY ui.action
    """)
    List<Object[]> countByActionForOffer(@Param("offerCode") String offerCode);

    long countByClientIdAndOfferCodeAndAction(
            String clientId, String offerCode, UserInteraction.InteractionAction action);

    /**
     * ★ NOUVEAU — Récupère les interactions accepted/rejected postérieures
     * à un curseur. Utilisé par MarketingFeedbackScheduler pour pousser
     * uniquement les nouvelles vers FastAPI (perf : indexé par recordedAt).
     */
    @Query("""
        SELECT ui FROM UserInteraction ui
        WHERE ui.recordedAt > :cursor
          AND ui.action IN (com.pfe.clientdashboard.recommendation.entities.UserInteraction.InteractionAction.accepted,
                            com.pfe.clientdashboard.recommendation.entities.UserInteraction.InteractionAction.rejected)
        ORDER BY ui.recordedAt ASC
    """)
    List<UserInteraction> findFeedbackAfterCursor(@Param("cursor") LocalDateTime cursor);

    /**
     * ★ NOUVEAU — Liste paginée des dernières interactions tout statut
     * (pour la vue admin "qui a fait quoi"). Limit par défaut côté caller.
     */
    @Query("""
        SELECT ui FROM UserInteraction ui
        ORDER BY ui.recordedAt DESC
    """)
    List<UserInteraction> findRecent(org.springframework.data.domain.Pageable pageable);
}
