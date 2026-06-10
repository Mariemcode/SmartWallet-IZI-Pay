package com.pfe.clientdashboard.provider.entities;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entité mappée sur la table "provider"
 *
 * Colonnes dataset :
 *   id, provider_code, provider_name
 *
 * 9 providers dans le dataset :
 *   SONEDE, BEE, TOPNET, STEG, TT (×2), ORANGE, OOREDOO, CNTE
 */
@Entity
@Table(name = "provider")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Provider {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "provider_code")
    private String providerCode;

    @Column(name = "provider_name")
    private String providerName;
}
