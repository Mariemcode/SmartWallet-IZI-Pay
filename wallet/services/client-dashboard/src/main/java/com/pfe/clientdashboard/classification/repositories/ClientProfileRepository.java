package com.pfe.clientdashboard.classification.repositories;

import com.pfe.clientdashboard.classification.entities.ClientProfileEntity;
import com.pfe.clientdashboard.classification.dtos.ClientWithProfileDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.pfe.clientdashboard.recommendation.dtos.ClientProfileDTO;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public interface ClientProfileRepository extends JpaRepository<ClientProfileEntity, String> {
    Optional<ClientProfileEntity> findByClientId(String clientId);
    List<ClientProfileEntity> findByChurnScore30jGreaterThanEqual(Double threshold);
    List<ClientProfileEntity> findByClusterId(Integer clusterId);

    @Query(value = """
    SELECT 
        cp.cluster_id,
        COUNT(t.id),
        COUNT(t.id) * 1.0 / NULLIF(COUNT(DISTINCT cp.client_id), 0)
    FROM client_profiles cp
    LEFT JOIN transaction t ON t.client_id = cp.client_id::varchar AND t.reversal_flag = 'N'
    WHERE cp.model_version = (
        SELECT model_version FROM model_runs ORDER BY run_at DESC LIMIT 1
    )
    GROUP BY cp.cluster_id
    ORDER BY cp.cluster_id
    """, nativeQuery = true)
    List<Object[]> getTransactionsCountByProfile();

    @Query(value = """
    SELECT 
        tt.category,
        COUNT(*) AS nbTransactions,
        ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 2) AS pct
    FROM client_profiles cp
    JOIN transaction t ON t.client_id = cp.client_id::varchar
    JOIN type_transaction tt ON t.transaction_type_id = tt.id
    WHERE cp.cluster_id = :profileId
      AND cp.model_version = (SELECT model_version FROM model_runs ORDER BY run_at DESC LIMIT 1)
    GROUP BY tt.category
    ORDER BY nbTransactions DESC
    """, nativeQuery = true)
    List<Object[]> getCategoriesByProfileId(@Param("profileId") Integer profileId);

    @Query(value = """
    SELECT 
        c.id AS clientId,
        c.first_name AS firstName,
        c.last_name AS lastName,
        cp.profile_final AS profileName,
        cp.confidence_score AS confidenceScore,
        cp.churn_score_30j AS churnScore30j,
        cp.rfm_score AS rfmScore
    FROM client_profiles cp
    INNER JOIN client c ON c.id = cp.client_id::varchar
    WHERE cp.profile_final = :profileName
    ORDER BY c.last_name, c.first_name
    OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
    """, nativeQuery = true)
    List<Object[]> findClientDTOsByProfileFinalNative(@Param("profileName") String profileName,
                                                      @Param("offset") int offset,
                                                      @Param("limit") int limit);

    @Query(value = "SELECT COUNT(*) FROM client_profiles WHERE profile_final = :profileName", nativeQuery = true)
    long countByProfileFinal(@Param("profileName") String profileName);

    default Page<ClientProfileDTO> findDTOByProfileFinal(String profileName, Pageable pageable) {
        long total = countByProfileFinal(profileName);
        List<Object[]> rows = findClientDTOsByProfileFinalNative(profileName, (int) pageable.getOffset(), pageable.getPageSize());
        List<ClientProfileDTO> dtos = rows.stream().map(row -> ClientProfileDTO.builder()
                .clientId(row[0] != null ? row[0].toString() : null)
                .firstName((String) row[1])
                .lastName((String) row[2])
                .profileName((String) row[3])
                .confidenceScore(row[4] != null ? ((Number) row[4]).doubleValue() : 0.0)
                .churnScore30j(row[5] != null ? ((Number) row[5]).doubleValue() : 0.0)
                .rfmScore(row[6] != null ? ((Number) row[6]).doubleValue() : 0.0)
                .build()
        ).collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, total);
    }

    // ════════════════════════════════════════════════════════════════
    //  Pagination CLIENTS par CLUSTER / PROFIL  (méthodes binôme)
    // ════════════════════════════════════════════════════════════════
    //  Issues du projet Admin — adaptées à la convention Wallet :
    //  - table client_profiles (et non client_profiles)
    //  - JOIN client c ON cp.client_id::varchar = c.id (String, pas UUID)
    //  - native query pour éviter la dépendance JPA Client ↔ ClientProfileEntity
    // ════════════════════════════════════════════════════════════════

    @Query(value = """
        SELECT
            c.id              AS clientId,
            c.first_name      AS firstName,
            c.last_name       AS lastName,
            cp.cluster_id     AS clusterId,
            cp.profile_name   AS profileName,
            cp.profile_final  AS profileFinal,
            cp.confidence_score AS confidenceScore,
            cp.churn_score_30j  AS churnScore30j,
            cp.churn_segment    AS churnSegment
          FROM client_profiles cp
          JOIN client c ON c.id = cp.client_id::varchar
         WHERE cp.cluster_id = :clusterId
         ORDER BY c.last_name NULLS LAST, c.first_name NULLS LAST
         OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """, nativeQuery = true)
    List<Object[]> findClientsByClusterIdNative(@Param("clusterId") Integer clusterId,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    @Query(value = "SELECT COUNT(*) FROM client_profiles WHERE cluster_id = :clusterId",
           nativeQuery = true)
    long countByClusterId(@Param("clusterId") Integer clusterId);

    default Page<ClientWithProfileDTO> findClientsByClusterId(Integer clusterId, Pageable pageable) {
        long total = countByClusterId(clusterId);
        List<Object[]> rows = findClientsByClusterIdNative(
                clusterId, (int) pageable.getOffset(), pageable.getPageSize());
        return new PageImpl<>(rows.stream().map(this::mapRowToClientWithProfile)
                .collect(Collectors.toList()), pageable, total);
    }

    @Query(value = """
        SELECT
            c.id              AS clientId,
            c.first_name      AS firstName,
            c.last_name       AS lastName,
            cp.cluster_id     AS clusterId,
            cp.profile_name   AS profileName,
            cp.profile_final  AS profileFinal,
            cp.confidence_score AS confidenceScore,
            cp.churn_score_30j  AS churnScore30j,
            cp.churn_segment    AS churnSegment
          FROM client_profiles cp
          JOIN client c ON c.id = cp.client_id::varchar
         WHERE cp.profile_final = :profileName
         ORDER BY c.last_name NULLS LAST, c.first_name NULLS LAST
         OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """, nativeQuery = true)
    List<Object[]> findClientsByProfileFinalNative(@Param("profileName") String profileName,
                                                   @Param("offset") int offset,
                                                   @Param("limit") int limit);

    default Page<ClientWithProfileDTO> findClientsByProfileFinal(String profileName, Pageable pageable) {
        long total = countByProfileFinal(profileName);
        List<Object[]> rows = findClientsByProfileFinalNative(
                profileName, (int) pageable.getOffset(), pageable.getPageSize());
        return new PageImpl<>(rows.stream().map(this::mapRowToClientWithProfile)
                .collect(Collectors.toList()), pageable, total);
    }

    private ClientWithProfileDTO mapRowToClientWithProfile(Object[] row) {
        return ClientWithProfileDTO.builder()
                .clientId(row[0] != null ? row[0].toString() : null)
                .firstName((String) row[1])
                .lastName((String) row[2])
                .clusterId(row[3] != null ? ((Number) row[3]).intValue() : null)
                .profileName((String) row[4])
                .profileFinal((String) row[5])
                .confidenceScore(row[6] != null ? ((Number) row[6]).doubleValue() : null)
                .churnScore30j(row[7] != null ? ((Number) row[7]).doubleValue() : null)
                .churnSegment((String) row[8])
                .build();
    }
}