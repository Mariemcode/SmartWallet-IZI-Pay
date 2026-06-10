package com.pfe.clientdashboard.transaction.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@Builder
public class GlobalSummaryDTO {

    // ── Dépenses ──────────────────────────────
    private BigDecimal totalExpenseAmount;
    private Long       totalExpenseCount;
    private Double     expensePercentAmount;   // % du total global en montant
    private Double     expensePercentCount;    // % du total global en nombre

    // ── Revenus ───────────────────────────────
    private BigDecimal totalRevenueAmount;
    private Long       totalRevenueCount;
    private Double     revenuePercentAmount;
    private Double     revenuePercentCount;

    // ── Global ────────────────────────────────
    private BigDecimal grandTotalAmount;
    private Long       grandTotalCount;
}