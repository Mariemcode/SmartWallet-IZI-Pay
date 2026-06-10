package com.pfe.clientdashboard.client.controller;

import com.pfe.clientdashboard.client.dtos.ClientDTO;
import com.pfe.clientdashboard.client.services.ClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    public ResponseEntity<List<ClientDTO>> getAllClients() {
        return ResponseEntity.ok(clientService.getAllClients());
    }

    @GetMapping("/search")
    public ResponseEntity<List<ClientDTO>> searchClients(
            @RequestParam(required = false, defaultValue = "") String q) {
        return ResponseEntity.ok(clientService.searchClients(q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientDTO> getClientById(@PathVariable String id) {
        return ResponseEntity.ok(clientService.getClientById(id));
    }
}