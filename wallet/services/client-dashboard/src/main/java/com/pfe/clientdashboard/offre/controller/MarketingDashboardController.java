package com.pfe.clientdashboard.offre.controller;

import com.pfe.clientdashboard.recommendation.entities.ClientRecommendation;
import com.pfe.clientdashboard.recommendation.entities.GeneratedOffer;
import com.pfe.clientdashboard.recommendation.entities.UserInteraction;
import com.pfe.clientdashboard.recommendation.repositories.ClientRecommendationRepository;
import com.pfe.clientdashboard.recommendation.repositories.GeneratedOfferRepository;
import com.pfe.clientdashboard.recommendation.repositories.UserInteractionRepository;
import com.pfe.clientdashboard.recommendation.repositories.RecommendationRepository;
import com.pfe.clientdashboard.offre.repository.ActiveOfferApplicationRepository;
import com.pfe.clientdashboard.offre.service.MarketingFeedbackScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MarketingDashboardController
 * ═════════════════════════════
 * Endpoints pour le dashboard admin "Marketing Feedback".
 *
 * Source de vérité = Spring (PostgreSQL local) — pas FastAPI :
 * les stats sont calculées directement depuis client_recommendations_v5
 * et user_interactions_v5 → toujours à jour, même sans push vers FastAPI.
 *
 * Routes :
 *   • GET  /api/marketing/dashboard            → KPIs globaux + listes
 *   • GET  /api/marketing/recommendations      → toutes les ClientRecommendation
 *   • GET  /api/marketing/interactions         → toutes les UserInteraction
 *   • GET  /api/marketing/offers/stats         → stats par offre (Spring)
 *   • GET  /api/marketing/profiles/stats       → stats par cluster (Spring)
 *   • GET  /api/marketing/active-applications  → effets RÉELS appliqués
 *
 * Le widget admin Angular consomme ces endpoints pour afficher
 * les VRAIES données du backend Spring.
 */
@Slf4j
@RestController
@RequestMapping("/api/marketing")
@RequiredArgsConstructor
public class MarketingDashboardController {

    private final ClientRecommendationRepository clientRecoRepo;
    private final UserInteractionRepository interactionRepo;
    private final GeneratedOfferRepository offerRepo;
    private final RecommendationRepository recoRepo;
    private final ActiveOfferApplicationRepository aoaRepo;
    private final MarketingFeedbackScheduler scheduler;

