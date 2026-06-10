package com.pfe.clientdashboard.recommendation.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.pfe.clientdashboard.recommendation.dtos.*;
import com.pfe.clientdashboard.recommendation.entities.Recommendation;

import java.util.List;
import java.util.Map;

public interface RecommendationService {

    Page<RecommendationResponseDTO> getRecommendations(
            Recommendation.RecommendationStatus status,
            String profile, Pageable pageable);

    RecommendationResponseDTO addManualRecommendation(
            ManualRecommendationDTO dto, String adminUser);

    RecommendationResponseDTO updateRecommendation(
            Long recoId, RecommendationStatusDTO dto, String adminUser);

    RecommendationResponseDTO approveRecommendation(
            Long recoId, String note, String adminUser);

    RecommendationResponseDTO rejectRecommendation(
            Long recoId, String note, String adminUser);

    Map<String, Object> bulkApprove(BulkApproveDTO dto, String adminUser);

    List<Map<String, Object>> getProfileStats();

    List<RecommendationResponseDTO> getRecommendationsForProfile(String profileName);

    long countPendingRecommendations();
    RecommendationResponseDTO getRecommendationById(Long id);
    Page<ClientProfileDTO> getClientsByProfile(String profileName, Pageable pageable);
    String generateDescription(String offerCode);

}
