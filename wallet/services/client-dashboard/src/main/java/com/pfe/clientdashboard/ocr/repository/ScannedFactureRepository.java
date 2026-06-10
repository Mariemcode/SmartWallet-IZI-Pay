package com.pfe.clientdashboard.ocr.repository;

import com.pfe.clientdashboard.ocr.entities.ScannedFacture;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScannedFactureRepository extends JpaRepository<ScannedFacture, UUID> {

    /** Recherche idempotente — utilisée par OcrController.programmerRappel */
    Optional<ScannedFacture> findByClientIdAndFournisseurLabelAndDateEcheance(
            String clientId, String fournisseurLabel, LocalDate dateEcheance);

    /** Toutes les factures d'un client */
    List<ScannedFacture> findByClientIdOrderByCreatedAtDesc(String clientId);

    /** Factures non payées du client */
    List<ScannedFacture> findByClientIdAndPayeFalseOrderByDateEcheanceAsc(String clientId);

    /** Pour la vue admin "qui a scanné quoi" */
    Page<ScannedFacture> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Pour ReminderScheduler — factures dont la date approche */
    @Query("""
        SELECT s FROM ScannedFacture s
         WHERE s.paye = false
           AND s.dateEcheance >= :today
           AND s.dateEcheance <= :limit
    """)
    List<ScannedFacture> findPendingBetween(
            @Param("today") LocalDate today,
            @Param("limit") LocalDate limit);

    /** Stats pour le dashboard admin */
    @Query("""
        SELECT s.fournisseurLabel, COUNT(s),
               COUNT(CASE WHEN s.paye = true THEN 1 END)
          FROM ScannedFacture s
         GROUP BY s.fournisseurLabel
         ORDER BY COUNT(s) DESC
    """)
    List<Object[]> statsByFournisseur();

    long countByPayeFalse();

    long countByCreatedAtAfter(java.time.LocalDateTime since);
}
