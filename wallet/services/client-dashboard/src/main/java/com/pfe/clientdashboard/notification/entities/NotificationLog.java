package com.pfe.clientdashboard.notification.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_log", indexes = {
        @Index(name = "idx_notification_log_client_id", columnList = "client_id"),
        @Index(name = "idx_notification_log_date_envoi", columnList = "date_envoi")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationLog {
    @Id @Column(length = 64)
    private String id;

    // ✅ FK vers client (nullable car broadcast possible)
    @Column(name = "client_id", length = 64)
    private String clientId;

    @Column(nullable = false, length = 100)
    private String titre;

    @Column(length = 500)
    private String message;

    @Column(length = 30)
    private String type;

    @Column(name = "envoye_par", length = 30)
    private String envoyePar;

    @Column(length = 20)
    private String statut;

    @Column(name = "date_envoi")
    private LocalDateTime dateEnvoi;

    @Column(name = "date_planifiee")
    private LocalDateTime datePlanifiee;
}