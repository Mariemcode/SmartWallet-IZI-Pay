package com.pfe.clientdashboard.offre;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ActiveOfferApplication
 * ═══════════════════════
 * Représente l'EFFET RÉEL d'une offre marketing approuvée par un client.
 *
 * Quand un client clique "Approuver" sur une recommandation marketing reçue
 * via FCM, l'OfferApplicationService crée une entrée ici. Le TransactionController
 * consulte cette table à chaque transaction pour appliquer les exonérations /
 * remises / cashbacks réellement, pas de manière décorative.
 *
 * Trois types d'effet (effectType) :
 *   • FEE_WAIVER     → exonération totale de frais sur Factures+Recharges
 *                       jusqu'à `endsAt`
 *   • CASHBACK_RATE  → taux de cashback à créditer après chaque transaction
 *                       éligible (payload.rate_pct, payload.category)
 *   • DISCOUNT_PCT   → remise sur les paiements à certains providers
 *                       (payload.discount_pct, payload.provider_id)
 *
 * Status :
 *   • PENDING     : créé mais pas encore actif (startsAt > now)
 *   • ACTIVE      : actif maintenant
 *   • EXHAUSTED   : quota épuisé (ex. cap atteint)
 *   • EXPIRED     : passé `endsAt`
 *   • REVOKED     : révoqué manuellement par admin
 *
 * Idempotence : (clientId, offerCode, status=ACTIVE) doit être unique —
 * si on tente d'appliquer 2× la même offre, on no-op.
 */
@Entity
@Table(name = "active_offer_application", indexes = {
        @Index(name = "idx_aoa_client_status", columnList = "client_id,status"),
        @Index(name = "idx_aoa_effect_type", columnList = "effect_type"),
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ActiveOfferApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "client_reco_id")
    private Long clientRecoId;

    @Column(name = "offer_code", nullable = false, length = 40)
    private String offerCode;

    @Column(name = "effect_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private EffectType effectType;

    /** Payload typé par effectType, exemples :
     *   FEE_WAIVER     : {} (rien — exonération totale jusqu'à endsAt)
     *   CASHBACK_RATE  : {"rate_pct": 5.0, "category": "Shopping & Paiements"}
     *   DISCOUNT_PCT   : {"discount_pct": 10.0, "provider_id": "..."}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();

    @Column(name = "status", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    /** Idempotence key : clientId|offerCode|monthStamp — évite double application. */
    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;

    @Column(name = "applied_amount_total", length = 32)
    private String appliedAmountTotal;  // string pour BigDecimal sérialisé (incrément simple)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (idempotencyKey == null) {
            // ex : "abc-123|FEE_WAIVER_MAY|2026-05"
            String month = String.format("%d-%02d",
                    startsAt.getYear(), startsAt.getMonthValue());
            idempotencyKey = clientId + "|" + offerCode + "|" + month;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum EffectType { FEE_WAIVER, CASHBACK_RATE, DISCOUNT_PCT }

    public enum Status { PENDING, ACTIVE, EXHAUSTED, EXPIRED, REVOKED }

    /** True si l'application est active à la date donnée (consultée par TransactionController). */
    public boolean isActiveAt(LocalDateTime now) {
        return status == Status.ACTIVE
                && (startsAt == null || !now.isBefore(startsAt))
                && (endsAt == null   || now.isBefore(endsAt));
    }
}
