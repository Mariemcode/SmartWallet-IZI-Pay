package com.pfe.clientdashboard.config;

import feign.Logger;
import feign.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.concurrent.TimeUnit;

// ══════════════════════════════════════════════════════════════
//  CONFIG FEIGN
// ══════════════════════════════════════════════════════════════
@Configuration
public class FeignConfig {

    /**
     * Logs Feign — FULL en dev, BASIC en prod.
     * FULL = headers + body de chaque appel FastAPI (utile pour débugger)
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    /**
     * Timeouts vers FastAPI :
     *   connect : 5 secondes
     *   read    : 30 secondes (XGBoost rapide, Prophet peut prendre plus)
     */
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
                5, TimeUnit.SECONDS,
                30, TimeUnit.SECONDS,
                true
        );
    }
}







