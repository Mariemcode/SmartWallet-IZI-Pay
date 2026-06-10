package com.pfe.clientdashboard.offre.service;

import com.pfe.clientdashboard.offre.ActiveOfferApplication;
import com.pfe.clientdashboard.offre.repository.ActiveOfferApplicationRepository;
import com.pfe.clientdashboard.recommendation.entities.ClientRecommendation;
import com.pfe.clientdashboard.recommendation.entities.GeneratedOffer;
import com.pfe.clientdashboard.recommendation.repositories.GeneratedOfferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * OfferApplicationService
 * ════════════════════════
 * Quand un utilisateur APPROUVE une recommandation marketing depuis le mobile,
 * ce service traduit l'offre en EFFET RÉEL sur la base de données :
 *
 *   • FEE_WAIVER     → exonération de frais sur Factures+Recharges jusqu'à
 *                       la fin du mois (consultée par TransactionController)
 *   • CASHBACK_RATE  → taux de cashback à appliquer après transaction
 *   • DISCOUNT_PCT   → remise sur certains providers
 *
 * Idempotence : tente d'appliquer 2× la même offre → no-op (renvoie l'existant).
 *
 * Heuristique de mapping offer → effectType :
 *   Le mapping est basé sur les attributs de GeneratedOffer :
 *     - cashbackPct > 0 → CASHBACK_RATE
 *     - discountPct > 0 → DISCOUNT_PCT
 *     - type = "FEE_WAIVER" ou title contient "frais" → FEE_WAIVER
 *     - sinon défaut sur le plus fort signal
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OfferApplicationService {

    private final ActiveOfferApplicationRepository repo;
    private final GeneratedOfferRepository offerRepo;

    // ══════════════════════════════════════════════════════════════
    //  APPLY — transforme une approbation en effet métier réel
    // ══════════════════════════════════════════════════════════════

    /**
     * Applique une offre approuvée. Idempotent.
     *
     * @return l'application active (créée ou existante), ou Optional.empty()
     *         si on n'arrive pas à mapper l'offre vers un effect type.
     */
    @Transactional
    public Optional<ActiveOfferApplication> apply(ClientRecommendation clientReco) {
        if (clientReco == null) {
            log.warn("apply() : clientReco null — skip");
            return Optional.empty();
        }
        if (clientReco.getOfferCode() == null) {
            log.warn("apply() : offerCode null pour clientReco#{}", clientReco.getId());
            return Optional.empty();
        }

        GeneratedOffer offer = offerRepo.findByOfferCode(clientReco.getOfferCode()).orElse(null);
        if (offer == null) {
            log.warn("apply() : offre {} introuvable", clientReco.getOfferCode());
            return Optional.empty();
        }

        ActiveOfferApplication.EffectType effectType = inferEffectType(offer);
        if (effectType == null) {
            log.info("apply() : aucun effet inférable pour {} — application no-op",
                    offer.getOfferCode());
            return Optional.empty();
        }

        LocalDateTime now      = LocalDateTime.now();
        LocalDateTime endsAt   = computeEndOfMonth(now);
        String clientIdStr     = clientReco.getClientId().toString();
        String month           = String.format(Locale.ROOT, "%d-%02d",
                                               now.getYear(), now.getMonthValue());
        String idempotencyKey  = clientIdStr + "|" + offer.getOfferCode() + "|" + month;

        // ── Idempotence : une seule application active par (client, offre, mois)
        Optional<ActiveOfferApplication> existing = repo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("apply() : déjà appliqué pour {} / {} ce mois — no-op",
                    clientIdStr, offer.getOfferCode());
            return existing;
        }

        Map<String, Object> payload = buildPayload(offer, effectType);

        ActiveOfferApplication aoa = ActiveOfferApplication.builder()
                .clientId(clientIdStr)
                .clientRecoId(clientReco.getId())
                .offerCode(offer.getOfferCode())
                .effectType(effectType)
                .payload(payload)
                .status(ActiveOfferApplication.Status.ACTIVE)
                .startsAt(now)
                .endsAt(endsAt)
                .idempotencyKey(idempotencyKey)
                .appliedAmountTotal("0")
                .build();

        ActiveOfferApplication saved = repo.save(aoa);
        log.info("✅ OfferApplication créée : client={} offre={} effet={} jusqu'au {}",
                clientIdStr, offer.getOfferCode(), effectType, endsAt);
        return Optional.of(saved);
    }

    // ══════════════════════════════════════════════════════════════
    //  LECTURE — consultée par TransactionController
    // ══════════════════════════════════════════════════════════════

    /** Y a-t-il une exonération de frais ACTIVE pour ce client maintenant ? */
    @Transactional(readOnly = true)
    public boolean hasFeeWaiver(String clientId) {
        return !repo.findActiveForClientByType(
                clientId,
                ActiveOfferApplication.EffectType.FEE_WAIVER,
                LocalDateTime.now()
        ).isEmpty();
    }

    /** Taux de cashback actif (en %), ou 0 si aucun. Prend le plus avantageux. */
    @Transactional(readOnly = true)
    public BigDecimal activeCashbackRate(String clientId) {
        return repo.findActiveForClientByType(
                clientId,
                ActiveOfferApplication.EffectType.CASHBACK_RATE,
                LocalDateTime.now()
        ).stream()
                .map(a -> {
                    Object v = a.getPayload().get("rate_pct");
                    return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
                })
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<ActiveOfferApplication> listForClient(String clientId) {
        return repo.findByClientIdOrderByCreatedAtDesc(clientId);
    }

    // ══════════════════════════════════════════════════════════════
    //  RÉVOCATION & EXPIRATION
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public void revoke(Long applicationId, String reason) {
        repo.findById(applicationId).ifPresent(a -> {
            a.setStatus(ActiveOfferApplication.Status.REVOKED);
            repo.save(a);
            log.info("⚠️ OfferApplication #{} révoquée : {}", applicationId, reason);
        });
    }

    /** Sweeper : passe en EXPIRED ce qui a dépassé endsAt. Toutes les heures. */
    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void sweepExpired() {
        LocalDateTime now = LocalDateTime.now();
        List<ActiveOfferApplication> expired = repo.findExpiredButStillActive(now);
        for (ActiveOfferApplication a : expired) {
            a.setStatus(ActiveOfferApplication.Status.EXPIRED);
            repo.save(a);
        }
        if (!expired.isEmpty()) {
            log.info("🧹 OfferApplication sweep : {} expirées", expired.size());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  HELPERS PRIVÉS
    // ══════════════════════════════════════════════════════════════

    /**
     * Mapping offre → type d'effet. Heuristique :
     *   1. type/title contient "frais" ou "fee" → FEE_WAIVER
     *   2. cashbackPct > 0 → CASHBACK_RATE
     *   3. discountPct > 0 → DISCOUNT_PCT
     */
    private ActiveOfferApplication.EffectType inferEffectType(GeneratedOffer offer) {
        String type  = offer.getType()  != null ? offer.getType().toUpperCase(Locale.ROOT)  : "";
        String title = offer.getTitle() != null ? offer.getTitle().toLowerCase(Locale.ROOT) : "";

        if (type.contains("FEE") || type.contains("FRAIS") || type.contains("WAIVER")
                || title.contains("frais") || title.contains("0%")
                || title.contains("sans frais") || title.contains("zéro frais")) {
            return ActiveOfferApplication.EffectType.FEE_WAIVER;
        }
        if (offer.getCashbackPct() != null
                && offer.getCashbackPct().compareTo(BigDecimal.ZERO) > 0) {
            return ActiveOfferApplication.EffectType.CASHBACK_RATE;
        }
        if (offer.getDiscountPct() != null
                && offer.getDiscountPct().compareTo(BigDecimal.ZERO) > 0) {
            return ActiveOfferApplication.EffectType.DISCOUNT_PCT;
        }
        return null;
    }

    private Map<String, Object> buildPayload(GeneratedOffer offer,
                                             ActiveOfferApplication.EffectType type) {
        Map<String, Object> p = new HashMap<>();
        switch (type) {
            case FEE_WAIVER -> {
                p.put("scope", "Factures & Services + Recharge Telephonique");
            }
            case CASHBACK_RATE -> {
                p.put("rate_pct", offer.getCashbackPct() != null
                        ? offer.getCashbackPct().toPlainString() : "0");
                if (offer.getCategory() != null) p.put("category", offer.getCategory());
            }
            case DISCOUNT_PCT -> {
                p.put("discount_pct", offer.getDiscountPct() != null
                        ? offer.getDiscountPct().toPlainString() : "0");
                if (offer.getProviderName() != null) p.put("provider_name", offer.getProviderName());
            }
        }
        p.put("title", offer.getTitle());
        p.put("offer_code", offer.getOfferCode());
        return p;
    }

    /** Fin du mois courant à 23:59:59. */
    private LocalDateTime computeEndOfMonth(LocalDateTime now) {
        LocalDate eom = now.toLocalDate().withDayOfMonth(now.toLocalDate().lengthOfMonth());
        return eom.atTime(23, 59, 59);
    }
}
