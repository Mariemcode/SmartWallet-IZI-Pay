package com.pfe.clientdashboard.prevision.Scheduler;

import com.pfe.clientdashboard.notification.entities.NotificationLog;
import com.pfe.clientdashboard.notification.repository.NotificationLogRepository;
import com.pfe.clientdashboard.prevision.service.PredictionAuditLogService;
import com.pfe.clientdashboard.prevision.service.RetrainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class RetrainScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetrainScheduler.class);

    /** Seuil de dégradation : MAE Live > MAE PKL x 1.5  -> modèle dégradé. */
    private static final double SEUIL_DEGRADATION = 1.5;

    @Autowired private RetrainService retrainService;
    @Autowired private PredictionAuditLogService auditLogService;
    @Autowired private RestTemplate restTemplate;
    @Autowired private NotificationLogRepository notifLogRepo;   // ← pour notifier l'admin

    @Value("${fastapi.base-url:http://127.0.0.1:8000}")
    private String fastapiBaseUrl;

    @Scheduled(cron = "0 0 2 1 * *") // 1er du mois à 2h
    public void retrainMensuel() {
        log.info("⏰ Démarrage réentraînement mensuel automatique");
        retrainService.triggerRetrain("scheduler");
    }

    @Scheduled(fixedDelay = 30_000) // Toutes les 30 secondes
    public void healthCheck() {
        retrainService.checkFastapiHealth();
    }

    /**
     * Monitoring quotidien (4h). Compare la MAE Live à la MAE PKL ; en cas de
     * dégradation : crée une NOTIFICATION ADMIN persistée ET déclenche le
     * réentraînement automatiquement. Le même contrôle est disponible à la
     * demande via POST /api/ia/admin/monitoring/check-now (pour la démo jury).
     */
    @Scheduled(cron = "0 0 4 * * *") // Tous les jours à 4h
    public void monitorPerformances() {
        log.info("📊 Monitoring performances ML...");
        try {
            Double maeOffline = getOfflineMAE();
            Double maeLive = auditLogService.getLiveMAE(30);

            if (maeOffline != null && maeLive != null && maeLive > maeOffline * SEUIL_DEGRADATION) {
                double ratio = maeLive / maeOffline;
                log.warn("⚠️ Dégradation modèle : MAE Live {} TND > Offline {} TND (ratio {})",
                        maeLive, maeOffline, String.format("%.2f", ratio));

                // 1) Notification admin persistée (visible dans l'historique)
                creerNotificationDegradation(maeLive, maeOffline, ratio);

                // 2) Réentraînement automatique
                retrainService.triggerRetrain("monitoring-degradation-auto");
                log.info("🔁 Réentraînement automatique déclenché suite à la dégradation.");
            } else {
                log.info("✅ Performances ML stables. Live: {} TND, Offline: {} TND", maeLive, maeOffline);
            }
        } catch (Exception e) {
            log.error("Erreur monitoring : {}", e.getMessage());
        }
    }

    /** Crée et enregistre une notification admin décrivant la dégradation. */
    private void creerNotificationDegradation(double maeLive, double maePkl, double ratio) {
        try {
            NotificationLog alerte = NotificationLog.builder()
                    .id(UUID.randomUUID().toString())
                    .clientId("ADMIN")
                    .titre("Dégradation du modèle détectée")
                    .message(String.format(
                            "MAE Live %.2f TND > MAE PKL %.2f TND (ratio %.2f, seuil %.1f). " +
                                    "Réentraînement automatique déclenché.",
                            maeLive, maePkl, ratio, SEUIL_DEGRADATION))
                    .type("MODEL_DEGRADATION")
                    .envoyePar("monitoring")
                    .statut("NON_LU")
                    .dateEnvoi(LocalDateTime.now())
                    .build();
            notifLogRepo.save(alerte);
        } catch (Exception e) {
            log.error("Impossible de créer la notification de dégradation : {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Double getOfflineMAE() {
        try {
            Map<String, Object> metrics = restTemplate.getForObject(fastapiBaseUrl + "/metrics", Map.class);
            if (metrics != null && metrics.containsKey("module1_factures")) {
                Map<String, Map<String, Double>> module1 =
                        (Map<String, Map<String, Double>>) metrics.get("module1_factures");
                return module1.values().stream()
                        .filter(m -> m.get("mae") != null)
                        .mapToDouble(m -> m.get("mae")).average().orElse(0);
            }
        } catch (Exception e) {
            log.warn("Impossible de récupérer MAE Offline : {}", e.getMessage());
        }
        return null;
    }
}