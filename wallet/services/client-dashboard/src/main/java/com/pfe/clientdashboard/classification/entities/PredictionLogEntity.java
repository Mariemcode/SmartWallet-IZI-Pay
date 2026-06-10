package com.pfe.clientdashboard.classification.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "prediction_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "cluster_id")
    private Integer clusterId;

    @Column(name = "profile_name")
    private String profileName;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "churn_score_30j")
    private Double churnScore30j;

    @Column(name = "churn_segment")
    private String churnSegment;

    @Column(name = "ltv_12m_base")
    private Double ltv12mBase;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "features_json", columnDefinition = "text")
    private String featuresJson;

    @Column(name = "status")
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "predicted_at")
    private LocalDateTime predictedAt;
}