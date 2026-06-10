package com.pfe.clientdashboard.recommendation.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * PipelineClient — Client HTTP vers FastAPI pour le module recommandation/pipeline.
 *
 * AVANT (admin séparé) : recommendation.python.base-url → port 5050 (serveur Python séparé)
 * APRÈS (fusionné)     : fastapi.base-url → port 8000   /api/v5/*
 *
 * Toutes les routes sont préfixées /api/v5/ pour mapper le router
 * recommendation de main.py.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PipelineClient {

    private final RestTemplate restTemplate;

    /** Port 8000 FastAPI — plus de port 5050 */
    @Value("${fastapi.base-url:http://localhost:8000}")
    private String baseUrl;

    /** Préfixe du router recommendation dans main.py */
    private static final String API_PREFIX = "/api/v5";

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String path) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl + API_PREFIX + path,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception e) {
            log.error("Erreur GET {} : {}", path, e.getMessage());
            throw new RuntimeException("FastAPI indisponible : " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> post(String path, Object body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Object> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl + API_PREFIX + path,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception e) {
            log.error("Erreur POST {} : {}", path, e.getMessage());
            throw new RuntimeException("FastAPI indisponible : " + e.getMessage(), e);
        }
    }
}
