package com.pfe.clientdashboard.transaction.service;

import com.pfe.clientdashboard.transaction.dto.TransactionDTO;
import com.pfe.clientdashboard.transaction.dto.TransactionFilterDTO;

import java.util.List;
import java.util.UUID;

public interface TransactionService {
    List<TransactionDTO> getTransactionsByClientId(String clientId);
    List<TransactionDTO> getTransactionsByClientIdFiltered(String clientId, TransactionFilterDTO filter);
    List<String> getDistinctCategories();

}