package com.pfe.clientdashboard.recommendation.controller;

import com.pfe.clientdashboard.recommendation.dtos.ApiResponse;
import com.pfe.clientdashboard.recommendation.dtos.MetricsSummaryDTO;
import com.pfe.clientdashboard.recommendation.services.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor

public class MetricsController {

    private final MetricsService metricsService;

    /** GET /api/v5/metrics?evaluation_type=simulated */
    @GetMapping
    public ResponseEntity<ApiResponse<MetricsSummaryDTO>> getMetrics(
            @RequestParam(name = "evaluation_type", defaultValue = "simulated")
            String evaluationType) {
        return ResponseEntity.ok(ApiResponse.success(
                metricsService.getMetrics(evaluationType), "OK"));
    }

    /** GET /api/v5/metrics/history?profile=...&limit=10 */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<MetricsSummaryDTO>> getHistory(
            @RequestParam String profile,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                metricsService.getMetricsHistory(profile, limit), "OK"));
    }
}
