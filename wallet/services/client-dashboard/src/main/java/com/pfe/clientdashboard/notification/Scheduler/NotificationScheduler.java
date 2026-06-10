package com.pfe.clientdashboard.notification.Scheduler;

import com.pfe.clientdashboard.notification.entities.FcmToken;
import com.pfe.clientdashboard.notification.service.FcmService;
import com.pfe.clientdashboard.notification.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * NotificationScheduler — Notifications push automatiques
 * =========================================================
 * Envoie des notifications même quand l'app est fermée :
 *
 *   - 8h00 chaque jour  → rappels factures à 3 jours
 *   - 9h00 chaque jour  → alertes solde pour les clients à risque
 *   - 10h00 chaque lundi → recommandations hebdomadaires
 *
 * Les notifications arrivent via Firebase FCM et s'affichent
 * en haut de l'écran avec son, même si l'app est fermée.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final FcmTokenRepository fcmTokenRepo;
    private final FcmService fcmService;
    private final JdbcTemplate jdbcTemplate;

    // ══════════════════════════════════════════════════════════════
    //  RAPPELS FACTURES — tous les jours à 8h00
    //  "Votre facture électricité arrive dans 3 jours (~120 TND)"
    // ══════════════════════════════════════════════════════════════
    @Scheduled(cron = "0 0 8 * * *")
    public void rappelFacturesQuotidien() {
        log.info("📋 Scheduler — Rappels factures quotidiens");

        // Chercher les clients qui ont une facture dans 1-3 jours
        // en se basant sur factures_ref (dernière date + intervalle)
        List<FcmToken> tokens = fcmTokenRepo.findAll();
        int envois = 0;

        for (FcmToken ft : tokens) {
            try {
                // Chercher les factures proches pour ce client
                List<Map<String, Object>> factures = jdbcTemplate.queryForList("""
                    SELECT DISTINCT tt.title, tt.id as type_id
                    FROM transaction t
                    JOIN type_transaction tt ON t.transaction_type_id = tt.id
                    WHERE t.client_id = ?
                      AND tt.category = 'Factures & Services'
                      AND t.reversal_flag = 'N'
                    GROUP BY tt.title, tt.id
                    HAVING MAX(t.transaction_date) < NOW() - INTERVAL '25 days'
                       AND MAX(t.transaction_date) > NOW() - INTERVAL '35 days'
                    """, ft.getClientId());

                for (Map<String, Object> fac : factures) {
                    String titre = (String) fac.get("title");
                    String nomSimple = traduire(titre);

                    fcmService.sendRappelFacture(
                            ft.getToken(), nomSimple.toUpperCase(),
                            0, 3, ft.getClientId());
                    envois++;
                }
            } catch (Exception e) {
                log.debug("Rappel échoué pour {} : {}", ft.getClientId(), e.getMessage());
            }
        }

        log.info("  → {} rappels envoyés", envois);
    }

    // ══════════════════════════════════════════════════════════════
    //  ALERTES SOLDE — tous les jours à 9h00
    //  Pour les clients dont le solde est < 50 TND
    // ══════════════════════════════════════════════════════════════
    @Scheduled(cron = "0 0 9 * * *")
    public void alerteSoldeQuotidien() {
        log.info("💰 Scheduler — Alertes solde quotidiennes");

        List<FcmToken> tokens = fcmTokenRepo.findAll();
        int envois = 0;

        for (FcmToken ft : tokens) {
            try {
                // Calculer le solde
                List<Map<String, Object>> result = jdbcTemplate.queryForList("""
                    SELECT COALESCE(
                        SUM(CASE WHEN tt.type = 'C' THEN t.amount ELSE -t.amount END),
                        0) as solde
                    FROM transaction t
                    JOIN type_transaction tt ON t.transaction_type_id = tt.id
                    WHERE t.client_id = ? AND t.reversal_flag = 'N'
                    """, ft.getClientId());

                if (result.isEmpty()) continue;
                double solde = ((Number) result.get(0).get("solde")).doubleValue();

                if (solde < 50 && solde > -500) {
                    fcmService.sendAlerteClient(
                            ft.getToken(),
                            solde < 20 ? "CRITIQUE" : "ATTENTION",
                            String.format("Votre solde est de %.0f TND. Pensez à recharger.", solde),
                            ft.getClientId());
                    envois++;
                }
            } catch (Exception e) {
                log.debug("Alerte solde échouée pour {} : {}", ft.getClientId(), e.getMessage());
            }
        }

        log.info("  → {} alertes solde envoyées", envois);
    }

    // ══════════════════════════════════════════════════════════════
    //  RECOMMANDATIONS — chaque lundi à 10h00
    //  "Découvrez vos conseils de la semaine pour mieux gérer"
    // ══════════════════════════════════════════════════════════════
    @Scheduled(cron = "0 0 10 * * MON")
    public void recommandationsHebdo() {
        log.info("💡 Scheduler — Recommandations hebdomadaires");

        List<FcmToken> tokens = fcmTokenRepo.findAll();

        if (tokens.isEmpty()) {
            log.info("  Aucun client avec token FCM");
            return;
        }

        List<String> allTokens = tokens.stream()
                .map(FcmToken::getToken)
                .toList();

        // Notification groupée à tous les clients
        fcmService.sendMulticast(
                allTokens,
                "💡 Vos conseils de la semaine",
                "Découvrez comment mieux gérer votre wallet cette semaine",
                "recommandation_hebdo"
        );

        log.info("  → {} notifications recommandation envoyées", allTokens.size());
    }

    // ══════════════════════════════════════════════════════════════
    //  ADMIN — Notification après réentraînement
    //  Envoi au topic "admin" (pas aux clients)
    // ══════════════════════════════════════════════════════════════
    public void notifyAdminRetrain(String status, String details) {
        String title = "success".equals(status)
                ? "✅ Modèles ML mis à jour"
                : "❌ Réentraînement échoué";
        fcmService.sendAdminNotification(title, details);
    }

    private String traduire(String title) {
        if (title == null) return "facture";
        String up = title.toUpperCase();
        if (up.contains("STEG")) return "électricité";
        if (up.contains("SONEDE")) return "eau";
        if (up.contains("TOPNET")) return "internet";
        if (up.contains("BEE")) return "internet";
        if (up.contains("TT") || up.contains("TUNISIE")) return "téléphone";
        if (up.contains("OOREDOO")) return "Ooredoo";
        return title;
    }
}
