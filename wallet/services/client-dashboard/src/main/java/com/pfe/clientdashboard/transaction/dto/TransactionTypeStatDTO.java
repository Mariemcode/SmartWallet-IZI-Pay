package com.pfe.clientdashboard.transaction.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data @AllArgsConstructor
public class TransactionTypeStatDTO {
    private String     typeTitle;
    private String     category;
    private String     subCategory;
    private String     typeDirection;
    private long       count;
    private BigDecimal totalAmount;
}