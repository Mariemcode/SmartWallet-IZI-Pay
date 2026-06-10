package com.pfe.clientdashboard.classification.services;

import com.pfe.clientdashboard.classification.dtos.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FastApiService — Interface du module classification V9 (binôme).
 *
 * RENOMMÉ depuis FlaskApiService → FastApiService
 * L'implémentation appelle FastAPI http://localhost:8000
 * (PLUS de Flask port 5000 ni secret-key Flask)
 */
public interface FastApiService {
    HealthDTO getHealth();
    PredictResponseDTO predictClient(PredictRequestDTO request);
    BatchPredictResponseDTO batchPredict(BatchPredictRequestDTO request);
    List<Map<String, Object>> getProfilesSummary();
    Map<String, Object> getProfileDetail(Integer profileId);
    List<Map<String, Object>> getKpiSummary();
    List<Map<String, Object>> getDriftStatus();
    List<Map<String, Object>> getMigrationsSummary();
    Map<String, Object> triggerRetrain(String adminUser);
    Map<String, Object> getRetrainStatus();
    ClientProfileDTO getClientProfile(String clientId);
    Map<String, Object> getClientHistory(String clientId);
    List<ClientProfileDTO> getHighChurnClients(double threshold);
    List<ProfileTransactionsCountDTO> getTransactionsCountByProfile();
    List<ProfileCategoryDTO> getCategoriesByProfile(Integer profileId);
    List<MonitoringAlertDTO> getMonitoringAlerts();

    // ── Pagination clients par cluster / profil (ajouté depuis Admin) ──
    Page<ClientWithProfileDTO> getClientsByCluster(Integer clusterId, Pageable pageable);
    Page<ClientWithProfileDTO> getClientsByProfile(String profileName, Pageable pageable);
}
