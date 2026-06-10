package com.pfe.clientdashboard.recommendation.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RecommendationStatusDTO {

    @NotBlank
    @Pattern(regexp = "APPROVED|REJECTED")
    private String status;

    private String note;
}
