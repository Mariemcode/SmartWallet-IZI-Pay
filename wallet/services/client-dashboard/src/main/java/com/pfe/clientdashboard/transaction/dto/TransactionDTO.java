package com.pfe.clientdashboard.transaction.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
public class TransactionDTO {

    private String id;
    private String clientId;

    // Montant
    private BigDecimal amount;
    private String currency;

    // Date — champ renommé
    private LocalDateTime transactionDate;

    // Flags
    private String reversalFlag;     // "Y" ou "N"

    // Provider (nullable)
    private String providerCode;
    private String providerName;

    // Type transaction
    private String typeCode;
    private String typeTitle;
    private String typeOriginalTitle;
    private String typeCategory;
    private String typeSubCategory;
    private String typeType;         // "C" = Crédit, "D" = Débit
}