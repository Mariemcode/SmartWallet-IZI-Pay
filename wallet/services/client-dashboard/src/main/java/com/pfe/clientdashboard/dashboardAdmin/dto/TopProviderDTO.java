package com.pfe.clientdashboard.dashboardAdmin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopProviderDTO {
    private String     id;
    private String     providerCode;
    private String     providerName;
    private long       transactionCount;
    private BigDecimal totalAmount;
    private double     percentCount;
    private double     percentAmount;
}
