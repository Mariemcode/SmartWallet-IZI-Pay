package com.pfe.clientdashboard.transaction.service;

import com.pfe.clientdashboard.transaction.repository.TransactionGlobalRepository;
import org.springframework.stereotype.Service;
import com.pfe.clientdashboard.transaction.dto.CategoryBreakdownDTO;
import com.pfe.clientdashboard.transaction.dto.GlobalSummaryDTO;
import com.pfe.clientdashboard.transaction.dto.SubCategoryBreakdownDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnalysisServiceImpl implements AnalysisService {

    private final TransactionGlobalRepository transactionRepository;

    // Formatter pour envoyer les dates en String vers la native query
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AnalysisServiceImpl(TransactionGlobalRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    //Calcule le résumé financier global sur une période donnée.
    // Il appelle globalTotalsAllUsers qui retourne deux lignes (une pour D, une pour C)
    //calcule les pourcentages dépenses/revenus par rapport au grand total et construit un GlobalSummaryDTO
    @Override
    public GlobalSummaryDTO getGlobalSummary(LocalDateTime from, LocalDateTime to) {

        String fromStr = (from != null ? from : LocalDateTime.of(2000, 1, 1, 0, 0))
                .format(FMT);
        String toStr   = (to   != null ? to   : LocalDateTime.of(2100, 1, 1, 0, 0))
                .format(FMT);

        List<Object[]> rows = transactionRepository.globalTotalsAllUsers(fromStr, toStr);

        BigDecimal expenseAmount = BigDecimal.ZERO;
        long       expenseCount  = 0L;
        BigDecimal revenueAmount = BigDecimal.ZERO;
        long       revenueCount  = 0L;

        for (Object[] row : rows) {
            String     type   = row[0].toString();           // ← .toString() au lieu de (String)
            BigDecimal amount = (BigDecimal)  row[1];
            Long       count  = ((Number)     row[2]).longValue();

            if ("D".equals(type)) {
                expenseAmount = amount;
                expenseCount  = count;
            } else if ("C".equals(type)) {
                revenueAmount = amount;
                revenueCount  = count;
            }
        }

        BigDecimal grandTotal      = expenseAmount.add(revenueAmount);
        long       grandTotalCount = expenseCount + revenueCount;

        return GlobalSummaryDTO.builder()
                .totalExpenseAmount   (expenseAmount)
                .totalExpenseCount    (expenseCount)
                .expensePercentAmount (calculatePercent(expenseAmount, grandTotal))
                .expensePercentCount  (grandTotalCount > 0
                        ? round((double) expenseCount / grandTotalCount * 100) : 0.0)
                .totalRevenueAmount   (revenueAmount)
                .totalRevenueCount    (revenueCount)
                .revenuePercentAmount (calculatePercent(revenueAmount, grandTotal))
                .revenuePercentCount  (grandTotalCount > 0
                        ? round((double) revenueCount / grandTotalCount * 100) : 0.0)
                .grandTotalAmount     (grandTotal)
                .grandTotalCount      (grandTotalCount)
                .build();
    }

    //Retourne la répartition des dépenses par catégorie avec pourcentages
    // Il appelle expenseRateByCategoryAllUsers
    //calcule d'abord le total global (somme de toutes les catégories)
    //puis pour chaque ligne construit un CategoryBreakdownDTO contenant la catégorie, le montant total, le count, la moyenne, et les deux pourcentages (sur le montant et sur le count).
    @Override
    public List<CategoryBreakdownDTO> getExpenseRateByCategory(LocalDateTime from,
                                                               LocalDateTime to) {

        // 1. Dates par défaut si non fournies
        String fromStr = (from != null ? from : LocalDateTime.of(2000, 1, 1, 0, 0)).format(FMT);
        String toStr   = (to   != null ? to   : LocalDateTime.of(2100, 1, 1, 0, 0)).format(FMT);

        List<Object[]> rows = transactionRepository
                .expenseRateByCategoryAllUsers(fromStr, toStr);

        // 2. Calcul du total global des dépenses (pour les %)
        BigDecimal grandTotalAmount = rows.stream()
                .map(r -> (BigDecimal) r[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long grandTotalCount = rows.stream()
                .mapToLong(r -> ((Number) r[2]).longValue())
                .sum();

        // 3. Construction de la liste de DTOs avec les pourcentages
        return rows.stream().map(row -> {

            String     category    = (String)    row[0];
            BigDecimal totalAmount = (BigDecimal) row[1];
            Long       totalCount  = ((Number)   row[2]).longValue();
            BigDecimal avgAmount   = (BigDecimal) row[3];

            Double percentAmount = calculatePercent(totalAmount, grandTotalAmount);
            Double percentCount  = grandTotalCount > 0
                    ? round((double) totalCount / grandTotalCount * 100) : 0.0;

            return CategoryBreakdownDTO.builder()
                    .category     (category)
                    .totalAmount  (totalAmount)
                    .totalCount   (totalCount)
                    .percentAmount(percentAmount)
                    .percentCount (percentCount)
                    .averageAmount(avgAmount != null
                            ? avgAmount.setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO)
                    .build();

        }).collect(Collectors.toList());
    }

    //Retourne la répartition des revenus par catégorie
    //Appelle revenueRateByCategoryAllUsers
    //retourne CategoryBreakdownDTO
    @Override
    public List<CategoryBreakdownDTO> getRevenueRateByCategory(LocalDateTime from,
                                                               LocalDateTime to) {

        // 1. Dates par défaut si non fournies
        String fromStr = (from != null ? from : LocalDateTime.of(2000, 1, 1, 0, 0)).format(FMT);
        String toStr   = (to   != null ? to   : LocalDateTime.of(2100, 1, 1, 0, 0)).format(FMT);

        List<Object[]> rows = transactionRepository
                .revenueRateByCategoryAllUsers(fromStr, toStr);

        // 2. Calcul du total global des revenus
        BigDecimal grandTotalAmount = rows.stream()
                .map(r -> (BigDecimal) r[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long grandTotalCount = rows.stream()
                .mapToLong(r -> ((Number) r[2]).longValue())
                .sum();

        // 3. Construction des DTOs avec pourcentages
        return rows.stream().map(row -> {

            String     category    = (String)     row[0];
            BigDecimal totalAmount = (BigDecimal)  row[1];
            Long       totalCount  = ((Number)    row[2]).longValue();
            BigDecimal avgAmount   = (BigDecimal)  row[3];

            Double percentAmount = calculatePercent(totalAmount, grandTotalAmount);
            Double percentCount  = grandTotalCount > 0
                    ? round((double) totalCount / grandTotalCount * 100) : 0.0;

            return CategoryBreakdownDTO.builder()
                    .category     (category)
                    .totalAmount  (totalAmount)
                    .totalCount   (totalCount)
                    .percentAmount(percentAmount)
                    .percentCount (percentCount)
                    .averageAmount(avgAmount != null
                            ? avgAmount.setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO)
                    .build();

        }).collect(Collectors.toList());
    }


    //===============================
    //SOUS CATEGORIES
    //================================

    //Pour une catégorie de dépense donnée, retourne le détail par sous-catégorie.
    //Appelle expenseSubCategoriesByCategory avec la catégorie en paramètre
    //calcule le total de la catégorie (pas le total global)
    //construit un SubCategoryBreakdownDTO
    //Si une sous-catégorie est null, elle est remplacée par "Non défini".
    @Override
    public List<SubCategoryBreakdownDTO> getExpenseSubCategories(
            String        category,
            LocalDateTime from,
            LocalDateTime to) {

        String fromStr = (from != null ? from
                : LocalDateTime.of(2000, 1, 1, 0, 0)).format(FMT);
        String toStr   = (to   != null ? to
                : LocalDateTime.of(2100, 1, 1, 0, 0)).format(FMT);

        List<Object[]> rows = transactionRepository
                .expenseSubCategoriesByCategory(category, fromStr, toStr);

        // Total de la catégorie pour les pourcentages
        BigDecimal grandTotal = rows.stream()
                .map(r -> toBD(r[1]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long grandCount = rows.stream()
                .mapToLong(r -> toLong(r[2]))
                .sum();

        return rows.stream().map(row -> {
            String     subCat      = row[0] != null ? row[0].toString() : "Non défini";
            BigDecimal totalAmount = toBD(row[1]);
            Long       totalCount  = toLong(row[2]);
            BigDecimal avgAmount   = toBD(row[3]);

            Double percentAmount = grandTotal.compareTo(BigDecimal.ZERO) != 0
                    ? round(totalAmount
                    .divide(grandTotal, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue())
                    : 0.0;

            Double percentCount = grandCount > 0
                    ? round((double) totalCount / grandCount * 100)
                    : 0.0;

            return SubCategoryBreakdownDTO.builder()
                    .subCategory  (subCat)
                    .totalAmount  (totalAmount)
                    .totalCount   (totalCount)
                    .averageAmount(avgAmount)
                    .percentAmount(percentAmount)
                    .percentCount (percentCount)
                    .build();

        }).collect(Collectors.toList());
    }

    //Retourne la liste des catégories de dépenses distinctes qui existent dans la période donnée
    //utilise distinctExpenseCategories
    @Override
    public List<String> getDistinctExpenseCategories(
            LocalDateTime from,
            LocalDateTime to) {

        String fromStr = (from != null ? from
                : LocalDateTime.of(2000, 1, 1, 0, 0)).format(FMT);
        String toStr   = (to   != null ? to
                : LocalDateTime.of(2100, 1, 1, 0, 0)).format(FMT);

        return transactionRepository.distinctExpenseCategories(fromStr, toStr);
    }

    //Pour une catégorie de revenues donnée, retourne le détail par sous-catégorie.
    //Appelle expenseSubCategoriesByCategory avec la catégorie en paramètre
    //calcule le total de la catégorie (pas le total global)
    //construit un SubCategoryBreakdownDTO
    //Si une sous-catégorie est null, elle est remplacée par "Non défini".
    @Override
    public List<SubCategoryBreakdownDTO> getRevenueSubCategories(
            String category, LocalDateTime from, LocalDateTime to) {

        String fromStr = (from != null ? from
                : LocalDateTime.of(2000,1,1,0,0)).format(FMT);
        String toStr   = (to   != null ? to
                : LocalDateTime.of(2100,1,1,0,0)).format(FMT);

        List<Object[]> rows = transactionRepository
                .revenueSubCategoriesByCategory(category, fromStr, toStr);

        BigDecimal grandTotal = rows.stream()
                .map(r -> toBD(r[1])).reduce(BigDecimal.ZERO, BigDecimal::add);
        long grandCount = rows.stream().mapToLong(r -> toLong(r[2])).sum();

        return rows.stream().map(row -> {
            String     subCat      = row[0] != null ? row[0].toString() : "Non défini";
            BigDecimal totalAmount = toBD(row[1]);
            Long       totalCount  = toLong(row[2]);
            BigDecimal avgAmount   = toBD(row[3]);

            Double percentAmount = grandTotal.compareTo(BigDecimal.ZERO) != 0
                    ? round(totalAmount
                    .divide(grandTotal, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue())
                    : 0.0;
            Double percentCount = grandCount > 0
                    ? round((double) totalCount / grandCount * 100) : 0.0;

            return SubCategoryBreakdownDTO.builder()
                    .subCategory(subCat).totalAmount(totalAmount)
                    .totalCount(totalCount).averageAmount(avgAmount)
                    .percentAmount(percentAmount).percentCount(percentCount)
                    .build();
        }).collect(Collectors.toList());
    }

    // ── Helpers (si pas encore présents) ─────────────────────────────────

    //Convertit n'importe quel objet numérique (BigDecimal, Double, Number) en BigDecimal arrondi à 2 décimales
    private BigDecimal toBD(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal)
            return ((BigDecimal) val).setScale(2, RoundingMode.HALF_UP);
        if (val instanceof Double)
            return BigDecimal.valueOf((Double) val).setScale(2, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(((Number) val).doubleValue())
                .setScale(2, RoundingMode.HALF_UP);
    }

    //Convertit un Object en long via l'interface Number
    //Retourne 0 si null.
    //Utilisé pour extraire les counts des résultats de requêtes natives.
    private long toLong(Object val) {
        if (val == null) return 0L;
        return ((Number) val).longValue();
    }

    //Calcule le pourcentage (part / total) * 100 avec une précision de 6 décimales
    //puis arrondit le résultat final à 2 décimales.
    // Retourne 0 si le total est null ou zéro.
    private double calculatePercent(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return round(part.divide(total, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue());
    }

    // Arrondit un double à exactement 2 décimales en multipliant par 100
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}