package com.pfe.clientdashboard.recommendation.dtos;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecommendationResponseDTO {
    private Long id;
    private String profileName;
    private Integer clusterId;
    private String offerCode;
    private String offerTitle;
    private String offerType;
    private BigDecimal cashbackPct;
    private BigDecimal discountPct;
    private String description;      // ← description de la recommandation (générée)
    private BigDecimal score;
    private BigDecimal scoreProfile;
    private BigDecimal scoreProvider;
    private BigDecimal scoreChurnBoost;
    private Boolean isTargeted;
    private String recommendationType;
    private String status;
    private String adminNote;
    private LocalDateTime generatedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
}