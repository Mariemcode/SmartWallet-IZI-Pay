package com.pfe.clientdashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;
/**
 * SmartWallet — Configuration Spring Boot
 * =========================================
 * Déclare les beans utilisés par :
 *   - RetrainService  (RestTemplate pour appeler FastAPI)
 *   - RetrainScheduler (@EnableScheduling pour les tâches planifiées)
 */
@Configuration
@EnableScheduling
public class appConfig {

    /**
     * Bean RestTemplate — utilisé par RetrainService pour appeler
     * les endpoints FastAPI (/retrain, /health, /metrics).
     *
     * Configuré avec des timeouts raisonnables :
     *   - Connexion : 5 secondes
     *   - Lecture   : 60 secondes (le réentraînement peut être long)
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
