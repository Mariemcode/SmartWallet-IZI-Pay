package com.pfe.clientdashboard.ocr.controller;

import com.pfe.clientdashboard.ocr.repository.ScanFeedbackRepository;
import com.pfe.clientdashboard.ocr.repository.ScannedFactureRepository;
import com.pfe.clientdashboard.ocr.entities.ScanFeedback;
import com.pfe.clientdashboard.ocr.entities.ScannedFacture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * OcrController — refonte v7
 * ═══════════════════════════
 *
 * Avant v7 : pur proxy vers FastAPI → AUCUNE persistance Spring → tables vides.
 * Maintenant : DOUBLE-WRITE — Spring est la source de vérité, FastAPI fait l'analyse ML.
 *
 * Endpoints (préfixe /api/ia/ocr) :
 *   POST /scan-facture        — Multipart image → FastAPI (analyse OCR) ; pas de persist
 *   POST /detecter-anomalie   — Body → FastAPI (Z-score historique) ; pas de persist
 *   POST /programmer-rappel   — Body → INSERT scanned_facture (Spring) + FastAPI fallback
 *   POST /feedback            — Body → INSERT scan_feedback (Spring) + FastAPI fallback
 *
 * Avantages de la nouvelle approche :
 *   • Spring connaît IMMÉDIATEMENT les factures programmées (ReminderScheduler les voit)
 *   • Pas de dépendance à la contrainte UNIQUE Postgres côté FastAPI (qui plantait)
 *   • Si FastAPI est down, Spring continue de persister
 *   • Le dashboard admin peut lister les factures en temps réel
 */
@Slf4j
@RestController
@RequestMapping("/api/ia/ocr")
@RequiredArgsConstructor
public class OcrController {

    @Value("${fastapi.base-url:http://127.0.0.1:8000}")
    private String fastapiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ScannedFactureRepository scannedFactureRepo;
    private final ScanFeedbackRepository scanFeedbackRepo;

