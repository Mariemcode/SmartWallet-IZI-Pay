package com.pfe.clientdashboard.recommendation.dtos;

import lombok.*;

/**
 * Résultat renvoyé après envoi d'une notification d'offre.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfferNotificationResultDTO {

    private String status;       // "SENT" | "ECHEC" | "NO_TOKEN"
    private String clientId;
    private String offerCode;
    private String title;
    private String message;
    private String fcmResponse;  // ID message FCM si succès
}
