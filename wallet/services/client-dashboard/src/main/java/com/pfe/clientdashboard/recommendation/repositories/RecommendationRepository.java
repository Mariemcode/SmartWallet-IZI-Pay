package com.pfe.clientdashboard.recommendation.repositories;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.pfe.clientdashboard.recommendation.entities.Recommendation;

import java.util.List;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    Page<Recommendation> findByStatus(
            Recommendation.RecommendationStatus status, Pageable pageable);

    Page<Recommendation> findByProfileNameAndStatus(
            String profileName,
            Recommendation.RecommendationStatus status,
            Pageable pageable);

    @Query("""
        SELECT r FROM Recommendation r
        WHERE (:status IS NULL OR r.status = :status)
          AND (:profile IS NULL OR r.profileName = :profile)
        ORDER BY r.profileName ASC, r.score DESC
    """)
    Page<Recommendation> findWithFilters(
            @Param("status") Recommendation.RecommendationStatus status,
            @Param("profile") String profile,
            Pageable pageable);

    List<Recommendation> findByProfileNameAndStatus(
            String profileName, Recommendation.RecommendationStatus status);

    @Modifying
    @Query("""
        UPDATE Recommendation r
        SET r.status = 'APPROVED', r.approvedAt = CURRENT_TIMESTAMP
        WHERE r.profileName = :profile AND r.status = 'PENDING'
    """)
    int bulkApproveByProfile(@Param("profile") String profileName);

    long countByStatus(Recommendation.RecommendationStatus status);

    @Query("""
        SELECT r.profileName, COUNT(r), AVG(r.score)
        FROM Recommendation r
        GROUP BY r.profileName
        ORDER BY COUNT(r) DESC
    """)
    List<Object[]> getProfileStats();


}
