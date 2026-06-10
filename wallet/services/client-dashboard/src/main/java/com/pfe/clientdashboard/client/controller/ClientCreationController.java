package com.pfe.clientdashboard.client.controller;

import com.pfe.clientdashboard.client.repository.ClientRepository;
import com.pfe.clientdashboard.client.entities.Client;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * SmartWallet — Création de client lors de l'inscription
 * =======================================================
 * Appelé par le Gateway après création Keycloak.
 *
 * POST /api/clients
 * Body : { "id": "keycloak-uuid", "phoneNumber": "216...", "firstName": "...", "lastName": "..." }
 */
@RestController
@RequestMapping("/api/clients")
public class ClientCreationController {

    private final ClientRepository clientRepository;

    public ClientCreationController(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @PostMapping
    public ResponseEntity<?> createClient(@RequestBody Map<String, String> body) {
        try {
            String id          = body.get("id");
            String phoneNumber = body.get("phoneNumber");
            String firstName   = body.get("firstName");
            String lastName    = body.get("lastName");

            if (id == null || id.isEmpty() || phoneNumber == null || phoneNumber.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "id et phoneNumber sont obligatoires"));
            }

            // Vérifier que le numéro n'existe pas déjà
            if (clientRepository.findByPhoneNumber(phoneNumber).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Un client avec ce numéro existe déjà"));
            }

            Client client = Client.builder()
                    .id(id)
                    .phoneNumber(phoneNumber)
                    .firstName(firstName)
                    .lastName(lastName)
                    .createDateTime(LocalDateTime.now())
                    .build();

            clientRepository.save(client);

            System.out.println("✅ Client créé en BDD : " + id + " / " + phoneNumber);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "client_id", id,
                            "phone_number", phoneNumber,
                            "message", "Client créé avec succès"
                    ));

        } catch (Exception e) {
            System.out.println("❌ Erreur création client : " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur : " + e.getMessage()));
        }
    }
}
