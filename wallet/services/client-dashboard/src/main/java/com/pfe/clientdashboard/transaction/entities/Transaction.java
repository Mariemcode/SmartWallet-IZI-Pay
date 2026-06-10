package com.pfe.clientdashboard.transaction.entities;

import com.pfe.clientdashboard.client.entities.Client;
import com.pfe.clientdashboard.transactionType.entities.TransactionType;
import com.pfe.clientdashboard.provider.entities.Provider;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "transaction")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "amount", nullable = false, precision = 15, scale = 3)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    /** "N" = normale | "R" = annulation */
    @Column(name = "reversal_flag", length = 1)
    private String reversalFlag;

    /** ID du wallet destinataire (pour transferts P2P) */
    @Column(name = "receiver_id")
    private String receiverId;

    // ── Relations ────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transaction_type_id")
    private TransactionType transactionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    // ── Helpers ──────────────────────────────────────────────────────

    /** true si la transaction est un crédit (argent entrant, type="C") */
    @Transient
    public boolean isCredit() {
        return transactionType != null && "C".equals(transactionType.getType());
    }

    /** true si la transaction est normale (pas une annulation) */
    @Transient
    public boolean isNormal() {
        return "N".equals(reversalFlag);
    }
}
