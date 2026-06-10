package com.pfe.clientdashboard.notification.repository;

import com.pfe.clientdashboard.notification.entities.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FcmTokenRepository extends JpaRepository<FcmToken, String> {
    Optional<FcmToken> findByClientId(String clientId);
}
