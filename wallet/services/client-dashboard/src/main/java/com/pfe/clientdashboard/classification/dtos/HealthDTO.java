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
public class HealthDTO {
    private String status;
    private String model;
    private Boolean loaded;
    private DriftInfo drift;
    private String timestamp;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DriftInfo {
        @JsonProperty("psi_max")
        private Double psiMax;
        private String status;
        @JsonProperty("churn_pct_high_risk")
        private Double churnPctHighRisk;
        @JsonProperty("n_clients")
        private Integer nClients;
        @JsonProperty("last_run")
        private String lastRun;
    }
}
