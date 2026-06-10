package com.pfe.clientdashboard.recommendation.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.pfe.clientdashboard.recommendation.config.PipelineClient;
import com.pfe.clientdashboard.recommendation.entities.GeneratedOffer;
import com.pfe.clientdashboard.recommendation.entities.Recommendation;
import com.pfe.clientdashboard.recommendation.repositories.GeneratedOfferRepository;
import com.pfe.clientdashboard.recommendation.repositories.RecommendationRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineServiceImpl implements PipelineService {

    private final PipelineClient pythonClient;
    private final GeneratedOfferRepository offerRepo;
    private final RecommendationRepository recoRepo;

    @Override
    public Map<String, Object> triggerPipeline() {
        log.info("Déclenchement pipeline complet V5");
        return pythonClient.post("/pipeline/run", null);
    }

    @Override
    public Map<String, Object> triggerOfferGeneration() {
        log.info("Déclenchement génération offres");
        return pythonClient.post("/offers/generate", null);
    }

    @Override
    public Map<String, Object> triggerRecommendationGeneration() {
        log.info("Déclenchement génération recommandations");
        return pythonClient.post("/recommendations/generate", null);
    }

//    @Override
//    public Map<String, Object> sendNotifications(String profileFilter) {
//        Map<String, Object> body = new HashMap<>();
//        if (profileFilter != null) body.put("profile_filter", profileFilter);
//        return pythonClient.post("/notifications/send", body);
//    }

    @Override
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("springApi", "OK");
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("activeOffers", offerRepo.countByStatus(GeneratedOffer.OfferStatus.ACTIVE));
        health.put("pendingRecos", recoRepo.countByStatus(
                Recommendation.RecommendationStatus.PENDING));
        try {
            Map<String, Object> pythonHealth = pythonClient.get("/health");
            health.put("pythonApi", pythonHealth);
        } catch (Exception e) {
            health.put("pythonApi", "UNREACHABLE: " + e.getMessage());
        }
        return health;
    }

    @Override
    public Map<String, Object> getGenerationRuns(int limit) {
        try {
            return pythonClient.get("/generation-runs?limit=" + limit);
        } catch (Exception e) {
            log.warn("Python API indisponible : {}", e.getMessage());
            return Map.of("error", "Python API indisponible", "runs", java.util.List.of());
        }
    }
}