package com.pfe.clientdashboard.dashboardAdmin.controller;

import com.pfe.clientdashboard.dashboardAdmin.service.DashboardAdminService;
import com.pfe.clientdashboard.dashboardAdmin.dto.DashboardResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardAdminController {

    private final DashboardAdminService service;

    @GetMapping
    public DashboardResponseDTO getDashboard() {
        return service.getDashboard();   // plus de paramètres from/to
    }
}