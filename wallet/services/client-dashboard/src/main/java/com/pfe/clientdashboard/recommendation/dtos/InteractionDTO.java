package com.pfe.clientdashboard.recommendation.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class InteractionDTO {

    @NotBlank
    @Size(max = 64)
    private String clientId;

    @NotBlank
    private String offerCode;

    @NotBlank
    @jakarta.validation.constraints.Pattern(regexp = "viewed|clicked|accepted|rejected")
    private String action;
}
