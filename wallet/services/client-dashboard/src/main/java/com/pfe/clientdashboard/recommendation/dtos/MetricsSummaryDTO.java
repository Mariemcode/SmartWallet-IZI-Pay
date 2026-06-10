package com.pfe.clientdashboard.recommendation.dtos;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MetricsSummaryDTO {
    private BigDecimal avgPrecision;
    private BigDecimal avgRecall;
    private BigDecimal avgF1;
    private String evaluationType;
    private Integer nProfiles;
    private List<MetricsDetailDTO> metrics;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MetricsDetailDTO {
        private String profileName;
        private BigDecimal precisionScore;
        private BigDecimal recallScore;
        private BigDecimal f1Score;
        private BigDecimal coverage;
        private BigDecimal acceptanceRate;
        private BigDecimal avgScore;
        private Integer nRecommendations;
        private String evaluationType;
        private LocalDateTime computedAt;
    }
}