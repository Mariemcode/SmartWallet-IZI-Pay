package com.pfe.clientdashboard.notification.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.pfe.clientdashboard.notification.entities.NotificationLog;
import com.pfe.clientdashboard.notification.repository.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class FcmService {
    @Autowired
    private NotificationLogRepository notificationLogRepo;

    private static final Logger log = LoggerFactory.getLogger(FcmService.class);

    @Value("${firebase.credentials-path:}")
    private String credentialsPath;

    @Value("${firebase.project-id:smartwallet-pfe}")
    private String projectId;

    private FirebaseMessaging messaging;

    @PostConstruct
    public void init() {
        log.info("═══════════════════════════════════════════════");
        log.info("🔥 DÉMARRAGE INITIALISATION FIREBASE");
        log.info("═══════════════════════════════════════════════");
        log.info("   credentialsPath = '{}'", credentialsPath);
        log.info("   projectId = '{}'", projectId);

        if (credentialsPath == null || credentialsPath.isEmpty()) {
            log.error("❌ credentialsPath est VIDE ou NULL !");
            log.error("   Vérifiez votre application.yml :");
            log.error("   firebase:");
            log.error("     credentials-path: C:/Users/MARIEM/Desktop/pfe/wallet/services/client-dashboard/serviceAccountKey.json");
            messaging = null;
            return;
        }

        try {
            InputStream serviceAccount = null;

            // Essayer plusieurs méthodes pour trouver le fichier
            log.info("🔍 Recherche du fichier serviceAccountKey.json...");

            // Méthode 1 : Chemin absolu
            File file1 = new File(credentialsPath);
            log.info("   Méthode 1 (absolu) : {} → existe ? {}", credentialsPath, file1.exists());
            if (file1.exists()) {
                serviceAccount = new FileInputStream(file1);
                log.info("   ✅ Fichier trouvé via chemin absolu");
            }

            // Méthode 2 : Classpath
            if (serviceAccount == null) {
                String classpathPath = credentialsPath.replace("\\", "/");
                if (classpathPath.contains("serviceAccountKey.json")) {
                    classpathPath = "serviceAccountKey.json";
                }
                log.info("   Méthode 2 (classpath) : {}", classpathPath);
                try {
                    serviceAccount = getClass().getClassLoader().getResourceAsStream(classpathPath);
                    if (serviceAccount != null) {
                        log.info("   ✅ Fichier trouvé via classpath");
                    } else {
                        log.warn("   ❌ Non trouvé via classpath");
                    }
                } catch (Exception e) {
                    log.warn("   ❌ Erreur classpath: {}", e.getMessage());
                }
            }

            // Méthode 3 : Chemins connus
            if (serviceAccount == null) {
                String[] fallbacks = {
                        "C:/Users/MARIEM/Desktop/pfe/wallet/services/client-dashboard/serviceAccountKey.json",
                        "C:/Users/MARIEM/Desktop/pfe/wallet/services/client-dashboard/src/main/resources/serviceAccountKey.json",
                        "./serviceAccountKey.json",
                        "../serviceAccountKey.json"
                };
                for (String fb : fallbacks) {
                    File f = new File(fb);
                    if (f.exists()) {
                        serviceAccount = new FileInputStream(f);
                        log.info("   ✅ Fichier trouvé via fallback: {}", fb);
                        break;
                    }
                }
            }

            if (serviceAccount == null) {
                log.error("═══════════════════════════════════════════════");
                log.error("❌ IMPOSSIBLE DE TROUVER serviceAccountKey.json");
                log.error("   Vérifiez que le fichier existe à :");
                log.error("   {}", credentialsPath);
                log.error("═══════════════════════════════════════════════");
                messaging = null;
                return;
            }

            log.info("✅ Fichier serviceAccountKey.json chargé");

            GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccount);
            log.info("✅ GoogleCredentials créées");

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(projectId)
                    .build();
            log.info("✅ FirebaseOptions construites");

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("✅ FirebaseApp initialisée");
            } else {
                log.info("✅ FirebaseApp déjà existante");
            }

            this.messaging = FirebaseMessaging.getInstance();
            log.info("═══════════════════════════════════════════════");
            log.info("✅ FIREBASE FCM INITIALISÉ AVEC SUCCÈS !");
            log.info("═══════════════════════════════════════════════");

        } catch (Exception e) {
            log.error("═══════════════════════════════════════════════");
            log.error("❌ ERREUR INITIALISATION FIREBASE:");
            log.error("   Type: {}", e.getClass().getName());
            log.error("   Message: {}", e.getMessage());
            log.error("═══════════════════════════════════════════════");
            e.printStackTrace();
            this.messaging = null;
        }
    }

    public void sendAlerteClient(String fcmToken, String niveau, String message, String clientId) {
        if (messaging == null) {
            log.error("❌ Firebase non initialisé");
            saveLog(clientId, getTitre(niveau), message, niveau, "SYSTEM", "ECHEC", null);
            return;
        }
        if (fcmToken == null || fcmToken.isEmpty()) {
            log.warn("⚠️ Token FCM vide pour client {}", clientId);
            saveLog(clientId, getTitre(niveau), message, niveau, "SYSTEM", "ECHEC", null);
            return;
        }

        String title = "CRITIQUE".equals(niveau)
                ? "💳 Votre wallet a besoin d'un rechargement"
                : "👀 Pensez à recharger votre wallet";

        Message fcmMessage = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder().setTitle(title).setBody(message).build())
                .putData("type", "alerte_solde")
                .putData("niveau", niveau)
                .putData("client_id", clientId)
                .putData("click_action", "FLUTTER_NOTIFICATION_CLICK")
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setSound("default")
                                .setChannelId("smartwallet_urgent")
                                .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                                .setColor("CRITIQUE".equals(niveau) ? "#FF6B6B" : "#FFE566")
                                .build())
                        .build())
                .build();

        CompletableFuture.runAsync(() -> {
            try {
                String response = messaging.send(fcmMessage);
                log.info("✅ Notification envoyée à {} - Response: {}",
                        clientId.substring(0, Math.min(8, clientId.length())), response);
                saveLog(clientId, title, message, niveau, "SYSTEM", "ENVOYE", LocalDateTime.now());
            } catch (FirebaseMessagingException e) {
                log.error("❌ Erreur FCM ({}): {}", e.getMessagingErrorCode(), e.getMessage());
                saveLog(clientId, title, message, niveau, "SYSTEM", "ECHEC", null);
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    log.warn("⚠️ Token FCM invalide pour client {}", clientId);
                }
            } catch (Exception e) {
                log.error("❌ Erreur inattendue: {}", e.getMessage(), e);
                saveLog(clientId, title, message, niveau, "SYSTEM", "ECHEC", null);
            }
        });
    }
    public void sendMulticast(List<String> tokens, String title, String body, String type) {
        if (messaging == null || tokens == null || tokens.isEmpty()) return;
        CompletableFuture.runAsync(() -> {
            try {
                MulticastMessage message = MulticastMessage.builder()
                        .addAllTokens(tokens)
                        .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                        .putData("type", type)
                        .putData("click_action", "FLUTTER_NOTIFICATION_CLICK")
                        .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                        .build();
                BatchResponse response = messaging.sendEachForMulticast(message);
                log.info("📱 Multicast: {}/{} succès", response.getSuccessCount(), tokens.size());
                saveLog(null, title, body, type, "SCHEDULER", "ENVOYE", LocalDateTime.now());
            } catch (FirebaseMessagingException e) {
                log.error("❌ Erreur multicast: {}", e.getMessage());
                saveLog(null, title, body, type, "SCHEDULER", "ECHEC", null);
            }
        });
    }

    public void sendRappelFacture(String fcmToken, String fournisseur, double montant,
                                  int joursRestants, String clientId) {
        if (messaging == null || fcmToken == null) return;
        String quand = joursRestants == 0 ? "aujourd'hui"
                : joursRestants == 1 ? "demain"
                : "dans " + joursRestants + " jours";
        String title = "📋 Facture " + fournisseur + " — " + quand;
        String body = String.format("Prévoyez environ %.0f TND", montant);

        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putData("type", "facture_urgente")
                .putData("client_id", clientId)
                .putData("click_action", "FLUTTER_NOTIFICATION_CLICK")
                .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                .build();

        CompletableFuture.runAsync(() -> {
            try {
                messaging.send(message);
                log.info("✅ Rappel facture envoyé à {}", clientId);
                saveLog(clientId, title, body, "FACTURE", "SCHEDULER", "ENVOYE", LocalDateTime.now());
            } catch (Exception e) {
                log.error("❌ Erreur rappel facture: {}", e.getMessage());
                saveLog(clientId, title, body, "FACTURE", "SCHEDULER", "ECHEC", null);
            }
        });
    }
    public void sendAdminNotification(String title, String body) {
        if (messaging == null) return;
        CompletableFuture.runAsync(() -> {
            try {
                Message message = Message.builder()
                        .setTopic("admin")
                        .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                        .build();
                messaging.send(message);
                log.info("✅ Notification admin envoyée");
                saveLog(null, title, body, "ADMIN", "SYSTEM", "ENVOYE", LocalDateTime.now());
            } catch (Exception e) {
                log.error("❌ Erreur notification admin: {}", e.getMessage());
                saveLog(null, title, body, "ADMIN", "SYSTEM", "ECHEC", null);
            }
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  TOPICS — diffusion à un groupe d'utilisateurs (par profil ML)
    //  Utilisé pour les RECOMMANDATIONS MARKETING ciblées sur un cluster.
    //  La version gratuite de FCM supporte les topics nativement.
    // ══════════════════════════════════════════════════════════════

    /**
     * Publie un message FCM sur un topic.
     * Tous les devices abonnés au topic reçoivent le push.
     *
     * @param topic     ex. "profile_3" pour le cluster ID 3
     * @param title     titre de la notif
     * @param body      corps du message
     * @param data      payload data (peut être null)
     * @return ID de message FCM, ou null en cas d'échec
     */
    public String sendToTopic(String topic, String title, String body,
                              java.util.Map<String, String> data) {
        if (messaging == null) {
            log.warn("⚠️ FCM non initialisé — sendToTopic skip");
            return null;
        }
        try {
            Message.Builder builder = Message.builder()
                    .setTopic(topic)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setSound("default")
                                    .setChannelId("smartwallet_urgent")
                                    .setColor("#00E5A0")
                                    .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                                    .build())
                            .build());
            if (data != null) {
                data.forEach(builder::putData);
            }
            String fcmId = messaging.send(builder.build());
            log.info("✅ Topic '{}' notifié — fcmId={}", topic, fcmId);
            saveLog(null, title, body, "TOPIC:" + topic, "SYSTEM", "ENVOYE", LocalDateTime.now());
            return fcmId;
        } catch (Exception e) {
            log.error("❌ sendToTopic({}) échoué : {}", topic, e.getMessage());
            saveLog(null, title, body, "TOPIC:" + topic, "SYSTEM", "ECHEC", null);
            return null;
        }
    }

    /**
     * Abonne une liste de tokens FCM à un topic.
     * Utilisé quand un client s'authentifie : son token est abonné au topic
     * profile_{clusterId} pour recevoir les offres ciblant son profil.
     *
     * @return nombre d'abonnements réussis
     */
    public int subscribeTokensToTopic(List<String> tokens, String topic) {
        if (messaging == null || tokens == null || tokens.isEmpty()) return 0;
        try {
            TopicManagementResponse resp = messaging.subscribeToTopic(tokens, topic);
            log.info("✅ Subscribe topic '{}' : {} succès, {} échecs",
                    topic, resp.getSuccessCount(), resp.getFailureCount());
            return resp.getSuccessCount();
        } catch (Exception e) {
            log.error("❌ subscribeTokensToTopic({}) échoué : {}", topic, e.getMessage());
            return 0;
        }
    }

    /** Désabonne des tokens d'un topic (logout, changement de cluster, etc.). */
    public int unsubscribeTokensFromTopic(List<String> tokens, String topic) {
        if (messaging == null || tokens == null || tokens.isEmpty()) return 0;
        try {
            TopicManagementResponse resp = messaging.unsubscribeFromTopic(tokens, topic);
            log.info("✅ Unsubscribe topic '{}' : {} succès, {} échecs",
                    topic, resp.getSuccessCount(), resp.getFailureCount());
            return resp.getSuccessCount();
        } catch (Exception e) {
            log.error("❌ unsubscribeTokensFromTopic({}) échoué : {}", topic, e.getMessage());
            return 0;
        }
    }

    /** Helper : abonne un seul token au topic profile_{clusterId}. */
    public boolean subscribeClientToProfile(String token, Integer clusterId) {
        if (token == null || clusterId == null) return false;
        return subscribeTokensToTopic(List.of(token), "profile_" + clusterId) > 0;
    }

    private String getTitre(String niveau) {
        return "CRITIQUE".equals(niveau) ? "💳 Rechargement urgent" : "⚠️ Solde faible";
    }

    // ✅ Nouvelle méthode de logging
    private void saveLog(String clientId, String titre, String message,
                         String type, String envoyePar, String statut, LocalDateTime dateEnvoi) {
        try {
            if (notificationLogRepo == null) return;
            NotificationLog logEntry = NotificationLog.builder()
                    .id(UUID.randomUUID().toString())
                    .clientId(clientId)
                    .titre(titre)
                    .message(message)
                    .type(type)
                    .envoyePar(envoyePar)
                    .statut(statut)
                    .dateEnvoi(dateEnvoi)
                    .build();
            notificationLogRepo.save(logEntry);
        } catch (Exception e) {
            log.debug("Logging notification ignoré: {}", e.getMessage());
        }
    }
}
