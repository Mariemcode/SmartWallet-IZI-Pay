package com.pfe.clientdashboard.recommendation.services;

import com.pfe.clientdashboard.offre.service.OfferApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pfe.clientdashboard.recommendation.dtos.*;
import com.pfe.clientdashboard.recommendation.entities.ClientRecommendation;
import com.pfe.clientdashboard.recommendation.entities.UserInteraction;
import com.pfe.clientdashboard.recommendation.repositories.ClientRecommendationRepository;
import com.pfe.clientdashboard.recommendation.repositories.GeneratedOfferRepository;
import com.pfe.clientdashboard.recommendation.repositories.UserInteractionRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InteractionServiceImpl implements InteractionService {

    private final UserInteractionRepository interactionRepo;
    private final ClientRecommendationRepository clientRecoRepo;
    private final GeneratedOfferRepository offerRepo;
    private final OfferApplicationService offerApplicationService;

    @Override
    @Transactional
    public void recordInteraction(InteractionDTO dto) {
        UserInteraction interaction = UserInteraction.builder()
                .clientId(dto.getClientId())
                .offerCode(dto.getOfferCode())
                .action(UserInteraction.InteractionAction.valueOf(dto.getAction()))
                .build();
        interactionRepo.save(interaction);

        if ("accepted".equals(dto.getAction()) || "rejected".equals(dto.getAction())) {
            clientRecoRepo.findActiveForClient(dto.getClientId()).stream()
                    .filter(cr -> cr.getOfferCode().equals(dto.getOfferCode()))
                    .forEach(cr -> {
                        if ("accepted".equals(dto.getAction())) {
                            cr.setStatus(ClientRecommendation.ClientRecoStatus.ACCEPTED);
                            cr.setAcceptedAt(LocalDateTime.now());
                        } else {
                            cr.setStatus(ClientRecommendation.ClientRecoStatus.REJECTED);
                            cr.setRejectedAt(LocalDateTime.now());
                        }
                        clientRecoRepo.save(cr);

                        if ("accepted".equals(dto.getAction())) {
                            try {
                                offerApplicationService.apply(cr).ifPresent(aoa ->
                                        log.info("🎯 Effet appliqué via Interaction : {} ({}) jusqu'au {}",
                                                aoa.getEffectType(), aoa.getOfferCode(), aoa.getEndsAt())
                                );
                            } catch (Exception e) {
                                log.error("❌ Application via Interaction échouée : {}", e.getMessage(), e);
                            }
                        }
                    });
        }
        log.debug("Interaction enregistrée : client={} offre={} action={}",
                dto.getClientId(), dto.getOfferCode(), dto.getAction());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getClientInteractions(String clientId) {
        return interactionRepo.findByClientIdOrderByRecordedAtDesc(clientId).stream()
                .map(i -> Map.<String, Object>of(
                        "id",         i.getId(),
                        "offerCode",  i.getOfferCode(),
                        "action",     i.getAction().name(),
                        "recordedAt", i.getRecordedAt().toString()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getOfferStats(String offerCode) {
        Map<String, Long> stats = new HashMap<>();
        interactionRepo.countByActionForOffer(offerCode)
                .forEach(row -> stats.put(row[0].toString(), (Long) row[1]));
        return stats;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getClientActiveRecommendations(String clientId) {
        return clientRecoRepo.findActiveForClient(clientId).stream()
                .map(cr -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("offerCode",    cr.getOfferCode());
                    m.put("profileName",  cr.getProfileName());
                    m.put("personalScore", cr.getPersonalScore());
                    m.put("status",       cr.getStatus().name());
                    m.put("sentAt",       cr.getSentAt() != null ? cr.getSentAt().toString() : null);
                    offerRepo.findByOfferCode(cr.getOfferCode()).ifPresent(o -> {
                        m.put("offerTitle",    o.getTitle());
                        m.put("cashbackPct",   o.getCashbackPct());
                        m.put("discountPct",   o.getDiscountPct());
                        m.put("description",   o.getDescription());
                    });
                    return m;
                })
                .collect(Collectors.toList());
    }

    /** ★ NOUVEAU — feed admin "qui a fait quoi" (paginé). */
    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRecentInteractions(int limit) {
        return interactionRepo.findRecent(PageRequest.of(0, Math.min(limit, 500))).stream()
                .map(i -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", i.getId());
                    m.put("clientId", i.getClientId());
                    m.put("offerCode", i.getOfferCode());
                    m.put("action", i.getAction().name());
                    m.put("recordedAt", i.getRecordedAt() != null ? i.getRecordedAt().toString() : null);
                    return m;
                })
                .collect(Collectors.toList());
    }
}
