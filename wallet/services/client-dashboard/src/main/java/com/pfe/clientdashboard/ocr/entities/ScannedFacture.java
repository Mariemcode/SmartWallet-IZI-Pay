package com.pfe.clientdashboard.ocr.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ScannedFacture
 * ═══════════════
 * Une facture scannée par OCR depuis le mobile.
 *
 * ★ Cette entité Spring est la SOURCE DE VÉRITÉ pour les factures programmées.
 * Avant v7, l'INSERT était fait uniquement par FastAPI mais plantait silencieusement
 * (contrainte UNIQUE manquante). Désormais, OcrController écrit directement ici
 * en plus d'appeler FastAPI pour l'analyse.
 *
 * Mirror exact de la table scanned_facture (cf. SQL_MIGRATION_v7_OCR.sql).
 */
@Entity
@Table(name = "scanned_facture",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_scanned_facture_unique",
                columnNames = {"client_id", "fournisseur_label", "date_echeance"}
        ),
        indexes = {
                @Index(name = "idx_scanned_facture_pending", columnList = "client_id,paye,date_echeance"),
                @Index(name = "idx_scanned_facture_created", columnList = "created_at")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScannedFacture {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "fournisseur_label", nullable = false, length = 20)
    private String fournisseurLabel;

    @Column(name = "fournisseur_nom", length = 50)
    private String fournisseurNom;

    @Column(precision = 10, scale = 2)
    private BigDecimal montant;

    @Column(name = "date_echeance", nullable = false)
    private LocalDate dateEcheance;

    @Column(length = 30)
    private String reference;

    @Column(name = "rappel_j7_envoye")
    @Builder.Default
    private Boolean rappelJ7Envoye = false;

    @Column(name = "rappel_j3_envoye")
    @Builder.Default
    private Boolean rappelJ3Envoye = false;

    @Column(name = "rappel_j1_envoye")
    @Builder.Default
    private Boolean rappelJ1Envoye = false;

    @Column(name = "rappel_j0_envoye")
    @Builder.Default
    private Boolean rappelJ0Envoye = false;

    @Builder.Default
    private Boolean paye = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
