package com.pfe.gateway.user;

import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final Keycloak keycloakAdminClient;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${keycloak.server-url:http://localhost:9098}")
    private String keycloakServerUrl;
    @Value("${keycloak.realm:master}")
    private String realm;
    @Value("${keycloak.client-id:gateway-client}")
    private String clientId;
    @Value("${keycloak.client-secret:}")
    private String clientSecret;
    @Value("${client-dashboard.url:http://localhost:8090}")
    private String clientDashboardUrl;

    public AuthController(Keycloak keycloakAdminClient) {
        this.keycloakAdminClient = keycloakAdminClient;
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/auth/login
    // ★ CORRIGÉ : renvoie aussi refresh_token
    // ════════════════════════════════════════════════════════════════
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            Map tokenBody = callKeycloakToken(
                    "password",
                    loginRequest.getUsername(),
                    loginRequest.getPassword(),
                    null
            );

            if (tokenBody == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Identifiants incorrects"));
            }

            String kcClientId = getClientIdFromDatabase(loginRequest.getUsername());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("access_token",  tokenBody.get("access_token"));
            result.put("refresh_token", tokenBody.get("refresh_token"));   // ← NOUVEAU
            result.put("token_type",    "Bearer");
            result.put("expires_in",    tokenBody.get("expires_in"));
            result.put("client_id",     kcClientId);
            result.put("username",      loginRequest.getUsername());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Numéro ou mot de passe incorrect"));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur serveur : " + e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/auth/refresh   ← NOUVEAU
    // Flutter appelle quand access_token expire (erreur 401)
    // ════════════════════════════════════════════════════════════════
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refresh_token");
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "refresh_token requis"));
        }

        try {
            Map tokenBody = callKeycloakToken(
                    "refresh_token", null, null, refreshToken
            );

            if (tokenBody == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Refresh token expiré — reconnectez-vous"));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("access_token",  tokenBody.get("access_token"));
            result.put("refresh_token", tokenBody.get("refresh_token"));
            result.put("expires_in",    tokenBody.get("expires_in"));

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Session expirée — reconnectez-vous"));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // HELPER — Appel Keycloak token endpoint
    // ════════════════════════════════════════════════════════════════
    private Map callKeycloakToken(String grantType, String username,
                                   String password, String refreshToken) {
        String tokenUrl = keycloakServerUrl
                + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", grantType);
        params.add("client_id",  clientId);

        if (clientSecret != null && !clientSecret.isEmpty()) {
            params.add("client_secret", clientSecret);
        }

        if ("password".equals(grantType)) {
            params.add("username", username);
            params.add("password", password);
        } else if ("refresh_token".equals(grantType)) {
            params.add("refresh_token", refreshToken);
        }

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            System.out.println("⚠ Keycloak token error: " + e.getMessage());
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/auth/register (inchangé)
    // ════════════════════════════════════════════════════════════════
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserDto userDto) {
        try {
            UserRepresentation user = new UserRepresentation();
            user.setUsername(userDto.getUsername());
            user.setEmail(userDto.getEmail());
            user.setFirstName(userDto.getFirstName());
            user.setLastName(userDto.getLastName());
            user.setEnabled(true);
            user.setEmailVerified(true);

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(userDto.getPassword());
            credential.setTemporary(false);
            user.setCredentials(Collections.singletonList(credential));

            UsersResource usersResource = keycloakAdminClient.realm(realm).users();
            Response response = usersResource.create(user);

            if (response.getStatus() == 409) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Ce numéro est déjà utilisé"));
            }
            if (response.getStatus() != 201) {
                return ResponseEntity.status(response.getStatus())
                        .body(Map.of("error", "Échec Keycloak : HTTP " + response.getStatus()));
            }

            String keycloakUserId = "";
            String locationHeader = response.getHeaderString("Location");
            if (locationHeader != null) {
                keycloakUserId = locationHeader.substring(locationHeader.lastIndexOf("/") + 1);
            }

            try {
                String createClientUrl = clientDashboardUrl + "/api/clients";
                Map<String, String> clientBody = new LinkedHashMap<>();
                clientBody.put("id", keycloakUserId);
                clientBody.put("phoneNumber", userDto.getUsername());
                clientBody.put("firstName", userDto.getFirstName());
                clientBody.put("lastName", userDto.getLastName());

                HttpHeaders httpHeaders = new HttpHeaders();
                httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, String>> httpEntity = new HttpEntity<>(clientBody, httpHeaders);
                restTemplate.postForEntity(createClientUrl, httpEntity, String.class);
            } catch (Exception dbEx) {
                System.out.println("⚠ Keycloak OK mais PostgreSQL erreur : " + dbEx.getMessage());
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Compte créé avec succès",
                    "client_id", keycloakUserId
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur : " + e.getMessage()));
        }
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        List<UserRepresentation> users = keycloakAdminClient.realm(realm).users().list();
        List<UserDto2> dtos = users.stream().map(u -> {
            UserDto2 dto = new UserDto2();
            dto.setUsername(u.getUsername());
            dto.setEmail(u.getEmail());
            return dto;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    private String getClientIdFromDatabase(String phoneNumber) {
        try {
            String url = clientDashboardUrl + "/api/clients/by-phone/" + phoneNumber;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object cid = response.getBody().get("client_id");
                if (cid != null && !cid.toString().isEmpty()) return cid.toString();
            }
        } catch (Exception e) {
            System.out.println("⚠ DB lookup failed: " + e.getMessage());
        }
        return getClientIdFromKeycloak(phoneNumber);
    }

    private String getClientIdFromKeycloak(String username) {
        try {
            List<UserRepresentation> users = keycloakAdminClient
                    .realm(realm).users().searchByUsername(username, true);
            if (users.isEmpty()) return "";
            return users.get(0).getId();
        } catch (Exception e) {
            return "";
        }
    }

    public static class LoginRequest {
        private String username;
        private String password;
        public String getUsername() { return username; }
        public void setUsername(String u) { this.username = u; }
        public String getPassword() { return password; }
        public void setPassword(String p) { this.password = p; }
    }
}
