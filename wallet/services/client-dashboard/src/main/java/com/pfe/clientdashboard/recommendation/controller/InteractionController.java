package com.pfe.clientdashboard.recommendation.controller;

import com.pfe.clientdashboard.recommendation.dtos.ApiResponse;
import com.pfe.clientdashboard.recommendation.dtos.InteractionDTO;
import com.pfe.clientdashboard.recommendation.services.InteractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * ★ FIX clientId : tous les @PathVariable UUID → String pour matcher Client.id.
 * ★ NOUVEAU : GET /api/interaction/recent pour la vue admin "qui a fait quoi".
 */
@RestController
@RequestMapping("/api/interaction")
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService interactionService;

    /** POST /api/interaction/interactions — appelé par Flutter */
    @PostMapping("/interactions")
    public ResponseEntity<ApiResponse<Void>> recordInteraction(
            @RequestBody @Valid InteractionDTO dto) {
        interactionService.recordInteraction(dto);
        return ResponseEntity.ok(ApiResponse.success(null, "Interaction enregistrée"));
    }

    /** GET /api/interaction/clients/{clientId}/interactions */
    @GetMapping("/clients/{clientId}/interactions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> clientInteractions(
            @PathVariable String clientId) {
        return ResponseEntity.ok(ApiResponse.success(
                interactionService.getClientInteractions(clientId), "OK"));
    }

    /** GET /api/interaction/clients/{clientId}/recommendations */
    @GetMapping("/clients/{clientId}/recommendations")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> clientRecommendations(
            @PathVariable String clientId) {
        return ResponseEntity.ok(ApiResponse.success(
                interactionService.getClientActiveRecommendations(clientId), "OK"));
    }

    /** GET /api/interaction/offers/{offerCode}/stats */
    @GetMapping("/offers/{offerCode}/stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> offerStats(
            @PathVariable String offerCode) {
        return ResponseEntity.ok(ApiResponse.success(
                interactionService.getOfferStats(offerCode), "OK"));
    }

    /**
     * ★ NOUVEAU — GET /api/interaction/recent?limit=50
     * Liste les dernières interactions (tous clients) pour la vue admin.
     * Utilisé par le widget Marketing Feedback.
     */
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> recentInteractions(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                interactionService.listRecentInteractions(limit), "OK"));
    }
}
