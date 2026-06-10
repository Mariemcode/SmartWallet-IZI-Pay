package com.pfe.clientdashboard.recommendation.dtos;

import lombok.*;

/**
 * DTO reçu par l'admin pour envoyer une offre à un client via FCM.
 * <p>
 * POST /api/recommendations/{id}/send-to-client
 * Body : { "clientId": "uuid-du-client" }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfferNotificationRequestDTO {

    /**
     * UUID du client cible (obligatoire)
     */
    private String clientId;

    /**
     * Message personnalisé optionnel — sinon le titre de l'offre est utilisé
     */
    private String customMessage;
}
