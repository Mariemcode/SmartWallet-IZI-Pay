package com.pfe.clientdashboard.recommendation.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class OfferStatusDTO {

    @NotBlank
    @Pattern(regexp = "ACTIVE|INACTIVE", message = "Statut doit être ACTIVE ou INACTIVE")
    private String status;
}
