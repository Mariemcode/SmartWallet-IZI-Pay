package com.pfe.clientdashboard.notification.Scheduler;

import com.pfe.clientdashboard.notification.entities.NotificationLog;
import com.pfe.clientdashboard.notification.service.FcmService;
import com.pfe.clientdashboard.notification.repository.FcmTokenRepository;
import com.pfe.clientdashboard.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * ReminderScheduler — Rappels intelligents de factures scannées
 * ==============================================================
 * Complète NotificationScheduler avec des rappels contextuels
 * basés sur les factures scannées via OCR (table scanned_facture).
 *
 * Planning des rappels :
 *   - 9h00 chaque jour  → rappels J-7, J-3, J-1, J pour factures scannées
 *   - 8h30 chaque jour  → vérification factures overdue (date_echeance dépassée)
 *
 * Table requise (SQL de migration inclus dans ce fichier en commentaire).
 *
 * ══ SQL MIGRATION ══════════════════════════════════════════════════════
 * CREATE TABLE IF NOT EXISTS scanned_facture (
 *   id               UUID DEFAULT gen_random_uuid() PRIMARY KEY,
 *   client_id        VARCHAR(64) NOT NULL,
 *   fournisseur_label VARCHAR(20) NOT NULL,
 *   fournisseur_nom  VARCHAR(50),
 *   montant          NUMERIC(10,3),
 *   date_echeance    DATE NOT NULL,
 *   reference        VARCHAR(30),
 *   rappel_j7_envoye BOOLEAN DEFAULT FALSE,
 *   rappel_j3_envoye BOOLEAN DEFAULT FALSE,
 *   rappel_j1_envoye BOOLEAN DEFAULT FALSE,
 *   rappel_j0_envoye BOOLEAN DEFAULT FALSE,
 *   paye             BOOLEAN DEFAULT FALSE,
 *   created_at       TIMESTAMP DEFAULT NOW(),
 *   UNIQUE(client_id, fournisseur_label, date_echeance)
 * );
 * ═══════════════════════════════════════════════════════════════════════
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final FcmTokenRepository    fcmTokenRepo;
    private final FcmService            fcmService;
    private final JdbcTemplate          jdbcTemplate;
    private final NotificationLogRepository notifLogRepo;

    // ══════════════════════════════════════════════════════════════
    //  RAPPELS FACTURES SCANNÉES — tous les jours à 9h00
    //  Lit la table scanned_facture et envoie les rappels J-7/J-3/J-1/J
    // ══════════════════════════════════════════════════════════════
    @Scheduled(cron = "0 0 9 * * *")
    public void rappelsFacturesScannees() {
        log.info("📅 ReminderScheduler — Rappels factures scannées");

        // Vérifier que la table existe
        if (!tableExists("scanned_facture")) {
            log.info("  ℹ️ Table scanned_facture absente — rappels OCR désactivés");
            return;
        }

        LocalDate today = LocalDate.now();
        int envois = 0;

        try {
            // Récupérer toutes les factures non payées avec leur statut de rappel
            List<Map<String, Object>> factures = jdbcTemplate.queryForList("""
                SELECT sf.id, sf.client_id, sf.fournisseur_nom, sf.montant, sf.date_echeance,
                       sf.rappel_j7_envoye, sf.rappel_j3_envoye,
                       sf.rappel_j1_envoye, sf.rappel_j0_envoye
                FROM scanned_facture sf
                WHERE sf.paye = FALSE
                  AND sf.date_echeance >= CURRENT_DATE - INTERVAL '1 day'
                  AND sf.date_echeance <= CURRENT_DATE + INTERVAL '8 days'
                """);

            for (Map<String, Object> fac : factures) {
                String clientId    = (String) fac.get("client_id");
                String nomFour     = (String) fac.get("fournisseur_nom");
                Object montantObj  = fac.get("montant");
                double montant     = montantObj instanceof Number n ? n.doubleValue() : 0;
                java.sql.Date sqlDate = (java.sql.Date) fac.get("date_echeance");
                LocalDate echeance = sqlDate.toLocalDate();
                String sfId        = fac.get("id").toString();

                long joursRestants = ChronoUnit.DAYS.between(today, echeance);

                // Trouver le token FCM du client
                var tokenOpt = fcmTokenRepo.findByClientId(clientId);
                if (tokenOpt.isEmpty()) continue;
                String token = tokenOpt.get().getToken();

                // ── J-7 ─────────────────────────────────────────
                if (joursRestants == 7 && Boolean.FALSE.equals(fac.get("rappel_j7_envoye"))) {
                    String msg = String.format(
                        "📋 Rappel : Facture %s de %.0f TND dans 7 jours (%s). Pensez à garder ce montant.",
                        nomFour, montant, echeance.format(DateTimeFormatter.ofPattern("dd/MM")));
                    envoyerEtLogger(token, clientId, "📋 Facture dans 7 jours", msg, "RAPPEL_J7");
                    jdbcTemplate.update(
                        "UPDATE scanned_facture SET rappel_j7_envoye=TRUE WHERE id=?::uuid", sfId);
                    envois++;
                }
                // ── J-3 ─────────────────────────────────────────
                else if (joursRestants == 3 && Boolean.FALSE.equals(fac.get("rappel_j3_envoye"))) {
                    // Récupérer le solde actuel pour l'afficher dans le rappel
                    double solde = getSoldeClient(clientId);
                    String statutSolde = solde >= montant ? "✅" : "⚠️";
                    String msg = String.format(
                        "⏰ %s dans 3 jours ! %.0f TND. Votre solde actuel : %.0f TND %s",
                        nomFour, montant, solde, statutSolde);
                    envoyerEtLogger(token, clientId, "⏰ " + nomFour + " dans 3 jours", msg, "RAPPEL_J3");
                    jdbcTemplate.update(
                        "UPDATE scanned_facture SET rappel_j3_envoye=TRUE WHERE id=?::uuid", sfId);
                    envois++;
                }
                // ── J-1 (veille) ─────────────────────────────────
                else if (joursRestants == 1 && Boolean.FALSE.equals(fac.get("rappel_j1_envoye"))) {
                    double solde = getSoldeClient(clientId);
                    String etat  = solde >= montant ? "Tout est prêt !" : "⚠️ Rechargez votre wallet !";
                    String msg   = String.format(
                        "⚠️ %s demain ! %.0f TND. Solde : %.0f TND. %s",
                        nomFour, montant, solde, etat);
                    envoyerEtLogger(token, clientId, "⚠️ " + nomFour + " demain !", msg, "RAPPEL_J1");
                    jdbcTemplate.update(
                        "UPDATE scanned_facture SET rappel_j1_envoye=TRUE WHERE id=?::uuid", sfId);
                    envois++;
                }
                // ── Jour J ──────────────────────────────────────
                else if (joursRestants <= 0 && Boolean.FALSE.equals(fac.get("rappel_j0_envoye"))) {
                    String msg = String.format(
                        "🚨 C'est aujourd'hui ! Payer votre facture %s de %.3f TND maintenant ?",
                        nomFour, montant);
                    envoyerEtLogger(token, clientId, "🚨 Payer " + nomFour + " maintenant", msg, "RAPPEL_J0");
                    jdbcTemplate.update(
                        "UPDATE scanned_facture SET rappel_j0_envoye=TRUE WHERE id=?::uuid", sfId);
                    envois++;
                }
            }

        } catch (Exception e) {
            log.error("❌ ReminderScheduler erreur : {}", e.getMessage());
        }

        log.info("  → {} rappels factures scannées envoyés", envois);
    }

    // ══════════════════════════════════════════════════════════════
    //  FACTURES OVERDUE — tous les jours à 8h30
    //  Notifie les clients dont la date d'échéance est dépassée
    // ══════════════════════════════════════════════════════════════
    @Scheduled(cron = "0 30 8 * * *")
    public void rappelFacturesOverdue() {
        log.info("🔴 ReminderScheduler — Factures overdue");

        if (!tableExists("scanned_facture")) return;

        try {
            List<Map<String, Object>> overdue = jdbcTemplate.queryForList("""
                SELECT sf.client_id, sf.fournisseur_nom, sf.montant, sf.date_echeance
                FROM scanned_facture sf
                WHERE sf.paye = FALSE
                  AND sf.date_echeance < CURRENT_DATE
                  AND sf.date_echeance >= CURRENT_DATE - INTERVAL '7 days'
                """);

            for (Map<String, Object> fac : overdue) {
                String clientId = (String) fac.get("client_id");
                String nom      = (String) fac.get("fournisseur_nom");
                double montant  = ((Number) fac.get("montant")).doubleValue();

                fcmTokenRepo.findByClientId(clientId).ifPresent(ft -> {
                    String msg = String.format(
                        "🔴 Facture %s de %.0f TND en retard ! Payez dès que possible.",
                        nom, montant);
                    try {
                        fcmService.sendAlerteClient(ft.getToken(), "CRITIQUE", msg, clientId);
                    } catch (Exception ignored) {}
                });
            }
            log.info("  → {} factures overdue notifiées", overdue.size());
        } catch (Exception e) {
            log.error("❌ Overdue check erreur : {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  API — Marquer une facture scannée comme payée
    //  Appelé par TransactionController après paiement
    // ══════════════════════════════════════════════════════════════
    public void marquerCommePayee(String clientId, String fournisseurLabel) {
        if (!tableExists("scanned_facture")) return;
        try {
            int updated = jdbcTemplate.update("""
                UPDATE scanned_facture
                SET paye = TRUE
                WHERE client_id = ? AND fournisseur_label = ? AND paye = FALSE
                  AND date_echeance >= CURRENT_DATE - INTERVAL '7 days'
                """, clientId, fournisseurLabel);
            if (updated > 0) {
                log.info("✅ Facture scannée marquée payée : {} → {}", fournisseurLabel, clientId);
            }
        } catch (Exception e) {
            log.warn("⚠️ marquerCommePayee échoué : {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  HELPERS PRIVÉS
    // ══════════════════════════════════════════════════════════════

    private void envoyerEtLogger(String token, String clientId,
                                  String titre, String message, String type) {
        try {
            fcmService.sendAlerteClient(token, "INFO", message, clientId);

            notifLogRepo.save(NotificationLog.builder()
                    .id(UUID.randomUUID().toString())
                    .clientId(clientId)
                    .titre(titre)
                    .message(message)
                    .type(type)
                    .envoyePar("REMINDER_SCHEDULER")
                    .statut("ENVOYE")
                    .dateEnvoi(LocalDateTime.now())
                    .build());

            log.info("  📱 {} → client {}", titre, clientId.substring(0, Math.min(8, clientId.length())));
        } catch (Exception e) {
            log.warn("  ⚠️ Envoi échoué pour {} : {}", clientId, e.getMessage());
        }
    }

    private double getSoldeClient(String clientId) {
        try {
            var result = jdbcTemplate.queryForObject("""
                SELECT COALESCE(
                    SUM(CASE WHEN tt.type = 'C' THEN t.amount ELSE -t.amount END),
                    0)
                FROM transaction t
                JOIN type_transaction tt ON t.transaction_type_id = tt.id
                WHERE t.client_id = ? AND t.reversal_flag = 'N'
                """, BigDecimal.class, clientId);
            return result != null ? result.doubleValue() : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private boolean tableExists(String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class, tableName);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
