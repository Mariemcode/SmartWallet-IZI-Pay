package com.pfe.clientdashboard.offre.controller;

import com.pfe.clientdashboard.recommendation.dtos.ClientOfferDTO;
import com.pfe.clientdashboard.recommendation.services.OfferNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ClientOfferController
 * ══════════════════════
 * Endpoints CÔTÉ MOBILE pour les offres marketing reçues.
 *
 * Flutter (lib/services/client_offer_service.dart) appelle :
 *   • GET    /api/client-offers/{clientId}        → toutes les offres reçues
 *   • PATCH  /api/client-offers/{id}/open         → marquer comme lue
 *   • PATCH  /api/client-offers/{id}/respond      → accepter / refuser
 *
 * Ces endpoints étaient référencés dans OfferNotificationService mais
 * jamais exposés par un @RestController — c'est pourquoi le mobile ne
 * voyait aucune offre même quand elles étaient en base.
 */
@Slf4j
@RestController
@RequestMapping("/api/client-offers")
@RequiredArgsConstructor
public class ClientOfferController {

    private final OfferNotificationService offerNotificationService;

    /**
     * GET /api/client-offers/{clientId}
     * Retourne toutes les offres reçues par le client
     * (status ∈ {SENT, OPENED, ACCEPTED, REJECTED}).
     *
     * Réponse : { "data": [ ClientOfferDTO, ... ], "message": "..." }
     */
    @GetMapping("/{clientId}")
    public ResponseEntity<Map<String, Object>> getOffers(@PathVariable String clientId) {
        log.info("→ GET /api/client-offers/{}", clientId);
        try {
            List<ClientOfferDTO> offers = offerNotificationService.getOffersForClient(clientId);
            return ResponseEntity.ok(Map.of(
                    "data", offers,
                    "message", "Offres récupérées"
            ));
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ clientId invalide : {}", clientId);
            return ResponseEntity.badRequest().body(Map.of(
                    "data", List.of(),
                    "message", "client_id invalide"
            ));
        } catch (Exception e) {
            log.error("❌ getOffers({}) : {}", clientId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "data", List.of(),
                    "message", "Erreur serveur : " + e.getMessage()
            ));
        }
    }

    /**
     * PATCH /api/client-offers/{clientRecoId}/open
     * Marque l'offre comme OUVERTE (le client a tapé la notif).
     */
    @PatchMapping("/{clientRecoId}/open")
    public ResponseEntity<Map<String, Object>> markAsOpened(@PathVariable Long clientRecoId) {
        log.info("→ PATCH /api/client-offers/{}/open", clientRecoId);
        try {
            ClientOfferDTO dto = offerNotificationService.markAsOpened(clientRecoId);
            return ResponseEntity.ok(Map.of("data", dto, "message", "Marquée comme ouverte"));
        } catch (Exception e) {
            log.error("❌ markAsOpened({}) : {}", clientRecoId, e.getMessage());
            return ResponseEntity.status(404).body(Map.of(
                    "message", "Offre introuvable : " + clientRecoId
            ));
        }
    }

    /**
     * PATCH /api/client-offers/{clientRecoId}/respond
     * Body : { "accept": true|false }
     *
     * Si accept=true → OfferApplicationService.apply() est déclenché et
     * l'offre est réellement appliquée en base (FEE_WAIVER / CASHBACK / DISCOUNT).
     */
    @PatchMapping("/{clientRecoId}/respond")
    public ResponseEntity<Map<String, Object>> respondToOffer(
            @PathVariable Long clientRecoId,
            @RequestBody Map<String, Object> body) {

        Boolean accept = body.get("accept") instanceof Boolean b ? b : null;
        if (accept == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Champ 'accept' requis (true ou false)"
            ));
        }

        log.info("→ PATCH /api/client-offers/{}/respond accept={}", clientRecoId, accept);
        try {
            ClientOfferDTO dto = offerNotificationService.respondToOffer(clientRecoId, accept);
            return ResponseEntity.ok(Map.of(
                    "data", dto,
                    "message", accept ? "Offre acceptée et appliquée" : "Offre refusée"
            ));
        } catch (Exception e) {
            log.error("❌ respondToOffer({}, {}) : {}", clientRecoId, accept, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "message", "Erreur : " + e.getMessage()
            ));
        }
    }
}
