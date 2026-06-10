package com.pfe.clientdashboard.offre.service;

import com.pfe.clientdashboard.recommendation.entities.ClientRecommendation;
import com.pfe.clientdashboard.recommendation.entities.UserInteraction;
import com.pfe.clientdashboard.recommendation.repositories.ClientRecommendationRepository;
import com.pfe.clientdashboard.recommendation.repositories.UserInteractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MarketingFeedbackScheduler
 * ───────────────────────────
 * Toutes les heures, pousse les nouvelles interactions accepted/rejected
 * vers FastAPI via POST /marketing-feedback/batch.
 *
 * ★ FIX v3 :
 *   • Utilise findFeedbackAfterCursor() au lieu de findAll().stream().filter()
 *     → bien plus performant si la table devient grosse
 *   • clientId est désormais String (matche le typage du reste de l'app)
 *
 * À ne pas confondre avec :
 *   - FeedbackLearningScheduler  → feedback OCR (factures scannées)
 *   - RetrainScheduler           → ré-entraînement complet des modèles
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingFeedbackScheduler {

    private final UserInteractionRepository interactionRepo;
    private final ClientRecommendationRepository clientRecoRepo;
    private final RestTemplate restTemplate;

    @Value("${fastapi.base-url:http://127.0.0.1:8000}")
    private String fastapiUrl;

    @Value("${retrain.secret:smartwallet-retrain-2026}")
    private String retrainSecret;

    /** Curseur en mémoire — dernier "recordedAt" déjà poussé vers FastAPI. */
    private volatile LocalDateTime lastPushedCursor = LocalDateTime.now().minusDays(7);

    @Scheduled(cron = "0 5 * * * *")
    public void pushMarketingFeedbackBatch() {
        try {
            int sent = pushBatch();
            if (sent > 0) {
                log.info("📡 [MarketingFeedbackScheduler] {} interactions poussées vers FastAPI", sent);
            } else {
                log.debug("ℹ️ [MarketingFeedbackScheduler] Rien à pousser (curseur={}).", lastPushedCursor);
            }
        } catch (Exception e) {
            log.error("❌ [MarketingFeedbackScheduler] Erreur batch : {}", e.getMessage(), e);
        }
    }

    /**
     * Pousse immédiatement le batch (appelable depuis l'admin).
     * ★ FIX : utilise la query indexée findFeedbackAfterCursor.
     */
    public int pushBatch() {
        List<UserInteraction> newInteractions = interactionRepo.findFeedbackAfterCursor(lastPushedCursor);
        if (newInteractions.isEmpty()) return 0;

        // Enrichir avec le clusterId via ClientRecommendation
        List<Map<String, Object>> items = newInteractions.stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("client_id", i.getClientId());
            m.put("offer_code", i.getOfferCode());
            m.put("decision", i.getAction().name());
            m.put("recorded_at", i.getRecordedAt().toString());

            // clusterId (best-effort, depuis la ClientRecommendation la + récente)
            Integer clusterId = clientRecoRepo
                    .findByClientIdOrderByGeneratedAtDesc(i.getClientId()).stream()
                    .filter(cr -> i.getOfferCode().equals(cr.getOfferCode()))
                    .map(ClientRecommendation::getClusterId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            m.put("profile_id", clusterId);

            return m;
        }).collect(Collectors.toList());

        // POST → FastAPI
        Map<String, Object> body = Map.of("items", items);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Retrain-Secret", retrainSecret);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    fastapiUrl + "/marketing-feedback/batch",
                    request,
                    Map.class
            );
            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("⚠️ FastAPI a refusé le batch : {}", resp.getStatusCode());
                return 0;
            }
        } catch (Exception e) {
            log.error("❌ POST /marketing-feedback/batch échoué : {}", e.getMessage());
            return 0;
        }

        // Avancer le curseur
        LocalDateTime maxRecorded = newInteractions.stream()
                .map(UserInteraction::getRecordedAt)
                .max(LocalDateTime::compareTo)
                .orElse(lastPushedCursor);
        lastPushedCursor = maxRecorded;

        return newInteractions.size();
    }

    /** Trigger retrain (POST /marketing-feedback/retrain). */
    public Map<String, Object> triggerRetrain() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Retrain-Secret", retrainSecret);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    fastapiUrl + "/marketing-feedback/retrain",
                    request,
                    Map.class
            );
            return resp.getBody();
        } catch (Exception e) {
            log.error("❌ triggerRetrain : {}", e.getMessage());
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    public LocalDateTime getLastPushedCursor() {
        return lastPushedCursor;
    }

    /** ★ Permet à l'admin de remettre le curseur à zéro (renvoyer tout depuis J-N). */
    public void resetCursor(int daysBack) {
        this.lastPushedCursor = LocalDateTime.now().minusDays(Math.max(1, daysBack));
        log.info("⏮️ Curseur marketing-feedback réinitialisé à {} (J-{})", lastPushedCursor, daysBack);
    }
}
