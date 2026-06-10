package com.pfe.clientdashboard.dashboardAdmin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopClientDTO {
    private String     clientId;
    private String     firstName;
    private String     lastName;
    private long       transactionCount;
    private BigDecimal totalAmount;
}
