package com.pfe.clientdashboard.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ProviderShareDTO {
    private String id;
    private String providerCode;
    private String providerName;
    private long transactionCount;
    private BigDecimal totalAmount;
    private double percentCount;
    private double percentAmount;
}