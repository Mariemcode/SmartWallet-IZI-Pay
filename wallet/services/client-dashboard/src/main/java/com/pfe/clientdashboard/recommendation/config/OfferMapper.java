package com.pfe.clientdashboard.recommendation.config;


import org.springframework.stereotype.Component;
import com.pfe.clientdashboard.recommendation.dtos.*;
import com.pfe.clientdashboard.recommendation.entities.GeneratedOffer;

@Component
public class OfferMapper {

    public OfferResponseDTO toDto(GeneratedOffer o) {
        return OfferResponseDTO.builder()
                .id(o.getId())
                .offerCode(o.getOfferCode())
                .title(o.getTitle())
                .type(o.getType())
                .providerName(o.getProviderName())
                .category(o.getCategory())
                .cashbackPct(o.getCashbackPct())
                .discountPct(o.getDiscountPct())
                .minAmount(o.getMinAmount())
                .targetProfiles(o.getTargetProfiles())
                .boost(o.getBoost())
                .description(o.getDescription())
                .status(o.getStatus() != null ? o.getStatus().name() : null)
                .generationMethod(o.getGenerationMethod())
                .generationRun(o.getGenerationRun())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .build();
    }

    public GeneratedOffer toEntity(OfferRequestDTO dto) {
        return GeneratedOffer.builder()
                .title(dto.getTitle())
                .type(dto.getType())
                .providerName(dto.getProviderName())
                .category(dto.getCategory())
                .cashbackPct(dto.getCashbackPct())
                .discountPct(dto.getDiscountPct())
                .minAmount(dto.getMinAmount())
                .targetProfiles(dto.getTargetProfiles())
                .boost(dto.getBoost())
                .description(dto.getDescription())
                .build();
    }

    public void updateEntity(GeneratedOffer offer, OfferUpdateDTO dto) {
        if (dto.getTitle() != null)          offer.setTitle(dto.getTitle());
        if (dto.getType() != null)           offer.setType(dto.getType());
        if (dto.getProviderName() != null)   offer.setProviderName(dto.getProviderName());
        if (dto.getCategory() != null)       offer.setCategory(dto.getCategory());
        if (dto.getCashbackPct() != null)    offer.setCashbackPct(dto.getCashbackPct());
        if (dto.getDiscountPct() != null)    offer.setDiscountPct(dto.getDiscountPct());
        if (dto.getMinAmount() != null)      offer.setMinAmount(dto.getMinAmount());
        if (dto.getTargetProfiles() != null) offer.setTargetProfiles(dto.getTargetProfiles());
        if (dto.getBoost() != null)          offer.setBoost(dto.getBoost());
        if (dto.getDescription() != null)    offer.setDescription(dto.getDescription());
    }
}
