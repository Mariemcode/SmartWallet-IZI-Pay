package com.pfe.clientdashboard.recommendation.services;


import com.pfe.clientdashboard.classification.entities.ClientProfileEntity;
import com.pfe.clientdashboard.classification.repositories.ClientProfileRepository;
import com.pfe.clientdashboard.exception.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pfe.clientdashboard.recommendation.config.RecommendationMapper;
import com.pfe.clientdashboard.recommendation.dtos.*;
import com.pfe.clientdashboard.recommendation.entities.GeneratedOffer;
import com.pfe.clientdashboard.recommendation.entities.Recommendation;
import com.pfe.clientdashboard.exception.BadRequestException;
import com.pfe.clientdashboard.recommendation.repositories.GeneratedOfferRepository;
import com.pfe.clientdashboard.recommendation.repositories.RecommendationRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationServiceImpl implements RecommendationService {

    private final RecommendationRepository recoRepo;
    private final GeneratedOfferRepository offerRepo;
    private final RecommendationMapper recoMapper;
    private final ClientProfileRepository clientProfileRepository;
    private final DescriptionGeneratorService descriptionGenerator;



    @Override
    @Transactional(readOnly = true)
    public Page<RecommendationResponseDTO> getRecommendations(
            Recommendation.RecommendationStatus status,
            String profile, Pageable pageable) {

        return recoRepo.findWithFilters(status, profile, pageable)
                .map(recoMapper::toDto);
    }

    @Override
    @Transactional
    public RecommendationResponseDTO addManualRecommendation(
            ManualRecommendationDTO dto, String adminUser) {

        GeneratedOffer offer = offerRepo.findByOfferCode(dto.getOfferCode())
                .filter(o -> o.getStatus() == GeneratedOffer.OfferStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Offre '" + dto.getOfferCode() + "' introuvable ou inactive"));

        Recommendation reco = Recommendation.builder()
                .profileName(dto.getProfileName())
                .offerCode(dto.getOfferCode())
                .score(new java.math.BigDecimal(dto.getScore().toString()))
                .scoreProfile(java.math.BigDecimal.ONE)
                .scoreProvider(java.math.BigDecimal.ZERO)
                .scoreCategory(java.math.BigDecimal.ZERO)
                .scoreAmount(java.math.BigDecimal.ZERO)
                .scoreLoyalty(java.math.BigDecimal.ZERO)
                .scoreChurnBoost(java.math.BigDecimal.ZERO)
                .isTargeted(true)
                .recommendationType("manual_override")
                .status(Recommendation.RecommendationStatus.PENDING)
                .description(dto.getDescription())      // ← sauvegarde la description
                .approvedAt(null)
                .adminNote(dto.getNote())
                .modelVersion("V5.0")
                .build();

        log.info("Ajout recommandation manuelle (PENDING) : profil={}, offre={}", dto.getProfileName(), dto.getOfferCode());
        return recoMapper.toDto(recoRepo.save(reco));
    }

    @Override
    @Transactional
    public RecommendationResponseDTO updateRecommendation(
            Long recoId, RecommendationStatusDTO dto, String adminUser) {

        Recommendation reco = getOrThrow(recoId);
        Recommendation.RecommendationStatus newStatus =
                Recommendation.RecommendationStatus.valueOf(dto.getStatus());

        reco.setStatus(newStatus);
        reco.setAdminNote(dto.getNote());
        if (newStatus == Recommendation.RecommendationStatus.APPROVED)
            reco.setApprovedAt(LocalDateTime.now());
        else if (newStatus == Recommendation.RecommendationStatus.REJECTED)
            reco.setRejectedAt(LocalDateTime.now());

        return recoMapper.toDto(recoRepo.save(reco));
    }

    @Override
    @Transactional
    public RecommendationResponseDTO approveRecommendation(
            Long recoId, String note, String adminUser) {
        Recommendation reco = getOrThrow(recoId);
        if (reco.getStatus() == Recommendation.RecommendationStatus.APPROVED)
            throw new BadRequestException("Recommandation déjà approuvée");
        reco.setStatus(Recommendation.RecommendationStatus.APPROVED);
        reco.setApprovedAt(LocalDateTime.now());
        if (note != null) reco.setAdminNote(note);
        log.info("Approbation recommandation #{}", recoId);
        return recoMapper.toDto(recoRepo.save(reco));
    }

    @Override
    @Transactional
    public RecommendationResponseDTO rejectRecommendation(
            Long recoId, String note, String adminUser) {
        Recommendation reco = getOrThrow(recoId);
        if (reco.getStatus() == Recommendation.RecommendationStatus.REJECTED)
            throw new BadRequestException("Recommandation déjà rejetée");
        reco.setStatus(Recommendation.RecommendationStatus.REJECTED);
        reco.setRejectedAt(LocalDateTime.now());
        if (note != null) reco.setAdminNote(note);
        return recoMapper.toDto(recoRepo.save(reco));
    }

    @Override
    @Transactional
    public Map<String, Object> bulkApprove(BulkApproveDTO dto, String adminUser) {
        int count = recoRepo.bulkApproveByProfile(dto.getProfileName());
        log.info("Bulk approve profil={} → {} recommandations", dto.getProfileName(), count);
        return Map.of("success", true, "approved", count, "profile", dto.getProfileName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getProfileStats() {
        return recoRepo.getProfileStats().stream()
                .map(row -> Map.of(
                        "profileName",        row[0],
                        "totalRecommendations", row[1],
                        "avgScore",           row[2]))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecommendationResponseDTO> getRecommendationsForProfile(String profileName) {
        List<Recommendation> recommendations = recoRepo.findByProfileNameAndStatus(
                profileName, Recommendation.RecommendationStatus.APPROVED);

        List<RecommendationResponseDTO> dtos = recommendations.stream()
                .map(recoMapper::toDto)
                .collect(Collectors.toList());

        Set<String> offerCodes = dtos.stream()
                .map(RecommendationResponseDTO::getOfferCode)
                .collect(Collectors.toSet());

        Map<String, GeneratedOffer> offerMap = offerRepo.findByOfferCodeIn(offerCodes)
                .stream()
                .collect(Collectors.toMap(GeneratedOffer::getOfferCode, Function.identity()));

        dtos.forEach(dto -> {
            GeneratedOffer offer = offerMap.get(dto.getOfferCode());
            recoMapper.enrichWithOffer(dto, offer);
        });

        return dtos;
    }

    @Override
    public long countPendingRecommendations() {
        return recoRepo.countByStatus(Recommendation.RecommendationStatus.PENDING);
    }

    @Override
    public RecommendationResponseDTO getRecommendationById(Long id) {
        Recommendation reco = recoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Recommandation non trouvée avec l'id : " + id));

        GeneratedOffer offer = offerRepo.findByOfferCode(reco.getOfferCode())
                .orElseThrow(() -> new EntityNotFoundException("Offre associée non trouvée"));

        RecommendationResponseDTO dto = recoMapper.toDto(reco);
        recoMapper.enrichWithOffer(dto, offer);
        return dto;
    }

    @Override
    public Page<ClientProfileDTO> getClientsByProfile(String profileName, Pageable pageable) {
        return clientProfileRepository.findDTOByProfileFinal(profileName, pageable);
    }

    @Override
    public String generateDescription(String offerCode) {
        if (offerCode == null || offerCode.isBlank()) {
            throw new BadRequestException("Le champ 'offerCode' est requis");
        }
        log.info("Génération de description pour l'offre {}", offerCode);
        return descriptionGenerator.generateDescription(offerCode);
    }

    private ClientProfileDTO toDTO(ClientProfileEntity cp) {
        return ClientProfileDTO.builder()
                .clientId(cp.getClientId())
                .profileName(cp.getProfileFinal())
                .confidenceScore(cp.getConfidenceScore())
                .churnScore30j(cp.getChurnScore30j())
                .build();
    }

    private Recommendation getOrThrow(Long id) {
        return recoRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Recommandation #" + id + " introuvable"));
    }
}