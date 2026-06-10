package com.pfe.clientdashboard.classification.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ClientWithProfileDTO — Client enrichi avec son profil ML.
 *
 * Utilisé par les endpoints paginés :
 *   GET /api/v1/classification/clusters/{clusterId}/clients
 *   GET /api/v1/classification/profiles/{profileName}/clients
 *
 * Note : clientId est un String (et non UUID) pour rester aligné avec
 * la convention Wallet — l'entité Client.id est String.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientWithProfileDTO {
    private String  clientId;
    private String  firstName;
    private String  lastName;
    private Integer clusterId;
    private String  profileName;
    private String  profileFinal;
    private Double  confidenceScore;
    private Double  churnScore30j;
    private String  churnSegment;
}
