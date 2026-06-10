package com.pfe.clientdashboard.dashboardAdmin.repository;

import com.pfe.clientdashboard.transaction.entities.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DashboardRepository extends JpaRepository<Transaction, UUID> {

    /* ── KPIs — toutes les données ──────────────────────────────────── */
    @Query(value = """
        SELECT
            COUNT(t.id),
            COALESCE(SUM(t.amount), 0),
            COALESCE(AVG(t.amount), 0),
            COUNT(DISTINCT t.client_id),
            COUNT(DISTINCT t.provider_id)
        FROM transaction t
        WHERE t.reversal_flag <> 'R'
        """, nativeQuery = true)
    List<Object[]> getKpiRaw();

    /* ── Activité — 30 derniers jours ───────────────────────────────── */
    @Query(value = """
        SELECT
            TO_CHAR(t.transaction_date, 'YYYY-MM-DD') AS day,
            COUNT(t.id),
            COALESCE(SUM(t.amount), 0)
        FROM transaction t
        WHERE t.reversal_flag <> 'R'
          AND t.transaction_date >= NOW() - INTERVAL '30 days'
        GROUP BY day
        ORDER BY day
        """, nativeQuery = true)
    List<Object[]> getDailyActivity();

    /* ── Débit / Crédit ─────────────────────────────────────────────── */
    @Query(value = """
        SELECT
            tt.type,
            COUNT(t.id),
            COALESCE(SUM(t.amount), 0),
            COALESCE(AVG(t.amount), 0)
        FROM transaction t
        JOIN type_transaction tt ON tt.id = t.transaction_type_id
        WHERE t.reversal_flag <> 'R'
        GROUP BY tt.type
        """, nativeQuery = true)
    List<Object[]> getDebitCreditRaw();

    /* ── Grand totaux ───────────────────────────────────────────────── */
    @Query(value = """
        SELECT COUNT(t.id), COALESCE(SUM(t.amount), 0)
        FROM transaction t
        WHERE t.reversal_flag <> 'R'
        """, nativeQuery = true)
    List<Object[]> getGrandTotals();

    /* ── Top 3 Providers ────────────────────────────────────────────── */
    @Query(value = """
        SELECT
            p.id::text,
            p.provider_code,
            p.provider_name,
            COUNT(t.id),
            COALESCE(SUM(t.amount), 0)
        FROM transaction t
        JOIN provider p ON p.id = t.provider_id
        WHERE t.reversal_flag <> 'R'
        GROUP BY p.id, p.provider_code, p.provider_name
        ORDER BY COUNT(t.id) DESC
        LIMIT 3
        """, nativeQuery = true)
    List<Object[]> getTopProviders();

    /* ── Top 3 Catégories ───────────────────────────────────────────── */
    @Query(value = """
        SELECT
            tt.category,
            COUNT(t.id),
            COALESCE(SUM(t.amount), 0)
        FROM transaction t
        JOIN type_transaction tt ON tt.id = t.transaction_type_id
        WHERE t.reversal_flag <> 'R'
        GROUP BY tt.category
        ORDER BY COUNT(t.id) DESC
        LIMIT 3
        """, nativeQuery = true)
    List<Object[]> getTopCategories();
}