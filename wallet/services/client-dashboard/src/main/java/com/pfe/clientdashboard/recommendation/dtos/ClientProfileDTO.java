package com.pfe.clientdashboard.recommendation.dtos;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Getter @Setter @Builder
public class ClientProfileDTO {
    private String clientId;
    private String firstName;
    private String lastName;
    private String profileName;
    private Double confidenceScore;
    private Double churnScore30j;
    private Double rfmScore;
}