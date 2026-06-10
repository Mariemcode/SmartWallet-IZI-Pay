package com.pfe.clientdashboard.notification.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Table fcm_token — stocke les tokens Firebase pour les notifications push.
 * Remplace le HashMap volatil qui perdait les tokens au redémarrage.
 */
@Entity
@Table(name = "fcm_token")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FcmToken {

    @Id
    @Column(name = "client_id", length = 64)
    private String clientId;

    @Column(name = "token", nullable = false, length = 512)
    private String token;

    @Column(name = "platform", length = 20)
    private String platform;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
