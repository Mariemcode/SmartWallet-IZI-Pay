package com.pfe.clientdashboard.provider.dto;

import com.pfe.clientdashboard.transaction.dto.TransactionTypeStatDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data @AllArgsConstructor
public class ProviderDetailDTO {
    // Identité
    private String   id;
    private String providerCode;
    private String providerName;

    // KPIs globaux
    private long       totalTransactions;
    private BigDecimal totalAmount;
    private BigDecimal avgAmount;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private long       distinctClients;

    // Positionnement
    private long   rankByCount;
    private long   rankByAmount;
    private double marketShareCount;
    private double marketShareAmount;

    // Dates
    private String firstTransaction;
    private String lastTransaction;

    // Types
    private List<TransactionTypeStatDTO> typeStats;

    // Clients
    private List<TopClientDTO> topClientsByAmount;
    private List<TopClientDTO> topClientsByCount;
    private long       recurringClients;
    private long       occasionalClients;
    private BigDecimal avgAmountPerClient;

    // Distribution montants
    private long   smallCount;
    private long   mediumCount;
    private long   largeCount;
    private double smallPct;
    private double mediumPct;
    private double largePct;
}