package com.pfe.clientdashboard.classification.controller;

import com.pfe.clientdashboard.classification.dtos.*;
import com.pfe.clientdashboard.classification.services.FastApiService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ClassificationController — Module binôme intégré dans clientdashboard.
 *
 * Préfixe : /api/v1/classification/*
 * Cible   : FastAPI port 8000 → router /v1/classification (classification_v9)
 *
 * AVANT (admin séparé) : appelait Flask port 5000
 * APRÈS (fusionné)     : appelle FastAPI port 8000 via ClassificationService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/classification")
@RequiredArgsConstructor
@Validated
public class ClassificationController {

    private final FastApiService classificationService;
    private final FastApiService fastApiService;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthDTO>> health() {
        return ResponseEntity.ok(ApiResponse.ok(classificationService.getHealth(), "Statut modèle V9"));
    }

    @PostMapping("/predict")
    public ResponseEntity<ApiResponse<PredictResponseDTO>> predict(@Valid @RequestBody PredictRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(classificationService.predictClient(req), "Prédiction effectuée"));
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<BatchPredictResponseDTO>> batch(@Valid @RequestBody BatchPredictRequestDTO req) {
        BatchPredictResponseDTO r = classificationService.batchPredict(req);
        return ResponseEntity.ok(ApiResponse.ok(r, "Batch traité : " + r.getCount() + " clients"));
    }

    @GetMapping("/profiles/summary")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> profilesSummary() {
        List<Map<String, Object>> s = classificationService.getProfilesSummary();
        return ResponseEntity.ok(ApiResponse.ok(s, s.size() + " profils"));
    }

    @GetMapping("/profiles/{profileId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> profileDetail(@PathVariable Integer profileId) {
        return ResponseEntity.ok(ApiResponse.ok(classificationService.getProfileDetail(profileId), "Détail profil " + profileId));
    }

    @GetMapping("/profiles/transactions-count")
    public ResponseEntity<ApiResponse<List<ProfileTransactionsCountDTO>>> transactionsCount() {
        return ResponseEntity.ok(ApiResponse.ok(classificationService.getTransactionsCountByProfile(), "Transactions par profil"));
    }

    @GetMapping("/profiles/{profileId}/categories")
    public ResponseEntity<ApiResponse<List<ProfileCategoryDTO>>> categoriesByProfile(@PathVariable Integer profileId) {
        return ResponseEntity.ok(ApiResponse.ok(classificationService.getCategoriesByProfile(profileId), "Catégories profil " + profileId));
    }

    @GetMapping("/clients/{clientId}")
    public ResponseEntity<ApiResponse<ClientProfileDTO>> clientProfile(@PathVariable String clientId) {
        ClientProfileDTO dto = classificationService.getClientProfile(clientId);
        if (dto == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Client non trouvé"));
        return ResponseEntity.ok(ApiResponse.ok(dto, "Profil client"));
    }

    @GetMapping("/clients/{clientId}/history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> clientHistory(@PathVariable String clientId) {
        return ResponseEntity.ok(ApiResponse.ok(classificationService.getClientHistory(clientId), "Historique migrations"));
    }

    @GetMapping("/clients/churn")
    public ResponseEntity<ApiResponse<List<ClientProfileDTO>>> highChurn(
            @RequestParam(defaultValue = "0.5") @DecimalMin("0.0") @DecimalMax("1.0") double threshold) {
        List<ClientProfileDTO> list = classificationService.getHighChurnClients(threshold);
        return ResponseEntity.ok(ApiResponse.ok(list, list.size() + " clients avec churn >= " + threshold));
    }

    @GetMapping("/kpi/summary")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> kpiSummary() {
        return ResponseEntity.ok(ApiResponse.ok(classificationService.getKpiSummary(), "KPIs par profil"));
    }

    @GetMapping("/drift/status")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> driftStatus() {
        return ResponseEntity.ok(ApiResponse.ok(classificationService.getDriftStatus(), "Statut drift"));
    }

    @GetMapping("/migrations/summary")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> migrationsSummary() {
        return ResponseEntity.ok(ApiResponse.ok(classificationService.getMigrationsSummary(), "Migrations résumées"));
    }

    @PostMapping("/admin/retrain")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerRetrain(@RequestBody Map<String, String> body) {
        String adminUser = body.getOrDefault("admin_user", "unknown");
        return ResponseEntity.accepted().body(ApiResponse.ok(classificationService.triggerRetrain(adminUser), "Réentraînement lancé"));
    }

    @GetMapping("/admin/retrain/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> retrainStatus() {
        return ResponseEntity.ok(ApiResponse.ok(classificationService.getRetrainStatus(), "Statut retrain"));
    }




    @GetMapping("/monitoring/alerts")
    public ResponseEntity<ApiResponse<List<MonitoringAlertDTO>>> monitoringAlerts() {
        return ResponseEntity.ok(ApiResponse.ok(classificationService.getMonitoringAlerts(), "Alertes monitoring"));
    }

    // ════════════════════════════════════════════════════════════════
    //  Pagination clients par CLUSTER / PROFIL (ajouté depuis Admin)
    // ════════════════════════════════════════════════════════════════
    //  Lit directement la table client_profiles_v9 (jointe avec client)
    //  via le repository — pas d'appel FastAPI ici.
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/clusters/{clusterId}/clients")
    public ResponseEntity<ApiResponse<Page<ClientWithProfileDTO>>> getClientsByCluster(
            @PathVariable Integer clusterId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ClientWithProfileDTO> clients = classificationService.getClientsByCluster(clusterId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(clients, "Clients du cluster " + clusterId));
    }

    @GetMapping("/profiles/{profileName}/clients")
    public ResponseEntity<ApiResponse<Page<ClientWithProfileDTO>>> getClientsByProfile(
            @PathVariable String profileName,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ClientWithProfileDTO> clients = classificationService.getClientsByProfile(profileName, pageable);
        return ResponseEntity.ok(ApiResponse.ok(clients, "Clients du profil " + profileName));
    }

}
