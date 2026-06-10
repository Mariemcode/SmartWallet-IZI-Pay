package com.pfe.clientdashboard.client.services;

import com.pfe.clientdashboard.client.repository.ClientRepository;
import com.pfe.clientdashboard.client.dtos.DashboardDto.*;
import com.pfe.clientdashboard.client.repository.TransactionRepository;
import com.pfe.clientdashboard.client.entities.Client;
import com.pfe.clientdashboard.transaction.entities.Transaction;
import com.pfe.clientdashboard.transactionType.entities.TransactionType;
import com.pfe.clientdashboard.exception.ResourceNotFoundException;
import com.pfe.clientdashboard.transactionType.repository.TransactionTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final TransactionRepository txRepo;
    private final ClientRepository clientRepo;
    private final TransactionTypeRepository typeRepo;

    // ════════════════════════════════════════════════════════════════
    //  1. RÉSUMÉ WALLET
    //     Solde, dépenses du mois, reçu du mois, variation
    // ════════════════════════════════════════════════════════════════

    public WalletSummaryResponse getWalletSummary(String clientId) {
        assertClientExists(clientId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startThisMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime startLastMonth = startThisMonth.minusMonths(1);
        LocalDateTime endLastMonth = startThisMonth.minusNanos(1);

        BigDecimal balance = safe(txRepo.calculateBalance(clientId));
        BigDecimal spentThisMonth = safe(txRepo.sumDebit(clientId, startThisMonth, now));
        BigDecimal receivedThisMonth = safe(txRepo.sumCredit(clientId, startThisMonth, now));
        BigDecimal spentLastMonth = safe(txRepo.sumDebit(clientId, startLastMonth, endLastMonth));
        long totalTx = txRepo.countNormal(clientId);
        long txThisMonth = txRepo.countByClientAndPeriod(clientId, startThisMonth, now);

        // Variation dépenses vs mois dernier en %
        Double variation = null;
        if (spentLastMonth.compareTo(BigDecimal.ZERO) > 0) {
            variation = spentThisMonth
                    .subtract(spentLastMonth)
                    .divide(spentLastMonth, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        return WalletSummaryResponse.builder()
                .balance(balance)
                .spentThisMonth(spentThisMonth)
                .receivedThisMonth(receivedThisMonth)
                .totalTransactions(totalTx)
                .transactionsThisMonth(txThisMonth)
                .currency("TND")
                .spentVariationPercent(variation)
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    //  2. HISTORIQUE TRANSACTIONS PAGINÉ
    //
    //  Paramètres :
    //    category : filtre par catégorie (null = toutes)
    //    flowType : "C" ou "D" (null = tous)
    //    page/size : pagination (défaut 0/20)
    // ════════════════════════════════════════════════════════════════

    public TransactionPageResponse getHistory(
            String clientId, String category, String flowType, int page, int size) {

        assertClientExists(clientId);
        Pageable pageable = PageRequest.of(page, size);

        boolean hasCategory = category != null && !category.isBlank();
        boolean hasFlow = flowType != null && !flowType.isBlank();

        Page<Transaction> txPage;
        if (hasCategory && hasFlow)
            txPage = txRepo.findByClientIdAndCategoryAndFlowType(clientId, category, flowType, pageable);
        else if (hasCategory) txPage = txRepo.findByClientIdAndCategory(clientId, category, pageable);
        else if (hasFlow) txPage = txRepo.findByClientIdAndFlowType(clientId, flowType, pageable);
        else txPage = txRepo.findByClientId(clientId, pageable);

        return TransactionPageResponse.builder()
                .transactions(txPage.getContent().stream()
                        .map(this::toTransactionResponse)
                        .collect(Collectors.toList()))
                .totalElements(txPage.getTotalElements())
                .totalPages(txPage.getTotalPages())
                .currentPage(page)
                .pageSize(size)
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    //  3. DÉTAIL D'UNE TRANSACTION
    // ════════════════════════════════════════════════════════════════

    public TransactionDetailResponse getTransactionDetail(String clientId, String txId) {
        assertClientExists(clientId);

        Transaction tx = txRepo.findById(txId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction introuvable : " + txId));

        // Vérification d'appartenance au client
        if (tx.getClient() == null || !clientId.equals(tx.getClient().getId())) {
            throw new ResourceNotFoundException("Transaction introuvable pour ce client");
        }

        TransactionType tt = tx.getTransactionType();
        return TransactionDetailResponse.builder()
                .id(tx.getId())
                .amount(tx.getAmount())
                .currency(tx.getCurrency() != null ? tx.getCurrency() : "TND")
                .transactionDate(tx.getTransactionDate())
                .flowType(tt != null ? tt.getType() : null)
                .credit(tx.isCredit())
                .typeCode(tt != null ? tt.getCode() : null)
                .typeTitle(tt != null ? tt.getTitle() : null)
                .typeOriginalTitle(tt != null ? tt.getOriginalTitle() : null)
                .category(tt != null ? tt.getCategory() : null)
                .subCategory(tt != null ? tt.getSubCategory() : null)
                .providerCode(tx.getProvider() != null ? tx.getProvider().getProviderCode() : null)
                .providerName(tx.getProvider() != null ? tx.getProvider().getProviderName() : null)
                .receiverId(tx.getReceiverId())
                .reversalFlag(tx.getReversalFlag())
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    //  4. RÉPARTITION PAR CATÉGORIE (camembert / barres)
    //
    //  flowType : "D" (dépenses) ou "C" (revenus) — défaut : "D"
    //  period   : THIS_MONTH | LAST_MONTH | LAST_3_MONTHS |
    //             LAST_6_MONTHS | THIS_YEAR | ALL
    // ════════════════════════════════════════════════════════════════

    public List<CategoryBreakdownItem> getCategoryBreakdown(
            String clientId, String flowType, String period) {

        assertClientExists(clientId);
        LocalDateTime[] range = resolvePeriod(period);

        List<Object[]> raw = "C".equalsIgnoreCase(flowType)
                ? txRepo.creditByCategory(clientId, range[0], range[1])
                : txRepo.debitByCategory(clientId, range[0], range[1]);

        BigDecimal total = raw.stream()
                .map(r -> toBD(r[1]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return raw.stream().map(row -> {
            BigDecimal amt = toBD(row[1]);
            long cnt = toLong(row[2]);
            double pct = total.compareTo(BigDecimal.ZERO) > 0
                    ? amt.divide(total, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0.0;

            return CategoryBreakdownItem.builder()
                    .category((String) row[0])
                    .totalAmount(amt)
                    .count(cnt)
                    .percentage(round2(pct))
                    .build();
        }).collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════════
    //  5. ÉVOLUTION MENSUELLE — courbe N mois
    //     months : nombre de mois à afficher (défaut 12)
    // ════════════════════════════════════════════════════════════════

    public List<MonthlyEvolutionItem> getMonthlyEvolution(String clientId, int months) {
        assertClientExists(clientId);

        LocalDateTime startDate = LocalDateTime.now()
                .minusMonths(months)
                .withDayOfMonth(1)
                .toLocalDate()
                .atStartOfDay();

        return txRepo.monthlyEvolution(clientId, startDate).stream().map(row -> {
            int year = toInt(row[0]);
            int month = toInt(row[1]);
            BigDecimal credit = toBD(row[2]);
            BigDecimal debit = toBD(row[3]);

            return MonthlyEvolutionItem.builder()
                    .monthLabel(String.format("%d-%02d", year, month))
                    .totalCredit(credit)
                    .totalDebit(debit)
                    .netBalance(credit.subtract(debit))
                    .build();
        }).collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════════
    //  6. ÉVOLUTION HEBDOMADAIRE — barres 7 derniers jours
    //     Retourne toujours 7 entrées même si aucune transaction ce jour
    // ════════════════════════════════════════════════════════════════

    public List<WeeklyEvolutionItem> getWeeklyEvolution(String clientId) {
        assertClientExists(clientId);

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = LocalDate.now().minusDays(6).atStartOfDay();

        // Index par date string pour lookup O(1)
        Map<String, Object[]> byDate = new LinkedHashMap<>();
        for (Object[] row : txRepo.dailyEvolution(clientId, start, end)) {
            byDate.put(row[0].toString(), row);
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.FRENCH);
        List<WeeklyEvolutionItem> result = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            Object[] row = byDate.get(day.toString());
            BigDecimal credit = row != null ? toBD(row[1]) : BigDecimal.ZERO;
            BigDecimal debit = row != null ? toBD(row[2]) : BigDecimal.ZERO;

            result.add(WeeklyEvolutionItem.builder()
                    .dayLabel(day.atStartOfDay().format(fmt))
                    .date(day.toString())
                    .totalCredit(credit)
                    .totalDebit(debit)
                    .build());
        }
        return result;
    }

    // ════════════════════════════════════════════════════════════════
    //  7. TOP 5 CATÉGORIES DE DÉPENSES
    // ════════════════════════════════════════════════════════════════

    public List<TopCategoryItem> getTopCategories(String clientId, String period) {
        assertClientExists(clientId);
        LocalDateTime[] range = resolvePeriod(period);

        List<Object[]> raw = txRepo.debitByCategory(clientId, range[0], range[1]);

        BigDecimal total = raw.stream()
                .map(r -> toBD(r[1]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int rank = 1;
        List<TopCategoryItem> result = new ArrayList<>();
        for (Object[] row : raw) {
            if (rank > 5) break;
            BigDecimal amt = toBD(row[1]);
            double pct = total.compareTo(BigDecimal.ZERO) > 0
                    ? amt.divide(total, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0.0;

            result.add(TopCategoryItem.builder()
                    .rank(rank++)
                    .category((String) row[0])
                    .totalAmount(amt)
                    .transactionCount(toLong(row[2]))
                    .percentageOfTotal(round2(pct))
                    .build());
        }
        return result;
    }

    // ════════════════════════════════════════════════════════════════
    //  8. STATISTIQUES PÉRIODE
    // ════════════════════════════════════════════════════════════════

    public PeriodStatsResponse getPeriodStats(String clientId, String period) {
        assertClientExists(clientId);
        LocalDateTime[] range = resolvePeriod(period);

        Object[] stats = txRepo.debitStats(clientId, range[0], range[1]);
        BigDecimal avg = (stats.length > 0 && stats[0] != null) ? toBD(stats[0]) : BigDecimal.ZERO;
        BigDecimal max = (stats.length > 1 && stats[1] != null) ? toBD(stats[1]) : BigDecimal.ZERO;
        BigDecimal min = (stats.length > 2 && stats[2] != null) ? toBD(stats[2]) : BigDecimal.ZERO;
        long activeDays = (stats.length > 3 && stats[3] != null) ? toLong(stats[3]) : 0L;

        long periodDays = ChronoUnit.DAYS.between(range[0].toLocalDate(), range[1].toLocalDate()) + 1;
        BigDecimal totalDebit = safe(txRepo.sumDebit(clientId, range[0], range[1]));
        BigDecimal avgDaily = periodDays > 0
                ? totalDebit.divide(BigDecimal.valueOf(periodDays), 3, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return PeriodStatsResponse.builder()
                .period(period != null ? period : "THIS_MONTH")
                .avgTransactionAmount(avg.setScale(3, RoundingMode.HALF_UP))
                .maxTransaction(max)
                .minTransaction(min)
                .activeDays(activeDays)
                .avgDailySpend(avgDaily)
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    //  9. FLUX CRÉDIT vs DÉBIT PAR CATÉGORIE
    // ════════════════════════════════════════════════════════════════

    public List<CategoryFlowItem> getCategoryFlow(String clientId, String period) {
        assertClientExists(clientId);
        LocalDateTime[] range = resolvePeriod(period);

        return txRepo.flowByCategory(clientId, range[0], range[1]).stream()
                .map(row -> CategoryFlowItem.builder()
                        .category((String) row[0])
                        .creditAmount(toBD(row[1]))
                        .debitAmount(toBD(row[2]))
                        .creditCount(toLong(row[3]))
                        .debitCount(toLong(row[4]))
                        .build())
                .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════════
    //  10. PROFIL CLIENT
    // ════════════════════════════════════════════════════════════════

    public ClientProfileResponse getClientProfile(String clientId) {
        Client client = clientRepo.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client introuvable : " + clientId));

        long totalTx = txRepo.countNormal(clientId);

        // Catégorie la plus dépensée sur les 3 derniers mois
        LocalDateTime start = LocalDateTime.now().minusMonths(3);
        List<Object[]> cats = txRepo.debitByCategory(clientId, start, LocalDateTime.now());
        String topCat = cats.isEmpty() ? null : (String) cats.get(0)[0];

        return ClientProfileResponse.builder()
                .id(client.getId())
                .firstName(client.getFirstName())
                .lastName(client.getLastName())
                .phoneNumber(client.getPhoneNumber())
                .memberSince(client.getCreateDateTime())
                .topSpendingCategory(topCat)
                .totalTransactions(totalTx)
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    //  11. LISTE DES CATÉGORIES DISPONIBLES (pour les filtres front)
    // ════════════════════════════════════════════════════════════════

    public List<String> getAllCategories() {
        return typeRepo.findAllDistinctCategories();
    }

    // ════════════════════════════════════════════════════════════════
    //  HELPERS PRIVÉS
    // ════════════════════════════════════════════════════════════════

    /**
     * Lance 404 si le client n'existe pas
     */
    private void assertClientExists(String clientId) {
        if (!clientRepo.existsById(clientId)) {
            throw new ResourceNotFoundException("Client introuvable : " + clientId);
        }
    }

    /**
     * Transaction → DTO liste
     */
    private TransactionResponse toTransactionResponse(Transaction tx) {
        TransactionType tt = tx.getTransactionType();
        return TransactionResponse.builder()
                .id(tx.getId())
                .amount(tx.getAmount())
                .currency(tx.getCurrency() != null ? tx.getCurrency() : "TND")
                .transactionDate(tx.getTransactionDate())
                .flowType(tt != null ? tt.getType() : null)
                .credit(tx.isCredit())
                .typeTitle(tt != null ? tt.getTitle() : null)
                .category(tt != null ? tt.getCategory() : null)
                .subCategory(tt != null ? tt.getSubCategory() : null)
                .providerName(tx.getProvider() != null ? tx.getProvider().getProviderName() : null)
                .reversalFlag(tx.getReversalFlag())
                .build();
    }

    /**
     * Résout une période nommée → [startDate, endDate]
     * <p>
     * Valeurs acceptées :
     * THIS_MONTH     → 1er du mois en cours → maintenant
     * LAST_MONTH     → 1er du mois dernier → fin du mois dernier
     * LAST_3_MONTHS  → il y a 3 mois → maintenant
     * LAST_6_MONTHS  → il y a 6 mois → maintenant
     * THIS_YEAR      → 1er janvier → maintenant
     * ALL            → 2020-01-01  → maintenant
     */
    private LocalDateTime[] resolvePeriod(String period) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start;
        LocalDateTime end = now;

        switch (period == null ? "THIS_MONTH" : period.toUpperCase()) {
            case "LAST_MONTH" -> {
                start = now.minusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay();
                end = now.withDayOfMonth(1).toLocalDate().atStartOfDay().minusNanos(1);
            }
            case "LAST_3_MONTHS" -> start = now.minusMonths(3).withDayOfMonth(1).toLocalDate().atStartOfDay();
            case "LAST_6_MONTHS" -> start = now.minusMonths(6).withDayOfMonth(1).toLocalDate().atStartOfDay();
            case "THIS_YEAR" -> start = now.withDayOfYear(1).toLocalDate().atStartOfDay();
            case "ALL" -> start = LocalDateTime.of(2020, 1, 1, 0, 0);
            default -> // THIS_MONTH
                    start = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        }
        return new LocalDateTime[]{start, end};
    }

    // ── Conversions de type sécurisées ────────────────────────────────

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private BigDecimal toBD(Object o) {
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private long toLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private int toInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
