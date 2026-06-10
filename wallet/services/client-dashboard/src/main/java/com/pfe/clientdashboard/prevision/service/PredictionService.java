package com.pfe.clientdashboard.prevision.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.clientdashboard.config.FastApiClient;
import com.pfe.clientdashboard.client.entities.Client;
import com.pfe.clientdashboard.notification.entities.FcmToken;
import com.pfe.clientdashboard.notification.service.FcmService;
import com.pfe.clientdashboard.client.repository.ClientRepository;
import com.pfe.clientdashboard.notification.repository.FcmTokenRepository;
import com.pfe.clientdashboard.client.repository.TransactionRepository;
import com.pfe.clientdashboard.prevision.dtos.PredictionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionService {

    private final FastApiClient fastApiClient;
    private final ClientRepository clientRepository;
    private final TransactionRepository transactionRepository;
    private final PredictionAuditLogService auditLogService;
    private final FcmService fcmService;
    private final FcmTokenRepository fcmTokenRepository;

    public PredictionResponse getFullPrediction(String clientId) {
        log.info("🔮 Prédiction pour client={}", clientId);

        // 1. Enrichissement depuis PostgreSQL
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client introuvable : " + clientId));
        BigDecimal solde = transactionRepository.calculateBalance(clientId);
        if (solde == null) solde = BigDecimal.ZERO;
        String nomClient = buildNom(client);

        // 2. Appel FastAPI (Source de Vérité)
        Object rawResponse;
        try {
            rawResponse = fastApiClient.getAllRaw(clientId, solde.setScale(2, RoundingMode.HALF_UP).doubleValue());
        } catch (Exception e) {
            log.error("FastAPI indisponible : {}", e.getMessage());
            // Retourner une réponse minimale pour ne pas bloquer l'UI
            return buildMinimalResponse(clientId, nomClient, solde.doubleValue(), e.getMessage());
        }

        // 3. Parse SANS MODIFICATION MÉTIER
        PredictionResponse response = parseRawResponse(rawResponse, clientId);
        if (response == null) {
            return buildMinimalResponse(clientId, nomClient, solde.doubleValue(), "Erreur de parsing");
        }

        // 4. Enrichissement Spring Boot (Données BDD uniquement)
        response.setClientId(clientId);
        response.setClientNom(nomClient);
        response.setSoldeActuelTnd(solde.doubleValue());
        response.setGeneratedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // 5. Audit Asynchrone (Traçabilité)
        logPredictionAudit(response, clientId);

        // 6. Notifications Push
        triggerNotificationsIfNeeded(response, clientId);

        return response;
    }

    private PredictionResponse parseRawResponse(Object rawResponse, String clientId) {
        if (rawResponse == null) return null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(rawResponse);
            return mapper.readValue(json, PredictionResponse.class);
        } catch (Exception e) {
            log.error("Erreur parsing FastAPI : {}", e.getMessage());
            return null;
        }
    }

    private PredictionResponse buildMinimalResponse(String clientId, String nom, Double solde, String error) {
        PredictionResponse res = new PredictionResponse();
        res.setClientId(clientId);
        res.setClientNom(nom);
        res.setSoldeActuelTnd(solde);
        res.setSegment("Inconnu");
        res.setGeneratedAt(LocalDateTime.now().toString());
        res.setModule5Alerte(new PredictionResponse.Module5Alerte());
        res.getModule5Alerte().setMessage("Service IA temporairement indisponible. Réessayez plus tard.");
        res.getModule5Alerte().setNiveauAlerte("ATTENTION");
        return res;
    }

    private void logPredictionAudit(PredictionResponse response, String clientId) {
        if (response.getModule1Factures() == null || response.getModule1Factures().getFactures() == null) return;
        CompletableFuture.runAsync(() -> {
            response.getModule1Factures().getFactures().forEach(f ->
                    auditLogService.saveSnapshot(clientId, f.getLabel(), f.getMontantPrevu(), f.getDatePrevue())
            );
        });
    }

    private void triggerNotificationsIfNeeded(PredictionResponse response, String clientId) {
        try {
            String fcmToken = fcmTokenRepository.findByClientId(clientId).map(FcmToken::getToken).orElse(null);
            if (fcmToken == null) return;

            if (response.getModule5Alerte() != null) {
                String niveau = response.getModule5Alerte().getNiveauAlerte();
                String message = response.getModule5Alerte().getMessage();
                if (("CRITIQUE".equals(niveau) || "ATTENTION".equals(niveau)) && message != null) {
                    fcmService.sendAlerteClient(fcmToken, niveau, message, clientId);
                    log.info("📱 Notification {} → client {}", niveau, clientId);
                }
            }
        } catch (Exception e) {
            log.debug("Notification non envoyée : {}", e.getMessage());
        }
    }

    private String buildNom(Client client) {
        String full = (client.getFirstName() + " " + client.getLastName()).trim();
        return full.isEmpty() ? "Client " + client.getId().substring(0, 8) : full;
    }

    public Double getSolde(String clientId) {
        return transactionRepository.calculateBalance(clientId).doubleValue();
    }
}