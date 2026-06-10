package com.pfe.clientdashboard.recommendation.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import com.pfe.clientdashboard.exception.BadRequestException;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DescriptionGeneratorService {

    private final RestTemplate restTemplate;

    @Value("${fastapi.base-url:http://localhost:8000}")
    private String baseUrl;

    private static final String API_PREFIX = "/api/v5";

    public String generateDescription(String offerCode) {
        String url = baseUrl + API_PREFIX + "/recommendations/generate-description";  // ← ajout de API_PREFIX
        Map<String, String> request = Map.of("offer_code", offerCode);
        try {
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            if (response != null && response.containsKey("data")) {
                Object dataObj = response.get("data");
                if (dataObj instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) dataObj;
                    Object desc = data.get("description");
                    if (desc != null) {
                        return desc.toString();
                    }
                }
            }
            throw new BadRequestException("Réponse inattendue de l'API Python");
        } catch (HttpClientErrorException e) {
            log.error("Erreur HTTP appel Python : {}", e.getResponseBodyAsString());
            throw new BadRequestException("Erreur API Python : " + e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de l'appel à l'API Python", e);
            throw new BadRequestException("Impossible de générer la description : " + e.getMessage());
        }
    }
}