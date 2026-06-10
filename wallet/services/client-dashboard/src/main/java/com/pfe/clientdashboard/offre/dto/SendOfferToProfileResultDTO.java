package com.pfe.clientdashboard.offre.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Réponse de POST /api/recommendations/{id}/send-to-profile
 *
 * status :
 *   • SENT          — FCM a accepté la publication sur le topic
 *   • ECHEC         — erreur d'envoi
 *   • NO_RECIPIENTS — aucun client connu dans ce cluster (warning, pas erreur)
 */
@Data @Builder
@NoArgsConstructor @AllArgsConstructor
public class SendOfferToProfileResultDTO {
    private String status;
    private Integer clusterId;
    private String topic;
    private String offerCode;
    private String title;
    private String message;
    /** Estimation du nombre d'abonnés au topic (basée sur les ClientRecommendation persistés). */
    private Long estimatedRecipients;
    private String fcmResponse;
}
