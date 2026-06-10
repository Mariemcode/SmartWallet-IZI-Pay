package com.pfe.clientdashboard.recommendation.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pfe.clientdashboard.recommendation.dtos.MetricsSummaryDTO;
import com.pfe.clientdashboard.recommendation.entities.RecommendationMetrics;
import com.pfe.clientdashboard.recommendation.repositories.RecommendationMetricsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetricsServiceImpl implements MetricsService {

    private final RecommendationMetricsRepository metricsRepo;

    @Override
    @Transactional(readOnly = true)
    public MetricsSummaryDTO getMetrics(String evaluationType) {
        List<RecommendationMetrics> metrics =
                metricsRepo.findByEvaluationTypeOrderByF1ScoreDesc(evaluationType);

        List<MetricsSummaryDTO.MetricsDetailDTO> details = metrics.stream()
                .map(m -> MetricsSummaryDTO.MetricsDetailDTO.builder()
                        .profileName(m.getProfileName())
                        .precisionScore(m.getPrecisionScore())
                        .recallScore(m.getRecallScore())
                        .f1Score(m.getF1Score())
                        .coverage(m.getCoverage())
                        .acceptanceRate(m.getAcceptanceRate())
                        .avgScore(m.getAvgScore())
                        .nRecommendations(m.getNRecommendations())
                        .evaluationType(m.getEvaluationType())
                        .computedAt(m.getComputedAt())
                        .build())
                .collect(Collectors.toList());

        if (metrics.isEmpty()) {
            return MetricsSummaryDTO.builder()
                    .evaluationType(evaluationType)
                    .nProfiles(0)
                    .metrics(details)
                    .build();
        }

        BigDecimal avgF1 = metrics.stream()
                .map(RecommendationMetrics::getF1Score)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(metrics.size()), 3, RoundingMode.HALF_UP);

        BigDecimal avgP = metrics.stream()
                .map(RecommendationMetrics::getPrecisionScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(metrics.size()), 3, RoundingMode.HALF_UP);

        BigDecimal avgR = metrics.stream()
                .map(RecommendationMetrics::getRecallScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(metrics.size()), 3, RoundingMode.HALF_UP);

        return MetricsSummaryDTO.builder()
                .avgPrecision(avgP)
                .avgRecall(avgR)
                .avgF1(avgF1)
                .evaluationType(evaluationType)
                .nProfiles(metrics.size())
                .metrics(details)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public MetricsSummaryDTO getMetricsHistory(String profileName, int limit) {
        List<RecommendationMetrics> history =
                metricsRepo.findByProfileNameOrderByComputedAtDesc(profileName);

        List<MetricsSummaryDTO.MetricsDetailDTO> details = history.stream()
                .limit(limit)
                .map(m -> MetricsSummaryDTO.MetricsDetailDTO.builder()
                        .profileName(m.getProfileName())
                        .precisionScore(m.getPrecisionScore())
                        .recallScore(m.getRecallScore())
                        .f1Score(m.getF1Score())
                        .acceptanceRate(m.getAcceptanceRate())
                        .avgScore(m.getAvgScore())
                        .evaluationType(m.getEvaluationType())
                        .computedAt(m.getComputedAt())
                        .build())
                .collect(Collectors.toList());

        return MetricsSummaryDTO.builder()
                .nProfiles(details.size())
                .metrics(details)
                .build();
    }
}
