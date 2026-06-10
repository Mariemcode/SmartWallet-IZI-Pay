package com.pfe.clientdashboard.classification.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientProfileEntity {
    @Id
    @Column(name = "client_id", columnDefinition = "uuid")
    private String clientId;

    @Column(name = "cluster_id")
    private Integer clusterId;

    @Column(name = "profile_name")
    private String profileName;

    @Column(name = "profile_final")
    private String profileFinal;

    @Column(name = "is_mixte")
    private Boolean isMixte;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "gbm_confidence")
    private Double gbmConfidence;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "churn_score_30j")
    private Double churnScore30j;

    @Column(name = "churn_segment")
    private String churnSegment;

    @Column(name = "ltv_12m")
    private Double ltv12m;

    @Column(name = "ltv_12m_optimiste")
    private Double ltv12mOptimiste;

    @Column(name = "ltv_12m_pessimiste")
    private Double ltv12mPessimiste;

    @Column(name = "hazard_rate")
    private Double hazardRate;

    @Column(name = "arpu_mensuel")
    private Double arpuMensuel;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "in_holdout")
    private Boolean inHoldout;


}
