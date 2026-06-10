package com.pfe.clientdashboard.classification.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileTransactionsCountDTO {
    @JsonProperty("cluster_id")
    private Integer clusterId;

    @JsonProperty("total_transactions")
    private Long totalTransactions;

    @JsonProperty("avg_transactions")
    private Double avgTransactions;
}
