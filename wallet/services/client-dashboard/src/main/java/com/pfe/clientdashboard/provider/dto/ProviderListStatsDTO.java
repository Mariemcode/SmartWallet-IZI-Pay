package com.pfe.clientdashboard.provider.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class ProviderListStatsDTO {
    private long totalProviders;
    private long grandTotalTransactions;
    private BigDecimal grandTotalAmount;
    private List<ProviderShareDTO> shares;
}