package com.pfe.clientdashboard.config;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * FastApiClient — interface Feign qui appelle FastAPI Python (port 8000).
 * <p>
 * L'URL est lue depuis application.yml :
 * fastapi.base-url: http://localhost:8000
 * <p>
 * Spring génère automatiquement l'implémentation HTTP.
 * Tous les endpoints correspondent exactement à main.py FastAPI.
 */
@FeignClient(name = "fastapi-ia", url = "${fastapi.base-url}")
public interface FastApiClient {

    /**
     * ★ ENDPOINT PRINCIPAL ★
     * Retourne TOUT en une seule requête (budget + forfaits + factures + alerte + mois_cher).
     * PredictionService.getFullPrediction() appelle uniquement celui-ci.
     * <p>
     * Spring Boot passe le solde calculé depuis PostgreSQL.
     * FastAPI orchestre les 3 couches et retourne un JSON complet.
     * <p>
     * GET /predictions/{clientId}?solde=232.50
     */
    @GetMapping("/predictions/{clientId}")
    Object getAllRaw(
            @PathVariable("clientId") String clientId,
            @RequestParam(value = "solde", required = false) Double solde
    );

    /**
     * Health check — vérifie que FastAPI + modèles sont opérationnels.
     * GET /health
     */
    @GetMapping("/health")
    Object healthCheck();

    @GetMapping("/recommendations/{clientId}")
    Object getRecommendations(@PathVariable("clientId") String clientId);

    // 🌟 NOUVEAU
    @GetMapping("/recommendations/live/{clientId}")
    Object getLiveRecommendations(@PathVariable("clientId") String clientId);
}
