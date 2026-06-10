package com.pfe.clientdashboard.client.controller;

import com.pfe.clientdashboard.client.dtos.DashboardDto.*;
import com.pfe.clientdashboard.client.services.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller Dashboard Visualisation
 * <p>
 * Base URL : /api/dashboard/{clientId}
 * <p>
 * ─────────────────────────────────────────────────────────────────
 * ENDPOINTS DISPONIBLES :
 * <p>
 * GET /summary                     → Résumé wallet (solde, KPIs)
 * GET /profile                     → Profil client
 * GET /history                     → Historique paginé + filtres
 * GET /transactions/{txId}         → Détail d'une transaction
 * GET /categories/list             → Liste des catégories disponibles
 * GET /categories/breakdown        → Répartition par catégorie (camembert)
 * GET /categories/top              → Top 5 catégories de dépenses
 * GET /categories/flow             → Flux crédit/débit par catégorie
 * GET /charts/monthly              → Évolution mensuelle (courbe N mois)
 * GET /charts/weekly               → Évolution hebdomadaire (7 jours)
 * GET /stats                       → Statistiques période
 * ─────────────────────────────────────────────────────────────────
 */
@RestController
@RequestMapping("/dashboard/{clientId}")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    // ════════════════════════════════════════════════════════════════
    //  RÉSUMÉ WALLET
    //
    //  Retourne :
    //    - balance          : solde calculé
    //    - spentThisMonth   : total dépensé ce mois
    //    - receivedThisMonth: total reçu ce mois
    //    - totalTransactions: nb total transactions
    //    - transactionsThisMonth
    //    - currency
    //    - spentVariationPercent : variation vs mois dernier
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/summary")
    public ResponseEntity<WalletSummaryResponse> getSummary(
            @PathVariable String clientId) {

        return ResponseEntity.ok(dashboardService.getWalletSummary(clientId));
    }

    // ════════════════════════════════════════════════════════════════
    //  PROFIL CLIENT
    //
    //  Retourne :
    //    - id, firstName, lastName, phoneNumber
    //    - memberSince         : date d'inscription
    //    - topSpendingCategory : catégorie la + dépensée (3 mois)
    //    - totalTransactions
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/profile")
    public ResponseEntity<ClientProfileResponse> getProfile(
            @PathVariable String clientId) {

        return ResponseEntity.ok(dashboardService.getClientProfile(clientId));
    }

    // ════════════════════════════════════════════════════════════════
    //  HISTORIQUE TRANSACTIONS PAGINÉ
    //
    //  Paramètres query :
    //    category (opt.) : filtre par catégorie
    //                      ex: "Factures & Services"
    //    flowType (opt.) : "C" (crédits) ou "D" (débits)
    //    page    (défaut 0)
    //    size    (défaut 20)
    //
    //  Exemple :
    //    GET /history?category=Factures & Services&flowType=D&page=0&size=10
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/history")
    public ResponseEntity<TransactionPageResponse> getHistory(
            @PathVariable String clientId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String flowType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
                dashboardService.getHistory(clientId, category, flowType, page, size));
    }

    // ════════════════════════════════════════════════════════════════
    //  DÉTAIL D'UNE TRANSACTION
    //
    //  Retourne tous les champs :
    //    typeCode, typeTitle, typeOriginalTitle,
    //    category, subCategory, providerCode, providerName,
    //    receiverId, reversalFlag, flowType
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/transactions/{txId}")
    public ResponseEntity<TransactionDetailResponse> getTransactionDetail(
            @PathVariable String clientId,
            @PathVariable String txId) {

        return ResponseEntity.ok(
                dashboardService.getTransactionDetail(clientId, txId));
    }

    // ════════════════════════════════════════════════════════════════
    //  LISTE DES CATÉGORIES DISPONIBLES (pour les filtres front)
    //
    //  12 catégories dans le dataset :
    //    Annulation & Correction, Argent Recu, Depot & Retrait,
    //    Education & Institutions, Factures & Services,
    //    Frais & Commissions, Recharge Telephonique,
    //    Restaurants & Livraison, Shopping & Paiements,
    //    Transferts Envoyes, Transferts Recus,
    //    Voyages & Reservations
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/categories/list")
    public ResponseEntity<List<String>> getCategoryList(
            @PathVariable String clientId) {

        return ResponseEntity.ok(dashboardService.getAllCategories());
    }

    // ════════════════════════════════════════════════════════════════
    //  RÉPARTITION PAR CATÉGORIE (camembert / barres)
    //
    //  Paramètres query :
    //    flowType (défaut "D") : "D" dépenses | "C" revenus
    //    period   (défaut "THIS_MONTH") :
    //      THIS_MONTH | LAST_MONTH | LAST_3_MONTHS |
    //      LAST_6_MONTHS | THIS_YEAR | ALL
    //
    //  Retourne une liste triée par montant décroissant,
    //  chaque item avec category, totalAmount, count, percentage
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/categories/breakdown")
    public ResponseEntity<List<CategoryBreakdownItem>> getCategoryBreakdown(
            @PathVariable String clientId,
            @RequestParam(defaultValue = "D") String flowType,
            @RequestParam(defaultValue = "THIS_MONTH") String period) {

        return ResponseEntity.ok(
                dashboardService.getCategoryBreakdown(clientId, flowType, period));
    }

    // ════════════════════════════════════════════════════════════════
    //  TOP 5 CATÉGORIES DE DÉPENSES
    //
    //  Paramètres query :
    //    period (défaut "THIS_MONTH")
    //
    //  Retourne 5 items max avec rank, category,
    //  totalAmount, transactionCount, percentageOfTotal
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/categories/top")
    public ResponseEntity<List<TopCategoryItem>> getTopCategories(
            @PathVariable String clientId,
            @RequestParam(defaultValue = "THIS_MONTH") String period) {

        return ResponseEntity.ok(
                dashboardService.getTopCategories(clientId, period));
    }

    // ════════════════════════════════════════════════════════════════
    //  FLUX CRÉDIT vs DÉBIT PAR CATÉGORIE
    //
    //  Paramètres query :
    //    period (défaut "THIS_MONTH")
    //
    //  Retourne pour chaque catégorie :
    //    creditAmount, debitAmount, creditCount, debitCount
    //  Utile pour graphe en barres groupées
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/categories/flow")
    public ResponseEntity<List<CategoryFlowItem>> getCategoryFlow(
            @PathVariable String clientId,
            @RequestParam(defaultValue = "THIS_MONTH") String period) {

        return ResponseEntity.ok(
                dashboardService.getCategoryFlow(clientId, period));
    }

    // ════════════════════════════════════════════════════════════════
    //  ÉVOLUTION MENSUELLE (courbe)
    //
    //  Paramètres query :
    //    months (défaut 12) : nb de mois à remonter
    //
    //  Retourne une liste d'items avec :
    //    monthLabel ("2024-03"), totalCredit, totalDebit, netBalance
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/charts/monthly")
    public ResponseEntity<List<MonthlyEvolutionItem>> getMonthlyEvolution(
            @PathVariable String clientId,
            @RequestParam(defaultValue = "12") int months) {

        return ResponseEntity.ok(
                dashboardService.getMonthlyEvolution(clientId, months));
    }

    // ════════════════════════════════════════════════════════════════
    //  ÉVOLUTION HEBDOMADAIRE (barres 7 jours)
    //
    //  Pas de paramètre — retourne toujours les 7 derniers jours
    //  avec 0 si aucune transaction ce jour
    //
    //  Retourne 7 items avec :
    //    dayLabel ("Lun 03 Mar"), date, totalDebit, totalCredit
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/charts/weekly")
    public ResponseEntity<List<WeeklyEvolutionItem>> getWeeklyEvolution(
            @PathVariable String clientId) {

        return ResponseEntity.ok(
                dashboardService.getWeeklyEvolution(clientId));
    }

    // ════════════════════════════════════════════════════════════════
    //  STATISTIQUES PÉRIODE
    //
    //  Paramètres query :
    //    period (défaut "THIS_MONTH")
    //
    //  Retourne :
    //    avgTransactionAmount, maxTransaction, minTransaction,
    //    activeDays, avgDailySpend
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/stats")
    public ResponseEntity<PeriodStatsResponse> getPeriodStats(
            @PathVariable String clientId,
            @RequestParam(defaultValue = "THIS_MONTH") String period) {

        return ResponseEntity.ok(
                dashboardService.getPeriodStats(clientId, period));
    }
}
