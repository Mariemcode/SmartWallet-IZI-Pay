package com.pfe.clientdashboard.recommendation.dtos;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Représente une offre envoyée à un client côté mobile.
 * Sert à l'affichage dans Flutter + aux actions ACCEPTED / REJECTED.
 * <p>
 * GET /api/client-offers/{clientId}
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientOfferDTO {

    private Long id;                    // id de ClientRecommendation
    private String offerCode;
    private String title;
    private String type;
    private String providerName;
    private String category;
    private BigDecimal cashbackPct;
    private BigDecimal discountPct;
    private BigDecimal minAmount;
    private String description;
    private String status;              // PENDING | SENT | OPENED | ACCEPTED | REJECTED
    private LocalDateTime sentAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime rejectedAt;

    // Champs offre complémentaires pour l'affichage mobile
    private String generationMethod;
    private BigDecimal boost;
}