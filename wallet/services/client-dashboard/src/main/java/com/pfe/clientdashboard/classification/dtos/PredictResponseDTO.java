package com.pfe.clientdashboard.classification.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictResponseDTO {
    @JsonProperty("client_id")
    private String clientId;
    @JsonProperty("cluster_id")
    private Integer clusterId;
    @JsonProperty("profile_name")
    private String profileName;
    @JsonProperty("profile_final")
    private String profileFinal;
    private Double confidence;
    @JsonProperty("is_mixte")
    private Boolean isMixte;
    @JsonProperty("churn_score_30j")
    private Double churnScore30j;
    @JsonProperty("churn_segment")
    private String churnSegment;
    @JsonProperty("ltv_12m_base")
    private Double ltv12mBase;
    @JsonProperty("ltv_12m_optimiste")
    private Double ltv12mOptimiste;
    @JsonProperty("ltv_12m_pessimiste")
    private Double ltv12mPessimiste;
    @JsonProperty("hazard_rate")
    private Double hazardRate;
    @JsonProperty("arpu_mensuel")
    private Double arpuMensuel;
    @JsonProperty("all_probabilities")
    private Map<String, Double> allProbabilities;
    @JsonProperty("predicted_at")
    private String predictedAt;
    @JsonProperty("model_version")
    private String modelVersion;
}
