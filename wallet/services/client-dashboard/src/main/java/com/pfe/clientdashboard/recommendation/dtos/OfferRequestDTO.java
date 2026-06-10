package com.pfe.clientdashboard.recommendation.dtos;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OfferRequestDTO {

    @NotBlank(message = "Le titre est obligatoire")
    @Size(max = 300)
    private String title;

    @NotBlank(message = "Le type est obligatoire")
    private String type;

    private String providerName;
    private String category;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private BigDecimal cashbackPct = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private BigDecimal discountPct = BigDecimal.ZERO;

    @DecimalMin("0.0")
    private BigDecimal minAmount = BigDecimal.ZERO;

    @NotNull @NotEmpty
    private List<String> targetProfiles;

    @DecimalMin("0.1")
    private BigDecimal boost = BigDecimal.ONE;

    private String description;
}
