package com.pfe.clientdashboard.transaction.dto;

import com.pfe.clientdashboard.transaction.entities.Transaction;
import com.pfe.clientdashboard.transactionType.entities.TransactionType;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TransactionSpecification {

    public static Specification<Transaction> filter(String clientId, TransactionFilterDTO f) {
        return (root, query, cb) -> {

            // Fetch joins pour éviter le N+1
            if (query.getResultType().equals(Transaction.class)) {
                root.fetch("provider", JoinType.LEFT);
                root.fetch("typeTransaction", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            // Client obligatoire
            predicates.add(cb.equal(root.get("client").get("id"), clientId));

            // Filtre catégorie
            if (f.getCategory() != null && !f.getCategory().isBlank()) {
                Join<Transaction, TransactionType> tt = root.join("typeTransaction", JoinType.LEFT);
                predicates.add(cb.equal(tt.get("category"), f.getCategory()));
            }

            // Filtre type C/D
            if (f.getTypeType() != null && !f.getTypeType().isBlank()) {
                Join<Transaction, TransactionType> tt = root.join("typeTransaction", JoinType.LEFT);
                predicates.add(cb.equal(tt.get("type"), f.getTypeType()));
            }

            // Filtre date exacte
            if (f.getDate() != null) {
                Expression<LocalDate> dateExpr = root.get("transactionDate").as(LocalDate.class);
                predicates.add(cb.equal(dateExpr, f.getDate()));
            }

            query.orderBy(cb.desc(root.get("transactionDate")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
