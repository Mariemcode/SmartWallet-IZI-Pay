package com.pfe.clientdashboard.recommendation.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.pfe.clientdashboard.recommendation.entities.GeneratedOffer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GeneratedOfferRepository extends JpaRepository<GeneratedOffer, Long> {

    Optional<GeneratedOffer> findByOfferCode(String offerCode);

    boolean existsByOfferCode(String offerCode);

    Page<GeneratedOffer> findByStatus(GeneratedOffer.OfferStatus status, Pageable pageable);

    Page<GeneratedOffer> findByStatusAndType(
            GeneratedOffer.OfferStatus status, String type, Pageable pageable);

    @Query("""
        SELECT o FROM GeneratedOffer o
        WHERE (:status IS NULL OR o.status = :status)
          AND (:type IS NULL OR o.type = :type)
          AND (:provider IS NULL OR o.providerName = :provider)
          AND (:category IS NULL OR o.category = :category)
        ORDER BY o.updatedAt DESC
    """)
    Page<GeneratedOffer> findWithFilters(
            @Param("status") GeneratedOffer.OfferStatus status,
            @Param("type") String type,
            @Param("provider") String provider,
            @Param("category") String category,
            Pageable pageable);

    @Modifying
    @Query("UPDATE GeneratedOffer o SET o.status = :status WHERE o.offerCode = :code")
    int updateStatus(@Param("code") String offerCode,
                     @Param("status") GeneratedOffer.OfferStatus status);

    long countByStatus(GeneratedOffer.OfferStatus status);
    List<GeneratedOffer> findByOfferCodeIn(Collection<String> offerCodes);
}