    // ════════════════════════════════════════════════════════════════
    //  GET /api/marketing/dashboard
    //  Vue d'ensemble : tous les KPIs + listes raccourcies
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        try {
            List<ClientRecommendation> allRecos = clientRecoRepo.findAll();
            List<UserInteraction> allInteractions = interactionRepo.findAll();

            // ── KPIs par statut ClientRecommendation
            Map<String, Long> byStatus = allRecos.stream()
                    .filter(cr -> cr.getStatus() != null)
                    .collect(Collectors.groupingBy(
                            cr -> cr.getStatus().name(), Collectors.counting()));

            long nbSent     = byStatus.getOrDefault("SENT", 0L);
            long nbOpened   = byStatus.getOrDefault("OPENED", 0L);
            long nbAccepted = byStatus.getOrDefault("ACCEPTED", 0L);
            long nbRejected = byStatus.getOrDefault("REJECTED", 0L);
            long nbPending  = byStatus.getOrDefault("PENDING", 0L);
            long total      = allRecos.size();
            long nbResponded = nbAccepted + nbRejected;

            double acceptRate = nbResponded > 0
                    ? (double) nbAccepted / nbResponded
                    : 0.0;
            double openRate = total > 0
                    ? (double) (nbOpened + nbAccepted + nbRejected) / total
                    : 0.0;

            // ── KPIs par action UserInteraction
            Map<String, Long> byAction = allInteractions.stream()
                    .filter(i -> i.getAction() != null)
                    .collect(Collectors.groupingBy(
                            i -> i.getAction().name(), Collectors.counting()));

            // ── Effets réels actifs (offres appliquées en base)
            long nbActiveApplications = aoaRepo.findAll().stream()
                    .filter(a -> "ACTIVE".equals(a.getStatus() != null ? a.getStatus().name() : null))
                    .count();

            // ── Distinct clients touchés
            long uniqueClientsReached = allRecos.stream()
                    .map(ClientRecommendation::getClientId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();

            // ── Top 5 dernières interactions
            List<Map<String, Object>> recentInteractions = allInteractions.stream()
                    .filter(i -> i.getRecordedAt() != null)
                    .sorted(Comparator.comparing(UserInteraction::getRecordedAt).reversed())
                    .limit(10)
                    .map(this::toInteractionMap)
                    .collect(Collectors.toList());

            // ── Top 5 dernières recos
            List<Map<String, Object>> recentRecos = allRecos.stream()
                    .filter(cr -> cr.getSentAt() != null)
                    .sorted(Comparator.comparing(ClientRecommendation::getSentAt).reversed())
                    .limit(10)
                    .map(this::toRecoMap)
                    .collect(Collectors.toList());

            // ── Compose
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kpis", Map.of(
                    "total_sent",        nbSent + nbOpened + nbAccepted + nbRejected,
                    "total_pending",     nbPending,
                    "total_opened",      nbOpened,
                    "total_accepted",    nbAccepted,
                    "total_rejected",    nbRejected,
                    "unique_clients",    uniqueClientsReached,
                    "accept_rate",       round(acceptRate, 3),
                    "open_rate",         round(openRate, 3),
                    "active_applications", nbActiveApplications,
                    "total_interactions", allInteractions.size()
            ));
            result.put("by_status", byStatus);
            result.put("by_action", byAction);
            result.put("recent_interactions", recentInteractions);
            result.put("recent_recommendations", recentRecos);
            result.put("scheduler_cursor", scheduler.getLastPushedCursor().toString());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ getDashboard : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/marketing/offers/stats
    //  Pour chaque offre : nb envoyée, ouverte, acceptée, refusée
    //  (calcul depuis client_recommendations_v5 + user_interactions_v5)
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/offers/stats")
    public ResponseEntity<?> getOffersStats() {
        try {
            List<ClientRecommendation> allRecos = clientRecoRepo.findAll();

            // Group by offer code
            Map<String, List<ClientRecommendation>> byOffer = allRecos.stream()
                    .filter(cr -> cr.getOfferCode() != null)
                    .collect(Collectors.groupingBy(ClientRecommendation::getOfferCode));

            List<Map<String, Object>> stats = byOffer.entrySet().stream()
                    .map(e -> {
                        String code = e.getKey();
                        List<ClientRecommendation> recos = e.getValue();

                        long sent = recos.stream()
                                .filter(r -> r.getStatus() != null && r.getStatus().name().equals("SENT")).count();
                        long opened = recos.stream()
                                .filter(r -> r.getStatus() != null && r.getStatus().name().equals("OPENED")).count();
                        long accepted = recos.stream()
                                .filter(r -> r.getStatus() != null && r.getStatus().name().equals("ACCEPTED")).count();
                        long rejected = recos.stream()
                                .filter(r -> r.getStatus() != null && r.getStatus().name().equals("REJECTED")).count();
                        long total = recos.size();
                        long responded = accepted + rejected;

                        // Enrichi avec les infos de l'offre
                        Optional<GeneratedOffer> offer = offerRepo.findByOfferCode(code);

                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("offer_code", code);
                        m.put("offer_title", offer.map(GeneratedOffer::getTitle).orElse(code));
                        m.put("offer_type", offer.map(GeneratedOffer::getType).orElse(null));
                        m.put("boost", offer.map(GeneratedOffer::getBoost).orElse(null));
                        m.put("total", total);
                        m.put("sent", sent);
                        m.put("opened", opened);
                        m.put("accepted", accepted);
                        m.put("rejected", rejected);
                        m.put("accept_rate", responded > 0 ? round((double) accepted / responded, 3) : null);
                        m.put("open_rate", total > 0 ? round((double) (opened + accepted + rejected) / total, 3) : null);
                        return m;
                    })
                    .sorted((a, b) -> Long.compare(
                            ((Number) b.get("total")).longValue(),
                            ((Number) a.get("total")).longValue()))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of("data", stats, "count", stats.size()));
        } catch (Exception e) {
            log.error("❌ getOffersStats : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/marketing/profiles/stats
    //  Pour chaque cluster : nb envoyée, acceptée, refusée
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/profiles/stats")
    public ResponseEntity<?> getProfilesStats() {
        try {
            List<ClientRecommendation> allRecos = clientRecoRepo.findAll();

            Map<Integer, List<ClientRecommendation>> byCluster = allRecos.stream()
                    .filter(cr -> cr.getClusterId() != null)
                    .collect(Collectors.groupingBy(ClientRecommendation::getClusterId));

            List<Map<String, Object>> stats = byCluster.entrySet().stream()
                    .map(e -> {
                        Integer cid = e.getKey();
                        List<ClientRecommendation> recos = e.getValue();

                        long accepted = recos.stream()
                                .filter(r -> r.getStatus() != null && r.getStatus().name().equals("ACCEPTED")).count();
                        long rejected = recos.stream()
                                .filter(r -> r.getStatus() != null && r.getStatus().name().equals("REJECTED")).count();
                        long opened = recos.stream()
                                .filter(r -> r.getStatus() != null && r.getStatus().name().equals("OPENED")).count();
                        long total = recos.size();
                        long responded = accepted + rejected;

                        long uniqueClients = recos.stream()
                                .map(ClientRecommendation::getClientId)
                                .filter(Objects::nonNull)
                                .distinct().count();

                        // Profile name le + fréquent
                        String profileName = recos.stream()
                                .map(ClientRecommendation::getProfileName)
                                .filter(Objects::nonNull)
                                .collect(Collectors.groupingBy(p -> p, Collectors.counting()))
                                .entrySet().stream()
                                .max(Map.Entry.comparingByValue())
                                .map(Map.Entry::getKey)
                                .orElse("Cluster " + cid);

                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("cluster_id", cid);
                        m.put("profile_name", profileName);
                        m.put("total", total);
                        m.put("unique_clients", uniqueClients);
                        m.put("opened", opened);
                        m.put("accepted", accepted);
                        m.put("rejected", rejected);
                        m.put("accept_rate", responded > 0 ? round((double) accepted / responded, 3) : null);
                        return m;
                    })
                    .sorted(Comparator.comparingInt(m -> ((Number) m.get("cluster_id")).intValue()))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of("data", stats, "count", stats.size()));
        } catch (Exception e) {
            log.error("❌ getProfilesStats : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/marketing/recommendations?limit=100
    //  Liste paginée des dernières recos envoyées
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/recommendations")
    public ResponseEntity<?> listRecommendations(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String offerCode) {
        try {
            List<ClientRecommendation> all = clientRecoRepo.findAll();

            List<Map<String, Object>> data = all.stream()
                    .filter(cr -> status == null || (cr.getStatus() != null && cr.getStatus().name().equalsIgnoreCase(status)))
                    .filter(cr -> offerCode == null || offerCode.equals(cr.getOfferCode()))
                    .sorted((a, b) -> {
                        LocalDateTime ta = a.getSentAt() != null ? a.getSentAt() : (a.getStatus() != null ? null : null);
                        LocalDateTime tb = b.getSentAt() != null ? b.getSentAt() : (b.getStatus() != null ? null : null);
                        if (ta == null && tb == null) return 0;
                        if (ta == null) return 1;
                        if (tb == null) return -1;
                        return tb.compareTo(ta);
                    })
                    .limit(Math.min(limit, 500))
                    .map(this::toRecoMap)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "count", data.size(),
                    "total_in_db", all.size()
            ));
        } catch (Exception e) {
            log.error("❌ listRecommendations : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/marketing/interactions?limit=100
    //  Liste des dernières interactions utilisateurs
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/interactions")
    public ResponseEntity<?> listInteractions(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String offerCode) {
        try {
            List<UserInteraction> all = interactionRepo.findAll();

            List<Map<String, Object>> data = all.stream()
                    .filter(i -> action == null || (i.getAction() != null && i.getAction().name().equalsIgnoreCase(action)))
                    .filter(i -> offerCode == null || offerCode.equals(i.getOfferCode()))
                    .sorted((a, b) -> {
                        if (a.getRecordedAt() == null && b.getRecordedAt() == null) return 0;
                        if (a.getRecordedAt() == null) return 1;
                        if (b.getRecordedAt() == null) return -1;
                        return b.getRecordedAt().compareTo(a.getRecordedAt());
                    })
                    .limit(Math.min(limit, 500))
                    .map(this::toInteractionMap)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "count", data.size(),
                    "total_in_db", all.size()
            ));
        } catch (Exception e) {
            log.error("❌ listInteractions : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/marketing/active-applications
    //  Liste des offres RÉELLEMENT appliquées en base
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/active-applications")
    public ResponseEntity<?> getActiveApplications(
            @RequestParam(defaultValue = "100") int limit) {
        try {
            var all = aoaRepo.findAll();

            List<Map<String, Object>> data = all.stream()
                    .filter(a -> a.getCreatedAt() != null)
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .limit(Math.min(limit, 500))
                    .map(a -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", a.getId());
                        m.put("client_id", a.getClientId());
                        m.put("offer_code", a.getOfferCode());
                        m.put("effect_type", a.getEffectType() != null ? a.getEffectType().name() : null);
                        m.put("status", a.getStatus() != null ? a.getStatus().name() : null);
                        m.put("starts_at", a.getStartsAt() != null ? a.getStartsAt().toString() : null);
                        m.put("ends_at", a.getEndsAt() != null ? a.getEndsAt().toString() : null);
                        m.put("applied_amount_total", a.getAppliedAmountTotal());
                        m.put("created_at", a.getCreatedAt().toString());
                        return m;
                    })
                    .collect(Collectors.toList());

            // Stats par type d'effet
            Map<String, Long> byEffect = all.stream()
                    .filter(a -> a.getEffectType() != null)
                    .collect(Collectors.groupingBy(
                            a -> a.getEffectType().name(), Collectors.counting()));

            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "count", data.size(),
                    "total_in_db", all.size(),
                    "by_effect_type", byEffect
            ));
        } catch (Exception e) {
            log.error("❌ getActiveApplications : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Helpers privés
    // ════════════════════════════════════════════════════════════════

    private Map<String, Object> toRecoMap(ClientRecommendation cr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", cr.getId());
        m.put("client_id", cr.getClientId());
        m.put("offer_code", cr.getOfferCode());
        m.put("profile_name", cr.getProfileName());
        m.put("cluster_id", cr.getClusterId());
        m.put("status", cr.getStatus() != null ? cr.getStatus().name() : "PENDING");
        m.put("personal_score", cr.getPersonalScore());
        m.put("sent_at",     cr.getSentAt()     != null ? cr.getSentAt().toString()     : null);
        m.put("opened_at",   cr.getOpenedAt()   != null ? cr.getOpenedAt().toString()   : null);
        m.put("accepted_at", cr.getAcceptedAt() != null ? cr.getAcceptedAt().toString() : null);
        m.put("rejected_at", cr.getRejectedAt() != null ? cr.getRejectedAt().toString() : null);
        // Enrichi avec titre de l'offre
        if (cr.getOfferCode() != null) {
            offerRepo.findByOfferCode(cr.getOfferCode()).ifPresent(o ->
                    m.put("offer_title", o.getTitle()));
        }
        return m;
    }

    private Map<String, Object> toInteractionMap(UserInteraction i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("client_id", i.getClientId());
        m.put("offer_code", i.getOfferCode());
        m.put("action", i.getAction() != null ? i.getAction().name() : null);
        m.put("recorded_at", i.getRecordedAt() != null ? i.getRecordedAt().toString() : null);
        // Enrichi avec titre de l'offre
        if (i.getOfferCode() != null) {
            offerRepo.findByOfferCode(i.getOfferCode()).ifPresent(o ->
                    m.put("offer_title", o.getTitle()));
        }
        return m;
    }

    private double round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(v * f) / f;
    }
}
