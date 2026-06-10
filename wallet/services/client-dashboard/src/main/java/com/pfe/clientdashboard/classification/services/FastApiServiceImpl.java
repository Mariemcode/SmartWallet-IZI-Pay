package com.pfe.clientdashboard.classification.services;

import com.pfe.clientdashboard.classification.dtos.*;
import com.pfe.clientdashboard.classification.entities.ClientProfileEntity;
import com.pfe.clientdashboard.classification.entities.PredictionLogEntity;
import com.pfe.clientdashboard.classification.repositories.ClientProfileRepository;
import com.pfe.clientdashboard.classification.repositories.PredictionLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FastApiServiceImpl — Implémentation du module classification V9.
 *
 * AVANT (admin séparé) :
 *   - flask.api.base-url = http://localhost:5000
 *   - flask.api.secret-key = ...  (Bearer token)
 *   - URLs : /predict, /batch, /profiles/summary ...
 *
 * APRÈS (fusionné) :
 *   - fastapi.base-url = http://localhost:8000
 *   - Pas de secret-key (le retrain a son propre header)
 *   - URLs : /v1/classification/predict, /v1/classification/batch ...
 *     (router classification_v9 dans main.py)
 */
@Service
@Slf4j
public class FastApiServiceImpl implements FastApiService {

    /** Propriété unique pour tout le backend Python — FastAPI port 8000 */
    @Value("${fastapi.base-url:http://127.0.0.1:8000}")
    private String fastApiBaseUrl;

    private final RestTemplate restTemplate;
    private final ClientProfileRepository clientProfileRepository;
    private final PredictionLogRepository predictionLogRepository;

    public FastApiServiceImpl(RestTemplate restTemplate,
                               ClientProfileRepository clientProfileRepository,
                               PredictionLogRepository predictionLogRepository) {
        this.restTemplate = restTemplate;
        this.clientProfileRepository = clientProfileRepository;
        this.predictionLogRepository = predictionLogRepository;
    }

