package com.pfe.clientdashboard.dashboardAdmin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor
public class DashboardKpiDTO {
    private long       totalTransactions;
    private BigDecimal totalAmount;
    private BigDecimal avgAmount;
    private long       totalClients;
    private long       totalProviders;
    // reversalRate supprimé
}