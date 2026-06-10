package com.pfe.clientdashboard.recommendation.services;

import com.pfe.clientdashboard.recommendation.dtos.InteractionDTO;

import java.util.List;
import java.util.Map;

/** ★ FIX clientId : String partout. */
public interface InteractionService {

    void recordInteraction(InteractionDTO dto);

    List<Map<String, Object>> getClientInteractions(String clientId);

    Map<String, Long> getOfferStats(String offerCode);

    List<Map<String, Object>> getClientActiveRecommendations(String clientId);

    /** ★ NOUVEAU — liste paginée pour la vue admin "qui a fait quoi". */
    List<Map<String, Object>> listRecentInteractions(int limit);
}
