package com.pfe.clientdashboard.recommendation.services;

import com.google.firebase.messaging.*;
import com.pfe.clientdashboard.notification.entities.NotificationLog;
import com.pfe.clientdashboard.notification.service.FcmService;
import com.pfe.clientdashboard.offre.service.OfferApplicationService;
import com.pfe.clientdashboard.offre.dto.SendOfferToProfileRequestDTO;
import com.pfe.clientdashboard.offre.dto.SendOfferToProfileResultDTO;
import com.pfe.clientdashboard.recommendation.dtos.ClientOfferDTO;
import com.pfe.clientdashboard.recommendation.dtos.OfferNotificationRequestDTO;
import com.pfe.clientdashboard.recommendation.dtos.OfferNotificationResultDTO;

import com.pfe.clientdashboard.recommendation.entities.ClientRecommendation;
import com.pfe.clientdashboard.recommendation.entities.GeneratedOffer;
import com.pfe.clientdashboard.recommendation.entities.Recommendation;
import com.pfe.clientdashboard.recommendation.entities.UserInteraction;
import com.pfe.clientdashboard.classification.entities.ClientProfileEntity;
import com.pfe.clientdashboard.classification.repositories.ClientProfileRepository;
import com.pfe.clientdashboard.recommendation.repositories.ClientRecommendationRepository;
import com.pfe.clientdashboard.recommendation.repositories.GeneratedOfferRepository;
import com.pfe.clientdashboard.recommendation.repositories.RecommendationRepository;
import com.pfe.clientdashboard.recommendation.repositories.UserInteractionRepository;
import com.pfe.clientdashboard.notification.repository.FcmTokenRepository;
import com.pfe.clientdashboard.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * OfferNotificationService
 * ════════════════════════
 * Gère l'envoi d'offres personnalisées aux clients via FCM.
 *
 * ★ FIX v3 :
 *   • clientId est désormais STRING partout (matche Client.id non-UUID)
 *   • respondToOffer() écrit dans user_interactions_v5 → alimente le scheduler
 *     d'auto-apprentissage (qui avant ne voyait jamais aucun feedback)
 *
 * Flux :
 *   1. Admin clique "Envoyer" sur la page recommandation (Angular)
 *   2. sendOfferToProfile() publie sur le topic FCM `profile_{clusterId}`
 *   3. createClientRecommendationsForCluster() crée 1 ligne par client du cluster
 *   4. Le mobile reçoit la notif, l'ouvre, affiche l'offre
 *   5. Le client ACCEPTE/REFUSE → respondToOffer() :
 *        - met à jour ClientRecommendation.status
 *        - crée une UserInteraction (signal de feedback)
 *        - applique l'offre en base si accepté (OfferApplicationService)
 *   6. Toutes les heures, MarketingFeedbackScheduler pousse les feedbacks à FastAPI
 *   7. L'admin peut consulter les stats et déclencher un retrain manuel
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OfferNotificationService {

    private final FcmTokenRepository fcmTokenRepository;
    private final ClientRecommendationRepository clientRecoRepo;
    private final GeneratedOfferRepository offerRepository;
    private final NotificationLogRepository notificationLogRepo;
    private final FcmService fcmService;
    private final RecommendationRepository recommendationRepo;
    private final OfferApplicationService offerApplicationService;
    private final ClientProfileRepository clientProfileRepo;
    private final UserInteractionRepository userInteractionRepo;  // ★ NOUVEAU

    // ══════════════════════════════════════════════════════════════
    //  ENVOYER UNE OFFRE À UN CLIENT SPÉCIFIQUE
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public OfferNotificationResultDTO sendOfferToClient(Long recommendationId,
                                                        OfferNotificationRequestDTO req) {
        String clientId = req.getClientId();
        log.info("📤 Envoi offre recommandation#{} → client {}", recommendationId, clientId);

        // 1. Token FCM
        String fcmToken = fcmTokenRepository.findByClientId(clientId)
                .map(t -> t.getToken())
                .orElse(null);

        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("⚠️ Aucun token FCM pour client {}", clientId);
            saveLog(clientId, "Offre SmartWallet", "Aucun token FCM", "OFFRE", "ECHEC");
            return OfferNotificationResultDTO.builder()
                    .status("NO_TOKEN")
                    .clientId(clientId)
                    .message("Le client n'a pas de token FCM enregistré — notification impossible.")
                    .build();
        }

        // 2. ClientRecommendation (create-or-get) — ★ FIX String
        ClientRecommendation clientReco = clientRecoRepo
                .findByClientIdAndRecommendationId(clientId, recommendationId)
                .orElseGet(() -> {
                    ClientRecommendation cr = ClientRecommendation.builder()
                            .clientId(clientId)
                            .recommendationId(recommendationId)
                            .status(ClientRecommendation.ClientRecoStatus.PENDING)
                            .build();
                    return clientRecoRepo.save(cr);
                });

        // 3. Récupérer les détails de l'offre
        String offerCode = clientReco.getOfferCode();
        GeneratedOffer offer = offerCode != null
                ? offerRepository.findByOfferCode(offerCode).orElse(null)
                : null;

        String offerTitle = offer != null ? offer.getTitle() : "Nouvelle offre SmartWallet";
        String bodyMsg = req.getCustomMessage() != null && !req.getCustomMessage().isBlank()
                ? req.getCustomMessage()
                : buildDefaultBody(offer);

        // 4. FCM message
        Message fcmMessage = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle("🎁 " + offerTitle)
                        .setBody(bodyMsg)
                        .build())
                .putData("type",               "offer_received")
                .putData("recommendation_id",  String.valueOf(recommendationId))
                .putData("client_reco_id",     String.valueOf(clientReco.getId()))
                .putData("offer_code",         offerCode != null ? offerCode : "")
                .putData("offer_title",        offerTitle)
                .putData("offer_body",         bodyMsg)
                .putData("cashback_pct",       offer != null && offer.getCashbackPct() != null ? offer.getCashbackPct().toPlainString() : "0")
                .putData("discount_pct",       offer != null && offer.getDiscountPct() != null ? offer.getDiscountPct().toPlainString() : "0")
                .putData("client_id",          clientId)
                .putData("click_action",       "FLUTTER_NOTIFICATION_CLICK")
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setSound("default")
                                .setChannelId("smartwallet_urgent")
                                .setColor("#00E5A0")
                                .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                                .build())
                        .build())
                .build();

        // 5. Envoi Firebase
        try {
            String fcmResponseId = FirebaseMessaging.getInstance().send(fcmMessage);
            log.info("✅ Notification offre envoyée → FCM ID : {}", fcmResponseId);

            clientReco.setStatus(ClientRecommendation.ClientRecoStatus.SENT);
            clientReco.setSentAt(LocalDateTime.now());
            clientRecoRepo.save(clientReco);

            saveLog(clientId, "🎁 " + offerTitle, bodyMsg, "OFFRE", "ENVOYE");

            return OfferNotificationResultDTO.builder()
                    .status("SENT")
                    .clientId(clientId)
                    .offerCode(offerCode)
                    .title(offerTitle)
                    .message(bodyMsg)
                    .fcmResponse(fcmResponseId)
                    .build();

        } catch (FirebaseMessagingException e) {
            log.error("❌ Erreur FCM ({}): {}", e.getMessagingErrorCode(), e.getMessage());
            saveLog(clientId, "🎁 " + offerTitle, bodyMsg, "OFFRE", "ECHEC");
            return OfferNotificationResultDTO.builder()
                    .status("ERROR")
                    .clientId(clientId)
                    .message("Erreur Firebase : " + e.getMessage())
                    .build();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ENVOYER UNE OFFRE À UN PROFIL (TOPIC FCM)
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public SendOfferToProfileResultDTO sendOfferToProfile(Long recommendationId,
                                                          SendOfferToProfileRequestDTO req) {
        Recommendation reco = recommendationRepo.findById(recommendationId)
                .orElseThrow(() -> new RuntimeException(
                        "Recommandation introuvable : " + recommendationId));

        Integer clusterId = req.getClusterId() != null ? req.getClusterId() : reco.getClusterId();
        if (clusterId == null) {
            return SendOfferToProfileResultDTO.builder()
                    .status("ECHEC")
                    .message("clusterId introuvable (ni dans la requête ni dans la recommandation)")
                    .build();
        }

        String topic = "profile_" + clusterId;
        String offerCode = reco.getOfferCode();
        GeneratedOffer offer = offerCode != null
                ? offerRepository.findByOfferCode(offerCode).orElse(null)
                : null;

        String offerTitle = offer != null ? offer.getTitle() : "Nouvelle offre SmartWallet";
        String bodyMsg = req.getCustomMessage() != null && !req.getCustomMessage().isBlank()
                ? req.getCustomMessage()
                : buildDefaultBody(offer);

        // Payload data envoyé à Flutter
        Map<String, String> data = new HashMap<>();
        data.put("type",               "marketing_offer");
        data.put("recommendation_id",  String.valueOf(recommendationId));
        data.put("cluster_id",         String.valueOf(clusterId));
        data.put("offer_code",         offerCode != null ? offerCode : "");
        data.put("offer_title",        offerTitle);
        data.put("offer_body",         bodyMsg);
        if (offer != null) {
            if (offer.getCashbackPct() != null) data.put("cashback_pct", offer.getCashbackPct().toPlainString());
            if (offer.getDiscountPct() != null) data.put("discount_pct", offer.getDiscountPct().toPlainString());
        }
        data.put("click_action", "FLUTTER_NOTIFICATION_CLICK");

        // Publication sur le topic
        String fcmId = fcmService.sendToTopic(topic, "🎁 " + offerTitle, bodyMsg, data);
        if (fcmId == null) {
            return SendOfferToProfileResultDTO.builder()
                    .status("ECHEC")
                    .clusterId(clusterId)
                    .topic(topic)
                    .offerCode(offerCode)
                    .title(offerTitle)
                    .message("Publication FCM échouée")
                    .build();
        }

        reco.setNotifiedAt(LocalDateTime.now());
        recommendationRepo.save(reco);

        long estimated = createClientRecommendationsForCluster(reco, clusterId);

        saveLog(null, "📡 [TOPIC " + topic + "] " + offerTitle, bodyMsg, "OFFRE_PROFIL", "ENVOYE");

        log.info("✅ Recommandation #{} diffusée au cluster {} (topic={}) — {} destinataires estimés",
                recommendationId, clusterId, topic, estimated);

        return SendOfferToProfileResultDTO.builder()
                .status("SENT")
                .clusterId(clusterId)
                .topic(topic)
                .offerCode(offerCode)
                .title(offerTitle)
                .message(bodyMsg)
                .estimatedRecipients(estimated)
                .fcmResponse(fcmId)
                .build();
    }

    /**
     * ★ FIX String : crée une entrée ClientRecommendation pour chaque client
     * dans le cluster. Source de vérité = `client_profiles` (vraie table ML),
     * pas `client_recommendations_v5` (qui était vide au premier envoi → 0 destinataires).
     *
     * Plus de UUID.fromString() qui pétait silencieusement pour les IDs non-UUID.
     */
    private long createClientRecommendationsForCluster(Recommendation reco, Integer clusterId) {
        try {
            List<ClientProfileEntity> profilesInCluster =
                    clientProfileRepo.findByClusterId(clusterId);

            if (profilesInCluster.isEmpty()) {
                log.warn("⚠️ Aucun client dans client_profiles pour cluster_id={} — " +
                        "le pipeline de classification a-t-il été exécuté ?", clusterId);
                return 0L;
            }

            // Précharger les déjà-envoyés (clientId String maintenant)
            var alreadySent = clientRecoRepo.findByRecommendationId(reco.getId()).stream()
                    .map(ClientRecommendation::getClientId)
                    .collect(Collectors.toSet());

            int created = 0;
            int skipped = 0;
            for (ClientProfileEntity profile : profilesInCluster) {
                String cid = profile.getClientId();
                if (cid == null || cid.isBlank()) continue;
                if (alreadySent.contains(cid)) {
                    skipped++;
                    continue;
                }

                ClientRecommendation cr = ClientRecommendation.builder()
                        .clientId(cid)
                        .recommendationId(reco.getId())
                        .offerCode(reco.getOfferCode())
                        .profileName(reco.getProfileName())
                        .clusterId(clusterId)
                        .status(ClientRecommendation.ClientRecoStatus.SENT)
                        .sentAt(LocalDateTime.now())
                        .build();
                clientRecoRepo.save(cr);
                created++;
            }

            log.info("📝 ClientRecommendation : {} créés, {} déjà envoyés pour cluster {} " +
                            "({} clients trouvés dans client_profiles)",
                    created, skipped, clusterId, profilesInCluster.size());
            return profilesInCluster.size();
        } catch (Exception e) {
            log.warn("⚠️ createClientRecommendationsForCluster : {}", e.getMessage(), e);
            return 0L;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  RÉPONSE CLIENTE — ACCEPTER OU REFUSER
    // ══════════════════════════════════════════════════════════════

    /**
     * ★ FIX MAJEUR : écrit dans UserInteraction (alimentation auto-apprentissage)
     * + applique en base si accepté.
     */
    @Transactional
    public ClientOfferDTO respondToOffer(Long clientRecoId, boolean accept) {
        ClientRecommendation cr = clientRecoRepo.findById(clientRecoId)
                .orElseThrow(() -> new RuntimeException("Offre introuvable : " + clientRecoId));

        if (accept) {
            cr.setStatus(ClientRecommendation.ClientRecoStatus.ACCEPTED);
            cr.setAcceptedAt(LocalDateTime.now());
            log.info("✅ Client {} a accepté l'offre #{}", cr.getClientId(), clientRecoId);
        } else {
            cr.setStatus(ClientRecommendation.ClientRecoStatus.REJECTED);
            cr.setRejectedAt(LocalDateTime.now());
            log.info("❌ Client {} a refusé l'offre #{}", cr.getClientId(), clientRecoId);
        }
        clientRecoRepo.save(cr);

        // ★ FIX CRITIQUE : écrire dans user_interactions_v5 (auto-apprentissage)
        try {
            UserInteraction interaction = UserInteraction.builder()
                    .clientId(cr.getClientId())
                    .offerCode(cr.getOfferCode())
                    .action(accept
                            ? UserInteraction.InteractionAction.accepted
                            : UserInteraction.InteractionAction.rejected)
                    .build();
            userInteractionRepo.save(interaction);
            log.debug("📊 Interaction enregistrée : client={} offre={} action={}",
                    cr.getClientId(), cr.getOfferCode(), interaction.getAction());
        } catch (Exception e) {
            log.error("⚠️ Écriture UserInteraction échouée (non-bloquant) : {}", e.getMessage());
        }

        // Application en base si accepté
        if (accept) {
            try {
                offerApplicationService.apply(cr).ifPresent(aoa ->
                        log.info("🎯 Effet appliqué : {} ({}) jusqu'au {}",
                                aoa.getEffectType(), aoa.getOfferCode(), aoa.getEndsAt())
                );
            } catch (Exception e) {
                log.error("❌ Application de l'offre #{} échouée : {}", clientRecoId, e.getMessage(), e);
            }
        }

        return toClientOfferDTO(cr);
    }

    // ══════════════════════════════════════════════════════════════
    //  LISTE DES OFFRES REÇUES PAR UN CLIENT
    // ══════════════════════════════════════════════════════════════

    /** ★ FIX String : plus de UUID.fromString() qui pétait pour les IDs non-UUID. */
    public List<ClientOfferDTO> getOffersForClient(String clientId) {
        List<ClientRecommendation> recos = clientRecoRepo
                .findByClientIdAndStatusIn(
                        clientId,
                        List.of(
                                ClientRecommendation.ClientRecoStatus.SENT,
                                ClientRecommendation.ClientRecoStatus.OPENED,
                                ClientRecommendation.ClientRecoStatus.ACCEPTED,
                                ClientRecommendation.ClientRecoStatus.REJECTED
                        )
                );
        return recos.stream().map(this::toClientOfferDTO).collect(Collectors.toList());
    }

    /**
     * ★ FIX : crée aussi une interaction "viewed" pour traquer les ouvertures
     * (signal de demi-feedback pour le scoring).
     */
    @Transactional
    public ClientOfferDTO markAsOpened(Long clientRecoId) {
        ClientRecommendation cr = clientRecoRepo.findById(clientRecoId)
                .orElseThrow(() -> new RuntimeException("Offre introuvable"));
        if (cr.getStatus() == ClientRecommendation.ClientRecoStatus.SENT) {
            cr.setStatus(ClientRecommendation.ClientRecoStatus.OPENED);
            cr.setOpenedAt(LocalDateTime.now());
            clientRecoRepo.save(cr);

            // ★ Trace la "vue" pour l'auto-apprentissage
            try {
                UserInteraction view = UserInteraction.builder()
                        .clientId(cr.getClientId())
                        .offerCode(cr.getOfferCode())
                        .action(UserInteraction.InteractionAction.viewed)
                        .build();
                userInteractionRepo.save(view);
            } catch (Exception ignored) {}
        }
        return toClientOfferDTO(cr);
    }

    // ══════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════

    private String buildDefaultBody(GeneratedOffer offer) {
        if (offer == null) return "Consultez votre nouvelle offre dans SmartWallet !";
        StringBuilder sb = new StringBuilder();
        if (offer.getCashbackPct() != null && offer.getCashbackPct().doubleValue() > 0) {
            sb.append(offer.getCashbackPct().toPlainString()).append("% de cashback. ");
        }
        if (offer.getDiscountPct() != null && offer.getDiscountPct().doubleValue() > 0) {
            sb.append(offer.getDiscountPct().toPlainString()).append("% de remise. ");
        }
        if (sb.isEmpty()) {
            sb.append("Offre exclusive sélectionnée pour vous !");
        }
        return sb.toString().trim();
    }

    private ClientOfferDTO toClientOfferDTO(ClientRecommendation cr) {
        GeneratedOffer offer = cr.getOfferCode() != null
                ? offerRepository.findByOfferCode(cr.getOfferCode()).orElse(null)
                : null;

        return ClientOfferDTO.builder()
                .id(cr.getId())
                .offerCode(cr.getOfferCode())
                .title(offer != null ? offer.getTitle() : cr.getOfferCode())
                .type(offer != null ? offer.getType() : null)
                .providerName(offer != null ? offer.getProviderName() : null)
                .category(offer != null ? offer.getCategory() : null)
                .cashbackPct(offer != null ? offer.getCashbackPct() : null)
                .discountPct(offer != null ? offer.getDiscountPct() : null)
                .minAmount(offer != null ? offer.getMinAmount() : null)
                .description(offer != null ? offer.getDescription() : null)
                .boost(offer != null ? offer.getBoost() : null)
                .generationMethod(offer != null ? offer.getGenerationMethod() : null)
                .status(cr.getStatus() != null ? cr.getStatus().name() : "UNKNOWN")
                .sentAt(cr.getSentAt())
                .acceptedAt(cr.getAcceptedAt())
                .rejectedAt(cr.getRejectedAt())
                .build();
    }

    private void saveLog(String clientId, String titre, String message, String type, String statut) {
        try {
            notificationLogRepo.save(NotificationLog.builder()
                    .id(UUID.randomUUID().toString())
                    .clientId(clientId)
                    .titre(titre)
                    .message(message)
                    .type(type)
                    .envoyePar("ADMIN")
                    .statut(statut)
                    .dateEnvoi(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.debug("Log notification ignoré: {}", e.getMessage());
        }
    }
}
