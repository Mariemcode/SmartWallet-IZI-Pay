package com.pfe.clientdashboard.transaction.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SubCategoryBreakdownDTO {

    private String     subCategory;
    private BigDecimal totalAmount;
    private Long       totalCount;
    private BigDecimal averageAmount;
    private Double     percentAmount;
    private Double     percentCount;
}