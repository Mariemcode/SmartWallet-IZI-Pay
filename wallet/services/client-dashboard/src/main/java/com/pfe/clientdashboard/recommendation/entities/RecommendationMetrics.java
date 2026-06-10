package com.pfe.clientdashboard.recommendation.entities;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "recommendation_metrics_v5")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecommendationMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "profile_name", length = 100)
    private String profileName;

    @Column(name = "precision_score", precision = 6, scale = 4)
    private BigDecimal precisionScore;

    @Column(name = "recall_score", precision = 6, scale = 4)
    private BigDecimal recallScore;

    @Column(name = "f1_score", precision = 6, scale = 4)
    private BigDecimal f1Score;

    @Column(name = "coverage", precision = 6, scale = 4)
    private BigDecimal coverage;

    @Column(name = "acceptance_rate", precision = 6, scale = 4)
    private BigDecimal acceptanceRate;

    @Column(name = "avg_score", precision = 6, scale = 4)
    private BigDecimal avgScore;

    @Column(name = "n_recommendations")
    private Integer nRecommendations;

    @Column(name = "n_offers_generated")
    private Integer nOffersGenerated;

    @Column(name = "evaluation_type", length = 20)
    private String evaluationType = "simulated";

    @Column(name = "computed_at")
    private LocalDateTime computedAt;

    @Column(name = "model_version", length = 20)
    private String modelVersion = "V5.0";

    @PrePersist
    protected void onCreate() {
        computedAt = LocalDateTime.now();
    }
}
