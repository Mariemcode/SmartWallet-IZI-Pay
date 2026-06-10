package com.pfe.clientdashboard.dashboardAdmin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DebitCreditDTO {
    private long       debitCount;
    private BigDecimal debitAmount;
    private double     debitPctCount;
    private double     debitPctAmount;

    private long       creditCount;
    private BigDecimal creditAmount;
    private double     creditPctCount;
    private double     creditPctAmount;

    private BigDecimal avgDebit;
    private BigDecimal avgCredit;
}
