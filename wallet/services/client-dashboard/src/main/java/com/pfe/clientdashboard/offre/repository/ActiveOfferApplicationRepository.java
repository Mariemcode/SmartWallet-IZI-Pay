package com.pfe.clientdashboard.offre.repository;

import com.pfe.clientdashboard.offre.ActiveOfferApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActiveOfferApplicationRepository
        extends JpaRepository<ActiveOfferApplication, Long> {

    /** Idempotence : empêche d'appliquer 2× la même offre au même client sur le même mois. */
    Optional<ActiveOfferApplication> findByIdempotencyKey(String idempotencyKey);

    /** Trouve les applications actives pour un client à une date donnée. */
    @Query("""
        SELECT a FROM ActiveOfferApplication a
         WHERE a.clientId = :clientId
           AND a.status = com.pfe.clientdashboard.offre.ActiveOfferApplication.Status.ACTIVE
           AND (a.startsAt IS NULL OR a.startsAt <= :now)
           AND (a.endsAt   IS NULL OR a.endsAt   >  :now)
    """)
    List<ActiveOfferApplication> findActiveForClient(@Param("clientId") String clientId,
                                                     @Param("now") LocalDateTime now);

    /** Filtre par type d'effet — utilisé par TransactionController pour FEE_WAIVER. */
    @Query("""
        SELECT a FROM ActiveOfferApplication a
         WHERE a.clientId    = :clientId
           AND a.effectType  = :effectType
           AND a.status      = com.pfe.clientdashboard.offre.ActiveOfferApplication.Status.ACTIVE
           AND (a.startsAt IS NULL OR a.startsAt <= :now)
           AND (a.endsAt   IS NULL OR a.endsAt   >  :now)
    """)
    List<ActiveOfferApplication> findActiveForClientByType(
            @Param("clientId") String clientId,
            @Param("effectType") ActiveOfferApplication.EffectType effectType,
            @Param("now") LocalDateTime now);

    /** Toutes les applications d'un client, historique compris (admin / debug). */
    List<ActiveOfferApplication> findByClientIdOrderByCreatedAtDesc(String clientId);

    /** Sweeper périodique : marquer EXPIRED ce qui a dépassé endsAt. */
    @Query("""
        SELECT a FROM ActiveOfferApplication a
         WHERE a.status   = com.pfe.clientdashboard.offre.ActiveOfferApplication.Status.ACTIVE
           AND a.endsAt  <= :now
    """)
    List<ActiveOfferApplication> findExpiredButStillActive(@Param("now") LocalDateTime now);
}