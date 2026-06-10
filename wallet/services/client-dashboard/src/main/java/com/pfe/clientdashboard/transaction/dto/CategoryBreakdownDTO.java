package com.pfe.clientdashboard.transaction.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@Builder
public class CategoryBreakdownDTO {

    private String     category;           // ex: "Shopping & Paiements"
    private BigDecimal totalAmount;        // ex: 45230.50
    private Long       totalCount;         // ex: 142
    private Double     percentAmount;      // ex: 36.2  (% du total dépenses)
    private Double     percentCount;       // ex: 41.5  (% du nb total dépenses)
    private BigDecimal averageAmount;      // ticket moyen
}