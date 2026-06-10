package com.pfe.clientdashboard.recommendation.dtos;


import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OfferResponseDTO {
    private Long id;
    private String offerCode;
    private String title;
    private String type;
    private String providerName;
    private String category;
    private BigDecimal cashbackPct;
    private BigDecimal discountPct;
    private BigDecimal minAmount;
    private List<String> targetProfiles;
    private BigDecimal boost;
    private String description;
    private String status;
    private String generationMethod;
    private String generationRun;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
