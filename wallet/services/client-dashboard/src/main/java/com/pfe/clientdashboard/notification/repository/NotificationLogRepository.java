package com.pfe.clientdashboard.notification.repository;
import com.pfe.clientdashboard.notification.entities.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationLogRepository extends JpaRepository<NotificationLog, String> {
    Page<NotificationLog> findAllByOrderByDateEnvoiDesc(Pageable pageable);
    Page<NotificationLog> findByClientIdOrderByDateEnvoiDesc(String clientId, Pageable pageable);
}
