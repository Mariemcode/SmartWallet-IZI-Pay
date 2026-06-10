package com.pfe.clientdashboard.recommendation.controller;

import com.pfe.clientdashboard.recommendation.dtos.ApiResponse;
import com.pfe.clientdashboard.recommendation.services.PipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;

    /** POST /api/v5/pipeline/run */
    @PostMapping("/run")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runPipeline() {
        return ResponseEntity.accepted()
                .body(ApiResponse.success(
                        pipelineService.triggerPipeline(), "Pipeline lancé"));
    }

    /** POST /api/v5/offers/generate */
    @PostMapping("/offers/generate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateOffers() {
        return ResponseEntity.accepted()
                .body(ApiResponse.success(
                        pipelineService.triggerOfferGeneration(),
                        "Génération offres lancée"));
    }

    /** POST /api/v5/recommendations/generate */
    @PostMapping("/recommendations/generate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateRecommendations() {
        return ResponseEntity.accepted()
                .body(ApiResponse.success(
                        pipelineService.triggerRecommendationGeneration(),
                        "Génération recommandations lancée"));
    }

//    /** POST /api/v5/notifications/send */
//    @PostMapping("/notifications/send")
//    public ResponseEntity<ApiResponse<Map<String, Object>>> sendNotifications(
//            @RequestBody(required = false) Map<String, String> body) {
//        String filter = body != null ? body.get("profile_filter") : null;
//        return ResponseEntity.ok(ApiResponse.success(
//                pipelineService.sendNotifications(filter), "Notifications envoyées"));
//    }

    /** GET /api/v5/health */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(
                ApiResponse.success(pipelineService.getHealth(), "OK"));
    }

    /** GET /api/v5/generation-runs */
    @GetMapping("/generation-runs")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generationRuns(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                pipelineService.getGenerationRuns(limit), "OK"));
    }


}
