package com.pfe.clientdashboard.classification.dtos;

import lombok.Data;

@Data
public class ProfileSummaryDTO {
    private Integer clusterId;
    private String profileName;
    private String description;
    private Integer nClients;
    private Double pctClients;
    private Double silMean;
    private Double silMin;
    private Boolean isFragile;
    private String dominantCategory;
    private Double dominantCategoryRatio;
    private Double churnScore30j;
    private Double arpuMensuel;
    private Double ltv12mBase;
    private Double scoreRisque;
    // autres champs selon besoin
}