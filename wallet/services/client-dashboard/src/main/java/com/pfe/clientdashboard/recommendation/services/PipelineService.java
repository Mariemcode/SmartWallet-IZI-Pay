package com.pfe.clientdashboard.recommendation.services;

import java.util.Map;

public interface PipelineService {

    Map<String, Object> triggerPipeline();

    Map<String, Object> triggerOfferGeneration();

    Map<String, Object> triggerRecommendationGeneration();

//    Map<String, Object> sendNotifications(String profileFilter);

    Map<String, Object> getHealth();

    Map<String, Object> getGenerationRuns(int limit);
}
