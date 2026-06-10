package com.pfe.gateway.Security;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.DedupeResponseHeaderGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String ISSUER_URI = "http://localhost:9098/realms/master";

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                                "/eureka/**",
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/webjars/**",
                                "/api/auth/**",
                                "/actuator/**",
                                "/api/stations/**",
                                "/api/clients/**",
                                "/api/v1/profile/**",
                                "/api/dashboard/**",
                                "/api/admin/**",
                                "/api/transaction/**",
                                "/api/providers/**",
                                "/api/ia/admin/**",
                                "/api/v1/classification/**",
                                "/api/interaction/**",
                                "/api/metrics/**",
                                "/api/offers/**",
                                "/api/pipeline/**",
                                "/api/recommendations/**",
                                "/api/analysis/**",
                                "/api/admin-service/**",
                                "/api/model/**",
                                "/api/v5/**",
                                "/api/client-offers/**",
                                "/api/marketing/dashboard/**",
                                "/api/marketing/**"
                        ).permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtDecoder(reactiveJwtDecoder()))
                )
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:4200"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return NimbusReactiveJwtDecoder.withIssuerLocation(ISSUER_URI).build();
    }
}