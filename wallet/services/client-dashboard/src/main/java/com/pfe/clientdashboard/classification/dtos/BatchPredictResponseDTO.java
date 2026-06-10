package com.pfe.clientdashboard.classification.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchPredictResponseDTO {
    private List<BatchItemResultDTO> results;
    private Integer count;
    @JsonProperty("n_mixte")
    private Integer nMixte;
    @JsonProperty("n_at_risk")
    private Integer nAtRisk;
    @JsonProperty("pct_at_risk")
    private Double pctAtRisk;
    @JsonProperty("avg_churn")
    private Double avgChurn;
}
