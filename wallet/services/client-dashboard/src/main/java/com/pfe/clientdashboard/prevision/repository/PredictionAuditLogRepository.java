package com.pfe.clientdashboard.prevision.repository;

import com.pfe.clientdashboard.prevision.entities.PredictionAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository pour la table d'audit des prédictions.
 * Utilisé uniquement pour le monitoring et la détection de dégradation.
 */
@Repository
public interface PredictionAuditLogRepository extends JpaRepository<PredictionAuditLog, String> {

    /**
     * Récupère tous les logs qui n'ont pas encore été évalués
     * ET dont la date de prévision est passée.
     * Utilisé par le job quotidien d'évaluation.
     */
    @Query("SELECT p FROM PredictionAuditLog p " +
            "WHERE p.resultatFinal IS NULL " +
            "AND p.datePrevue < :dateLimite " +
            "ORDER BY p.datePrevue ASC")
    List<PredictionAuditLog> findPendingEvaluations(@Param("dateLimite") LocalDate dateLimite);

    /**
     * Version simplifiée sans @Query explicite (Spring Data dérive la requête)
     */
    List<PredictionAuditLog> findByResultatFinalIsNullAndDatePrevueBefore(LocalDate date);

    /**
     * Calcule la MAE moyenne sur les prédictions confirmées (PAYE)
     * depuis une date donnée.
     */
    @Query("SELECT AVG(p.maeReel) FROM PredictionAuditLog p " +
            "WHERE p.resultatFinal = 'PAYE' " +
            "AND p.maeReel IS NOT NULL " +
            "AND p.snapshotDate >= :since")
    Double getAverageMAE(@Param("since") LocalDate since);

    /**
     * Compte le nombre de prédictions par statut sur une période donnée.
     * Utile pour les dashboards admin.
     */
    @Query("SELECT p.resultatFinal, COUNT(p) FROM PredictionAuditLog p " +
            "WHERE p.snapshotDate >= :since " +
            "GROUP BY p.resultatFinal")
    List<Object[]> countByResultatSince(@Param("since") LocalDate since);

    /**
     * MAE moyenne par fournisseur sur les 90 derniers jours.
     */
    @Query("SELECT p.fournisseur, AVG(p.maeReel), COUNT(p) " +
            "FROM PredictionAuditLog p " +
            "WHERE p.resultatFinal = 'PAYE' " +
            "AND p.maeReel IS NOT NULL " +
            "AND p.snapshotDate >= :since " +
            "GROUP BY p.fournisseur " +
            "ORDER BY p.fournisseur")
    List<Object[]> getMAEByFournisseur(@Param("since") LocalDate since);

    /**
     * Taux de confirmation (prédictions qui ont abouti à un paiement).
     */
    @Query("SELECT " +
            "CAST(COUNT(CASE WHEN p.resultatFinal = 'PAYE' THEN 1 END) AS double) / " +
            "CAST(COUNT(*) AS double) " +
            "FROM PredictionAuditLog p " +
            "WHERE p.snapshotDate >= :since")
    Double getConfirmationRate(@Param("since") LocalDate since);

    /**
     * Supprime les logs plus vieux qu'une certaine date (nettoyage périodique).
     */
    void deleteBySnapshotDateBefore(LocalDate dateLimite);
    @Query("SELECT p FROM PredictionAuditLog p " +
            "WHERE p.clientId = :clientId " +
            "AND p.fournisseur = :fournisseur " +
            "AND p.resultatFinal IS NULL " +
            "ORDER BY p.datePrevue DESC")
    List<PredictionAuditLog> findByClientIdAndFournisseurAndResultatFinalIsNull(
            @Param("clientId") String clientId,
            @Param("fournisseur") String fournisseur);
}