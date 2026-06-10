package com.pfe.clientdashboard.recommendation.entities;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "generated_offers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GeneratedOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offer_code", nullable = false, unique = true, length = 40)
    private String offerCode;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "provider_name", length = 100)
    private String providerName;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "cashback_pct", precision = 5, scale = 2)
    private BigDecimal cashbackPct = BigDecimal.ZERO;

    @Column(name = "discount_pct", precision = 5, scale = 2)
    private BigDecimal discountPct = BigDecimal.ZERO;

    @Column(name = "min_amount", precision = 10, scale = 2)
    private BigDecimal minAmount = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_profiles", columnDefinition = "jsonb")
    private List<String> targetProfiles;

    @Column(name = "boost", precision = 4, scale = 2)
    private BigDecimal boost = BigDecimal.ONE;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private OfferStatus status = OfferStatus.ACTIVE;

    @Column(name = "generation_method", length = 50)
    private String generationMethod;

    @Column(name = "generation_run", length = 50)
    private String generationRun;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum OfferStatus {
        ACTIVE, INACTIVE
    }
}
