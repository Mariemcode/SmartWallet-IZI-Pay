package com.pfe.clientdashboard.transaction.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.pfe.clientdashboard.transaction.dto.TransactionDTO;
import com.pfe.clientdashboard.transaction.dto.TransactionFilterDTO;
import com.pfe.clientdashboard.transaction.service.TransactionService;

import java.util.List;

@RestController
@RequestMapping("/api/transaction")
public class TransactionAdminController {

    private final TransactionService transactionService;

    public TransactionAdminController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // GET toutes les transactions d'un client
    @GetMapping("/clients/{clientId}/transactions")
    public ResponseEntity<List<TransactionDTO>> getAll(@PathVariable String clientId) {
        return ResponseEntity.ok(transactionService.getTransactionsByClientId(clientId));
    }

    // GET avec filtres (query params)
    @GetMapping("/clients/{clientId}/transactions/filter")
    public ResponseEntity<List<TransactionDTO>> getFiltered(
            @PathVariable String clientId,
            @ModelAttribute TransactionFilterDTO filter) {
        return ResponseEntity.ok(
                transactionService.getTransactionsByClientIdFiltered(clientId, filter));
    }

    // GET toutes les catégories disponibles
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(transactionService.getDistinctCategories());
    }
}