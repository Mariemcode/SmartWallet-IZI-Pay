package com.pfe.clientdashboard.recommendation.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class BulkApproveDTO {

    @NotBlank(message = "profile_name requis")
    private String profileName;
}
