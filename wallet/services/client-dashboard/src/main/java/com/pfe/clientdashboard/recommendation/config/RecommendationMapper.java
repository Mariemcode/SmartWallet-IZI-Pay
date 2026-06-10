package com.pfe.clientdashboard.recommendation.config;

import org.springframework.stereotype.Component;
import com.pfe.clientdashboard.recommendation.dtos.*;
import com.pfe.clientdashboard.recommendation.entities.GeneratedOffer;
import com.pfe.clientdashboard.recommendation.entities.Recommendation;

@Component
public class RecommendationMapper {

    public RecommendationResponseDTO toDto(Recommendation r) {
        return RecommendationResponseDTO.builder()
                .id(r.getId())
                .profileName(r.getProfileName())
                .clusterId(r.getClusterId())
                .offerCode(r.getOfferCode())
                .score(r.getScore())
                .scoreProfile(r.getScoreProfile())
                .scoreProvider(r.getScoreProvider())
                .scoreChurnBoost(r.getScoreChurnBoost())
                .isTargeted(r.getIsTargeted())
                .recommendationType(r.getRecommendationType())
                .status(r.getStatus() != null ? r.getStatus().name() : null)
                .adminNote(r.getAdminNote())
                .generatedAt(r.getGeneratedAt())
                .approvedAt(r.getApprovedAt())
                .rejectedAt(r.getRejectedAt())
                .description(r.getDescription())   // ← description de la recommandation
                .build();
    }

    // Enrichit le DTO avec les détails de l'offre (sans écraser la description)
    public void enrichWithOffer(RecommendationResponseDTO dto, GeneratedOffer offer) {
        if (offer != null) {
            dto.setOfferTitle(offer.getTitle());
            dto.setOfferType(offer.getType());
            dto.setCashbackPct(offer.getCashbackPct());
            dto.setDiscountPct(offer.getDiscountPct());
            // ⚠️ Ne pas modifier dto.getDescription() – ici on ne fait rien
        }
    }
}