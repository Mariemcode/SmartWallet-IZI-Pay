// DailyStatDTO.java
package com.pfe.clientdashboard.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DailyStatDTO {
    private LocalDate date;
    private long count;
    private BigDecimal totalAmount;
}