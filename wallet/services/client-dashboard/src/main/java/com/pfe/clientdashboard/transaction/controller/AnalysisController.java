package com.pfe.clientdashboard.transaction.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.pfe.clientdashboard.transaction.dto.CategoryBreakdownDTO;
import com.pfe.clientdashboard.transaction.dto.GlobalSummaryDTO;
import com.pfe.clientdashboard.transaction.dto.SubCategoryBreakdownDTO;
import com.pfe.clientdashboard.transaction.service.AnalysisService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * GET /api/analysis/global-summary
     * Paramètres optionnels :
     *   ?from=2023-11-01T00:00:00
     *   ?to=2023-11-30T23:59:59
     */
    @GetMapping("/global-summary")
    public ResponseEntity<GlobalSummaryDTO> getGlobalSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to) {

        return ResponseEntity.ok(analysisService.getGlobalSummary(from, to));
    }

    @GetMapping("/expense-by-category")
    public ResponseEntity<List<CategoryBreakdownDTO>> getExpenseByCategory(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to) {

        return ResponseEntity.ok(
                analysisService.getExpenseRateByCategory(from, to));
    }

    @GetMapping("/revenue-by-category")
    public ResponseEntity<List<CategoryBreakdownDTO>> getRevenueByCategory(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to) {

        return ResponseEntity.ok(
                analysisService.getRevenueRateByCategory(from, to));
    }

    /**
     * GET /api/analysis/expense-sub-categories?category=Transferts Envoyés&from=...&to=...
     * Retourne les sous-catégories d'une catégorie de dépenses
     */
    @GetMapping("/expense-sub-categories")
    public ResponseEntity<List<SubCategoryBreakdownDTO>> getExpenseSubCategories(
            @RequestParam String category,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to) {

        return ResponseEntity.ok(
                analysisService.getExpenseSubCategories(category, from, to));
    }

    /**
     * GET /api/analysis/expense-categories?from=...&to=...
     * Retourne la liste des catégories distinctes de dépenses
     */
    @GetMapping("/expense-categories")
    public ResponseEntity<List<String>> getDistinctExpenseCategories(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to) {

        return ResponseEntity.ok(
                analysisService.getDistinctExpenseCategories(from, to));
    }

    @GetMapping("/revenue-sub-categories")
    public ResponseEntity<List<SubCategoryBreakdownDTO>> getRevenueSubCategories(
            @RequestParam String category,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return ResponseEntity.ok(
                analysisService.getRevenueSubCategories(category, from, to));
    }
}