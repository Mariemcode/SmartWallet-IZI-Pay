// ProviderStatsDTO.java
package com.pfe.clientdashboard.provider.dto;

import com.pfe.clientdashboard.transaction.dto.TransactionTypeStatDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProviderStatsDTO {
    private String id;
    private String providerCode;
    private String providerName;
    private long totalTransactions;
    private BigDecimal totalAmount;
    private BigDecimal avgAmount;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private long distinctClients;
    private long reversalCount;
    private double reversalRate;
    private List<DailyStatDTO> dailyStats;
    private List<TransactionTypeStatDTO> typeStats;
    private List<TopClientDTO> topClients;
}