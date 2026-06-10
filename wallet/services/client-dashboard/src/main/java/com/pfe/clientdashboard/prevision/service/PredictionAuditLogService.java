package com.pfe.clientdashboard.prevision.service;

import com.pfe.clientdashboard.prevision.entities.PredictionAuditLog;
import com.pfe.clientdashboard.prevision.repository.PredictionAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionAuditLogService {

    private final PredictionAuditLogRepository logRepo;
    private final JdbcTemplate jdbcTemplate;

    private static final Map<String, String> TYPE_IDS = Map.of(
            "TOPNET", "1461b464-fa44-477a-90c3-a9d68acdf29a",
            "BEE",    "08e939ae-af2c-428f-8ece-5862e56de5d3",
            "SONEDE", "18de5279-60d1-40f0-bb68-54b29d6f1ba8",
            "STEG",   "07ba063e-a1fb-4fd9-87a7-243efcc55af2",
            "TT",     "c428964b-5929-4480-a569-cb2ef1bc3b27",
            "OOREDOO","2d0d0c45-9941-42d9-9849-0f61d06c4a7b"
    );

    @Async
    public void saveSnapshot(String clientId, String fournisseur, Double montant, String datePrevue) {
        try {
            PredictionAuditLog log = PredictionAuditLog.builder()
                    .id(UUID.randomUUID().toString())
                    .clientId(clientId)
                    .fournisseur(fournisseur)
                    .montantPrevu(BigDecimal.valueOf(montant))
                    .datePrevue(LocalDate.parse(datePrevue))
                    .snapshotDate(LocalDateTime.now())
                    .build();
            logRepo.save(log);
        } catch (Exception e) {
            log.error("Erreur sauvegarde audit pour {} - {}", clientId, fournisseur, e);
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void evaluatePastPredictions() {
        log.info("📋 Début évaluation des prédictions passées...");
        List<PredictionAuditLog> pending = logRepo.findByResultatFinalIsNullAndDatePrevueBefore(LocalDate.now());

        int updated = 0;
        // ✅ RENOMMER "log" → "auditLog" pour éviter le conflit avec le logger
        for (PredictionAuditLog auditLog : pending) {
            String typeId = TYPE_IDS.get(auditLog.getFournisseur());
            if (typeId == null) continue;

            LocalDateTime start = auditLog.getDatePrevue().minusDays(15).atStartOfDay();
            LocalDateTime end = auditLog.getDatePrevue().plusDays(15).atTime(LocalTime.MAX);

            List<Map<String, Object>> payments = jdbcTemplate.queryForList(
                    "SELECT amount, transaction_date FROM transaction " +
                            "WHERE client_id = ? AND transaction_type_id = ? " +
                            "AND transaction_date BETWEEN ? AND ? AND reversal_flag = 'N' " +
                            "ORDER BY transaction_date ASC LIMIT 1",
                    auditLog.getClientId(), typeId, start, end
            );

            if (!payments.isEmpty()) {
                Map<String, Object> p = payments.get(0);
                BigDecimal reel = (BigDecimal) p.get("amount");

                Object dateObj = p.get("transaction_date");
                LocalDateTime dateP;
                if (dateObj instanceof java.sql.Timestamp) {
                    dateP = ((java.sql.Timestamp) dateObj).toLocalDateTime();
                } else if (dateObj instanceof LocalDateTime) {
                    dateP = (LocalDateTime) dateObj;
                } else {
                    // ✅ Maintenant ça fonctionne car "log" n'est plus masqué
                    log.warn("Type inattendu pour transaction_date: {}",
                            dateObj != null ? dateObj.getClass().getName() : "null");
                    continue;
                }

                auditLog.setResultatFinal("PAYE");
                auditLog.setMontantReel(reel);
                auditLog.setDatePaiementReel(dateP);
                auditLog.setMaeReel(auditLog.getMontantPrevu().subtract(reel).abs());
            } else {
                auditLog.setResultatFinal("NON_PAYE");
            }
            logRepo.save(auditLog);
            updated++;
        }
        log.info("✅ Évaluation terminée. {} logs mis à jour.", updated);
    }

    public Double getLiveMAE(int days) {
        LocalDate since = LocalDate.now().minusDays(days);
        return logRepo.getAverageMAE(since);
    }

    @Scheduled(cron = "0 0 5 * * SUN")
    @Transactional
    public void cleanupOldLogs() {
        LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6);
        log.info("🧹 Nettoyage des logs d'audit antérieurs au {}", sixMonthsAgo);
        long countBefore = logRepo.count();
        logRepo.deleteBySnapshotDateBefore(sixMonthsAgo);
        long countAfter = logRepo.count();
        log.info("✅ Nettoyage terminé. {} logs supprimés, {} restants.", countBefore - countAfter, countAfter);
    }

    public Map<String, Object> getMonitoringStats() {
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        LocalDate ninetyDaysAgo = LocalDate.now().minusDays(90);

        Map<String, Object> stats = new LinkedHashMap<>();

        try {
            Double tauxConfirmation = logRepo.getConfirmationRate(thirtyDaysAgo);
            stats.put("taux_confirmation_30j", tauxConfirmation != null
                    ? Math.round(tauxConfirmation * 1000) / 10.0 + "%"
                    : "N/A");
        } catch (Exception e) {
            stats.put("taux_confirmation_30j", "N/A");
        }

        try {
            Double maeLive = logRepo.getAverageMAE(thirtyDaysAgo);
            stats.put("mae_en_ligne_30j_TND", maeLive != null
                    ? Math.round(maeLive * 100) / 100.0
                    : "N/A");
        } catch (Exception e) {
            stats.put("mae_en_ligne_30j_TND", "N/A");
        }

        try {
            Map<String, Object> maeParFournisseur = new LinkedHashMap<>();
            List<Object[]> maeRows = logRepo.getMAEByFournisseur(ninetyDaysAgo);
            if (maeRows != null) {
                for (Object[] row : maeRows) {
                    String fournisseur = (String) row[0];
                    Double mae = row[1] != null ? ((Number) row[1]).doubleValue() : null;
                    Long count = ((Number) row[2]).longValue();
                    maeParFournisseur.put(fournisseur, Map.of(
                            "mae_TND", mae != null ? Math.round(mae * 100) / 100.0 : "N/A",
                            "nb_confirmees", count
                    ));
                }
            }
            stats.put("mae_par_fournisseur", maeParFournisseur);
        } catch (Exception e) {
            stats.put("mae_par_fournisseur", Map.of());
        }

        try {
            Map<String, Long> parStatut = new LinkedHashMap<>();
            List<Object[]> statutRows = logRepo.countByResultatSince(thirtyDaysAgo);
            if (statutRows != null) {
                for (Object[] row : statutRows) {
                    String statut = row[0] != null ? row[0].toString() : "EN_ATTENTE";
                    Long count = ((Number) row[1]).longValue();
                    parStatut.put(statut, count);
                }
            }
            stats.put("repartition_statuts_30j", parStatut);
        } catch (Exception e) {
            stats.put("repartition_statuts_30j", Map.of());
        }

        try {
            stats.put("total_logs_audit", logRepo.count());
        } catch (Exception e) {
            stats.put("total_logs_audit", 0);
        }

        return stats;
    }
    /**
     * Met à jour immédiatement le log d'audit après un paiement.
     * À appeler depuis TransactionController.
     */
    @Transactional
    public void markAsPaidImmediately(String clientId, String fournisseur, BigDecimal montantReel) {
        log.info("📝 Mise à jour immédiate du log pour {} - {}", clientId, fournisseur);

        // Trouver le log le plus récent pour ce client et fournisseur
        List<PredictionAuditLog> logs = logRepo.findByClientIdAndFournisseurAndResultatFinalIsNull(
                clientId, fournisseur);

        if (logs.isEmpty()) {
            log.warn("⚠️ Aucun log trouvé pour {} - {}", clientId, fournisseur);
            return;
        }

        // Prendre le plus récent (trié par date_prevue DESC)
        PredictionAuditLog auditLog = logs.stream()  // 🌟 RENOMMÉ ICI
                .max(Comparator.comparing(PredictionAuditLog::getDatePrevue))
                .orElse(null);

        if (auditLog != null) {  // 🌟 ET ICI
            auditLog.setResultatFinal("PAYE");
            auditLog.setMontantReel(montantReel);
            auditLog.setDatePaiementReel(LocalDateTime.now());
            auditLog.setMaeReel(auditLog.getMontantPrevu().subtract(montantReel).abs());
            logRepo.save(auditLog);
            log.info("✅ Log mis à jour immédiatement : {} - {} payé", clientId, fournisseur);
        }
    }
    // ════════════════════════════════════════════════════════════════
//  PredictionAuditLogService — PATCH v7
//
//  Ajoute 4 méthodes simples utilisées par les endpoints admin :
//    countTotal(), countPending(), countByResultat(), getOldestPendingDate()
//
//  À insérer dans `service/PredictionAuditLogService.java` n'importe où
//  dans la classe (par exemple juste avant la méthode getMonitoringStats).
//
//  Aucun nouvel import requis (utilise ceux déjà présents).
// ════════════════════════════════════════════════════════════════


    /**
     * Total de tous les snapshots d'audit (peu importe le statut).
     * Utilisé par l'admin pour afficher "X prédictions auditées au total".
     */
    public long countTotal() {
        try {
            return logRepo.count();
        } catch (Exception e) {
            log.warn("countTotal : {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Nombre de logs en attente d'évaluation
     * (resultatFinal IS NULL ET date_prevue < today).
     */
    public long countPending() {
        try {
            return logRepo.findByResultatFinalIsNullAndDatePrevueBefore(LocalDate.now()).size();
        } catch (Exception e) {
            log.warn("countPending : {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Nombre de logs avec un certain resultatFinal (PAYE ou NON_PAYE).
     * Implémentation simple : on charge tout et on filtre.
     * Si le volume devient gros, ajouter une query JPQL dédiée.
     */
    public long countByResultat(String resultat) {
        try {
            return logRepo.findAll().stream()
                    .filter(l -> resultat.equals(l.getResultatFinal()))
                    .count();
        } catch (Exception e) {
            log.warn("countByResultat : {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Date du plus vieux snapshot pending — donne une idée de l'ancienneté
     * des données non encore évaluées.
     */
    public String getOldestPendingDate() {
        try {
            return logRepo.findByResultatFinalIsNullAndDatePrevueBefore(LocalDate.now()).stream()
                    .map(l -> l.getDatePrevue())
                    .min(java.time.LocalDate::compareTo)
                    .map(java.time.LocalDate::toString)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("getOldestPendingDate : {}", e.getMessage());
            return null;
        }
    }
}