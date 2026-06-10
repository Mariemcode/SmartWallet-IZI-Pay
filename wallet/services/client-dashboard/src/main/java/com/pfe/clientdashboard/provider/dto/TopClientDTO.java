package com.pfe.clientdashboard.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data @AllArgsConstructor
public class TopClientDTO {
    private String       clientId;
    private String     firstName;
    private String     lastName;
    private long       transactionCount;
    private BigDecimal totalAmount;
}