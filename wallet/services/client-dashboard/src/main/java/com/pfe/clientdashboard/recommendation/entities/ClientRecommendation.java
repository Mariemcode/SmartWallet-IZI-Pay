package com.pfe.clientdashboard.recommendation.entities;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ClientRecommendation
 * ═════════════════════
 * Une recommandation/offre marketing envoyée à un client spécifique.
 *
 * ★ FIX clientId : auparavant `UUID`, ce qui plantait silencieusement
 * pour les clients dont l'id (table `client.id`) n'est pas un UUID valide.
 * Désormais `String` pour matcher `Client.id` et `ClientProfileEntity.clientId`.
 */
@Entity
@Table(name = "client_recommendations_v5")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClientRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "recommendation_id")
    private Long recommendationId;

    @Column(name = "offer_code", nullable = false, length = 40)
    private String offerCode;

    @Column(name = "profile_name", length = 100)
    private String profileName;

    @Column(name = "cluster_id")
    private Integer clusterId;

    @Column(name = "personal_score", precision = 6, scale = 4)
    private BigDecimal personalScore;

    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    private ClientRecoStatus status = ClientRecoStatus.PENDING;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @PrePersist
    protected void onCreate() {
        generatedAt = LocalDateTime.now();
    }

    public enum ClientRecoStatus {
        PENDING, SENT, OPENED, ACCEPTED, REJECTED
    }
}
