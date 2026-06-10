package com.pfe.clientdashboard.client.dtos;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tous les DTOs de réponse pour le dashboard de visualisation.
 * Organisés en classes statiques internes pour une lisibilité maximale.
 */
public class DashboardDto {

    // ════════════════════════════════════════════════════════════════
    //  1. RÉSUMÉ WALLET  ─  carte principale du dashboard
    //     GET /dashboard/{clientId}/summary
    // ════════════════════════════════════════════════════════════════

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalletSummaryResponse {
        /**
         * Solde = Σ crédits − Σ débits (reversal_flag = 'N')
         */
        private BigDecimal balance;
        /**
         * Total dépensé ce mois (type D)
         */
        private BigDecimal spentThisMonth;
        /**
         * Total reçu ce mois (type C)
         */
        private BigDecimal receivedThisMonth;
        /**
         * Nombre total de transactions normales
         */
        private long totalTransactions;
        /**
         * Nombre de transactions ce mois
         */
        private long transactionsThisMonth;
        private String currency;
        /**
         * Variation des dépenses vs mois précédent en % (null si pas de données)
         */
        private Double spentVariationPercent;
    }

    // ════════════════════════════════════════════════════════════════
    //  2. TRANSACTION — ligne dans l'historique
    //     GET /dashboard/{clientId}/history
    // ════════════════════════════════════════════════════════════════

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionResponse {
        private String id;
        private BigDecimal amount;
        private String currency;
        private LocalDateTime transactionDate;
        /**
         * "C" = Crédit (vert sur l'UI) | "D" = Débit (rouge sur l'UI)
         */
        private String flowType;
        /**
         * true = crédit, false = débit
         */
        private boolean credit;
        /**
         * Libellé du type (ex: "Paiement facture")
         */
        private String typeTitle;
        /**
         * Catégorie dashboard (ex: "Factures & Services")
         */
        private String category;
        /**
         * Sous-catégorie (ex: "Paiement de facture")
         */
        private String subCategory;
        /**
         * Fournisseur si présent (ex: "Sonede", "Topnet")
         */
        private String providerName;
        /**
         * "N" ou "R"
         */
        private String reversalFlag;
    }

    // ════════════════════════════════════════════════════════════════
    //  3. HISTORIQUE PAGINÉ
    // ════════════════════════════════════════════════════════════════

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionPageResponse {
        private List<TransactionResponse> transactions;
        private long totalElements;
        private int totalPages;
        private int currentPage;
        private int pageSize;
    }

    // ════════════════════════════════════════════════════════════════
    //  4. DÉTAIL COMPLET D'UNE TRANSACTION
    //     GET /dashboard/{clientId}/transactions/{txId}
    // ════════════════════════════════════════════════════════════════

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionDetailResponse {
        private String id;
        private BigDecimal amount;
        private String currency;
        private LocalDateTime transactionDate;
        private String flowType;
        private boolean credit;
        private String typeCode;
        private String typeTitle;
        private String typeOriginalTitle;
        private String category;
        private String subCategory;
        private String providerCode;
        private String providerName;
        private String receiverId;
        private String reversalFlag;
    }

    // ════════════════════════════════════════════════════════════════
    //  5. RÉPARTITION PAR CATÉGORIE — camembert / barres
    //     GET /dashboard/{clientId}/categories/breakdown
    // ════════════════════════════════════════════════════════════════

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryBreakdownItem {
        /**
         * ex: "Factures & Services"
         */
        private String category;
        private BigDecimal totalAmount;
        private long count;
        /**
         * % sur total de la période
         */
        private Double percentage;
    }

    // ════════════════════════════════════════════════════════════════
    //  6. ÉVOLUTION MENSUELLE — courbe sur N mois
    //     GET /dashboard/{clientId}/charts/monthly
    // ════════════════════════════════════════════════════════════════

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyEvolutionItem {
        /**
         * Format "YYYY-MM" (ex: "2024-03")
         */
        private String monthLabel;
        private BigDecimal totalCredit;
        private BigDecimal totalDebit;
        /**
         * Solde net = crédit − débit
         */
        private BigDecimal netBalance;
    }

    // ════════════════════════════════════════════════════════════════
    //  7. ÉVOLUTION HEBDOMADAIRE — barres 7 derniers jours
    //     GET /dashboard/{clientId}/charts/weekly
    // ════════════════════════════════════════════════════════════════

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyEvolutionItem {
        /**
         * ex: "Lun 03 Mar"
         */
        private String dayLabel;
        /**
         * ISO date ex: "2024-03-04"
         */
        private String date;
        private BigDecimal totalDebit;
        private BigDecimal totalCredit;
    }

    // ════════════════════════════════════════════════════════════════
    //  8. TOP 5 CATÉGORIES DE DÉPENSES
    //     GET /dashboard/{clientId}/categories/top
    // ════════════════════════════════════════════════════════════════

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopCategoryItem {
        private int rank;
        private String category;
        private BigDecimal totalAmount;
        private long transactionCount;
        private Double percentageOfTotal;
    }

    // ════════════════════════════════════════════════════════════════
    //  9. STATISTIQUES PÉRIODE — KPIs chiffrés
    //     GET /dashboard/{clientId}/stats
    // ════════════════════════════════════════════════════════════════

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodStatsResponse {
        private String period;
        /**
         * Montant moyen par débit
         */
        private BigDecimal avgTransactionAmount;
        /**
         * Débit max sur la période
         */
        private BigDecimal maxTransaction;
        /**
         * Débit min sur la période
         */
        private BigDecimal minTransaction;
        /**
         * Nb de jours avec au moins 1 transaction
         */
        private long activeDays;
        /**
         * Dépense journalière moyenne
         */
        private BigDecimal avgDailySpend;
    }

    // ════════════════════════════════════════════════════════════════
    //  10. FLUX CRÉDIT vs DÉBIT PAR CATÉGORIE — graphe comparatif
    //      GET /dashboard/{clientId}/categories/flow
    // ════════════════════════════════════════════════════════════════

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryFlowItem {
        private String category;
        private BigDecimal creditAmount;
        private BigDecimal debitAmount;
        private long creditCount;
        private long debitCount;
    }

    // ════════════════════════════════════════════════════════════════
    //  11. PROFIL CLIENT — en-tête dashboard
    //      GET /dashboard/{clientId}/profile
    // ════════════════════════════════════════════════════════════════

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientProfileResponse {
        private String id;
        private String firstName;
        private String lastName;
        private String phoneNumber;
        private LocalDateTime memberSince;
        /**
         * Catégorie de dépense la plus fréquente (3 derniers mois)
         */
        private String topSpendingCategory;
        private long totalTransactions;
    }
}
