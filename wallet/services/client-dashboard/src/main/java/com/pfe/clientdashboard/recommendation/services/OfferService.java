package com.pfe.clientdashboard.recommendation.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.pfe.clientdashboard.recommendation.dtos.*;
import com.pfe.clientdashboard.recommendation.entities.GeneratedOffer;

public interface OfferService {

    Page<OfferResponseDTO> getOffers(
            GeneratedOffer.OfferStatus status, String type,
            String provider, String category, Pageable pageable);

    OfferResponseDTO getOfferByCode(String offerCode);

    OfferResponseDTO createOffer(OfferRequestDTO dto);

    OfferResponseDTO updateOffer(String offerCode, OfferUpdateDTO dto);

    OfferResponseDTO setOfferStatus(String offerCode, OfferStatusDTO dto);

    void deleteOffer(String offerCode);

    long countActiveOffers();
}
