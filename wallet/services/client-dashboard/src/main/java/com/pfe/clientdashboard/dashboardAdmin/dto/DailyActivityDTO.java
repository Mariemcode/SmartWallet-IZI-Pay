package com.pfe.clientdashboard.dashboardAdmin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyActivityDTO {
    private String     date;        // "2024-03-15"
    private long       count;
    private BigDecimal totalAmount;
}
