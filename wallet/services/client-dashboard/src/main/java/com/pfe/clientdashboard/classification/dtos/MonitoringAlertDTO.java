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
public class MonitoringAlertDTO {
    private Long id;
    @JsonProperty("alert_type")
    private String alertType;
    @JsonProperty("feature_name")
    private String featureName;
    @JsonProperty("psi_value")
    private Double psiValue;
    @JsonProperty("ks_pvalue")
    private Double ksPvalue;
    private String severity;
    private String message;
    @JsonProperty("model_version")
    private String modelVersion;
    @JsonProperty("triggered_at")
    private String triggeredAt;
    private Boolean resolved;
}
