package com.pfe.clientdashboard.prevision.controller;

import com.pfe.clientdashboard.notification.Scheduler.ReminderScheduler;
import com.pfe.clientdashboard.client.entities.Client;
import com.pfe.clientdashboard.prevision.service.PredictionAuditLogService;
import com.pfe.clientdashboard.provider.entities.Provider;
import com.pfe.clientdashboard.transaction.entities.Transaction;
import com.pfe.clientdashboard.transactionType.entities.TransactionType;
import com.pfe.clientdashboard.offre.service.OfferApplicationService;
import com.pfe.clientdashboard.client.repository.ClientRepository;
import com.pfe.clientdashboard.client.repository.ProviderRepository;
import com.pfe.clientdashboard.client.repository.TransactionRepository;
import com.pfe.clientdashboard.transactionType.repository.TransactionTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


/**
 * SmartWallet — Création de transactions
 * ========================================
 * POST /dashboard/{clientId}/transactions
 * Body : { "amount": 50.0, "transactionTypeId": "...", "receiverId": "..." }
 *
 * GET /dashboard/{clientId}/transaction-types
 * → Liste des types de transaction disponibles pour le dropdown Flutter
 */
@Slf4j
@RestController
@RequestMapping("/dashboard/{clientId}")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionRepository txRepo;
    private final ClientRepository clientRepo;
    private final TransactionTypeRepository typeRepo;
    private final ProviderRepository providerRepo;
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final PredictionAuditLogService auditLogService;
    private final OfferApplicationService offerApplicationService;  // ★ remplace les anciens reward repos
    private final ReminderScheduler reminderScheduler;

    @Value("${fastapi.base-url:http://127.0.0.1:8000}")
    private String fastapiBaseUrl;

    /**
     * Catégories réservées au système / agents — jamais visibles ni créables par le client.
     */
    private static final Set<String> HIDDEN_CATEGORIES = Set.of(
            "Frais & Commissions",
            "Annulation & Correction",
            "Depot & Retrait",
            "Argent Recu"
    );

    /** Taux de frais standard appliqué aux Factures & Services / Recharges (1 %).
     *  Une offre marketing FEE_WAIVER active exonère totalement ce taux. */
    private static final BigDecimal FRAIS_TAUX = new BigDecimal("0.01");

    // ════════════════════════════════════════════════════════════════
    //  POST /dashboard/{clientId}/transactions
    // ════════════════════════════════════════════════════════════════
    @PostMapping("/transactions")
    @Transactional
    public ResponseEntity<?> createTransaction(
            @PathVariable String clientId,
            @RequestBody Map<String, Object> body) {
        // ✅ P1-4 : déclarés ici pour rester accessibles dans le bloc résultat
        BigDecimal fraisAppliques = BigDecimal.ZERO;
        boolean reductionActive = false;
        try {
            // 1. Vérifier que le client existe
            Client client = clientRepo.findById(clientId).orElse(null);
            if (client == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Client introuvable : " + clientId));
            }

            // 2. Extraire les champs du body
            BigDecimal amount;
            try {
                Object amountObj = body.get("amount");
                if (amountObj == null) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Le montant est obligatoire"));
                }
                amount = new BigDecimal(amountObj.toString());
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Le montant doit être positif"));
                }
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Montant invalide"));
            }

            String transactionTypeId = (String) body.get("transactionTypeId");
            if (transactionTypeId == null || transactionTypeId.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Le type de transaction est obligatoire"));
            }

            String receiverId = (String) body.get("receiverId");
            String providerId = (String) body.get("providerId");

            // 3. Vérifier le type de transaction
            TransactionType txType = typeRepo.findById(transactionTypeId).orElse(null);
            if (txType == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Type de transaction invalide : " + transactionTypeId));
            }

            // 3b. Bloquer les catégories réservées au système
            if (txType.getCategory() != null && HIDDEN_CATEGORIES.contains(txType.getCategory())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error",
                                "Cette opération est réservée au système et ne peut pas être créée manuellement"));
            }

            // 4. Vérifier le provider (optionnel)
            Provider provider = null;
            if (providerId != null && !providerId.isEmpty()) {
                provider = providerRepo.findById(providerId).orElse(null);
            }

            // 5. Créer la transaction
            String txId = UUID.randomUUID().toString();
            Transaction transaction = Transaction.builder()
                    .id(txId)
                    .amount(amount)
                    .currency("TND")
                    .transactionDate(LocalDateTime.now())
                    .reversalFlag("N")
                    .receiverId(receiverId)
                    .client(client)
                    .transactionType(txType)
                    .provider(provider)
                    .build();

            txRepo.saveAndFlush(transaction);
            if ("Factures & Services".equals(txType.getCategory())) {
                String label = mapTypeIdToLabel(transactionTypeId);
                reminderScheduler.marquerCommePayee(clientId, label);
            }

            // ★ Application réelle d'une offre marketing FEE_WAIVER :
            // si le client a accepté une recommandation marketing d'exonération
            // de frais ce mois-ci, on lui rembourse intégralement les frais via
            // une transaction de crédit compensatoire.
            if ("Factures & Services".equals(txType.getCategory()) || "Recharge Telephonique".equals(txType.getCategory())) {
                reductionActive = offerApplicationService.hasFeeWaiver(clientId);
                if (reductionActive) {
                    fraisAppliques = amount.multiply(FRAIS_TAUX).setScale(3, java.math.RoundingMode.HALF_UP);
                    if (fraisAppliques.compareTo(BigDecimal.ZERO) > 0) {
                        applyFraisCredit(clientId, fraisAppliques, txId);
                        log.info("✅ FEE_WAIVER (offre marketing) : {} TND remboursés à {}", fraisAppliques, clientId);
                    }
                }
            }

            // 🌟 CORRECTION : Appeler la bonne méthode
            // 🌟 Tracker les factures ET les recharges
            if ("Factures & Services".equals(txType.getCategory())) {
                String label = mapTypeIdToLabel(transactionTypeId);
                auditLogService.markAsPaidImmediately(clientId, label, amount);
                notifyFastApiPaid(clientId, transactionTypeId, amount.doubleValue());
            } else if ("Recharge Telephonique".equals(txType.getCategory())) {
                notifyFastApiPaid(clientId, transactionTypeId, amount.doubleValue());
            }

            log.info("✅ Transaction créée : {} — {} TND — type: {} — client: {}",
                    txId, amount, txType.getTitle(), clientId);

            // 6. Retourner la transaction créée
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", txId);
            result.put("amount", amount);
            result.put("currency", "TND");
            result.put("transactionDate", transaction.getTransactionDate().toString());
            result.put("flowType", txType.getType());
            result.put("credit", "C".equals(txType.getType()));
            result.put("typeTitle", txType.getTitle());
            result.put("category", txType.getCategory());
            result.put("subCategory", txType.getSubCategory());
            result.put("reversalFlag", "N");
            if (provider != null) {
                result.put("providerName", provider.getProviderName());
            }
            if (receiverId != null) {
                result.put("receiverId", receiverId);
            }
            result.put("message", "Transaction créée avec succès");
            // Indiquer au client Flutter si des frais ont été remboursés (via offre marketing FEE_WAIVER)
            if (reductionActive && fraisAppliques.compareTo(BigDecimal.ZERO) > 0) {
                result.put("reduction_frais_appliquee", true);
                result.put("frais_rembourses_tnd", fraisAppliques);
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(result);

        } catch (Exception e) {
            log.error("❌ Erreur création transaction : {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur : " + e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  FEE_WAIVER helper — applique le remboursement réel des frais
    //  La détection d'éligibilité est faite par OfferApplicationService.
    // ════════════════════════════════════════════════════════════════

    /**
     * Crée une transaction de crédit compensatoire (remboursement des frais)
     * suite à une offre marketing FEE_WAIVER active. Pas de consommation d'une
     * récompense — l'offre reste active jusqu'à `endsAt` (fin du mois).
     */
    private void applyFraisCredit(String clientId, BigDecimal fraisMontant, String refTxId) {
        try {
            // Chercher type_transaction "Argent Recu" (C) pour le remboursement
            String creditTypeId = jdbcTemplate.queryForObject(
                    "SELECT id FROM type_transaction WHERE type='C' AND category='Argent Recu' LIMIT 1",
                    String.class);

            if (creditTypeId != null) {
                jdbcTemplate.update(
                        "INSERT INTO transaction(id, amount, currency, transaction_date, reversal_flag, client_id, transaction_type_id) " +
                                "VALUES (?,?,?,?,?,?,?)",
                        UUID.randomUUID().toString(), fraisMontant, "TND", LocalDateTime.now(), "N", clientId, creditTypeId);
            }
        } catch (Exception e) {
            log.warn("⚠️ applyFraisCredit échoué : {}", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  NOTIFICATION FASTAPI
    // ════════════════════════════════════════════════════════════════
    private void notifyFastApiPaid(String clientId, String typeId, double amount) {
        CompletableFuture.runAsync(() -> {
            try {
                String label = mapTypeIdToLabel(typeId);
                String url = fastapiBaseUrl + "/transactions/paid/" + clientId + "?label=" + label;
                restTemplate.postForEntity(url, null, String.class);
                log.info("📡 FastAPI notifié : {} payé pour {}", label, clientId);
            } catch (Exception e) {
                log.warn("⚠️ Notification FastAPI échouée: {}", e.getMessage());
            }
        });
    }

    private String mapTypeIdToLabel(String typeId) {
        return switch (typeId) {
            case "1461b464-fa44-477a-90c3-a9d68acdf29a" -> "TOPNET";
            case "08e939ae-af2c-428f-8ece-5862e56de5d3" -> "BEE";
            case "18de5279-60d1-40f0-bb68-54b29d6f1ba8" -> "SONEDE";
            case "07ba063e-a1fb-4fd9-87a7-243efcc55af2" -> "STEG";
            case "c428964b-5929-4480-a569-cb2ef1bc3b27" -> "TT";
            case "2d0d0c45-9941-42d9-9849-0f61d06c4a7b" -> "OOREDOO";
            // Recharges
            case "a0f3b202-3d88-4893-b5d3-db3613b7cc4a" -> "RECH_TT";
            case "cc3fb138-ffbe-4b22-9e83-d5426127d5ca" -> "RECH_OOREDOO";
            default -> "UNKNOWN";
        };
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /dashboard/{clientId}/transaction-types
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/transaction-types")
    public ResponseEntity<?> getTransactionTypes(@PathVariable String clientId) {
        try {
            if (!clientRepo.existsById(clientId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Client introuvable"));
            }

            List<String> activeIds = jdbcTemplate.queryForList(
                    "SELECT DISTINCT transaction_type_id FROM transaction", String.class);
            Set<String> activeSet = new HashSet<>(activeIds);

            List<Map<String, String>> types = typeRepo.findAll().stream()
                    .filter(tt -> tt.getCategory() == null
                            || !HIDDEN_CATEGORIES.contains(tt.getCategory()))
                    .filter(tt -> activeSet.contains(tt.getId()))
                    .map(tt -> {
                        Map<String, String> item = new LinkedHashMap<>();
                        item.put("id", tt.getId());
                        item.put("title", tt.getTitle());
                        item.put("category", tt.getCategory());
                        item.put("type", tt.getType());
                        item.put("subCategory", tt.getSubCategory());
                        return item;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(types);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /dashboard/{clientId}/providers
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/providers")
    public ResponseEntity<?> getProviders(@PathVariable String clientId) {
        try {
            if (!clientRepo.existsById(clientId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Client introuvable"));
            }

            List<Map<String, String>> providers = providerRepo.findAll().stream()
                    .map(p -> {
                        Map<String, String> item = new LinkedHashMap<>();
                        item.put("id", p.getId());
                        item.put("providerName", p.getProviderName());
                        item.put("providerCode", p.getProviderCode());
                        return item;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(providers);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}