package com.pfe.clientdashboard.client.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SmartWallet — Gestion des clients
 * ===================================
 * Endpoints :
 *   GET  /api/clients/by-phone/{phone}  — lookup client_id par téléphone (login)
 *   POST /api/clients/register          — créer un nouveau client (register)
 *
 * Appelé par le Gateway après les opérations Keycloak.
 * Lit/écrit directement dans PostgreSQL table "client".
 */
@RestController
@RequestMapping("/api/clients")
public class ClientLookupController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ════════════════════════════════════════════════════════════════
    // POST /api/clients/register
    // Crée un nouveau client dans PostgreSQL.
    // Appelé par le Gateway pendant le processus d'inscription.
    //
    // Body : { "phone_number": "21612345678", "first_name": "Mariem", "last_name": "Chahem" }
    // Retourne : { "client_id": "uuid", "phone_number": "21612345678", "status": "created" }
    // ════════════════════════════════════════════════════════════════
    @PostMapping("/register")
    public ResponseEntity<?> registerClient(@RequestBody Map<String, String> body) {
        try {
            String phoneNumber = body.getOrDefault("phone_number", "").trim()
                    .replace(" ", "").replace("+", "");
            String firstName   = body.getOrDefault("first_name", "").trim();
            String lastName    = body.getOrDefault("last_name", "").trim();

            // Validation
            if (phoneNumber.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "phone_number est requis"));
            }

            // Vérifier si le client existe déjà
            String checkSql = "SELECT id FROM client WHERE phone_number = ? LIMIT 1";
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(checkSql, phoneNumber);

            if (!existing.isEmpty()) {
                // Client déjà existant → retourner son ID (idempotent)
                String existingId = existing.get(0).get("id").toString();
                System.out.println("⚠ Client déjà existant : " + existingId);
                return ResponseEntity.ok(Map.of(
                        "client_id",    existingId,
                        "phone_number", phoneNumber,
                        "status",       "already_exists"
                ));
            }

            // Générer un UUID et insérer dans PostgreSQL
            String clientId = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now();

            String insertSql = """
                INSERT INTO client (id, phone_number, first_name, last_name, create_date_time)
                VALUES (?, ?, ?, ?, ?)
                """;

            jdbcTemplate.update(insertSql, clientId, phoneNumber,
                    firstName.isEmpty() ? null : firstName,
                    lastName.isEmpty()  ? null : lastName,
                    now);

            System.out.println("✅ Nouveau client créé : " + clientId + " (" + phoneNumber + ")");

            return ResponseEntity.ok(Map.of(
                    "client_id",    clientId,
                    "phone_number", phoneNumber,
                    "status",       "created"
            ));

        } catch (Exception e) {
            System.out.println("❌ Erreur création client : " + e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Erreur serveur : " + e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/clients/by-phone/{phone}
    // Lookup client_id par téléphone — appelé par le Gateway au login.
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/by-phone/{phone}")
    public ResponseEntity<?> getClientIdByPhone(@PathVariable String phone) {
        try {
            // Nettoyer le numéro — retirer espaces et +
            String phoneClean = phone.trim()
                    .replace(" ", "")
                    .replace("+", "");

            // Requête PostgreSQL — chercher dans la table client
            // Le phone_number peut être stocké avec ou sans indicatif
            String sql = """
                SELECT id, phone_number
                FROM client
                WHERE phone_number = ?
                   OR phone_number = ?
                   OR phone_number = ?
                LIMIT 1
                """;

            // Essayer plusieurs formats du numéro
            String withIndicatif    = phoneClean;                           // 21626147732
            String sansIndicatif    = phoneClean.replaceFirst("^216", ""); // 26147732 (8 chiffres)
            String avecZero         = "0" + sansIndicatif;                 // 026147732

            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                    sql,
                    withIndicatif,
                    sansIndicatif,
                    avecZero
            );

            if (results.isEmpty()) {
                System.out.println("⚠ Client non trouvé pour phone: " + phoneClean);
                return ResponseEntity.status(404)
                        .body(Map.of("error", "Client non trouvé pour le numéro " + phoneClean));
            }

            String clientId    = results.get(0).get("id").toString();
            String phoneNumber = results.get(0).get("phone_number").toString();

            System.out.println("✅ Client trouvé : " + clientId + " pour " + phoneNumber);

            return ResponseEntity.ok(Map.of(
                    "client_id",    clientId,
                    "phone_number", phoneNumber
            ));

        } catch (Exception e) {
            System.out.println("❌ Erreur lookup client : " + e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Erreur serveur : " + e.getMessage()));
        }
    }
}
