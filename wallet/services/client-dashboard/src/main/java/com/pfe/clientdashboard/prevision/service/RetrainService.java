package com.pfe.clientdashboard.prevision.service;

import com.pfe.clientdashboard.notification.service.FcmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SmartWallet — Service Réentraînement
 * =====================================
 * Gère toute la communication Spring Boot ↔ FastAPI pour :
 *   - Déclencher le réentraînement
 *   - Surveiller l'état
 *   - Monitorer les performances
 *   - Envoyer des notifications si problème
 */
@Service
public class RetrainService {

    private static final Logger log = LoggerFactory.getLogger(RetrainService.class);

    @Value("${fastapi.base-url:http://localhost:8000}")
    private String fastapiBaseUrl;

    @Value("${retrain.secret:smartwallet-retrain-2026}")
    private String retrainSecret;

    // Seuils de dégradation
    private static final double R2_MIN_TOPNET = 0.99;
    private static final double R2_MIN_SONEDE = 0.70;
    private static final double MAE_MAX_TOPNET = 2.0;
    private static final double MAE_MAX_SONEDE = 20.0;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired(required = false)
    private FcmService fcmService;   // null si Firebase non configuré

    // ── État interne ──────────────────────────────────────────────
    private String lastRetrainStatus = "unknown";
    private String lastHealthStatus  = "unknown";

