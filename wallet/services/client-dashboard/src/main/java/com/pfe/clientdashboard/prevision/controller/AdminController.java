package com.pfe.clientdashboard.prevision.controller;

import com.pfe.clientdashboard.client.repository.ClientRepository;
import com.pfe.clientdashboard.config.FastApiClient;
import com.pfe.clientdashboard.notification.Scheduler.NotificationScheduler;
import com.pfe.clientdashboard.notification.Scheduler.ReminderScheduler;
import com.pfe.clientdashboard.ocr.Scheduler.FeedbackLearningScheduler;
import com.pfe.clientdashboard.notification.entities.FcmToken;
import com.pfe.clientdashboard.notification.entities.NotificationLog;
import com.pfe.clientdashboard.notification.service.FcmService;
import com.pfe.clientdashboard.notification.repository.FcmTokenRepository;
import com.pfe.clientdashboard.notification.repository.NotificationLogRepository;
import com.pfe.clientdashboard.ocr.repository.ScanFeedbackRepository;
import com.pfe.clientdashboard.ocr.repository.ScannedFactureRepository;
import com.pfe.clientdashboard.offre.service.MarketingFeedbackScheduler;
import com.pfe.clientdashboard.offre.service.OfferApplicationService;
import com.pfe.clientdashboard.prevision.service.PredictionAuditLogService;
import com.pfe.clientdashboard.prevision.service.RetrainService;
import com.pfe.clientdashboard.recommendation.services.InteractionService;
import com.pfe.clientdashboard.client.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/ia/admin")
@RequiredArgsConstructor
public class AdminController {

    private final FastApiClient fastApiClient;
    private final RetrainService retrainService;
    private final PredictionAuditLogService auditLogService;
    private final ClientRepository clientRepo;
    private final TransactionRepository txRepo;
    private final FcmTokenRepository fcmTokenRepo;
    private final FcmService fcmService;
    private final JdbcTemplate jdbc;
    private final NotificationLogRepository notifLogRepo;
    private final InteractionService interactionService;
    private final ScanFeedbackRepository scanFeedbackRepo;
    private final ScannedFactureRepository scannedFactureRepo;

    @Autowired
    private FeedbackLearningScheduler feedbackLearningScheduler;

    @Autowired
    private MarketingFeedbackScheduler marketingFeedbackScheduler;

    @Autowired
    private NotificationScheduler notificationScheduler;
    @Autowired
    private ReminderScheduler reminderScheduler;
    @Autowired
    private OfferApplicationService offerApplicationService;


    @Value("${fastapi.base-url:http://127.0.0.1:8000}")
    private String fastapiUrl;
    @Autowired
    private RestTemplate restTemplate;


