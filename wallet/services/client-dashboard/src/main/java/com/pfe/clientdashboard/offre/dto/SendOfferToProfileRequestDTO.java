package com.pfe.clientdashboard.offre.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body de POST /api/recommendations/{id}/send-to-profile
 *
 * Si clusterId est null, le service le lit depuis Recommendation.clusterId.
 * customMessage est optionnel — si absent, on construit un message par défaut.
 */
@Data @Builder
@NoArgsConstructor @AllArgsConstructor
public class SendOfferToProfileRequestDTO {
    /** ID du cluster ML à cibler (0..N). Si null, on prend celui de la recommandation. */
    private Integer clusterId;

    /** Message custom optionnel ; sinon généré depuis l'offre. */
    private String customMessage;
}
