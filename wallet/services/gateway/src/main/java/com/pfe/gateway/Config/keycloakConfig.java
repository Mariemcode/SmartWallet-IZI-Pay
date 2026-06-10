package com.pfe.gateway.Config;


import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class keycloakConfig {

    @Bean
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl("http://localhost:9098") // ✅ URL de base SEULEMENT
                .realm("master") // ✅ Realm MASTER pour l'authentification admin
                .username("admin")
                .password("admin")
                .clientId("admin-cli") // ✅ Client ADMIN-CLI spécial
                .grantType(OAuth2Constants.PASSWORD)
                .build();
    }
}
