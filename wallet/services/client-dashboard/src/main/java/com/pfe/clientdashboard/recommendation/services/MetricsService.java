package com.pfe.clientdashboard.recommendation.services;


import com.pfe.clientdashboard.recommendation.dtos.MetricsSummaryDTO;

public interface MetricsService {

    MetricsSummaryDTO getMetrics(String evaluationType);

    MetricsSummaryDTO getMetricsHistory(String profileName, int limit);
}
