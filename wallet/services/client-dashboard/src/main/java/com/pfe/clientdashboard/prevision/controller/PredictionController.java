package com.pfe.clientdashboard.prevision.controller;

import com.pfe.clientdashboard.prevision.service.PredictionService;
import com.pfe.clientdashboard.prevision.dtos.PredictionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * PredictionController — endpoints REST du microservice IA.
 * <p>
 * Base URL : /api/ia
 * </p>
 * <p>
 * Via API Gateway (Spring Cloud Gateway) :
 * Flutter → Gateway (8080) → ai-service (8091) via Eureka lb://ai-service
 * </p>
 * <p>
 * Le JWT est validé par le Gateway AVANT d'arriver ici.
 * Le client_id vient du path variable (passé par Flutter directement).
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
public class PredictionController {

    private final PredictionService predictionService;

    // ════════════════════════════════════════════════════════════════
    //  GET /api/ia/predictions/{clientId}
    //  ★ ENDPOINT PRINCIPAL ★
    //  Flutter appelle celui-ci au chargement du dashboard IA.
    //  Retourne TOUT en une seule requête :
    //    - Solde calculé depuis la DB (Crédits - Débits)
    //    - Budget mensuel prévu (XGBoost)
    //    - Prochaines factures avec dates
    //    - Prochain rechargement
    //    - Alerte solde (OK/ATTENTION/CRITIQUE)
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/predictions/{clientId}")
    public ResponseEntity<PredictionResponse> getAllPredictions(
            @PathVariable String clientId
    ) {
        log.info("→ GET /api/ia/predictions/{}", clientId);
        PredictionResponse result = predictionService.getFullPrediction(clientId);
        return ResponseEntity.ok(result);
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/ia/solde/{clientId}
    //  Retourne uniquement le solde calculé.
    //  Utile après chaque transaction pour rafraîchir Flutter.
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/solde/{clientId}")
    public ResponseEntity<Map<String, Object>> getSolde(
            @PathVariable String clientId
    ) {
        Double solde = predictionService.getSolde(clientId);
        return ResponseEntity.ok(Map.of(
                "client_id", clientId,
                "solde_TND", solde,
                "niveau", solde >= 100 ? "OK"
                        : solde >= 0 ? "FAIBLE"
                        : "NEGATIF"
        ));
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/ia/segment/{clientId}
    //  Retourne Gold/Moyen/Faible — utile pour le dashboard Flutter
    //  ✅ CORRIGÉ : Récupère le segment depuis la réponse complète
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/segment/{clientId}")
    public ResponseEntity<Map<String, String>> getSegment(
            @PathVariable String clientId
    ) {
        // Option 1 : Récupérer depuis la réponse complète (plus cohérent)
        PredictionResponse response = predictionService.getFullPrediction(clientId);
        String segment = response.getSegment() != null ? response.getSegment() : "Faible";

        return ResponseEntity.ok(Map.of(
                "client_id", clientId,
                "segment", segment
        ));
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/ia/health
    //  Vérifie que Spring Boot est opérationnel
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "ai-service",
                "port", "8091"
        ));
    }
}