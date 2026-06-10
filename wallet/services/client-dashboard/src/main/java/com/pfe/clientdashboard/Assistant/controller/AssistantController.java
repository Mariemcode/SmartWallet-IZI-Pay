package com.pfe.clientdashboard.Assistant.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * AssistantController — v2 (refonte chatbot)
 * ═══════════════════════════════════════════════════════════════════
 *
 * Proxy mince entre Flutter et FastAPI pour le nouveau chatbot.
 *
 * Changements v2 :
 *   • URL forward : /assistant/chat → /chatbot/message
 *   • Réponse enrichie : maintenant inclut rich_card, quick_replies,
 *     intent, confidence, sources, response_time_ms
 *   • Endpoints additionnels : /history, /stats pour le dashboard admin
 *
 * Route Flutter inchangée : POST /api/ia/assistant/chat (compatibilité)
 *
 * ═══════════════════════════════════════════════════════════════════
 */
@Slf4j
@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
public class AssistantController {

    @Value("${fastapi.base-url:http://127.0.0.1:8000}")
    private String fastapiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // ════════════════════════════════════════════════════════════════
    //  POST /api/ia/assistant/chat  (compatibilité Flutter existant)
    //  → forward vers FastAPI /chatbot/message
    // ════════════════════════════════════════════════════════════════
    @PostMapping("/assistant/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> body) {
        String clientId = body.get("client_id");
        String message = body.get("message");

        if (clientId == null || message == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "client_id et message requis"));
        }

        log.info("💬 Chatbot: client={} msg='{}'",
                clientId.length() > 8 ? clientId.substring(0, 8) : clientId,
                message.length() > 50 ? message.substring(0, 50) + "..." : message);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(
                    Map.of("client_id", clientId, "message", message),
                    headers);

            // ★ NEW : forward vers /chatbot/message (au lieu de /assistant/chat)
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    fastapiUrl + "/chatbot/message", request, Map.class);

            return ResponseEntity.ok(response.getBody());

        } catch (Exception e) {
            log.error("❌ Chatbot error: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Chatbot temporairement indisponible",
                    "reply", "Désolé, je ne peux pas répondre pour le moment. " +
                            "Réessayez dans quelques instants.",
                    "intent", "ERROR",
                    "confidence", 0.0
            ));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/ia/assistant/history/{clientId} — debug/admin
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/assistant/history/{clientId}")
    public ResponseEntity<?> history(@PathVariable String clientId) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    fastapiUrl + "/chatbot/history/" + clientId, Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("history error: {}", e.getMessage());
            return ResponseEntity.status(503)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/ia/assistant/stats — pour dashboard admin
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/assistant/stats")
    public ResponseEntity<?> stats() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    fastapiUrl + "/chatbot/stats", Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("stats error: {}", e.getMessage());
            return ResponseEntity.status(503)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
