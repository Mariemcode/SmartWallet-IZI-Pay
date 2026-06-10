package com.pfe.clientdashboard.recommendation.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GenerateDescriptionRequest {
    @JsonProperty("offer_code")
    private String offerCode;
}