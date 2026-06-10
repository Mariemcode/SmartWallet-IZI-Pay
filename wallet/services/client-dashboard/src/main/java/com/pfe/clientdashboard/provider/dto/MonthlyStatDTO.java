package com.pfe.clientdashboard.provider.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data @AllArgsConstructor
public class MonthlyStatDTO {
    private int year;
    private int month;
    private long count;
    private BigDecimal totalAmount;
}