package com.pfe.clientdashboard.provider.controller;

import com.pfe.clientdashboard.provider.dto.*;
import com.pfe.clientdashboard.provider.entities.Provider;
import com.pfe.clientdashboard.provider.services.ProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;

    @GetMapping
    public ResponseEntity<List<Provider>> getAllProviders() {
        return ResponseEntity.ok(providerService.getAllProviders());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Provider> getProviderById(@PathVariable String id) {
        return ResponseEntity.ok(providerService.getProviderById(id));
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<ProviderStatsDTO> getProviderStats(@PathVariable String id) {
        return ResponseEntity.ok(providerService.getProviderStats(id));
    }

    // Dans ProviderController.java — ajouter
    @GetMapping("/summaries")
    public ResponseEntity<List<ProviderSummaryDTO>> getAllSummaries() {
        return ResponseEntity.ok(providerService.getAllProviderSummaries());
    }

    @GetMapping("/list-stats")
    public ResponseEntity<ProviderListStatsDTO> getListStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(providerService.getProviderListStats(from, to));
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<ProviderDetailDTO> getProviderDetail(
            @PathVariable String id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(providerService.getProviderDetail(id, from, to));
    }
}
