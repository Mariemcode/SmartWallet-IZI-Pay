package com.pfe.clientdashboard.transaction.service;

import com.pfe.clientdashboard.transaction.entities.Transaction;
import com.pfe.clientdashboard.transaction.repository.TransactionGlobalRepository;
import org.springframework.stereotype.Service;
import com.pfe.clientdashboard.transaction.dto.TransactionDTO;
import com.pfe.clientdashboard.transaction.dto.TransactionFilterDTO;
import com.pfe.clientdashboard.transaction.dto.TransactionSpecification;
import com.pfe.clientdashboard.transactionType.repository.TransactionTypeRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionGlobalRepository transactionRepository;
    private final TransactionTypeRepository typeTransactionRepository;

    public TransactionServiceImpl(TransactionGlobalRepository transactionRepository,
                                  TransactionTypeRepository typeTransactionRepository) {
        this.transactionRepository = transactionRepository;
        this.typeTransactionRepository = typeTransactionRepository;
    }

    //Récupère toutes les transactions d'un client
    @Override
    public List<TransactionDTO> getTransactionsByClientId(String clientId) {
        return transactionRepository.findByClientId(clientId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    //Récupère les transactions d'un client avec des filtres dynamiques. Utilise TransactionSpecification.filter(clientId, f)
    @Override
    public List<TransactionDTO> getTransactionsByClientIdFiltered(String clientId,
                                                                  TransactionFilterDTO f) {
        return transactionRepository
                .findAll(TransactionSpecification.filter(clientId, f))
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    //Récupère les transactions d'un client avec des filtres dynamiques.
    // Utilise TransactionSpecification.filter(clientId, f)
    @Override
    public List<String> getDistinctCategories() {
        return typeTransactionRepository.findDistinctCategories();
    }

    //Convertit une entité JPA Transaction en TransactionDTO
    private TransactionDTO toDTO(Transaction t) {
        TransactionDTO dto = new TransactionDTO();
        dto.setId(t.getId());
        dto.setClientId(t.getClient().getId());
        dto.setAmount(t.getAmount());
        dto.setCurrency(t.getCurrency());
        dto.setTransactionDate(t.getTransactionDate());
        dto.setReversalFlag(t.getReversalFlag());

        if (t.getProvider() != null) {
            dto.setProviderCode(t.getProvider().getProviderCode());
            dto.setProviderName(t.getProvider().getProviderName());
        }
        if (t.getTransactionType() != null) {
            dto.setTypeCode(t.getTransactionType().getCode());
            dto.setTypeTitle(t.getTransactionType().getTitle());
            dto.setTypeOriginalTitle(t.getTransactionType().getOriginalTitle());
            dto.setTypeCategory(t.getTransactionType().getCategory());
            dto.setTypeSubCategory(t.getTransactionType().getSubCategory());
            dto.setTypeType(t.getTransactionType().getType());
        }
        return dto;
    }
}