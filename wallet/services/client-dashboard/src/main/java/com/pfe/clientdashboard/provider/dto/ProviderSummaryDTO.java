// ProviderSummaryDTO.java
package com.pfe.clientdashboard.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ProviderSummaryDTO {
    private String id;
    private String providerCode;
    private String providerName;
    private long totalTransactions;
    private BigDecimal totalAmount;
    private long debitCount;
    private BigDecimal debitAmount;
    private long creditCount;
    private BigDecimal creditAmount;
    private double debitPercentCount;
    private double creditPercentCount;
    private double debitPercentAmount;
    private double creditPercentAmount;
}