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
public class ProfileCategoryDTO {
    private String category;
    @JsonProperty("nb_transactions")
    private Long nbTransactions;
    private Double pct;
}
