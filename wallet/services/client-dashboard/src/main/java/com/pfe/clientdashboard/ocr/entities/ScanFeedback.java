package com.pfe.clientdashboard.ocr.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ScanFeedback
 * ═════════════
 * Trace les corrections utilisateur sur une facture OCR.
 *
 * ★ Source de vérité Spring (à partir de v7). Avant, l'INSERT était fait par FastAPI
 * uniquement et parfois échouait silencieusement.
 *
 * Alimente FeedbackLearningScheduler qui tourne tous les dimanches à 3h
 * pour analyser les patterns d'erreurs OCR par fournisseur.
 *
 * Mirror exact de la table scan_feedback.
 */
@Entity
@Table(name = "scan_feedback",
        indexes = {
                @Index(name = "idx_scan_feedback_processed", columnList = "processed_at"),
                @Index(name = "idx_scan_feedback_created",   columnList = "created_at"),
                @Index(name = "idx_scan_feedback_fournisseur", columnList = "ocr_fournisseur")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScanFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    // ── Ce que l'OCR a détecté ────────────────────────────────────
    @Column(name = "ocr_fournisseur", length = 32)
    private String ocrFournisseur;

    @Column(name = "ocr_montant", precision = 10, scale = 2)
    private BigDecimal ocrMontant;

    @Column(name = "ocr_date_echeance", length = 10)
    private String ocrDateEcheance;

    @Column(name = "ocr_reference", length = 32)
    private String ocrReference;

    @Column(name = "ocr_confiance", length = 10)
    private String ocrConfiance;

    @Column(name = "ocr_text_brut", columnDefinition = "text")
    private String ocrTextBrut;

    // ── Ce que l'utilisateur a corrigé ────────────────────────────
    @Column(name = "user_fournisseur", length = 32)
    private String userFournisseur;

    @Column(name = "user_montant", precision = 10, scale = 2)
    private BigDecimal userMontant;

    @Column(name = "user_date_echeance", length = 10)
    private String userDateEcheance;

    @Column(name = "user_reference", length = 32)
    private String userReference;

    // ── Drapeaux d'évolution (calculés au moment de l'INSERT) ─────
    @Column(name = "fournisseur_corrige")
    @Builder.Default
    private Boolean fournisseurCorrige = false;

    @Column(name = "montant_corrige")
    @Builder.Default
    private Boolean montantCorrige = false;

    @Column(name = "date_corrigee")
    @Builder.Default
    private Boolean dateCorrigee = false;

    @Column(name = "reference_corrigee")
    @Builder.Default
    private Boolean referenceCorrigee = false;

    @Column(name = "valide_sans_correction")
    @Builder.Default
    private Boolean valideSansCorrection = false;

    @Column(name = "action_finale", length = 20)
    @Builder.Default
    private String actionFinale = "paye";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
