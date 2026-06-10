package com.pfe.clientdashboard.classification.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictRequestDTO {

    @NotNull(message = "client_id est obligatoire")
    @JsonProperty("client_id")
    private UUID clientId;

    @NotNull(message = "features est obligatoire")
    private Map<String, Double> features;
}