    /**
     * Déclenche le réentraînement via POST /retrain sur FastAPI.
     * Exécuté en asynchrone pour ne pas bloquer le scheduler.
     */
    @Async
    public void triggerRetrain(String trigger) {
        log.info("🔄 Déclenchement réentraînement (trigger={})", trigger);

        try {
            String url = fastapiBaseUrl + "/retrain?trigger=" + trigger;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Retrain-Secret", retrainSecret);

            HttpEntity<String> entity = new HttpEntity<>(null, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = response.getBody();
                String status = body != null ? (String) body.get("status") : "unknown";
                log.info("✅ Réentraînement démarré — status={}", status);

                // Attendre et vérifier le résultat (poll)
                pollRetrainStatus(30);  // Vérifier pendant 30 minutes max
            } else {
                log.error("❌ Erreur déclenchement réentraînement : {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("❌ Exception déclenchement réentraînement : {}", e.getMessage());
            notifyAdmin("Erreur réentraînement", "Impossible de contacter FastAPI : " + e.getMessage());
        }
    }

    /**
     * Poll GET /retrain/status jusqu'à succès ou échec.
     * @param maxMinutes Durée maximale d'attente en minutes.
     */
    private void pollRetrainStatus(int maxMinutes) {
        log.info("⏳ Poll status réentraînement (max {}min)...", maxMinutes);

        int attempts  = 0;
        int maxChecks = maxMinutes * 2;  // Vérification toutes les 30 secondes

        while (attempts < maxChecks) {
            try {
                Thread.sleep(30_000);  // Attendre 30 secondes

                String url = fastapiBaseUrl + "/retrain/status";
                ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    Map<String, Object> body = response.getBody();
                    String status = body != null ? (String) body.get("status") : "unknown";
                    lastRetrainStatus = status;

                    switch (status) {
                        case "success":
                            log.info("✅ Réentraînement terminé avec succès");
                            String lastSuccess = body != null ? (String) body.get("last_success") : "";
                            notifyAdmin(
                                "Réentraînement ML réussi",
                                "Les modèles SmartWallet ont été mis à jour le " + lastSuccess
                            );
                            return;

                        case "failed":
                            String error = body != null ? (String) body.get("last_error") : "Erreur inconnue";
                            log.error("❌ Réentraînement échoué : {}", error);
                            notifyAdmin("❌ Réentraînement échoué", error);
                            return;

                        case "running":
                            log.info("  ...en cours (tentative {}/{})", attempts+1, maxChecks);
                            break;

                        default:
                            log.warn("  Status inattendu : {}", status);
                    }
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("  Erreur poll status : {}", e.getMessage());
            }
            attempts++;
        }

        log.error("❌ Timeout poll réentraînement après {}min", maxMinutes);
        notifyAdmin("Timeout réentraînement", "Le réentraînement n'a pas répondu après " + maxMinutes + " minutes");
    }

    /**
     * Vérifie l'état de santé de FastAPI.
     * Appelé toutes les 30 secondes par le scheduler.
     */
    public void checkFastapiHealth() {
        try {
            String url = fastapiBaseUrl + "/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = response.getBody();
                String status = body != null ? (String) body.get("status") : "unknown";

                // Alerte si changement d'état
                if (!status.equals(lastHealthStatus)) {
                    log.info("🔵 FastAPI health : {} → {}", lastHealthStatus, status);
                    lastHealthStatus = status;

                    if ("DOWN".equals(status) || "DEGRADED".equals(status)) {
                        notifyAdmin("⚠️ FastAPI ML " + status,
                                "Le service ML est en état " + status + ". Vérifiez les modèles PKL.");
                    }
                }

                // Vérifier si réentraînement nécessaire
                if (body != null) {
                    Boolean needsRetrain = (Boolean) body.get("needs_retrain");
                    if (Boolean.TRUE.equals(needsRetrain)) {
                        log.warn("⚠️ Modèles ML anciens de plus de 30 jours — réentraînement recommandé");
                    }
                }

            } else {
                log.warn("⚠️ FastAPI health non OK : {}", response.getStatusCode());
                lastHealthStatus = "DOWN";
            }

        } catch (Exception e) {
            if (!"DOWN".equals(lastHealthStatus)) {
                log.error("❌ FastAPI injoignable : {}", e.getMessage());
                lastHealthStatus = "DOWN";
                notifyAdmin("FastAPI ML injoignable", e.getMessage());
            }
        }
    }

    /**
     * Vérifie les performances des modèles ML.
     * Déclenche un réentraînement automatique si dégradation détectée.
     */
    public void checkModelPerformances() {
        try {
            String url = fastapiBaseUrl + "/metrics";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("⚠️ Impossible de récupérer les métriques ML");
                return;
            }

            Map<String, Object> body = response.getBody();

            // Vérifier les alertes de dégradation
            @SuppressWarnings("unchecked")
            List<String> alerts = (List<String>) body.get("degradation_alerts");

            if (alerts != null && !alerts.isEmpty()) {
                log.warn("⚠️ Dégradation détectée :");
                alerts.forEach(a -> log.warn("  {}", a));

                // Notification et réentraînement automatique
                notifyAdmin(
                    "⚠️ Dégradation modèles ML",
                    "Alertes détectées :\n" + String.join("\n", alerts) +
                    "\n\nRéentraînement automatique déclenché."
                );
                triggerRetrain("auto-degradation");

            } else {
                log.info("✅ Performances ML OK — aucune dégradation");
            }

        } catch (Exception e) {
            log.error("❌ Erreur vérification performances : {}", e.getMessage());
        }
    }

    /**
     * Retourne l'état actuel du service de réentraînement.
     * Appelé par le RetrainController pour l'API REST admin.
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("last_retrain_status", lastRetrainStatus);
        status.put("fastapi_health",      lastHealthStatus);
        status.put("fastapi_url",         fastapiBaseUrl);
        return status;
    }

    /**
     * Envoie une notification admin (Firebase FCM si disponible, sinon log).
     */
    private void notifyAdmin(String title, String body) {
        if (fcmService != null) {
            try {
                fcmService.sendAdminNotification(title, body);
                log.info("📱 Notification admin envoyée : {}", title);
            } catch (Exception e) {
                log.warn("⚠️ Impossible d'envoyer la notification : {}", e.getMessage());
            }
        } else {
            log.info("📋 [ADMIN NOTIFICATION] {} : {}", title, body);
        }
    }
}
