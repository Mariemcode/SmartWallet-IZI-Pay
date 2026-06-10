package com.pfe.clientdashboard.recommendation.controller;

import com.pfe.clientdashboard.offre.dto.SendOfferToProfileRequestDTO;
import com.pfe.clientdashboard.offre.dto.SendOfferToProfileResultDTO;
import com.pfe.clientdashboard.recommendation.dtos.*;
import com.pfe.clientdashboard.recommendation.entities.Recommendation;
import com.pfe.clientdashboard.recommendation.services.DescriptionGeneratorService;
import com.pfe.clientdashboard.recommendation.services.OfferNotificationService;
import com.pfe.clientdashboard.recommendation.services.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recoService;
    private final DescriptionGeneratorService descriptionGenerator;
    private final OfferNotificationService offerNotificationService;  // ← NOUVEAU

    /**
     * GET /api/recommendations?status=PENDING&profile=...&limit=50
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> listRecommendations(
            @RequestParam(required = false) Recommendation.RecommendationStatus status,
            @RequestParam(required = false) String profile,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {

        Page<RecommendationResponseDTO> page = recoService.getRecommendations(
                status, profile, PageRequest.of(offset / limit, limit));
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("recommendations", page.getContent(),
                        "count", page.getTotalElements()), "OK"));
    }

    /**
     * POST /api/recommendations
     */
    @PostMapping
    public ResponseEntity<ApiResponse<RecommendationResponseDTO>> addManual(
            @RequestBody @Valid ManualRecommendationDTO dto,
            @RequestHeader(value = "X-Admin-User", defaultValue = "admin") String adminUser) {
        return ResponseEntity.status(201)
                .body(ApiResponse.success(
                        recoService.addManualRecommendation(dto, adminUser),
                        "Recommandation ajoutée"));
    }

    /**
     * PUT /api/recommendations/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RecommendationResponseDTO>> update(
            @PathVariable Long id,
            @RequestBody RecommendationStatusDTO dto,
            @RequestHeader(value = "X-Admin-User", defaultValue = "admin") String adminUser) {
        return ResponseEntity.ok(ApiResponse.success(
                recoService.updateRecommendation(id, dto, adminUser), "Mis à jour"));
    }

    /**
     * PATCH /api/recommendations/{id}/approve
     */
    @PatchMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<RecommendationResponseDTO>> approve(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "X-Admin-User", defaultValue = "admin") String adminUser) {
        String note = body != null ? body.get("note") : null;
        return ResponseEntity.ok(ApiResponse.success(
                recoService.approveRecommendation(id, note, adminUser),
                "Recommandation approuvée"));
    }

    /**
     * PATCH /api/recommendations/{id}/reject
     */
    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<RecommendationResponseDTO>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "X-Admin-User", defaultValue = "admin") String adminUser) {
        String note = body != null ? body.get("note") : null;
        return ResponseEntity.ok(ApiResponse.success(
                recoService.rejectRecommendation(id, note, adminUser),
                "Recommandation rejetée"));
    }

    /**
     * POST /api/recommendations/bulk-approve
     */
    @PostMapping("/bulk-approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkApprove(
            @RequestBody @Valid BulkApproveDTO dto,
            @RequestHeader(value = "X-Admin-User", defaultValue = "admin") String adminUser) {
        return ResponseEntity.ok(ApiResponse.success(
                recoService.bulkApprove(dto, adminUser),
                "Approbation en masse effectuée"));
    }

    /**
     * GET /api/recommendations/profile/{profileName}
     */
    @GetMapping("/profile/{profileName}")
    public ResponseEntity<ApiResponse<List<RecommendationResponseDTO>>> byProfile(
            @PathVariable String profileName) {
        return ResponseEntity.ok(ApiResponse.success(
                recoService.getRecommendationsForProfile(profileName),
                "Recommandations du profil"));
    }

    /**
     * GET /api/recommendations/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RecommendationResponseDTO>> getRecommendationById(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(recoService.getRecommendationById(id), "OK"));
    }

    /**
     * POST /api/recommendations/generate-description
     */
    @PostMapping("/generate-description")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateDescription(
            @RequestBody Map<String, String> payload) {
        String offerCode = payload.getOrDefault("offerCode", payload.get("offer_code"));
        if (offerCode == null || offerCode.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Le champ 'offerCode' est requis"));
        }
        String description = descriptionGenerator.generateDescription(offerCode);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("description", description), "Description générée"));
    }

    /**
     * GET /api/recommendations/{profileName}/clients
     */
    @GetMapping("/{profileName}/clients")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getClientsByProfile(
            @PathVariable String profileName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ClientProfileDTO> pageResult = recoService.getClientsByProfile(profileName, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("clients", pageResult.getContent(),
                        "totalElements", pageResult.getTotalElements(),
                        "totalPages", pageResult.getTotalPages(),
                        "currentPage", pageResult.getNumber()),
                "OK"));
    }

    // ══════════════════════════════════════════════════════════════
    //  ★ NOUVEAU — Envoyer l'offre au client via FCM
    // ══════════════════════════════════════════════════════════════

    /**
     * POST /api/recommendations/{id}/send-to-client
     * <p>
     * Body : { "clientId": "uuid-du-client", "customMessage": "..." (optionnel) }
     * <p>
     * Logique :
     * 1. Vérifie que la recommandation est APPROVED
     * 2. Récupère le token FCM du client
     * 3. Envoie une notification push Firebase (type = "offer_received")
     * 4. Met à jour ClientRecommendation.status → SENT
     * 5. Logue dans notification_log
     * <p>
     * Réponse : { status: "SENT"|"NO_TOKEN"|"ECHEC", ... }
     */
    @PostMapping("/{id}/send-to-client")
    public ResponseEntity<ApiResponse<OfferNotificationResultDTO>> sendOfferToClient(
            @PathVariable Long id,
            @RequestBody OfferNotificationRequestDTO req,
            @RequestHeader(value = "X-Admin-User", defaultValue = "admin") String adminUser) {

        // Vérifier que la recommandation existe et est approuvée
        RecommendationResponseDTO reco = recoService.getRecommendationById(id);
        if (!"APPROVED".equals(reco.getStatus())) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Seules les recommandations APPROUVÉES peuvent être envoyées aux clients."));
        }

        // Injecter l'offerCode dans la requête si absent
        if (req.getClientId() == null || req.getClientId().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("clientId est requis"));
        }

        OfferNotificationResultDTO result = offerNotificationService.sendOfferToClient(id, req);

        String msg = "SENT".equals(result.getStatus())
                ? "Offre envoyée avec succès au client"
                : "Échec de l'envoi : " + result.getMessage();

        return ResponseEntity.ok(ApiResponse.success(result, msg));
    }

    // ══════════════════════════════════════════════════════════════
    //  ★ NOUVEAU — Envoyer l'offre à TOUT UN PROFIL via FCM topic
    //  (recommandation marketing : 1 publication = N destinataires)
    // ══════════════════════════════════════════════════════════════

    /**
     * POST /api/recommendations/{id}/send-to-profile
     * <p>
     * Body : { "clusterId": 3, "customMessage": "..." (optionnel) }
     * Si clusterId est null, on prend Recommendation.clusterId.
     * <p>
     * Diffuse la recommandation à tous les utilisateurs abonnés au topic
     * `profile_{clusterId}` via FCM. La version gratuite de Firebase Cloud
     * Messaging supporte les topics nativement.
     * <p>
     * Réponse : SendOfferToProfileResultDTO avec topic, estimation
     * destinataires, fcmResponse.
     */
    @PostMapping("/{id}/send-to-profile")
    public ResponseEntity<ApiResponse<SendOfferToProfileResultDTO>> sendOfferToProfile(
            @PathVariable Long id,
            @RequestBody(required = false) SendOfferToProfileRequestDTO req,
            @RequestHeader(value = "X-Admin-User", defaultValue = "admin") String adminUser) {

        // Vérifier que la recommandation existe et est APPROVED
        RecommendationResponseDTO reco = recoService.getRecommendationById(id);
        if (!"APPROVED".equals(reco.getStatus())) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Seules les recommandations APPROUVÉES peuvent être diffusées."));
        }

        if (req == null) {
            req = SendOfferToProfileRequestDTO.builder().build();
        }

        SendOfferToProfileResultDTO result =
                offerNotificationService.sendOfferToProfile(id, req);

        String msg = "SENT".equals(result.getStatus())
                ? String.format("Offre diffusée au cluster %s (topic=%s, ~%d destinataires)",
                result.getClusterId(), result.getTopic(),
                result.getEstimatedRecipients() != null ? result.getEstimatedRecipients() : 0L)
                : "Échec de la diffusion : " + result.getMessage();

        return ResponseEntity.ok(ApiResponse.success(result, msg));
    }


}
