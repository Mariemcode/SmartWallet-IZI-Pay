package com.pfe.clientdashboard.recommendation.dtos;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ManualRecommendationDTO {

    @NotBlank(message = "profile_name requis")
    private String profileName;

    @NotBlank(message = "offer_code requis")
    private String offerCode;

    @DecimalMin("0.0") @DecimalMax("1.0")
    private Double score = 0.8;

    private String note;

    private String description;   // ← Nouveau
}