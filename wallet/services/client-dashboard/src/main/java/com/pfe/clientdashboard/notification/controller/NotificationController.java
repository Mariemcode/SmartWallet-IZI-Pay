package com.pfe.clientdashboard.notification.controller;

import com.pfe.clientdashboard.notification.entities.FcmToken;
import com.pfe.clientdashboard.notification.service.FcmService;
import com.pfe.clientdashboard.recommendation.repositories.ClientRecommendationRepository;
import com.pfe.clientdashboard.notification.repository.FcmTokenRepository;
import com.pfe.clientdashboard.prevision.service.RetrainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SmartWallet — Controller Notifications + Réentraînement Admin
 * ==============================================================
 *
 * Endpoints :
 *   POST /api/notifications/register-token  — Flutter envoie son token FCM
 *   GET  /api/admin/retrain/status          — état du dernier réentraînement
 *   POST /api/admin/retrain/trigger         — déclenche manuellement
 *   GET  /api/admin/health                  — santé complète du système
 */
@RestController
@RequestMapping("/api")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    @Autowired(required = false)
    private FcmService fcmService;

    @Autowired
    private RetrainService retrainService;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @Autowired(required = false)
    private ClientRecommendationRepository clientRecoRepo;

    // ════════════════════════════════════════════════════════════════
    // ENREGISTREMENT TOKEN FCM — stocké en PostgreSQL (persiste au redémarrage)
    // + AUTO-SUBSCRIBE au topic profile_{clusterId} pour recevoir les
    //   recommandations marketing ciblées sur le profil du client.
    // ════════════════════════════════════════════════════════════════

    /**
     * Flutter appelle cet endpoint au login pour enregistrer son token FCM.
     * Le token est nécessaire pour envoyer des notifications push au client.
     * <p>
     * Si le client a un clusterId connu (issu du pipeline de classification),
     * on l'abonne automatiquement au topic `profile_{clusterId}` afin qu'il
     * reçoive les diffusions de recommandations marketing.
     * <p>
     * POST /api/notifications/register-token
     * Body : { "client_id": "...", "fcm_token": "...", "platform": "android" }
     * Réponse : { status, client_id, platform, subscribed_to_topic? }
     */
    @PostMapping("/notifications/register-token")
    public ResponseEntity<Map<String, Object>> registerToken(
            @RequestBody Map<String, String> body) {

        String clientId  = body.get("client_id");
        String fcmToken  = body.get("fcm_token");
        String platform  = body.getOrDefault("platform", "android");

        if (clientId == null || fcmToken == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "client_id et fcm_token sont requis"
            ));
        }

        // Stocker le token en PostgreSQL (persiste au redémarrage)
        fcmTokenRepository.save(FcmToken.builder()
                .clientId(clientId)
                .token(fcmToken)
                .platform(platform)
                .updatedAt(LocalDateTime.now())
                .build());
        log.info("📱 Token FCM enregistré en BDD — client={}...  platform={}",
                clientId.substring(0, Math.min(8, clientId.length())), platform);

        // ── AUTO-SUBSCRIBE au topic profil ─────────────────────────
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "registered");
        resp.put("client_id", clientId);
        resp.put("platform", platform);

        Integer clusterId = lookupClusterId(clientId);
        if (clusterId != null && fcmService != null) {
            boolean subscribed = fcmService.subscribeClientToProfile(fcmToken, clusterId);
            resp.put("cluster_id", clusterId);
            resp.put("subscribed_to_topic", subscribed ? "profile_" + clusterId : null);
            if (subscribed) {
                log.info("📡 Auto-subscribe au topic profile_{} pour client {}...",
                        clusterId, clientId.substring(0, Math.min(8, clientId.length())));
            }
        } else {
            resp.put("cluster_id", null);
            resp.put("subscribed_to_topic", null);
            log.debug("ℹ️ Pas d'auto-subscribe — clusterId inconnu pour {}", clientId);
        }

        return ResponseEntity.ok(resp);
    }

    /**
     * Récupère le clusterId du client à partir de ses ClientRecommendation
     * (meilleur effort — la classification se fait via FastAPI / pipeline).
     * Retourne null si aucun cluster n'a été assigné.
     */
    private Integer lookupClusterId(String clientId) {
        if (clientRecoRepo == null) return null;
        try {
            return clientRecoRepo.findByClientIdOrderByGeneratedAtDesc(clientId).stream()
                    .map(cr -> cr.getClusterId())
                    .filter(c -> c != null)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.debug("lookupClusterId({}) skip : {}", clientId, e.getMessage());
            return null;
        }
    }

    /**
     * Récupère le token FCM d'un client (usage interne — PredictionService).
     */
    public String getFcmToken(String clientId) {
        return fcmTokenRepository.findByClientId(clientId)
                .map(FcmToken::getToken).orElse(null);
    }

    /**
     * Envoie une notification d'alerte solde à un client.
     * Appelé par PredictionService quand M5 = CRITIQUE.
     */
    public void notifyClientAlerte(String clientId, String niveau, String message) {
        String token = getFcmToken(clientId);
        if (token != null && fcmService != null) {
            fcmService.sendAlerteClient(token, niveau, message, clientId);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // ENDPOINTS ADMIN — Réentraînement
    // ════════════════════════════════════════════════════════════════

    /**
     * Déclenche manuellement le réentraînement.
     * POST /api/admin/retrain/trigger
     *
     * En production : protéger avec @PreAuthorize("hasRole('ADMIN')")
     */
    @PostMapping("/admin/retrain/trigger")
    public ResponseEntity<Map<String, Object>> triggerRetrain(
            @RequestParam(defaultValue = "manual") String trigger) {

        log.info("🔄 Réentraînement déclenché manuellement (trigger={})", trigger);
        retrainService.triggerRetrain(trigger);

        return ResponseEntity.ok(Map.of(
                "status",     "started",
                "trigger",    trigger,
                "message",    "Réentraînement démarré. Vérifiez /api/admin/retrain/status",
                "check_url",  "/api/admin/retrain/status"
        ));
    }

    /**
     * Retourne l'état du dernier réentraînement.
     * GET /api/admin/retrain/status
     */
    @GetMapping("/admin/retrain/status")
    public ResponseEntity<Map<String, Object>> getRetrainStatus() {
        return ResponseEntity.ok(retrainService.getStatus());
    }

    /**
     * Santé complète du système (Spring Boot + FastAPI).
     * GET /api/admin/health
     */
    @GetMapping("/admin/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        Map<String, Object> health = new java.util.LinkedHashMap<>();
        health.put("spring_boot", "UP");
        health.put("retrain_service", retrainService.getStatus());
        health.put("fcm_registered_clients", fcmTokenRepository.count());
        health.put("firebase_enabled", fcmService != null);

        return ResponseEntity.ok(health);
    }
}
