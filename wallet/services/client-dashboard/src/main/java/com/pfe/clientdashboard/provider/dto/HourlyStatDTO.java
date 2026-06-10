package com.pfe.clientdashboard.provider.dto;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class HourlyStatDTO {
    private int hour;
    private long count;
}

