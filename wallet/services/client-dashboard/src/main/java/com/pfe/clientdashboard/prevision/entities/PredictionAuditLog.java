package com.pfe.clientdashboard.prevision.entities;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "prediction_audit_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PredictionAuditLog {
    @Id @Column(length = 64)
    private String id;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(nullable = false, length = 20)
    private String fournisseur;

    @Column(name = "date_prevue", nullable = false)
    private LocalDate datePrevue;

    @Column(name = "montant_prevu", precision = 10, scale = 2)
    private BigDecimal montantPrevu;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDateTime snapshotDate;

    @Column(name = "modele_version", length = 50)
    private String modeleVersion;

    // Rempli par le job de nuit
    @Column(length = 20)
    private String resultatFinal; // 'PAYE', 'NON_PAYE'

    @Column(name = "montant_reel", precision = 10, scale = 2)
    private BigDecimal montantReel;

    @Column(name = "date_paiement_reel")
    private LocalDateTime datePaiementReel;

    @Column(name = "mae_reel", precision = 10, scale = 2)
    private BigDecimal maeReel;
}