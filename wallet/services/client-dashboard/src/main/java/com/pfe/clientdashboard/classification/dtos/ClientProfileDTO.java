package com.pfe.clientdashboard.classification.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientProfileDTO {

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("cluster_id")
    private Integer clusterId;

    @JsonProperty("profile_name")
    private String profileName;

    @JsonProperty("profile_final")
    private String profileFinal;

    @JsonProperty("is_mixte")
    private Boolean isMixte;

    @JsonProperty("confidence_score")
    private Double confidenceScore;

    @JsonProperty("gbm_confidence")
    private Double gbmConfidence;

    @JsonProperty("model_version")
    private String modelVersion;

    // Métriques comportementales
    @JsonProperty("total_transactions")
    private Integer totalTransactions;

    @JsonProperty("freq_mensuelle")
    private Double freqMensuelle;

    @JsonProperty("montant_moyen")
    private Double montantMoyen;

    @JsonProperty("montant_median")
    private Double montantMedian;

    @JsonProperty("montant_total")
    private Double montantTotal;

    private Double regularite;

    @JsonProperty("rfm_score")
    private Double rfmScore;

    @JsonProperty("loyalty_score")
    private Double loyaltyScore;

    @JsonProperty("momentum_court")
    private Double momentumCourt;

    @JsonProperty("momentum_long")
    private Double momentumLong;

    @JsonProperty("recence_jours")
    private Integer recenceJours;

    // KPIs
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

    @JsonProperty("assigned_at")
    private LocalDateTime assignedAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
