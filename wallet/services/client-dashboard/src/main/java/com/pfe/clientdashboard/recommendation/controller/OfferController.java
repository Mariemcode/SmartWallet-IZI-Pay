package com.pfe.clientdashboard.recommendation.controller;

import com.pfe.clientdashboard.recommendation.dtos.*;
import com.pfe.clientdashboard.recommendation.entities.GeneratedOffer;
import com.pfe.clientdashboard.recommendation.services.OfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.Map;

@RestController
@RequestMapping("/api/offers")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService offerService;

    /**
     * GET /api/v5/offers?status=ACTIVE&type=cashback&limit=50&offset=0
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> listOffers(
            @RequestParam(required = false) GeneratedOffer.OfferStatus status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {

        Pageable pageable = PageRequest.of(offset / limit, limit);
        Page<OfferResponseDTO> page = offerService.getOffers(
                status, type, provider, category, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                Map.of("offers", page.getContent(), "count", page.getTotalElements()),
                "Offres récupérées"));
    }

    /**
     * GET /api/v5/offers/{offerCode}
     */
    @GetMapping("/{offerCode}")
    public ResponseEntity<ApiResponse<OfferResponseDTO>> getOffer(
            @PathVariable String offerCode) {
        return ResponseEntity.ok(
                ApiResponse.success(offerService.getOfferByCode(offerCode), "OK"));
    }

    /**
     * POST /api/v5/offers
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OfferResponseDTO>> createOffer(
            @RequestBody @Valid OfferRequestDTO dto) {
        return ResponseEntity.status(201)
                .body(ApiResponse.success(offerService.createOffer(dto), "Offre créée"));
    }

    /**
     * PUT /api/v5/offers/{offerCode}
     */
    @PutMapping("/{offerCode}")
    public ResponseEntity<ApiResponse<OfferResponseDTO>> updateOffer(
            @PathVariable String offerCode,
            @RequestBody OfferUpdateDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(
                offerService.updateOffer(offerCode, dto), "Offre mise à jour"));
    }

    /**
     * PATCH /api/v5/offers/{offerCode}/status
     */
    @PatchMapping("/{offerCode}/status")
    public ResponseEntity<ApiResponse<OfferResponseDTO>> setStatus(
            @PathVariable String offerCode,
            @RequestBody @Valid OfferStatusDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(
                offerService.setOfferStatus(offerCode, dto),
                "Statut changé en " + dto.getStatus()));
    }

    /**
     * DELETE /api/v5/offers/{offerCode}
     */
    @DeleteMapping("/{offerCode}")
    public ResponseEntity<ApiResponse<Void>> deleteOffer(
            @PathVariable String offerCode) {
        offerService.deleteOffer(offerCode);
        return ResponseEntity.ok(ApiResponse.success(null, "Offre supprimée"));
    }
}
