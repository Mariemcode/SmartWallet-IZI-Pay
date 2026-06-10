package com.pfe.clientdashboard.Assistant.controller;

import com.pfe.clientdashboard.config.FastApiClient;
import com.pfe.clientdashboard.client.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
public class ClientRecommendationController {

    private final FastApiClient fastApiClient;
    private final ClientRepository clientRepository;

    @GetMapping("/recommendations/{clientId}")
    public ResponseEntity<?> getRecommendations(@PathVariable String clientId) {
        log.info("→ GET /api/ia/recommendations/{}", clientId);

        if (!clientRepository.existsById(clientId)) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Client introuvable : " + clientId));
        }

        try {
            // 🌟🌟🌟 CORRECTION ICI 🌟🌟🌟
            // Appeler le NOUVEL endpoint (v9 avec challenges et gamification)
            Object result = fastApiClient.getRecommendations(clientId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ FastAPI recommendations indisponible : {}", e.getMessage());
            return ResponseEntity.status(503)
                    .body(Map.of("error", "Service de recommandation indisponible"));
        }
    }
}