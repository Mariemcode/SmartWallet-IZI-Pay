package com.pfe.clientdashboard.recommendation.services;

import com.pfe.clientdashboard.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pfe.clientdashboard.recommendation.config.OfferMapper;
import com.pfe.clientdashboard.recommendation.dtos.*;
import com.pfe.clientdashboard.recommendation.entities.GeneratedOffer;
import com.pfe.clientdashboard.recommendation.repositories.GeneratedOfferRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfferServiceImpl implements OfferService {

    private final GeneratedOfferRepository offerRepo;
    private final OfferMapper offerMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<OfferResponseDTO> getOffers(
            GeneratedOffer.OfferStatus status, String type,
            String provider, String category, Pageable pageable) {

        return offerRepo.findWithFilters(status, type, provider, category, pageable)
                .map(offerMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public OfferResponseDTO getOfferByCode(String offerCode) {
        return offerRepo.findByOfferCode(offerCode)
                .map(offerMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Offre '" + offerCode + "' introuvable"));
    }

    @Override
    @Transactional
    public OfferResponseDTO createOffer(OfferRequestDTO dto) {
        GeneratedOffer offer = offerMapper.toEntity(dto);
        offer.setGenerationMethod("manual");
        offer.setStatus(GeneratedOffer.OfferStatus.ACTIVE);
        // Génère un code unique
        String code = "MAN_" + Integer.toHexString(
                (dto.getTitle() + dto.getType()).hashCode()).toUpperCase();
        offer.setOfferCode(code);
        log.info("Création offre manuelle : {}", code);
        return offerMapper.toDto(offerRepo.save(offer));
    }

    @Override
    @Transactional
    public OfferResponseDTO updateOffer(String offerCode, OfferUpdateDTO dto) {
        GeneratedOffer offer = offerRepo.findByOfferCode(offerCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Offre '" + offerCode + "' introuvable"));
        offerMapper.updateEntity(offer, dto);
        return offerMapper.toDto(offerRepo.save(offer));
    }

    @Override
    @Transactional
    public OfferResponseDTO setOfferStatus(String offerCode, OfferStatusDTO dto) {
        GeneratedOffer offer = offerRepo.findByOfferCode(offerCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Offre '" + offerCode + "' introuvable"));
        offer.setStatus(GeneratedOffer.OfferStatus.valueOf(dto.getStatus()));
        log.info("Statut offre {} → {}", offerCode, dto.getStatus());
        return offerMapper.toDto(offerRepo.save(offer));
    }

    @Override
    @Transactional
    public void deleteOffer(String offerCode) {
        GeneratedOffer offer = offerRepo.findByOfferCode(offerCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Offre '" + offerCode + "' introuvable"));
        log.warn("Suppression offre : {}", offerCode);
        offerRepo.delete(offer);
    }

    @Override
    public long countActiveOffers() {
        return offerRepo.countByStatus(GeneratedOffer.OfferStatus.ACTIVE);
    }
}
