package com.pfe.clientdashboard.client.repository;

import com.pfe.clientdashboard.transaction.entities.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    // ════════════════════════════════════════════════════════════════
    //  HISTORIQUE — requêtes paginées
    // ════════════════════════════════════════════════════════════════

    /** Toutes les transactions d'un client, triées par date décroissante */
    @Query("""
           SELECT t FROM Transaction t
           JOIN FETCH t.transactionType tt
           LEFT JOIN FETCH t.provider p
           WHERE t.client.id = :clientId
           ORDER BY t.transactionDate DESC
           """)
    Page<Transaction> findByClientId(
            @Param("clientId") String clientId,
            Pageable pageable);

    /** Filtrées par catégorie */
    @Query("""
           SELECT t FROM Transaction t
           JOIN FETCH t.transactionType tt
           LEFT JOIN FETCH t.provider p
           WHERE t.client.id = :clientId
             AND tt.category = :category
           ORDER BY t.transactionDate DESC
           """)
    Page<Transaction> findByClientIdAndCategory(
            @Param("clientId") String clientId,
            @Param("category") String category,
            Pageable pageable);

    /** Filtrées par flux (C ou D) */
    @Query("""
           SELECT t FROM Transaction t
           JOIN FETCH t.transactionType tt
           LEFT JOIN FETCH t.provider p
           WHERE t.client.id = :clientId
             AND tt.type = :flowType
           ORDER BY t.transactionDate DESC
           """)
    Page<Transaction> findByClientIdAndFlowType(
            @Param("clientId") String clientId,
            @Param("flowType") String flowType,
            Pageable pageable);

    /** Filtrées par catégorie ET flux */
    @Query("""
           SELECT t FROM Transaction t
           JOIN FETCH t.transactionType tt
           LEFT JOIN FETCH t.provider p
           WHERE t.client.id = :clientId
             AND tt.category = :category
             AND tt.type = :flowType
           ORDER BY t.transactionDate DESC
           """)
    Page<Transaction> findByClientIdAndCategoryAndFlowType(
            @Param("clientId") String clientId,
            @Param("category") String category,
            @Param("flowType") String flowType,
            Pageable pageable);

    // ════════════════════════════════════════════════════════════════
    //  SOLDE ET AGRÉGATIONS DE BASE
    // ════════════════════════════════════════════════════════════════

    @Query("""
           SELECT COALESCE(
               SUM(CASE WHEN tt.type = 'C' THEN t.amount ELSE -t.amount END),
               0)
           FROM Transaction t JOIN t.transactionType tt
           WHERE t.client.id = :clientId
             AND t.reversalFlag = 'N'
           """)
    BigDecimal calculateBalance(@Param("clientId") String clientId);

    @Query("""
           SELECT COALESCE(SUM(t.amount), 0)
           FROM Transaction t JOIN t.transactionType tt
           WHERE t.client.id = :clientId
             AND tt.type = 'D'
             AND t.reversalFlag = 'N'
             AND t.transactionDate BETWEEN :start AND :end
           """)
    BigDecimal sumDebit(
            @Param("clientId") String clientId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
           SELECT COALESCE(SUM(t.amount), 0)
           FROM Transaction t JOIN t.transactionType tt
           WHERE t.client.id = :clientId
             AND tt.type = 'C'
             AND t.reversalFlag = 'N'
             AND t.transactionDate BETWEEN :start AND :end
           """)
    BigDecimal sumCredit(
            @Param("clientId") String clientId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
           SELECT COUNT(t)
           FROM Transaction t
           WHERE t.client.id = :clientId
             AND t.transactionDate BETWEEN :start AND :end
           """)
    long countByClientAndPeriod(
            @Param("clientId") String clientId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
           SELECT COUNT(t)
           FROM Transaction t
           WHERE t.client.id = :clientId
             AND t.reversalFlag = 'N'
           """)
    long countNormal(@Param("clientId") String clientId);

    // ════════════════════════════════════════════════════════════════
    //  RÉPARTITION PAR CATÉGORIE
    // ════════════════════════════════════════════════════════════════

    @Query("""
           SELECT tt.category, SUM(t.amount), COUNT(t)
           FROM Transaction t JOIN t.transactionType tt
           WHERE t.client.id = :clientId
             AND tt.type = 'D'
             AND t.reversalFlag = 'N'
             AND tt.category NOT IN ('Annulation & Correction', 'Frais & Commissions')
             AND t.transactionDate BETWEEN :start AND :end
           GROUP BY tt.category
           ORDER BY SUM(t.amount) DESC
           """)
    List<Object[]> debitByCategory(
            @Param("clientId") String clientId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
           SELECT tt.category, SUM(t.amount), COUNT(t)
           FROM Transaction t JOIN t.transactionType tt
           WHERE t.client.id = :clientId
             AND tt.type = 'C'
             AND t.reversalFlag = 'N'
             AND tt.category NOT IN ('Annulation & Correction', 'Frais & Commissions')
             AND t.transactionDate BETWEEN :start AND :end
           GROUP BY tt.category
           ORDER BY SUM(t.amount) DESC
           """)
    List<Object[]> creditByCategory(
            @Param("clientId") String clientId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
           SELECT tt.category,
                  SUM(CASE WHEN tt.type = 'C' THEN t.amount ELSE 0 END),
                  SUM(CASE WHEN tt.type = 'D' THEN t.amount ELSE 0 END),
                  SUM(CASE WHEN tt.type = 'C' THEN 1 ELSE 0 END),
                  SUM(CASE WHEN tt.type = 'D' THEN 1 ELSE 0 END)
           FROM Transaction t JOIN t.transactionType tt
           WHERE t.client.id = :clientId
             AND t.reversalFlag = 'N'
             AND t.transactionDate BETWEEN :start AND :end
           GROUP BY tt.category
           ORDER BY SUM(t.amount) DESC
           """)
    List<Object[]> flowByCategory(
            @Param("clientId") String clientId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // ════════════════════════════════════════════════════════════════
    //  ÉVOLUTION TEMPORELLE
    // ════════════════════════════════════════════════════════════════

    @Query("""
           SELECT YEAR(t.transactionDate),
                  MONTH(t.transactionDate),
                  SUM(CASE WHEN tt.type = 'C' THEN t.amount ELSE 0 END),
                  SUM(CASE WHEN tt.type = 'D' THEN t.amount ELSE 0 END)
           FROM Transaction t JOIN t.transactionType tt
           WHERE t.client.id = :clientId
             AND t.reversalFlag = 'N'
             AND t.transactionDate >= :startDate
           GROUP BY YEAR(t.transactionDate), MONTH(t.transactionDate)
           ORDER BY YEAR(t.transactionDate) ASC, MONTH(t.transactionDate) ASC
           """)
    List<Object[]> monthlyEvolution(
            @Param("clientId") String clientId,
            @Param("startDate") LocalDateTime startDate);

    @Query("""
           SELECT CAST(t.transactionDate AS date),
                  SUM(CASE WHEN tt.type = 'C' THEN t.amount ELSE 0 END),
                  SUM(CASE WHEN tt.type = 'D' THEN t.amount ELSE 0 END)
           FROM Transaction t JOIN t.transactionType tt
           WHERE t.client.id = :clientId
             AND t.reversalFlag = 'N'
             AND t.transactionDate BETWEEN :start AND :end
           GROUP BY CAST(t.transactionDate AS date)
           ORDER BY CAST(t.transactionDate AS date) ASC
           """)
    List<Object[]> dailyEvolution(
            @Param("clientId") String clientId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // ════════════════════════════════════════════════════════════════
    //  STATISTIQUES PÉRIODE
    // ════════════════════════════════════════════════════════════════

    @Query("""
           SELECT AVG(t.amount),
                  MAX(t.amount),
                  MIN(t.amount),
                  COUNT(DISTINCT(CAST(t.transactionDate AS date)))
           FROM Transaction t JOIN t.transactionType tt
           WHERE t.client.id = :clientId
             AND tt.type = 'D'
             AND t.reversalFlag = 'N'
             AND t.transactionDate BETWEEN :start AND :end
           """)
    Object[] debitStats(
            @Param("clientId") String clientId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
           SELECT COUNT(DISTINCT(CONCAT(
               CAST(YEAR(t.transactionDate) AS string), '-',
               CAST(MONTH(t.transactionDate) AS string)
           )))
           FROM Transaction t
           JOIN t.transactionType tt
           WHERE t.client.id = :clientId
             AND tt.category = 'Factures & Services'
             AND t.reversalFlag = 'N'
           """)
    Long countDistinctMonthsByClientId(@Param("clientId") String clientId);
}