    /** Préfixe du router classification dans main.py */
    private String url(String path) {
        return fastApiBaseUrl + "/api/v1/classification" + path;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Override
    public HealthDTO getHealth() {
        try {
            return restTemplate.getForObject(url("/health"), HealthDTO.class);
        } catch (Exception e) {
            log.error("Erreur /api/v1/classification/health : {}", e.getMessage());
            return HealthDTO.builder().status("unreachable").loaded(false).build();
        }
    }

    @Override
    public PredictResponseDTO predictClient(PredictRequestDTO request) {
        HttpEntity<PredictRequestDTO> entity = new HttpEntity<>(request, jsonHeaders());
        try {
            ResponseEntity<PredictResponseDTO> response = restTemplate.exchange(
                    url("/predict"), HttpMethod.POST, entity, PredictResponseDTO.class);
            PredictResponseDTO result = response.getBody();
            savePredictionLog(request, result, "SUCCESS", null);
            syncClientProfile(result);
            return result;
        } catch (HttpClientErrorException e) {
            log.error("Erreur /api/v1/classification/predict : {}", e.getResponseBodyAsString());
            savePredictionLog(request, null, "ERROR", e.getMessage());
            throw new RuntimeException("Erreur prédiction: " + e.getMessage());
        }
    }

    @Override
    public BatchPredictResponseDTO batchPredict(BatchPredictRequestDTO request) {
        HttpEntity<BatchPredictRequestDTO> entity = new HttpEntity<>(request, jsonHeaders());
        try {
            ResponseEntity<BatchPredictResponseDTO> response = restTemplate.exchange(
                    url("/batch"), HttpMethod.POST, entity, BatchPredictResponseDTO.class);
            BatchPredictResponseDTO batchResult = response.getBody();
            if (batchResult != null && batchResult.getResults() != null) {
                batchResult.getResults().forEach(item -> {
                    PredictResponseDTO fakeResp = PredictResponseDTO.builder()
                            .clientId(item.getClientId())
                            .clusterId(item.getClusterId())
                            .profileName(item.getProfileName())
                            .confidence(item.getConfidence())
                            .isMixte(item.getIsMixte())
                            .churnScore30j(item.getChurnScore30j())
                            .churnSegment(item.getChurnSegment())
                            .ltv12mBase(item.getLtv12mBase())
                            .build();
                    syncClientProfile(fakeResp);
                });
            }
            return batchResult;
        } catch (Exception e) {
            log.error("Erreur batch predict", e);
            throw new RuntimeException("Erreur batch: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getProfilesSummary() {
        try {
            // FastAPI retourne UN TABLEAU DIRECT, pas {"data": [...]}
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url("/profiles/summary"),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.error("Erreur /api/v1/classification/profiles/summary : {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public Map<String, Object> getProfileDetail(Integer profileId) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url("/profiles/" + profileId),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception e) {
            log.error("Erreur /api/v1/classification/profiles/{} : {}", profileId, e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getKpiSummary() {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url("/kpi/summary"),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.error("Erreur /api/v1/classification/kpi/summary : {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> getDriftStatus() {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url("/drift/status"),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.error("Erreur /api/v1/classification/drift/status : {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> getMigrationsSummary() {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url("/migrations/summary"),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.error("Erreur /api/v1/classification/migrations/summary : {}", e.getMessage());
            return List.of();
        }
    }
    @Override
    public Map<String, Object> triggerRetrain(String adminUser) {
        // Le retrain est géré par le router principal de FastAPI, pas /v1/classification
        String url = fastApiBaseUrl + "/admin/retrain";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Retrain-Secret",
                System.getenv().getOrDefault("RETRAIN_SECRET", "smartwallet-retrain-2026"));
        Map<String, String> body = Map.of("admin_user", adminUser, "trigger", "admin");
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, h);
        try {
            ResponseEntity<Map<String, Object>> r = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    (Class<Map<String, Object>>) (Class<?>) Map.class);
            return r.getBody() != null ? r.getBody() : Map.of("status", "sent");
        } catch (Exception e) {
            log.error("Erreur déclenchement retrain : {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getRetrainStatus() {
        try {
            return restTemplate.getForObject(fastApiBaseUrl + "/admin/retrain/status",
                    (Class<Map<String, Object>>) (Class<?>) Map.class);
        } catch (Exception e) {
            log.error("Erreur /retrain/status : {}", e.getMessage());
            return Map.of("status", "unreachable", "error", e.getMessage());
        }
    }

    @Override
    public ClientProfileDTO getClientProfile(String clientId) {
        Optional<ClientProfileEntity> opt = clientProfileRepository.findByClientId(clientId);
        if (opt.isPresent()) return mapToDto(opt.get());
        try {
            return restTemplate.getForObject(url("/clients/" + clientId), ClientProfileDTO.class);
        } catch (Exception e) {
            log.error("Client {} non trouvé : {}", clientId, e.getMessage());
            return null;
        }
    }

    @Override
    public Map<String, Object> getClientHistory(String clientId) {
        try {
            return restTemplate.getForObject(url("/clients/" + clientId + "/history"),
                    (Class<Map<String, Object>>) (Class<?>) Map.class);
        } catch (Exception e) {
            log.error("Erreur historique client {} : {}", clientId, e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    @Override
    public List<ClientProfileDTO> getHighChurnClients(double threshold) {
        List<ClientProfileEntity> entities =
                clientProfileRepository.findByChurnScore30jGreaterThanEqual(threshold);
        return entities.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Override
    public List<ProfileTransactionsCountDTO> getTransactionsCountByProfile() {
        List<Object[]> results = clientProfileRepository.getTransactionsCountByProfile();
        List<ProfileTransactionsCountDTO> dtos = new ArrayList<>();
        for (Object[] row : results) {
            dtos.add(ProfileTransactionsCountDTO.builder()
                    .clusterId(((Number) row[0]).intValue())
                    .totalTransactions(((Number) row[1]).longValue())
                    .avgTransactions(((Number) row[2]).doubleValue())
                    .build());
        }
        return dtos;
    }

    @Override
    public List<ProfileCategoryDTO> getCategoriesByProfile(Integer profileId) {
        List<Object[]> results = clientProfileRepository.getCategoriesByProfileId(profileId);
        List<ProfileCategoryDTO> dtos = new ArrayList<>();
        for (Object[] row : results) {
            dtos.add(ProfileCategoryDTO.builder()
                    .category((String) row[0])
                    .nbTransactions(((Number) row[1]).longValue())
                    .pct(((Number) row[2]).doubleValue())
                    .build());
        }
        return dtos;
    }

    @Override
    public List<MonitoringAlertDTO> getMonitoringAlerts() {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url("/monitoring/alerts"),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            List<Map<String, Object>> data = response.getBody();
            if (data != null) {
                return data.stream().map(map -> MonitoringAlertDTO.builder()
                        .alertType(map.get("alert_type") != null ? map.get("alert_type").toString() : null)
                        .featureName(map.get("feature_name") != null ? map.get("feature_name").toString() : null)
                        .psiValue(map.get("psi_value") != null ? ((Number) map.get("psi_value")).doubleValue() : null)
                        .ksPvalue(map.get("ks_pvalue") != null ? ((Number) map.get("ks_pvalue")).doubleValue() : null)
                        .severity(map.get("severity") != null ? map.get("severity").toString() : null)
                        .message(map.get("message") != null ? map.get("message").toString() : null)
                        .modelVersion(map.get("model_version") != null ? map.get("model_version").toString() : null)
                        .triggeredAt(map.get("triggered_at") != null ? map.get("triggered_at").toString() : null)
                        .build()).collect(Collectors.toList());
            }
            return List.of();
        } catch (Exception e) {
            log.error("Erreur /v1/classification/monitoring/alerts : {}", e.getMessage());
            return List.of();
        }
    }

    // ── Helpers privés ────────────────────────────────────────────────────

    private void savePredictionLog(PredictRequestDTO req, PredictResponseDTO res,
                                    String status, String errorMsg) {
        try {
            PredictionLogEntity log = PredictionLogEntity.builder()
                    .clientId(req.getClientId())
                    .clusterId(res != null ? res.getClusterId() : null)
                    .profileName(res != null ? res.getProfileName() : null)
                    .confidence(res != null ? res.getConfidence() : null)
                    .churnScore30j(res != null ? res.getChurnScore30j() : null)
                    .churnSegment(res != null ? res.getChurnSegment() : null)
                    .ltv12mBase(res != null ? res.getLtv12mBase() : null)
                    .modelVersion(res != null ? res.getModelVersion() : null)
                    .featuresJson(req.getFeatures() != null
                            ? req.getFeatures().toString() : null)
                    .status(status)
                    .errorMessage(errorMsg)
                    .predictedAt(LocalDateTime.now())
                    .build();
            predictionLogRepository.save(log);
        } catch (Exception e) {
            // Log non bloquant
        }
    }

    private void syncClientProfile(PredictResponseDTO dto) {
        if (dto == null || dto.getClientId() == null) return;
        ClientProfileEntity entity = clientProfileRepository
                .findByClientId(dto.getClientId())
                .orElse(ClientProfileEntity.builder().clientId(dto.getClientId()).build());
        entity.setClusterId(dto.getClusterId());
        entity.setProfileName(dto.getProfileName());
        entity.setProfileFinal(dto.getProfileFinal());
        entity.setIsMixte(dto.getIsMixte());
        entity.setConfidenceScore(dto.getConfidence());
        entity.setGbmConfidence(dto.getConfidence());
        entity.setModelVersion(dto.getModelVersion());
        entity.setChurnScore30j(dto.getChurnScore30j());
        entity.setChurnSegment(dto.getChurnSegment());
        entity.setLtv12m(dto.getLtv12mBase());
        entity.setLtv12mOptimiste(dto.getLtv12mOptimiste());
        entity.setLtv12mPessimiste(dto.getLtv12mPessimiste());
        entity.setHazardRate(dto.getHazardRate());
        entity.setArpuMensuel(dto.getArpuMensuel());
        entity.setAssignedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        clientProfileRepository.save(entity);
    }

    // ════════════════════════════════════════════════════════════════
    //  Pagination clients par CLUSTER / PROFIL (ajouté depuis Admin)
    // ════════════════════════════════════════════════════════════════
    //  Délégation directe au repository — pas d'appel FastAPI ici, on
    //  lit la table client_profiles_v9 maintenue par le sync de
    //  predictClient(). C'est plus rapide et plus cohérent (la source
    //  de vérité côté Java reste la DB).
    // ════════════════════════════════════════════════════════════════

    @Override
    public Page<ClientWithProfileDTO> getClientsByCluster(Integer clusterId, Pageable pageable) {
        return clientProfileRepository.findClientsByClusterId(clusterId, pageable);
    }

    @Override
    public Page<ClientWithProfileDTO> getClientsByProfile(String profileName, Pageable pageable) {
        return clientProfileRepository.findClientsByProfileFinal(profileName, pageable);
    }

    private ClientProfileDTO mapToDto(ClientProfileEntity e) {
        return ClientProfileDTO.builder()
                .clientId(e.getClientId())
                .clusterId(e.getClusterId())
                .profileName(e.getProfileName())
                .profileFinal(e.getProfileFinal())
                .isMixte(e.getIsMixte())
                .confidenceScore(e.getConfidenceScore())
                .gbmConfidence(e.getGbmConfidence())
                .modelVersion(e.getModelVersion())
                .churnScore30j(e.getChurnScore30j())
                .churnSegment(e.getChurnSegment())
                .ltv12mBase(e.getLtv12m())
                .ltv12mOptimiste(e.getLtv12mOptimiste())
                .ltv12mPessimiste(e.getLtv12mPessimiste())
                .hazardRate(e.getHazardRate())
                .arpuMensuel(e.getArpuMensuel())
                .assignedAt(e.getAssignedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
