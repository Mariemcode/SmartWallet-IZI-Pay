package com.pfe.clientdashboard.recommendation.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * UserInteraction
 * ════════════════
 * Un signal de feedback utilisateur sur une offre : viewed/clicked/accepted/rejected.
 * Alimente l'auto-apprentissage du modèle de scoring marketing
 * (lu par MarketingFeedbackScheduler → POST /marketing-feedback/batch → FastAPI).
 *
 * ★ FIX clientId : auparavant `UUID`, ce qui plantait silencieusement pour les
 * clients dont l'id (table `client.id`) n'est pas un UUID valide. Désormais `String`.
 */
@Entity
@Table(name = "user_interactions_v5")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "offer_code", nullable = false, length = 40)
    private String offerCode;

    @Column(name = "action", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private InteractionAction action;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        recordedAt = LocalDateTime.now();
    }

    public enum InteractionAction {
        viewed, clicked, accepted, rejected
    }
}
