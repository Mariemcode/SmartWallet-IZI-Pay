package com.pfe.clientdashboard.transaction.service;

import com.pfe.clientdashboard.transaction.dto.CategoryBreakdownDTO;
import com.pfe.clientdashboard.transaction.dto.GlobalSummaryDTO;
import com.pfe.clientdashboard.transaction.dto.SubCategoryBreakdownDTO;

import java.time.LocalDateTime;
import java.util.List;

public interface AnalysisService {

    GlobalSummaryDTO getGlobalSummary(LocalDateTime from, LocalDateTime to);
    List<CategoryBreakdownDTO> getExpenseRateByCategory(LocalDateTime from,
                                                        LocalDateTime to);
    List<CategoryBreakdownDTO> getRevenueRateByCategory(LocalDateTime from, LocalDateTime to);

    // ── Sous-catégories dépenses ──────────────────────────────────────────
    List<SubCategoryBreakdownDTO> getExpenseSubCategories(
            String category,
            LocalDateTime from,
            LocalDateTime to);

    List<String> getDistinctExpenseCategories(
            LocalDateTime from,
            LocalDateTime to);

    List<SubCategoryBreakdownDTO> getRevenueSubCategories(
            String category, LocalDateTime from, LocalDateTime to) ;
}