package com.pfe.clientdashboard.provider.services;


import com.pfe.clientdashboard.provider.entities.Provider;
import com.pfe.clientdashboard.provider.dto.ProviderDetailDTO;
import com.pfe.clientdashboard.provider.dto.ProviderListStatsDTO;
import com.pfe.clientdashboard.provider.dto.ProviderStatsDTO;
import com.pfe.clientdashboard.provider.dto.ProviderSummaryDTO;

import java.time.LocalDateTime;
import java.util.List;

public interface ProviderService {

    List<Provider> getAllProviders();

    Provider getProviderById(String id);

    ProviderStatsDTO getProviderStats(String providerId);
    // Dans ProviderService.java — ajouter
    List<ProviderSummaryDTO> getAllProviderSummaries();

    ProviderListStatsDTO getProviderListStats(LocalDateTime from, LocalDateTime to);
    ProviderDetailDTO getProviderDetail(String providerId, LocalDateTime from, LocalDateTime to);
}