    // ════════════════════════════════════════════════════════════════
    //  POST /scan-facture — multipart image
    //  Pas de persist côté Spring : c'est juste l'analyse OCR brute
    //  qui retourne les champs détectés. La persistance se fait à
    //  l'étape suivante (programmer-rappel ou feedback).
    // ════════════════════════════════════════════════════════════════
    @PostMapping("/scan-facture")
    public ResponseEntity<?> scanFacture(
            @RequestParam("image") MultipartFile image,
            @RequestParam("client_id") String clientId,
            @RequestParam(value = "solde", required = false) Double solde) {

        log.info("📸 OCR scan facture reçu : client={}, solde={}", clientId, solde);

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource imageResource = new ByteArrayResource(image.getBytes()) {
                @Override public String getFilename() { return image.getOriginalFilename(); }
            };
            body.add("image", imageResource);
            body.add("client_id", clientId);
            if (solde != null) body.add("solde", solde.toString());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    fastapiUrl + "/api/ocr/scan-facture", requestEntity, Map.class);
            return ResponseEntity.ok(response.getBody());

        } catch (Exception e) {
            log.error("❌ Erreur OCR scan : {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Service OCR indisponible",
                    "message", e.getMessage()
            ));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  POST /detecter-anomalie — pas de persist (juste Z-score)
    // ════════════════════════════════════════════════════════════════
    @PostMapping("/detecter-anomalie")
    public ResponseEntity<?> detecterAnomalie(@RequestBody Map<String, Object> body) {
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    fastapiUrl + "/api/ocr/detecter-anomalie", body, Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("❌ Erreur détection anomalie : {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Service indisponible", "message", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  POST /programmer-rappel
    //  ★ NOUVEAU : INSERT côté Spring directement (source of truth)
    //  ★ FastAPI est appelé seulement pour le calcul des dates de rappels
    //    (logique métier J-7, J-3, J-1, J0)
    // ════════════════════════════════════════════════════════════════
    @PostMapping("/programmer-rappel")
    public ResponseEntity<?> programmerRappel(@RequestBody Map<String, Object> body) {
        log.info("📅 Programmation rappel reçue : {}", body);

        String clientId = (String) body.get("client_id");
        String fournisseurLabel = (String) body.getOrDefault("fournisseur_label", "");
        String fournisseurNom = (String) body.getOrDefault("fournisseur_nom", fournisseurLabel);
        Object montantObj = body.get("montant");
        BigDecimal montant = montantObj != null
                ? new BigDecimal(montantObj.toString())
                : BigDecimal.ZERO;
        String dateEchStr = (String) body.getOrDefault("date_echeance", "");
        String reference = (String) body.getOrDefault("reference", "");

        if (clientId == null || clientId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "client_id requis"));
        }

        // ── Parser la date d'échéance (multi-formats) ──
        LocalDate dateEcheance = parseDate(dateEchStr);

        // ★ INSERT côté Spring (source of truth) ──────────────────────
        ScannedFacture facture;
        try {
            facture = scannedFactureRepo
                    .findByClientIdAndFournisseurLabelAndDateEcheance(
                            clientId, fournisseurLabel, dateEcheance)
                    .orElseGet(() -> ScannedFacture.builder()
                            .clientId(clientId)
                            .fournisseurLabel(fournisseurLabel)
                            .build());

            facture.setFournisseurNom(fournisseurNom);
            facture.setMontant(montant);
            facture.setDateEcheance(dateEcheance);
            facture.setReference(reference);
            facture = scannedFactureRepo.save(facture);

            log.info("✅ Facture scannée enregistrée : id={} client={} fournisseur={} montant={}",
                    facture.getId(), clientId, fournisseurLabel, montant);
        } catch (Exception e) {
            log.error("❌ Erreur INSERT scanned_facture : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error_persist",
                    "error", e.getMessage()
            ));
        }

        // ── Appel FastAPI pour calcul des rappels (optionnel) ──
        Map<String, Object> fastApiResult = null;
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    fastapiUrl + "/api/ocr/programmer-rappel", body, Map.class);
            fastApiResult = response.getBody();
        } catch (Exception e) {
            log.warn("⚠️ FastAPI indisponible pour programmer-rappel, mais Spring a déjà persisté : {}",
                    e.getMessage());
        }

        // ── Réponse riche au mobile ──
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "rappel_programme");
        result.put("id", facture.getId().toString());
        result.put("client_id", clientId);
        result.put("fournisseur", fournisseurNom);
        result.put("fournisseur_label", fournisseurLabel);
        result.put("montant", montant);
        result.put("date_echeance", dateEcheance.toString());
        if (fastApiResult != null && fastApiResult.get("rappels") != null) {
            result.put("rappels", fastApiResult.get("rappels"));
        }
        return ResponseEntity.ok(result);
    }

    // ════════════════════════════════════════════════════════════════
    //  POST /feedback — corrections OCR de l'utilisateur
    //  ★ NOUVEAU : INSERT côté Spring + appel FastAPI pour analyse
    // ════════════════════════════════════════════════════════════════
    @PostMapping("/feedback")
    public ResponseEntity<?> feedback(@RequestBody Map<String, Object> body) {
        log.info("🧠 Feedback OCR reçu : client={}", body.get("client_id"));

        String clientId = (String) body.get("client_id");
        if (clientId == null || clientId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "client_id requis"));
        }

        // ── Calcul des drapeaux de correction ──
        boolean fournCorr = isDifferent(body.get("ocr_fournisseur"),     body.get("user_fournisseur"));
        boolean montCorr  = isDifferent(body.get("ocr_montant"),         body.get("user_montant"));
        boolean dateCorr  = isDifferent(body.get("ocr_date_echeance"),  body.get("user_date_echeance"));
        boolean refCorr   = isDifferent(body.get("ocr_reference"),       body.get("user_reference"));
        boolean valide    = !fournCorr && !montCorr && !dateCorr && !refCorr;

        // ── INSERT côté Spring ──
        ScanFeedback feedback = ScanFeedback.builder()
                .clientId(clientId)
                .ocrFournisseur(asString(body.get("ocr_fournisseur")))
                .ocrMontant(asBigDecimal(body.get("ocr_montant")))
                .ocrDateEcheance(asString(body.get("ocr_date_echeance")))
                .ocrReference(asString(body.get("ocr_reference")))
                .ocrConfiance(asString(body.getOrDefault("ocr_confiance", "faible")))
                .ocrTextBrut(truncate(asString(body.get("ocr_text_brut")), 2000))
                .userFournisseur(asString(body.get("user_fournisseur")))
                .userMontant(asBigDecimal(body.get("user_montant")))
                .userDateEcheance(asString(body.get("user_date_echeance")))
                .userReference(asString(body.get("user_reference")))
                .fournisseurCorrige(fournCorr)
                .montantCorrige(montCorr)
                .dateCorrigee(dateCorr)
                .referenceCorrigee(refCorr)
                .valideSansCorrection(valide)
                .actionFinale(asString(body.getOrDefault("action_finale", "paye")))
                .build();

        Long feedbackId = null;
        try {
            feedback = scanFeedbackRepo.save(feedback);
            feedbackId = feedback.getId();
            log.info("✅ Feedback OCR enregistré : id={} corrections={}",
                    feedbackId,
                    List.of(fournCorr ? "fournisseur" : "",
                            montCorr ? "montant" : "",
                            dateCorr ? "date" : "",
                            refCorr ? "reference" : ""));
        } catch (Exception e) {
            log.error("❌ Erreur INSERT scan_feedback : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error_persist",
                    "error", e.getMessage()
            ));
        }

        // ── Forward FastAPI pour analyse temps-réel (optionnel) ──
        try {
            restTemplate.postForEntity(fastapiUrl + "/api/ocr/feedback", body, Map.class);
        } catch (Exception e) {
            log.debug("FastAPI feedback non-critique : {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of(
                "status", "feedback_enregistre",
                "id", feedbackId,
                "corrections_detectees", List.of(
                        fournCorr ? "fournisseur" : null,
                        montCorr  ? "montant"    : null,
                        dateCorr  ? "date"       : null,
                        refCorr   ? "reference"  : null
                ).stream().filter(Objects::nonNull).toList(),
                "valide_sans_correction", valide
        ));
    }

    // ════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return LocalDate.now().plusDays(30);
        for (String fmt : new String[]{"dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy", "yyyy/MM/dd"}) {
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern(fmt));
            } catch (Exception ignored) {}
        }
        return LocalDate.now().plusDays(30);
    }

    private static boolean isDifferent(Object a, Object b) {
        if (a == null && b == null) return false;
        if (a == null || b == null) return true;
        try {
            return Math.abs(Double.parseDouble(a.toString()) - Double.parseDouble(b.toString())) > 0.001;
        } catch (Exception ignored) {}
        return !a.toString().trim().equalsIgnoreCase(b.toString().trim());
    }

    private static String asString(Object o) { return o == null ? null : o.toString(); }
    private static BigDecimal asBigDecimal(Object o) {
        if (o == null) return null;
        try { return new BigDecimal(o.toString()); } catch (Exception e) { return null; }
    }
    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}