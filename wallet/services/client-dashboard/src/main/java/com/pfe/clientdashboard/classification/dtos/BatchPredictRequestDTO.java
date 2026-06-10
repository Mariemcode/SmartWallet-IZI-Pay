package com.pfe.clientdashboard.classification.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchPredictRequestDTO {
    @NotEmpty(message = "La liste clients ne peut pas être vide")
    @Valid
    private List<PredictRequestDTO> clients;
}
