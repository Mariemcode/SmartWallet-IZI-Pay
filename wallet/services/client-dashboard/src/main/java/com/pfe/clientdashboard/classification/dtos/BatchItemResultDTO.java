package com.pfe.clientdashboard.classification.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchItemResultDTO {
    @JsonProperty("client_id")
    private String clientId;
    @JsonProperty("cluster_id")
    private Integer clusterId;
    @JsonProperty("profile_name")
    private String profileName;
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
}