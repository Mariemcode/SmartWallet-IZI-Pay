package com.pfe.clientdashboard.provider.services;

import com.pfe.clientdashboard.client.entities.Client;
import com.pfe.clientdashboard.provider.entities.Provider;
import com.pfe.clientdashboard.transactionType.entities.TransactionType;
import com.pfe.clientdashboard.provider.repository.ProviderAdminRepository;
import com.pfe.clientdashboard.provider.dto.*;
import com.pfe.clientdashboard.transaction.repository.TransactionGlobalRepository;
import com.pfe.clientdashboard.transaction.dto.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProviderServiceImpl implements ProviderService {

    private final ProviderAdminRepository providerRepository;
    private final TransactionGlobalRepository transactionRepository;

    @Override
    public List<Provider> getAllProviders() {
        return providerRepository.findAll();
    }

    @Override
    public Provider getProviderById(String id) {                          // String
        return providerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Provider not found: " + id));
    }

    @Override
    public ProviderStatsDTO getProviderStats(String providerId) {         // String

        Provider provider = getProviderById(providerId);

        long       totalTransactions = transactionRepository.countByProvider(providerId);
        BigDecimal totalAmount       = transactionRepository.sumAmountByProvider(providerId);
        BigDecimal avgAmount         = transactionRepository.avgAmountByProvider(providerId);
        BigDecimal minAmount         = transactionRepository.minAmountByProvider(providerId);
        BigDecimal maxAmount         = transactionRepository.maxAmountByProvider(providerId);
        long       distinctClients   = transactionRepository.countDistinctClientsByProvider(providerId);
        long       reversalCount     = transactionRepository.countReversalsByProvider(providerId);

        double reversalRate = totalTransactions > 0
                ? BigDecimal.valueOf(reversalCount * 100.0 / totalTransactions)
                .setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        List<DailyStatDTO> dailyStats = transactionRepository
                .getDailyStatsByProvider(providerId).stream()
                .map(r -> new DailyStatDTO(
                        ((java.sql.Date) r[0]).toLocalDate(),
                        ((Number) r[1]).longValue(),
                        r[2] != null ? (BigDecimal) r[2] : BigDecimal.ZERO))
                .toList();

        List<TransactionTypeStatDTO> typeStats = transactionRepository
                .getTransactionTypeStatsByProvider(providerId).stream()
                .map(r -> {
                    TransactionType tt = (TransactionType) r[0];
                    return new TransactionTypeStatDTO(
                            tt.getTitle(), tt.getCategory(), tt.getSubCategory(), tt.getType(),
                            ((Number) r[1]).longValue(),
                            r[2] != null ? (BigDecimal) r[2] : BigDecimal.ZERO);
                }).toList();

        List<TopClientDTO> topClients = transactionRepository
                .getTopClientsByProvider(providerId).stream()
                .map(r -> {
                    Client c = (Client) r[0];
                    return new TopClientDTO(
                            c.getId(), c.getFirstName(), c.getLastName(),
                            ((Number) r[1]).longValue(),
                            r[2] != null ? (BigDecimal) r[2] : BigDecimal.ZERO);
                }).toList();

        return new ProviderStatsDTO(
                provider.getId(), provider.getProviderCode(), provider.getProviderName(),
                totalTransactions, totalAmount, avgAmount, minAmount, maxAmount,
                distinctClients, reversalCount, reversalRate,
                dailyStats, typeStats, topClients);
    }

    @Override
    public List<ProviderSummaryDTO> getAllProviderSummaries() {
        return providerRepository.findAll().stream()
                .map(p -> {
                    String id = p.getId();                               // String

                    long       total     = transactionRepository.countByProvider(id);
                    BigDecimal totalAmt  = transactionRepository.sumAmountByProvider(id);
                    long       debitCnt  = transactionRepository.countDebitByProvider(id);
                    BigDecimal debitAmt  = transactionRepository.sumDebitByProvider(id);
                    long       creditCnt = transactionRepository.countCreditByProvider(id);
                    BigDecimal creditAmt = transactionRepository.sumCreditByProvider(id);

                    double totalAmtD     = totalAmt  != null ? totalAmt.doubleValue()  : 0;
                    double debitAmtD     = debitAmt  != null ? debitAmt.doubleValue()  : 0;
                    double creditAmtD    = creditAmt != null ? creditAmt.doubleValue() : 0;

                    double debitPctCount  = total > 0 ? round(debitCnt  * 100.0 / total) : 0;
                    double creditPctCount = total > 0 ? round(creditCnt * 100.0 / total) : 0;
                    double debitPctAmt    = totalAmtD > 0 ? round(debitAmtD  * 100.0 / totalAmtD) : 0;
                    double creditPctAmt   = totalAmtD > 0 ? round(creditAmtD * 100.0 / totalAmtD) : 0;

                    return new ProviderSummaryDTO(
                            id, p.getProviderCode(), p.getProviderName(),
                            total, totalAmt != null ? totalAmt : BigDecimal.ZERO,
                            debitCnt,  debitAmt  != null ? debitAmt  : BigDecimal.ZERO,
                            creditCnt, creditAmt != null ? creditAmt : BigDecimal.ZERO,
                            debitPctCount, creditPctCount,
                            debitPctAmt,   creditPctAmt);
                }).toList();
    }

    @Override
    public ProviderListStatsDTO getProviderListStats(LocalDateTime from, LocalDateTime to) {

        boolean hasFilter = from != null && to != null;

        long grandCount = hasFilter
                ? transactionRepository.grandTotalCountWithFilter(from, to)
                : transactionRepository.grandTotalCountNoFilter();

        BigDecimal grandAmt = hasFilter
                ? transactionRepository.grandTotalAmountWithFilter(from, to)
                : transactionRepository.grandTotalAmountNoFilter();
        if (grandAmt == null) grandAmt = BigDecimal.ZERO;

        Map<String, Provider> providerMap = providerRepository.findAll().stream()   // String
                .collect(Collectors.toMap(Provider::getId, p -> p));

        List<Object[]> rows = hasFilter
                ? transactionRepository.getStatsGroupedByProviderWithFilter(from, to)
                : transactionRepository.getStatsGroupedByProviderNoFilter();

        final BigDecimal finalGrandAmt   = grandAmt;
        final long       finalGrandCount = grandCount;

        List<ProviderShareDTO> shares = rows.stream()
                .map(row -> {
                    String     pid   = row[0].toString();                 // String
                    long       count = ((Number)    row[1]).longValue();
                    BigDecimal amt   = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;

                    Provider p    = providerMap.get(pid);
                    String   code = p != null ? p.getProviderCode() : "?";
                    String   name = p != null ? p.getProviderName() : "Inconnu";

                    double pctCount = finalGrandCount > 0
                            ? round(count * 100.0 / finalGrandCount) : 0;
                    double pctAmt   = finalGrandAmt.doubleValue() > 0
                            ? round(amt.doubleValue() * 100.0 / finalGrandAmt.doubleValue()) : 0;

                    return new ProviderShareDTO(pid, code, name, count, amt, pctCount, pctAmt);
                })
                .sorted(Comparator.comparingDouble(ProviderShareDTO::getPercentAmount).reversed())
                .toList();

        return new ProviderListStatsDTO(providerMap.size(), grandCount, grandAmt, shares);
    }

    @Override
    public ProviderDetailDTO getProviderDetail(String providerId, LocalDateTime from, LocalDateTime to) {  // String

        boolean hasFilter = from != null && to != null;
        Provider provider = getProviderById(providerId);

        long total = hasFilter
                ? transactionRepository.countByProviderWithFilter(providerId, from, to)
                : transactionRepository.countByProvider(providerId);

        BigDecimal totalAmt = nvl(hasFilter
                ? transactionRepository.sumAmountByProviderWithFilter(providerId, from, to)
                : transactionRepository.sumAmountByProvider(providerId));

        BigDecimal avgAmt = nvl(hasFilter
                ? transactionRepository.avgAmountByProviderWithFilter(providerId, from, to)
                : transactionRepository.avgAmountByProvider(providerId));

        BigDecimal minAmt = nvl(hasFilter
                ? transactionRepository.minAmountByProviderWithFilter(providerId, from, to)
                : transactionRepository.minAmountByProvider(providerId));

        BigDecimal maxAmt = nvl(hasFilter
                ? transactionRepository.maxAmountByProviderWithFilter(providerId, from, to)
                : transactionRepository.maxAmountByProvider(providerId));

        long distinct = hasFilter
                ? transactionRepository.countDistinctClientsByProviderWithFilter(providerId, from, to)
                : transactionRepository.countDistinctClientsByProvider(providerId);

        long grandCount     = transactionRepository.grandTotalCountNoFilter();
        BigDecimal grandAmt = nvl(transactionRepository.grandTotalAmountNoFilter());

        List<Object[]> allStats     = transactionRepository.getStatsGroupedByProviderNoFilter();
        List<Long>     countsSorted = allStats.stream()
                .map(r -> ((Number) r[1]).longValue())
                .sorted(Comparator.reverseOrder()).toList();
        List<Double>   amtsSorted   = allStats.stream()
                .map(r -> ((BigDecimal) r[2]).doubleValue())
                .sorted(Comparator.reverseOrder()).toList();

        long rankByCount  = Math.max(1, countsSorted.indexOf(total) + 1L);
        long rankByAmount = Math.max(1, amtsSorted.indexOf(totalAmt.doubleValue()) + 1L);
        double mktShareCount  = grandCount > 0
                ? round(total * 100.0 / grandCount) : 0;
        double mktShareAmount = grandAmt.doubleValue() > 0
                ? round(totalAmt.doubleValue() * 100.0 / grandAmt.doubleValue()) : 0;

        List<Object[]> dateRange = transactionRepository.getDateRangeByProvider(providerId);
        String firstTx = "", lastTx = "";
        if (!dateRange.isEmpty() && dateRange.get(0)[0] != null) {
            firstTx = dateRange.get(0)[0].toString();
            lastTx  = dateRange.get(0)[1].toString();
        }

        List<TransactionTypeStatDTO> typeStats = (hasFilter
                ? transactionRepository.getTransactionTypeStatsByProviderWithFilter(providerId, from, to)
                : transactionRepository.getTransactionTypeStatsByProvider(providerId))
                .stream()
                .map(r -> {
                    TransactionType tt = (TransactionType) r[0];
                    return new TransactionTypeStatDTO(
                            tt.getTitle(), tt.getCategory(), tt.getSubCategory(), tt.getType(),
                            ((Number) r[1]).longValue(),
                            r[2] != null ? (BigDecimal) r[2] : BigDecimal.ZERO);
                }).toList();

        List<TopClientDTO> topByAmt = (hasFilter
                ? transactionRepository.getTopClientsByAmountWithFilter(providerId, from, to)
                : transactionRepository.getTopClientsByAmountForProvider(providerId))
                .stream()
                .map(r -> {
                    Client c = (Client) r[0];
                    return new TopClientDTO(
                            c.getId(), c.getFirstName(), c.getLastName(),
                            ((Number) r[1]).longValue(),
                            r[2] != null ? (BigDecimal) r[2] : BigDecimal.ZERO);
                }).toList();

        List<TopClientDTO> topByCount = (hasFilter
                ? transactionRepository.getTopClientsByCountWithFilter(providerId, from, to)
                : transactionRepository.getTopClientsByCountForProvider(providerId))
                .stream()
                .map(r -> {
                    Client c = (Client) r[0];
                    return new TopClientDTO(
                            c.getId(), c.getFirstName(), c.getLastName(),
                            ((Number) r[1]).longValue(),
                            r[2] != null ? (BigDecimal) r[2] : BigDecimal.ZERO);
                }).toList();

        long recurringClients  = hasFilter
                ? transactionRepository.countRecurringClientsWithFilter(providerId, from, to)
                : transactionRepository.countRecurringClients(providerId);
        long occasionalClients = Math.max(0, distinct - recurringClients);

        BigDecimal avgPerClient = distinct > 0
                ? totalAmt.divide(BigDecimal.valueOf(distinct), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long smallCount = hasFilter
                ? transactionRepository.countSmallTransactionsWithFilter(providerId, from, to)
                : transactionRepository.countSmallTransactions(providerId);
        long mediumCount = hasFilter
                ? transactionRepository.countMediumTransactionsWithFilter(providerId, from, to)
                : transactionRepository.countMediumTransactions(providerId);
        long largeCount = hasFilter
                ? transactionRepository.countLargeTransactionsWithFilter(providerId, from, to)
                : transactionRepository.countLargeTransactions(providerId);

        double smallPct  = total > 0 ? round(smallCount  * 100.0 / total) : 0;
        double mediumPct = total > 0 ? round(mediumCount * 100.0 / total) : 0;
        double largePct  = total > 0 ? round(largeCount  * 100.0 / total) : 0;

        return new ProviderDetailDTO(
                provider.getId(), provider.getProviderCode(), provider.getProviderName(),
                total, totalAmt, avgAmt, minAmt, maxAmt, distinct,
                rankByCount, rankByAmount, mktShareCount, mktShareAmount,
                firstTx, lastTx,
                typeStats,
                topByAmt, topByCount, recurringClients, occasionalClients, avgPerClient,
                smallCount, mediumCount, largeCount, smallPct, mediumPct, largePct);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}