package com.pfe.clientdashboard.ocr.Scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * FeedbackLearningScheduler
 * ─────────────────────────
 * Déclenche l'analyse hebdomadaire des feedbacks OCR chaque dimanche à 3h00.
 * Appelle FastAPI → /api/ocr/analyser-feedback
 * Puis notifie l'admin si le taux de succès global est en dessous du seuil.
 *
 * Complémentaire au NotificationScheduler (factures/solde/reco)
 * et au RetrainScheduler (re-entraînement ML).
 */
@Slf4j
@Component
public class FeedbackLearningScheduler {

    @Value("${fastapi.base-url:http://127.0.0.1:8000}")
    private String fastapiUrl;

    @Value("${retrain.secret:smartwallet-retrain-2026}")
    private String retrainSecret;

    /** Seuil d'alerte : si taux_succes_global < ce seuil, notifier l'admin */
    private static final double SEUIL_ALERTE_PCT = 70.0;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Chaque dimanche à 3h00 du matin
     * Cron : sec min heure jour mois jourSemaine
     *        0   0   3    ?   *    SUN
     */
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void analyserFeedbacksOCR() {
        log.info("🧠 [FeedbackLearningScheduler] Début analyse OCR — {}",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        try {
            // ── 1. Appeler FastAPI pour analyser les feedbacks ───────────
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Retrain-Secret", retrainSecret);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    fastapiUrl + "/api/ocr/analyser-feedback",
                    request,
                    Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("⚠️ FastAPI analyser-feedback : réponse inattendue {}", response.getStatusCode());
                return;
            }

            Map<String, Object> result = response.getBody();
            String status = (String) result.getOrDefault("status", "inconnu");

            // ── 2. Rien à analyser ───────────────────────────────────────
            if ("rien_a_analyser".equals(status)) {
                log.info("ℹ️ [FeedbackLearningScheduler] Aucun feedback en attente cette semaine.");
                return;
            }

            // ── 3. Lire le rapport ───────────────────────────────────────
            int feedbacksAnalyses = ((Number) result.getOrDefault("feedbacks_analyses", 0)).intValue();
            double tauxGlobal     = ((Number) result.getOrDefault("taux_succes_global", 0.0)).doubleValue();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ameliorations =
                    (List<Map<String, Object>>) result.getOrDefault("ameliorations", List.of());

            log.info("✅ [FeedbackLearningScheduler] Analyse terminée : {} feedbacks | taux global = {}%",
                    feedbacksAnalyses, tauxGlobal);

            // ── 4. Logger les fournisseurs problématiques ────────────────
            for (Map<String, Object> a : ameliorations) {
                String fournisseur = (String) a.get("fournisseur");
                double taux        = ((Number) a.getOrDefault("taux_succes_pct", 0.0)).doubleValue();
                String action      = (String) a.getOrDefault("action", "");

                if (taux < 75.0) {
                    log.warn("  ⚠️ {} : {}% succès — Action recommandée : {}",
                            fournisseur, taux, action);
                } else {
                    log.info("  ✅ {} : {}% succès — {}", fournisseur, taux, action);
                }
            }

            // ── 5. Alerte si taux global trop bas ────────────────────────
            if (tauxGlobal < SEUIL_ALERTE_PCT) {
                log.warn("🚨 [FeedbackLearningScheduler] Taux global OCR = {}% < seuil {}% — "
                         + "Révision des patterns regex recommandée !", tauxGlobal, SEUIL_ALERTE_PCT);
                // TODO : envoyer une notification Firebase à l'admin
                // (à brancher avec FcmService si un token admin est configuré)
            }

        } catch (Exception e) {
            log.error("❌ [FeedbackLearningScheduler] Erreur : {}", e.getMessage(), e);
        }
    }

    /**
     * Récupérer les stats OCR (appelable manuellement depuis AdminController).
     * Retourne null en cas d'erreur.
     */
    public Map<String, Object> getOcrStats() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Retrain-Secret", retrainSecret);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    fastapiUrl + "/api/ocr/stats",
                    HttpMethod.GET,
                    request,
                    Map.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("❌ Impossible de récupérer les stats OCR : {}", e.getMessage());
            return null;
        }
    }
}
