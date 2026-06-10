package com.pfe.clientdashboard.recommendation.entities;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "recommendations_v5")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_name", nullable = false, length = 100)
    private String profileName;

    @Column(name = "cluster_id")
    private Integer clusterId;

    @Column(name = "offer_code", nullable = false, length = 40)
    private String offerCode;

    @Column(name = "score", nullable = false, precision = 6, scale = 4)
    private BigDecimal score;

    @Column(name = "score_profile", precision = 6, scale = 4)
    private BigDecimal scoreProfile;

    @Column(name = "score_provider", precision = 6, scale = 4)
    private BigDecimal scoreProvider;

    @Column(name = "score_category", precision = 6, scale = 4)
    private BigDecimal scoreCategory;

    @Column(name = "score_amount", precision = 6, scale = 4)
    private BigDecimal scoreAmount;

    @Column(name = "score_loyalty", precision = 6, scale = 4)
    private BigDecimal scoreLoyalty;

    @Column(name = "score_churn_boost", precision = 6, scale = 4)
    private BigDecimal scoreChurnBoost;

    @Column(name = "is_targeted")
    private Boolean isTargeted = false;

    @Column(name = "recommendation_type", length = 30)
    private String recommendationType;

    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    private RecommendationStatus status = RecommendationStatus.PENDING;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;   // ← NOUVEAU

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "model_version", length = 20)
    private String modelVersion = "V5.0";

    @PrePersist
    protected void onCreate() {
        generatedAt = LocalDateTime.now();
    }

    public enum RecommendationStatus {
        PENDING, APPROVED, REJECTED
    }
}