package com.pfe.clientdashboard.transaction.dto;

import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class TransactionFilterDTO {

    private String category;
    private String typeType;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;       // ← une seule date exacte
}