    // ═══════════════════════════════════════════════════
    //  SYSTÈME — Health, Metrics, Stats
    // ═══════════════════════════════════════════════════

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        try {
            return ResponseEntity.ok(fastApiClient.healthCheck());
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("status", "DOWN", "error", e.getMessage()));
        }
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> metrics() {
        try {
            var r = new RestTemplate().getForEntity(fastapiUrl + "/metrics", Map.class);
            return ResponseEntity.ok(r.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/monitoring")
    public ResponseEntity<?> monitoring() {
        try {
            Map<String, Object> s = auditLogService.getMonitoringStats();
            try {
                var r = new RestTemplate().getForEntity(fastapiUrl + "/metrics", Map.class);
                if (r.getBody() != null) s.put("metrics_ml", r.getBody());
            } catch (Exception ignored) {
            }
            return ResponseEntity.ok(s);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("total_clients", clientRepo.count());
        s.put("total_transactions", txRepo.count());
        s.put("clients_fcm", fcmTokenRepo.count());
        s.put("total_notifications", notifLogRepo.count());
        try {
            var tx = jdbc.queryForMap("SELECT COUNT(*) as nb, COALESCE(SUM(amount),0) as vol FROM transaction WHERE DATE_TRUNC('month',transaction_date)=DATE_TRUNC('month',NOW()) AND reversal_flag='N'");
            s.put("tx_ce_mois", tx.get("nb"));
            s.put("volume_ce_mois_tnd", tx.get("vol"));
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok(s);
    }

    // ═══════════════════════════════════════════════════
    //  RETRAIN
    // ═══════════════════════════════════════════════════

    @PostMapping("/retrain")
    public ResponseEntity<?> retrain() {
        retrainService.triggerRetrain("admin");
        return ResponseEntity.ok(Map.of("status", "started", "message", "Réentraînement lancé"));
    }

    @GetMapping("/retrain/status")
    public ResponseEntity<?> retrainStatus() {
        try {
            Map<String, Object> s = retrainService.getStatus();
            try {
                var r = new RestTemplate().getForEntity(fastapiUrl + "/retrain/status", Map.class);
                if (r.getBody() != null) s.putAll(r.getBody());
            } catch (Exception ignored) {
            }
            return ResponseEntity.ok(s);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("status", "idle"));
        }
    }

    @GetMapping("/reco/meta")
    public ResponseEntity<?> recoMeta() {
        try {
            var r = new RestTemplate().getForEntity(fastapiUrl + "/recommendations/meta", Map.class);
            return ResponseEntity.ok(r.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", "Module 6 non disponible"));
        }
    }

    // ═══════════════════════════════════════════════════
    //  MARKETING FEEDBACK — push manuel + retrain
    //  (remplace les anciens endpoints /challenges/**)
    // ═══════════════════════════════════════════════════

    /**
     * Force un push immédiat du batch de feedback marketing vers FastAPI.
     * Utile pour déclencher manuellement l'envoi sans attendre le cron horaire.
     * POST /api/ia/admin/marketing-feedback/push
     */
    @PostMapping("/marketing-feedback/push")
    public ResponseEntity<?> pushMarketingFeedback() {
        int sent = marketingFeedbackScheduler.pushBatch();
        return ResponseEntity.ok(Map.of(
                "status", "pushed",
                "items_sent", sent,
                "last_cursor", marketingFeedbackScheduler.getLastPushedCursor().toString()
        ));
    }

    /**
     * Déclenche le retrain marketing côté FastAPI (re-pondère les boosts
     * des offres en fonction des taux d'acceptation observés).
     * POST /api/ia/admin/marketing-feedback/retrain
     */
    @PostMapping("/marketing-feedback/retrain")
    public ResponseEntity<?> triggerMarketingRetrain() {
        Map<String, Object> result = marketingFeedbackScheduler.triggerRetrain();
        return ResponseEntity.ok(result);
    }

    /**
     * Récupère les statistiques de feedback agrégées par offre / par profil.
     * GET /api/ia/admin/marketing-feedback/stats
     */
    @GetMapping("/marketing-feedback/stats")
    public ResponseEntity<?> getMarketingFeedbackStats() {
        try {
            var r = new RestTemplate().getForEntity(
                    fastapiUrl + "/marketing-feedback/stats", Map.class);
            return ResponseEntity.ok(r.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════
    //  CLIENTS
    // ═══════════════════════════════════════════════════

    @GetMapping("/clients")
    public ResponseEntity<?> clients(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String search) {
        try {
            String where = (search != null && !search.isBlank()) ? "AND (c.phone_number LIKE '%" + search + "%' OR c.first_name ILIKE '%" + search + "%' OR c.last_name ILIKE '%" + search + "%')" : "";
            List<Map<String, Object>> list = jdbc.queryForList(
                    "SELECT c.id, c.first_name, c.last_name, c.phone_number, c.create_date_time, " +
                            "COUNT(t.id) as nb_tx, COALESCE(SUM(CASE WHEN tt.type='C' THEN t.amount ELSE -t.amount END),0) as solde, " +
                            "MAX(t.transaction_date) as last_activity, (SELECT COUNT(*)>0 FROM fcm_token ft WHERE ft.client_id=c.id) as has_fcm " +
                            "FROM client c LEFT JOIN transaction t ON t.client_id=c.id AND t.reversal_flag='N' LEFT JOIN type_transaction tt ON t.transaction_type_id=tt.id " +
                            "WHERE 1=1 " + where + " GROUP BY c.id ORDER BY nb_tx DESC LIMIT ? OFFSET ?", size, page * size);
            return ResponseEntity.ok(Map.of("data", list, "total", clientRepo.count(), "page", page));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/clients/{id}")
    public ResponseEntity<?> clientDetail(@PathVariable String id) {
        var client = clientRepo.findById(id).orElse(null);
        if (client == null) return ResponseEntity.notFound().build();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", client.getId());
        d.put("firstName", client.getFirstName());
        d.put("lastName", client.getLastName());
        d.put("phone", client.getPhoneNumber());
        d.put("since", client.getCreateDateTime());
        BigDecimal solde = txRepo.calculateBalance(id);
        d.put("solde", solde != null ? solde.doubleValue() : 0);
        d.put("nbTx", txRepo.countNormal(id));
        // Predictions
        try {
            d.put("predictions", fastApiClient.getAllRaw(id, solde != null ? solde.doubleValue() : 0));
        } catch (Exception ignored) {
        }
        // Recommendations
        try {
            d.put("recommendations", new RestTemplate().getForEntity(fastapiUrl + "/recommendations/" + id, Map.class).getBody());
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok(d);
    }

    // ═══════════════════════════════════════════════════
    //  NOTIFICATIONS
    // ═══════════════════════════════════════════════════

    @PostMapping("/notifications/send")
    public ResponseEntity<?> sendNotification(@RequestBody Map<String, String> body) {
        String cid = body.getOrDefault("client_id", "");
        String titre = body.getOrDefault("titre", "SmartWallet");
        String message = body.getOrDefault("message", "");
        String type = body.getOrDefault("type", "INFO");
        boolean toAll = "ALL".equals(cid);

        int sent = 0;
        List<FcmToken> tokens = toAll ? fcmTokenRepo.findAll() : fcmTokenRepo.findByClientId(cid).map(List::of).orElse(List.of());
        for (FcmToken ft : tokens) {
            try {
                fcmService.sendAlerteClient(ft.getToken(), type, message, ft.getClientId());
                sent++;
            } catch (Exception ignored) {
            }
        }
        // Log
        notifLogRepo.save(NotificationLog.builder().id(UUID.randomUUID().toString())
                .clientId(toAll ? null : cid).titre(titre).message(message).type(type)
                .envoyePar("ADMIN").statut(sent > 0 ? "ENVOYE" : "ECHEC").dateEnvoi(LocalDateTime.now()).build());
        return ResponseEntity.ok(Map.of("sent", sent, "target", toAll ? "ALL" : cid));
    }

    @PostMapping("/notifications/schedule")
    public ResponseEntity<?> scheduleNotification(@RequestBody Map<String, String> body) {
        String datePlan = body.getOrDefault("date_planifiee", "");
        notifLogRepo.save(NotificationLog.builder().id(UUID.randomUUID().toString())
                .clientId(body.getOrDefault("client_id", null)).titre(body.getOrDefault("titre", ""))
                .message(body.getOrDefault("message", "")).type(body.getOrDefault("type", "INFO"))
                .envoyePar("ADMIN").statut("PLANIFIE").datePlanifiee(LocalDateTime.parse(datePlan)).build());
        return ResponseEntity.ok(Map.of("status", "scheduled", "date", datePlan));
    }

    @GetMapping("/notifications/history")
    public ResponseEntity<?> notifHistory(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<NotificationLog> p = notifLogRepo.findAllByOrderByDateEnvoiDesc(PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of("data", p.getContent(), "total", p.getTotalElements(), "page", page));
    }


    @GetMapping("/ocr/stats")
    public ResponseEntity<?> getOcrStats() {
        log.info("📊 Stats OCR demandées");
        Map<String, Object> stats = feedbackLearningScheduler.getOcrStats();
        if (stats == null) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Service OCR indisponible"
            ));
        }
        return ResponseEntity.ok(stats);
    }

    /**
     * POST /api/admin/ocr/analyser-feedback
     * Déclenche manuellement l'analyse des feedbacks OCR (sans attendre dimanche).
     * Utile en phase de développement / démo PFE.
     */
    @PostMapping("/ocr/analyser-feedback")
    public ResponseEntity<?> triggerFeedbackAnalysis() {
        log.info("🧠 Analyse OCR déclenchée manuellement depuis l'admin");
        try {
            feedbackLearningScheduler.analyserFeedbacksOCR();
            return ResponseEntity.ok(Map.of(
                    "status", "analyse_lancee",
                    "message", "Analyse des feedbacks OCR déclenchée"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
    // ════════════════════════════════════════════════════════════════
// AJOUTER DANS AdminController.java
// ════════════════════════════════════════════════════════════════

    /**
     * GET /api/ia/admin/alerts
     * Récupère les alertes de détection d'anomalies (Z-Score + Isolation Forest)
     * depuis FastAPI /recommendations/alerts/all
     */
    @GetMapping("/alerts")
    public ResponseEntity<?> getAnomalyAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String severity) {


        try {
            String url = fastapiUrl + "/recommendations/alerts/all?page=" + page + "&size=" + size;
            if (severity != null && !severity.isEmpty()) {
                url += "&severity=" + severity;
            }

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return ResponseEntity.ok(response.getBody());

        } catch (Exception e) {
            log.error("❌ Erreur récupération alertes : {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Service IA indisponible",
                    "data", List.of(),
                    "total", 0
            ));
        }
    }

    @GetMapping("/retrain/history")
    public ResponseEntity<?> getRetrainHistory() {


        try {
            String url = fastapiUrl + "/retrain/history";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return ResponseEntity.ok(response.getBody());

        } catch (Exception e) {
            log.error("❌ Erreur historique réentraînement : {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                    "history", List.of(),
                    "total", 0
            ));
        }
    }

    @GetMapping("/metrics/history")
    public ResponseEntity<?> metricsHistory(@RequestParam(defaultValue = "30") int days) {
        try {
            String url = fastapiUrl + "/metrics/history?days=" + days;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("❌ Erreur historique métriques : {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "history", List.of(),
                    "days", days,
                    "message", "Service IA indisponible"
            ));
        }
    }

    // ════════════════════════════════════════════════════════════════
//  AdminController — PATCH À APPLIQUER
// ════════════════════════════════════════════════════════════════
//
//  Ces endpoints sont à AJOUTER à ton fichier
//  `controller/AdminController.java` existant.
//
//  À insérer juste après la section "MARKETING FEEDBACK" (après la
//  méthode `getMarketingFeedbackStats()`), avant la section "CLIENTS".
//
//  Ajoute aussi en haut du fichier :
//      import com.pfe.clientdashboard.recommendation.services.InteractionService;
//
//  Et dans les champs injectés (entre les autres @Autowired) :
//      @Autowired
//      private InteractionService interactionService;
//
// ════════════════════════════════════════════════════════════════


    /**
     * GET /api/ia/admin/marketing-feedback/cursor
     * Renvoie l'état du curseur du scheduler (debug).
     */
    @GetMapping("/marketing-feedback/cursor")
    public ResponseEntity<?> getFeedbackCursor() {
        return ResponseEntity.ok(Map.of(
                "last_pushed_cursor", marketingFeedbackScheduler.getLastPushedCursor().toString()
        ));
    }

    /**
     * POST /api/ia/admin/marketing-feedback/reset-cursor
     * Body : { "days_back": 7 }
     * <p>
     * Permet à l'admin de réinitialiser le curseur pour repousser toutes
     * les interactions des N derniers jours. Utile pour démo / debug.
     */
    @PostMapping("/marketing-feedback/reset-cursor")
    public ResponseEntity<?> resetFeedbackCursor(@RequestBody(required = false) Map<String, Object> body) {
        int daysBack = 7;
        if (body != null && body.get("days_back") instanceof Number n) {
            daysBack = n.intValue();
        }
        marketingFeedbackScheduler.resetCursor(daysBack);
        return ResponseEntity.ok(Map.of(
                "status", "reset",
                "days_back", daysBack,
                "new_cursor", marketingFeedbackScheduler.getLastPushedCursor().toString()
        ));
    }

    /**
     * GET /api/ia/admin/marketing-feedback/recent-interactions?limit=50
     * Renvoie la liste des dernières interactions utilisateurs sur les offres.
     * Utilisé par le widget admin pour voir "qui a accepté/refusé quoi".
     */
    @GetMapping("/marketing-feedback/recent-interactions")
    public ResponseEntity<?> getRecentInteractions(@RequestParam(defaultValue = "50") int limit) {
        try {
            return ResponseEntity.ok(Map.of(
                    "data", interactionService.listRecentInteractions(limit),
                    "limit", limit
            ));
        } catch (Exception e) {
            log.error("❌ getRecentInteractions : {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage(),
                    "data", List.of()
            ));
        }
    }

    // ════════════════════════════════════════════════════════════════
//  AdminController — PATCH v2 (à AJOUTER, pas à remplacer)
//
//  Ajoute 2 endpoints utiles pour l'admin :
//   • GET /schedulers              — liste les 9 schedulers actifs
//   • GET /recommendations/stats   — stats Module 6 (proxy FastAPI)
//
//  À insérer dans `controller/AdminController.java`, n'importe où dans la classe.
//  Aucun import supplémentaire requis (utilise ceux déjà présents).
// ════════════════════════════════════════════════════════════════


    /**
     * GET /api/ia/admin/schedulers
     * <p>
     * Liste statique de tous les schedulers actifs dans Spring.
     * Hardcodé à partir du code source — tu vois immédiatement quels jobs
     * tournent, leur cron, et leur statut "actif" (= classe @Component
     * chargée par Spring).
     * <p>
     * Pour vraiment savoir si un cron a tourné récemment, regarde les logs
     * applicatifs — Spring ne stocke pas l'historique d'exécution par défaut.
     */
    @GetMapping("/schedulers")
    public ResponseEntity<?> listSchedulers() {
        try {
            // Schedulers identifiés dans le code source :
            //   config/RetrainScheduler.java          → 3 jobs
            //   config/NotificationScheduler.java     → 3 jobs
            //   config/ReminderScheduler.java         → 2 jobs
            //   config/FeedbackLearningScheduler.java → 1 job
            //   offre/MarketingFeedbackScheduler.java → 1 job
            //   offre/OfferApplicationService.java    → 1 job (sweep expirés)
            //   service/PredictionAuditLogService.java→ 1 job (cleanup logs)

            List<Map<String, Object>> schedulers = List.of(
                    // ─── RetrainScheduler ───────────────────────────────
                    Map.of(
                            "id", "retrain_mensuel",
                            "category", "Forecasting ML",
                            "description", "Réentraînement complet des modèles factures (mensuel)",
                            "cron", "0 0 2 1 * *",
                            "cron_human", "Le 1er du mois à 02:00",
                            "class", "RetrainScheduler.retrainMensuel",
                            "impact", "Recalcule tous les PKL forecasting (TOPNET, STEG, SONEDE, BEE, TT, OOREDOO)"
                    ),
                    Map.of(
                            "id", "fastapi_health_check",
                            "category", "Surveillance",
                            "description", "Health-check FastAPI",
                            "cron", "fixedDelay=30s",
                            "cron_human", "Toutes les 30 secondes",
                            "class", "RetrainScheduler.healthCheck",
                            "impact", "Met à jour fastapi_health dans le status retrain"
                    ),
                    Map.of(
                            "id", "monitor_performance",
                            "category", "Forecasting ML",
                            "description", "Comparaison MAE Live vs Offline (PKL) — détection de dégradation",
                            "cron", "0 0 4 * * *",
                            "cron_human", "Tous les jours à 04:00",
                            "class", "RetrainScheduler.monitorPerformances",
                            "impact", "Logs un WARN si MAE Live > 1.5 × MAE Offline (dégradation modèle)"
                    ),
                    // ─── NotificationScheduler ──────────────────────────
                    Map.of(
                            "id", "rappel_factures_quotidien",
                            "category", "Notifications",
                            "description", "Rappel des factures à venir",
                            "cron", "0 0 8 * * *",
                            "cron_human", "Tous les jours à 08:00",
                            "class", "NotificationScheduler.rappelFacturesQuotidien",
                            "impact", "Envoie une notif FCM aux clients qui ont une facture à payer dans ≤ 3 jours"
                    ),
                    Map.of(
                            "id", "alerte_solde_quotidien",
                            "category", "Notifications",
                            "description", "Alerte solde faible vs factures à venir",
                            "cron", "0 0 9 * * *",
                            "cron_human", "Tous les jours à 09:00",
                            "class", "NotificationScheduler.alerteSoldeQuotidien",
                            "impact", "Notif FCM si solde < total factures à venir"
                    ),
                    Map.of(
                            "id", "bilan_hebdo_lundi",
                            "category", "Notifications",
                            "description", "Bilan hebdomadaire",
                            "cron", "0 0 10 * * MON",
                            "cron_human", "Tous les lundis à 10:00",
                            "class", "NotificationScheduler.bilanHebdo",
                            "impact", "Récap des dépenses + segment + recos prioritaires"
                    ),
                    // ─── ReminderScheduler (OCR) ────────────────────────
                    Map.of(
                            "id", "rappels_factures_scannees",
                            "category", "OCR",
                            "description", "Rappel des factures scannées (OCR) non payées",
                            "cron", "0 0 9 * * *",
                            "cron_human", "Tous les jours à 09:00",
                            "class", "ReminderScheduler.rappelsFacturesScannees",
                            "impact", "Notif FCM si une facture OCR approche sa date d'échéance"
                    ),
                    Map.of(
                            "id", "rappel_factures_demain",
                            "category", "OCR",
                            "description", "Rappel factures du lendemain",
                            "cron", "0 30 8 * * *",
                            "cron_human", "Tous les jours à 08:30",
                            "class", "ReminderScheduler.rappelLendemain",
                            "impact", "Rappels J-1 spécifiques OCR"
                    ),
                    // ─── FeedbackLearningScheduler (OCR auto-learning) ─
                    Map.of(
                            "id", "ocr_feedback_analyse",
                            "category", "OCR",
                            "description", "Analyse des feedbacks OCR pour auto-apprentissage",
                            "cron", "0 0 3 ? * SUN",
                            "cron_human", "Tous les dimanches à 03:00",
                            "class", "FeedbackLearningScheduler.analyserFeedbacksOCR",
                            "impact", "Calcule les patterns d'erreurs OCR + suggestions de correction"
                    ),
                    // ─── MarketingFeedbackScheduler ────────────────────
                    Map.of(
                            "id", "marketing_feedback_push",
                            "category", "Marketing IA",
                            "description", "Push batch interactions → FastAPI",
                            "cron", "0 5 * * * *",
                            "cron_human", "Toutes les heures à HH:05",
                            "class", "MarketingFeedbackScheduler.pushMarketingFeedbackBatch",
                            "impact", "Envoie les nouvelles UserInteraction (accepted/rejected) à FastAPI pour auto-apprentissage"
                    ),
                    // ─── OfferApplicationService (sweep expirés) ──────
                    Map.of(
                            "id", "sweep_offres_expirees",
                            "category", "Marketing IA",
                            "description", "Marquage des offres expirées",
                            "cron", "0 5 * * * *",
                            "cron_human", "Toutes les heures à HH:05",
                            "class", "OfferApplicationService.sweepExpired",
                            "impact", "Bascule ACTIVE → EXPIRED quand ends_at < now()"
                    ),
                    // ─── PredictionAuditLogService (cleanup) ──────────
                    Map.of(
                            "id", "cleanup_audit_logs",
                            "category", "Maintenance",
                            "description", "Nettoyage des logs d'audit de prédictions",
                            "cron", "0 0 5 * * SUN",
                            "cron_human", "Tous les dimanches à 05:00",
                            "class", "PredictionAuditLogService.cleanupOldLogs",
                            "impact", "Supprime les prediction_audit_logs antérieurs à 6 mois"
                    )
            );

            return ResponseEntity.ok(Map.of(
                    "count", schedulers.size(),
                    "schedulers", schedulers,
                    "note", "Liste statique extraite du code source. Vérifie les logs applicatifs pour l'historique d'exécution réel."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }


    /**
     * GET /api/ia/admin/recommendations/stats
     * <p>
     * Proxy direct vers FastAPI /recommendations/stats — retourne le nb de clients
     * profilés, le nb total de recos/alertes/challenges, et la répartition par cluster.
     * <p>
     * Données utilisées par le dashboard admin pour la section "Peer-Comparison".
     */
    @GetMapping("/recommendations/stats")
    public ResponseEntity<?> getRecommendationsStats() {
        try {
            String url = fastapiUrl + "/recommendations/stats";
            ResponseEntity<Map> resp = new RestTemplate().getForEntity(url, Map.class);
            return ResponseEntity.ok(resp.getBody());
        } catch (Exception e) {
            log.error("❌ Erreur recommendations/stats : {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Module 6 indisponible : " + e.getMessage()
            ));
        }
    }
    // ════════════════════════════════════════════════════════════════
//  AdminController — PATCH v7 (à AJOUTER, pas à remplacer)
//
//  Ajoute 5 endpoints pour brancher l'admin sur la nouvelle logique
//  OCR + audit PKL/Live :
//
//   ⚡ AUDIT (PKL vs Live)
//   • POST /audit/evaluate-now   — force evaluatePastPredictions()
//                                  → calcule maeReel pour les logs pending
//   • GET  /audit/status         — combien pending / done / par fournisseur
//
//   📷 OCR (scanned_facture + scan_feedback)
//   • GET  /ocr/scanned-factures — liste des factures scannées (debug)
//   • GET  /ocr/feedbacks        — liste des feedbacks reçus
//   • GET  /ocr/stats-v2         — stats Spring (vs FastAPI obsolète)
//
//  Imports nécessaires en haut du fichier :
//      import com.pfe.clientdashboard.ocr.entities.ScannedFacture;
//      import com.pfe.clientdashboard.ocr.entities.ScanFeedback;
//      import com.pfe.clientdashboard.ocr.repository.ScannedFactureRepository;
//      import com.pfe.clientdashboard.ocr.repository.ScanFeedbackRepository;
//      import org.springframework.data.domain.PageRequest;
//
//  Champs injectés (Spring DI automatique avec @RequiredArgsConstructor) :
//      private final ScannedFactureRepository scannedFactureRepo;
//      private final ScanFeedbackRepository scanFeedbackRepo;
// ════════════════════════════════════════════════════════════════


    // ────────────────────────────────────────────────────────────
    //  AUDIT (PKL vs Live)
    // ────────────────────────────────────────────────────────────

    /**
     * POST /api/ia/admin/audit/evaluate-now
     * <p>
     * Déclenche manuellement evaluatePastPredictions() sans attendre
     * le cron de 3h du matin. Utile pour :
     * • voir tout de suite si Live MAE est calculé après une démo
     * • forcer l'évaluation après un INSERT manuel dans prediction_audit_log
     */
    @PostMapping("/audit/evaluate-now")
    public ResponseEntity<?> evaluateAuditNow() {
        try {
            long before = auditLogService.countPending();
            auditLogService.evaluatePastPredictions();
            long after = auditLogService.countPending();
            long evaluated = before - after;
            return ResponseEntity.ok(Map.of(
                    "status", "evaluation_terminee",
                    "logs_pending_avant", before,
                    "logs_pending_apres", after,
                    "logs_evalues", evaluated,
                    "message", evaluated > 0
                            ? "✅ " + evaluated + " prédictions confirmées — Live MAE recalculée"
                            : "ℹ️ Aucun nouveau log à évaluer (soit pas de prédiction passée, soit déjà évalué)"
            ));
        } catch (Exception e) {
            log.error("❌ evaluateAuditNow : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/ia/admin/audit/status
     * <p>
     * Donne l'état du audit log : combien de prédictions ont été snapshottées,
     * combien sont en attente d'évaluation, combien ont abouti à un paiement,
     * répartition par fournisseur.
     */
    @GetMapping("/audit/status")
    public ResponseEntity<?> getAuditStatus() {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total_snapshots", auditLogService.countTotal());
            result.put("pending_evaluation", auditLogService.countPending());
            result.put("evaluated_paye", auditLogService.countByResultat("PAYE"));
            result.put("evaluated_non_paye", auditLogService.countByResultat("NON_PAYE"));
            result.put("oldest_pending", auditLogService.getOldestPendingDate());
            result.put("monitoring", auditLogService.getMonitoringStats());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ getAuditStatus : {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ────────────────────────────────────────────────────────────
    //  OCR — visibilité admin
    // ────────────────────────────────────────────────────────────

    /**
     * GET /api/ia/admin/ocr/scanned-factures?limit=50
     * <p>
     * Liste paginée des factures scannées. Utile pour voir en temps réel
     * si les "Planifier rappel" du mobile fonctionnent.
     */
    @GetMapping("/ocr/scanned-factures")
    public ResponseEntity<?> listScannedFactures(
            @RequestParam(defaultValue = "50") int limit) {
        try {
            var page = scannedFactureRepo.findAllByOrderByCreatedAtDesc(
                    org.springframework.data.domain.PageRequest.of(0, Math.min(limit, 200))
            );

            List<Map<String, Object>> data = page.getContent().stream().map(f -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", f.getId().toString());
                m.put("client_id", f.getClientId());
                m.put("fournisseur_label", f.getFournisseurLabel());
                m.put("fournisseur_nom", f.getFournisseurNom());
                m.put("montant", f.getMontant());
                m.put("date_echeance", f.getDateEcheance().toString());
                m.put("reference", f.getReference());
                m.put("paye", f.getPaye());
                m.put("rappel_j7", f.getRappelJ7Envoye());
                m.put("rappel_j3", f.getRappelJ3Envoye());
                m.put("rappel_j1", f.getRappelJ1Envoye());
                m.put("rappel_j0", f.getRappelJ0Envoye());
                m.put("created_at", f.getCreatedAt().toString());
                return m;
            }).toList();

            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "count", data.size(),
                    "total_in_db", page.getTotalElements()
            ));
        } catch (Exception e) {
            log.error("❌ listScannedFactures : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/ia/admin/ocr/feedbacks?limit=50
     * <p>
     * Liste paginée des feedbacks OCR utilisateurs.
     * Permet à l'admin de voir quels champs sont le plus souvent corrigés.
     */
    @GetMapping("/ocr/feedbacks")
    public ResponseEntity<?> listFeedbacks(
            @RequestParam(defaultValue = "50") int limit) {
        try {
            var page = scanFeedbackRepo.findAllByOrderByCreatedAtDesc(
                    org.springframework.data.domain.PageRequest.of(0, Math.min(limit, 200))
            );

            List<Map<String, Object>> data = page.getContent().stream().map(f -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", f.getId());
                m.put("client_id", f.getClientId());
                m.put("ocr_fournisseur", f.getOcrFournisseur());
                m.put("ocr_montant", f.getOcrMontant());
                m.put("ocr_date_echeance", f.getOcrDateEcheance());
                m.put("ocr_confiance", f.getOcrConfiance());
                m.put("user_fournisseur", f.getUserFournisseur());
                m.put("user_montant", f.getUserMontant());
                m.put("user_date_echeance", f.getUserDateEcheance());
                m.put("fournisseur_corrige", f.getFournisseurCorrige());
                m.put("montant_corrige", f.getMontantCorrige());
                m.put("date_corrigee", f.getDateCorrigee());
                m.put("reference_corrigee", f.getReferenceCorrigee());
                m.put("valide_sans_correction", f.getValideSansCorrection());
                m.put("action_finale", f.getActionFinale());
                m.put("created_at", f.getCreatedAt().toString());
                m.put("processed_at", f.getProcessedAt() != null ? f.getProcessedAt().toString() : null);
                return m;
            }).toList();

            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "count", data.size(),
                    "total_in_db", page.getTotalElements()
            ));
        } catch (Exception e) {
            log.error("❌ listFeedbacks : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/ia/admin/ocr/stats-v2
     * <p>
     * Stats OCR calculées directement depuis Spring (pas depuis FeedbackLearningScheduler).
     * Toujours à jour, ne dépend pas du cron hebdomadaire.
     */
    @GetMapping("/ocr/stats-v2")
    public ResponseEntity<?> getOcrStatsV2() {
        try {
            Map<String, Object> result = new LinkedHashMap<>();

            // ── Compteurs globaux ──
            long totalScans = scannedFactureRepo.count();
            long totalFeedbacks = scanFeedbackRepo.count();
            long nonPayees = scannedFactureRepo.countByPayeFalse();
            long validesSansCorrection = scanFeedbackRepo.countByValideSansCorrectionTrue();

            double tauxSucces = totalFeedbacks > 0
                    ? Math.round((double) validesSansCorrection / totalFeedbacks * 1000) / 10.0
                    : 0.0;

            result.put("total_factures_scannees", totalScans);
            result.put("total_feedbacks", totalFeedbacks);
            result.put("factures_non_payees", nonPayees);
            result.put("valides_sans_correction", validesSansCorrection);
            result.put("taux_succes_pct", tauxSucces);

            // ── Stats par fournisseur (scanned_facture) ──
            Map<String, Object> parFournisseurScan = new LinkedHashMap<>();
            for (Object[] row : scannedFactureRepo.statsByFournisseur()) {
                parFournisseurScan.put((String) row[0], Map.of(
                        "total", row[1],
                        "payees", row[2]
                ));
            }
            result.put("scans_par_fournisseur", parFournisseurScan);

            // ── Stats par fournisseur (scan_feedback : taux de correction) ──
            Map<String, Object> parFournisseurFeedback = new LinkedHashMap<>();
            for (Object[] row : scanFeedbackRepo.statsByFournisseur()) {
                long total = ((Number) row[1]).longValue();
                long parfaits = ((Number) row[2]).longValue();
                long corrMontant = ((Number) row[3]).longValue();
                long corrFournisseur = ((Number) row[4]).longValue();
                double taux = total > 0 ? Math.round((double) parfaits / total * 1000) / 10.0 : 0.0;
                parFournisseurFeedback.put((String) row[0], Map.of(
                        "nb_scans", total,
                        "parfaits", parfaits,
                        "corrections_montant", corrMontant,
                        "corrections_fournisseur", corrFournisseur,
                        "taux_succes_pct", taux,
                        "dernier_scan", row[5] != null ? row[5].toString() : null
                ));
            }
            result.put("par_fournisseur", parFournisseurFeedback);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ getOcrStatsV2 : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
//  MONITORING LIVE  —  à AJOUTER dans AdminController.java
//  (n'importe où dans la classe ; utilise jdbc, fastapiUrl, notifLogRepo,
//   retrainService — déjà injectés dans ton AdminController)
//
//  Imports à vérifier en haut du fichier :
//      import com.pfe.clientdashboard.notification.entities.NotificationLog;
//      import java.time.LocalDateTime;
//      import java.util.UUID;
//      import java.util.LinkedHashMap;
//      import java.util.Map;
//
//  Endpoints exposés :
//      GET  /api/ia/admin/monitoring/live        -> MAE/RMSE Live vs PKL + statut
//      POST /api/ia/admin/monitoring/check-now   -> détecte la dégradation,
//             crée une notification admin, (option) déclenche le retrain.
// ════════════════════════════════════════════════════════════════════════

    /**
     * Seuils de comparaison Live vs PKL.
     */
    private static final double SEUIL_CRITICAL = 1.5;   // MAE Live > MAE PKL x 1.5  -> CRITICAL
    private static final double SEUIL_WARNING = 1.2;   // x1.2 .. x1.5              -> WARNING

    /**
     * MAE PKL (offline) = moyenne des MAE du module factures (FastAPI /metrics).
     */
    private Double getMaePkl() {
        try {
            Map<?, ?> m = new RestTemplate().getForObject(fastapiUrl + "/metrics", Map.class);
            if (m != null && m.get("module1_factures") instanceof Map<?, ?> mod && !mod.isEmpty()) {
                double sum = 0;
                int n = 0;
                for (Object v : mod.values()) {
                    if (v instanceof Map<?, ?> mm && mm.get("mae") instanceof Number num) {
                        sum += num.doubleValue();
                        n++;
                    }
                }
                return n > 0 ? sum / n : null;
            }
        } catch (Exception e) {
            log.warn("MAE PKL indisponible : {}", e.getMessage());
        }
        return null;
    }

    /**
     * MAE PKL par fournisseur (module1_factures de FastAPI /metrics).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Double>> getPklParFournisseur() {
        try {
            Map<?, ?> m = new RestTemplate().getForObject(fastapiUrl + "/metrics", Map.class);
            if (m != null && m.get("module1_factures") instanceof Map<?, ?> mod) {
                return (Map<String, Map<String, Double>>) mod;
            }
        } catch (Exception e) {
            log.warn("PKL par fournisseur indisponible : {}", e.getMessage());
        }
        return new java.util.HashMap<>();
    }

    /**
     * Calcule le résumé Live sur N jours (MAE, RMSE, nb confirmées) à partir de
     * prediction_audit_log, puis le compare au PKL (ratio + statut de santé).
     */
    private Map<String, Object> computeLiveSummary(int days) {
        Double maeLive = null, rmseLive = null;
        long nb = 0;
        try {
            Map<String, Object> r = jdbc.queryForMap(
                    "SELECT AVG(mae_reel)                                   AS mae, " +
                            "       SQRT(AVG(POWER(montant_prevu - montant_reel, 2))) AS rmse, " +
                            "       COUNT(*)                                        AS n " +
                            "FROM prediction_audit_log " +
                            "WHERE resultat_final = 'PAYE' AND montant_reel IS NOT NULL " +
                            "  AND date_prevue >= CURRENT_DATE - make_interval(days => ?)", days);
            if (r.get("mae") != null) maeLive = ((Number) r.get("mae")).doubleValue();
            if (r.get("rmse") != null) rmseLive = ((Number) r.get("rmse")).doubleValue();
            if (r.get("n") != null) nb = ((Number) r.get("n")).longValue();
        } catch (Exception e) {
            log.warn("computeLiveSummary : {}", e.getMessage());
        }

        Double maePkl = getMaePkl();
        Double ratio = (maeLive != null && maePkl != null && maePkl > 0) ? maeLive / maePkl : null;

        String statut;
        boolean degradation = false;
        if (nb == 0) statut = "AUCUNE_DONNEE";
        else if (ratio == null) statut = "PKL_INDISPONIBLE";
        else if (ratio > SEUIL_CRITICAL) {
            statut = "CRITICAL";
            degradation = true;
        } else if (ratio > SEUIL_WARNING) statut = "WARNING";
        else statut = "HEALTHY";

        // ---- Détail PAR FOURNISSEUR (Live via SQL + PKL via FastAPI) ----------
        Map<String, Map<String, Double>> pklParFour = getPklParFournisseur();
        Map<String, Object> parFournisseur = new LinkedHashMap<>();
        try {
            java.util.List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT fournisseur, " +
                            "       AVG(mae_reel)                                   AS mae, " +
                            "       SQRT(AVG(POWER(montant_prevu - montant_reel, 2))) AS rmse, " +
                            "       COUNT(*)                                        AS n " +
                            "FROM prediction_audit_log " +
                            "WHERE resultat_final = 'PAYE' AND montant_reel IS NOT NULL " +
                            "  AND date_prevue >= CURRENT_DATE - make_interval(days => ?) " +
                            "GROUP BY fournisseur", days);
            for (Map<String, Object> row : rows) {
                String f = String.valueOf(row.get("fournisseur"));
                Double maeF = row.get("mae") != null ? ((Number) row.get("mae")).doubleValue() : null;
                Double rmseF = row.get("rmse") != null ? ((Number) row.get("rmse")).doubleValue() : null;
                long nF = row.get("n") != null ? ((Number) row.get("n")).longValue() : 0;
                Double pklF = (pklParFour.get(f) != null) ? pklParFour.get(f).get("mae") : null;
                Double ratioF = (maeF != null && pklF != null && pklF > 0) ? maeF / pklF : null;
                String statutF = ratioF == null ? "PKL_INDISPONIBLE"
                        : ratioF > SEUIL_CRITICAL ? "CRITICAL"
                        : ratioF > SEUIL_WARNING ? "WARNING" : "HEALTHY";
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("mae_live", maeF != null ? Math.round(maeF * 100) / 100.0 : null);
                e.put("rmse_live", rmseF != null ? Math.round(rmseF * 100) / 100.0 : null);
                e.put("mae_pkl", pklF != null ? Math.round(pklF * 100) / 100.0 : null);
                e.put("nb_confirmees", nF);
                e.put("ratio", ratioF != null ? Math.round(ratioF * 100) / 100.0 : null);
                e.put("statut", statutF);
                parFournisseur.put(f, e);
            }
        } catch (Exception e) {
            log.warn("par_fournisseur : {}", e.getMessage());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("par_fournisseur", parFournisseur);
        out.put("mae_live", maeLive != null ? Math.round(maeLive * 100) / 100.0 : null);
        out.put("rmse_live", rmseLive != null ? Math.round(rmseLive * 100) / 100.0 : null);
        out.put("mae_pkl", maePkl != null ? Math.round(maePkl * 100) / 100.0 : null);
        out.put("ratio_live_pkl", ratio != null ? Math.round(ratio * 100) / 100.0 : null);
        out.put("statut", statut);          // HEALTHY | WARNING | CRITICAL | AUCUNE_DONNEE
        out.put("degradation", degradation);
        out.put("nb_confirmees", nb);
        out.put("seuil_critical", SEUIL_CRITICAL);
        out.put("seuil_warning", SEUIL_WARNING);
        out.put("fenetre_jours", days);
        return out;
    }

    /**
     * GET /api/ia/admin/monitoring/live — vraies valeurs Live vs PKL.
     */
    @GetMapping("/monitoring/live")
    public ResponseEntity<?> monitoringLive(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(computeLiveSummary(days));
    }

    /**
     * POST /api/ia/admin/monitoring/check-now
     * Lance la vérification de dégradation À LA DEMANDE (démo jury).
     * Si dégradation : crée une notification admin (visible dans l'historique)
     * et, si body { "auto_retrain": true }, déclenche le réentraînement.
     */
    @PostMapping("/monitoring/check-now")
    public ResponseEntity<?> checkDegradationNow(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> summary = computeLiveSummary(30);
        boolean degradation = Boolean.TRUE.equals(summary.get("degradation"));
        boolean autoRetrain = body != null && Boolean.TRUE.equals(body.get("auto_retrain"));
        boolean alerteCreee = false, retrainLance = false;

        if (degradation) {
            NotificationLog alerte = NotificationLog.builder()
                    .id(UUID.randomUUID().toString())
                    .clientId("ADMIN")
                    .titre("Dégradation du modèle détectée")
                    .message(String.format(
                            "MAE Live %.2f TND > MAE PKL %.2f TND (ratio %.2f, seuil %.1f). Réentraînement recommandé.",
                            (Double) summary.get("mae_live"), (Double) summary.get("mae_pkl"),
                            (Double) summary.get("ratio_live_pkl"), SEUIL_CRITICAL))
                    .type("MODEL_DEGRADATION")
                    .envoyePar("monitoring")
                    .statut("NON_LU")
                    .dateEnvoi(LocalDateTime.now())
                    .build();
            notifLogRepo.save(alerte);
            alerteCreee = true;
            log.warn("⚠️ Dégradation détectée et notifiée à l'admin.");

            if (autoRetrain) {
                retrainService.triggerRetrain("monitoring-degradation");
                retrainLance = true;
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>(summary);
        resp.put("alerte_creee", alerteCreee);
        resp.put("retrain_lance", retrainLance);
        return ResponseEntity.ok(resp);
    }

// ════════════════════════════════════════════════════════════════════════
//  SCHEDULERS — EXÉCUTER N'IMPORTE QUEL JOB À LA DEMANDE  (les 12)
//  À AJOUTER dans AdminController.java (arborescence prevision/).
//
//  1) Ajoute ces 3 champs injectés (les autres sont déjà présents) :
//
//      @Autowired private com.pfe.clientdashboard.notification.Scheduler.NotificationScheduler notificationScheduler;
//      @Autowired private com.pfe.clientdashboard.notification.Scheduler.ReminderScheduler      reminderScheduler;
//      @Autowired private com.pfe.clientdashboard.offre.OfferApplicationService                 offerApplicationService;
//
//  2) Ajoute la méthode ci-dessous. (Imports déjà présents : NotificationLog,
//     LocalDateTime, UUID, LinkedHashMap, Map ; ajoute PathVariable si besoin.)
//
//      POST /api/ia/admin/schedulers/run/{id}
// ════════════════════════════════════════════════════════════════════════

    @PostMapping("/schedulers/run/{id}")
    public ResponseEntity<?> runScheduler(@PathVariable String id) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        long t0 = System.currentTimeMillis();
        try {
            switch (id) {

                // ── Forecasting ML ──────────────────────────────────────
                case "retrain_mensuel" -> {
                    retrainService.triggerRetrain("manuel-admin");
                    resp.put("message", "Réentraînement déclenché (voir Historique retrain).");
                }
                case "fastapi_health_check" -> {
                    retrainService.checkFastapiHealth();
                    resp.put("message", "Health-check FastAPI exécuté.");
                }
                case "monitor_performance" -> {
                    Map<String, Object> summary = computeLiveSummary(30);
                    boolean degradation = Boolean.TRUE.equals(summary.get("degradation"));
                    if (degradation) {
                        notifLogRepo.save(NotificationLog.builder()
                                .id(UUID.randomUUID().toString()).clientId("ADMIN")
                                .titre("Dégradation du modèle détectée")
                                .message(String.format(
                                        "MAE Live %.2f TND > MAE PKL %.2f TND (ratio %.2f). Réentraînement recommandé.",
                                        (Double) summary.get("mae_live"), (Double) summary.get("mae_pkl"),
                                        (Double) summary.get("ratio_live_pkl")))
                                .type("MODEL_DEGRADATION").envoyePar("monitoring").statut("NON_LU")
                                .dateEnvoi(LocalDateTime.now()).build());
                    }
                    resp.put("degradation", degradation);
                    resp.put("resultat", summary);
                    resp.put("message", degradation
                            ? "Dégradation détectée — notification admin créée."
                            : "Performances ML stables (statut : " + summary.get("statut") + ").");
                }
                case "cleanup_audit_logs" -> {
                    auditLogService.cleanupOldLogs();
                    resp.put("message", "Nettoyage des anciens logs d'audit exécuté.");
                }

                // ── Notifications ───────────────────────────────────────
                case "rappel_factures_quotidien" -> {
                    notificationScheduler.rappelFacturesQuotidien();
                    resp.put("message", "Rappels de factures à venir envoyés.");
                }
                case "alerte_solde_quotidien" -> {
                    notificationScheduler.alerteSoldeQuotidien();
                    resp.put("message", "Alertes de solde bas envoyées.");
                }
                case "bilan_hebdo_lundi" -> {
                    notificationScheduler.recommandationsHebdo();
                    resp.put("message", "Bilan hebdomadaire / recommandations envoyés.");
                }

                // ── Rappels (Reminder) ──────────────────────────────────
                case "rappels_factures_scannees" -> {
                    reminderScheduler.rappelsFacturesScannees();
                    resp.put("message", "Rappels des factures scannées envoyés.");
                }
                case "rappel_factures_demain" -> {
                    reminderScheduler.rappelFacturesOverdue();
                    resp.put("message", "Rappels des factures dues envoyés.");
                }

                // ── OCR ─────────────────────────────────────────────────
                case "ocr_feedback_analyse" -> {
                    feedbackLearningScheduler.analyserFeedbacksOCR();
                    resp.put("message", "Analyse des feedbacks OCR exécutée.");
                }

                // ── Marketing / Offres ──────────────────────────────────
                case "marketing_feedback_push" -> {
                    marketingFeedbackScheduler.pushMarketingFeedbackBatch();
                    resp.put("message", "Batch marketing poussé vers FastAPI.");
                }
                case "sweep_offres_expirees" -> {
                    offerApplicationService.sweepExpired();
                    resp.put("message", "Balayage des offres expirées exécuté.");
                }

                default -> {
                    resp.put("status", "info");
                    resp.put("message", "Job inconnu : " + id);
                    resp.put("duree_ms", System.currentTimeMillis() - t0);
                    return ResponseEntity.ok(resp);
                }
            }
            resp.put("status", "ok");
        } catch (Exception e) {
            resp.put("status", "error");
            resp.put("message", "Échec : " + e.getMessage());
        }
        resp.put("duree_ms", System.currentTimeMillis() - t0);
        return ResponseEntity.ok(resp);
    }
}
