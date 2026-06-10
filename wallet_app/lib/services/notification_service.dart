// ═══════════════════════════════════════════════════════════════════
//  SmartWallet — NotificationService MISE À JOUR
//
//  ★ AJOUT : Gestion du type "offer_received"
//    Quand le client tape une notification d'offre →
//    navigation vers ReceivedOffersScreen avec highlight sur l'offre
//
//  Le reste est identique à la version originale.
// ═══════════════════════════════════════════════════════════════════

import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'api_service.dart';
import '../config/app_config.dart';

class NotificationService {
  static final NotificationService _instance = NotificationService._internal();
  factory NotificationService() => _instance;
  NotificationService._internal();

  final FlutterLocalNotificationsPlugin _local =
  FlutterLocalNotificationsPlugin();

  Function(String clientId)? onOpenPrevision;

  /// ★ NOUVEAU callback — appelé quand le client tape une notification d'offre
  /// Paramètres : clientId, clientRecoId (id de ClientRecommendation)
  Function(String clientId, int clientRecoId)? onOpenOffer;

  bool _initialized = false;
  Timer? _pollTimer;
  String? _fcmToken;
  double? _lastSolde;

  static const AndroidNotificationChannel _channel = AndroidNotificationChannel(
    'smartwallet_urgent',
    'Alertes SmartWallet',
    description: 'Solde, factures, recommandations, offres',
    importance: Importance.max,
    playSound: true,
    enableVibration: true,
    showBadge: true,
  );

  // ══════════════════════════════════════════════════════════════
  //  INIT
  // ══════════════════════════════════════════════════════════════
  Future<void> init({
    Function(String clientId)? onOpenPrevision,
    Function(String clientId, int clientRecoId)? onOpenOffer,  // ★ NOUVEAU
  }) async {
    if (_initialized) return;
    this.onOpenPrevision = onOpenPrevision;
    this.onOpenOffer = onOpenOffer;

    try {
      final androidPlugin = _local.resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin>();

      if (androidPlugin != null) {
        await androidPlugin.createNotificationChannel(_channel);
        final granted = await androidPlugin.requestNotificationsPermission();
        debugPrint(granted == true
            ? '✅ Permission notifications Android accordée'
            : '⚠️ Permission notifications refusée');
      }

      await _local.initialize(
        const InitializationSettings(
          android: AndroidInitializationSettings('ic_launcher'),
          iOS: DarwinInitializationSettings(),
        ),
        onDidReceiveNotificationResponse: (r) => _onTap(r.payload),
      );

      await _tryInitFirebase();
      _initialized = true;
    } catch (e) {
      debugPrint('⚠️ Init notifications: $e');
      _initialized = true;
    }
  }

  // ══════════════════════════════════════════════════════════════
  //  FIREBASE INIT
  // ══════════════════════════════════════════════════════════════
  Future<void> _tryInitFirebase() async {
    try {
      final messaging = FirebaseMessaging.instance;
      final settings = await messaging.requestPermission(
        alert: true, badge: true, sound: true, provisional: false,
      );
      if (settings.authorizationStatus == AuthorizationStatus.denied) return;

      _fcmToken = await messaging.getToken();
      if (_fcmToken == null || _fcmToken!.isEmpty) return;
      await _trySendToken();

      // ★ Gestion des messages Firebase en foreground
      FirebaseMessaging.onMessage.listen(_showFirebaseNotification);

      // ★ Gestion du tap sur une notification (app en background)
      FirebaseMessaging.onMessageOpenedApp.listen(_handleFcmMessageTap);

      // ★ Gestion du tap sur notification (app fermée)
      final initial = await messaging.getInitialMessage();
      if (initial != null) _handleFcmMessageTap(initial);

    } catch (e) {
      debugPrint('❌ Firebase init: $e');
    }
  }

  // ══════════════════════════════════════════════════════════════
  //  ★ NOUVEAU — Gestion du tap sur notification Firebase
  //  Distingue "offer_received" vs "alerte_solde" vs autres
  // ══════════════════════════════════════════════════════════════
  void _handleFcmMessageTap(RemoteMessage message) {
    debugPrint('🔔 Tap notification Firebase : ${message.data}');
    _navigate(message.data);
  }

  void _navigate(Map<String, dynamic> data) {
    final type     = data['type'] as String? ?? '';
    final clientId = data['client_id'] as String? ?? '';

    if (type == 'offer_received' && onOpenOffer != null && clientId.isNotEmpty) {
      // Naviguer vers l'écran des offres reçues
      final clientRecoIdStr = data['client_reco_id'] as String? ?? '0';
      final clientRecoId = int.tryParse(clientRecoIdStr) ?? 0;
      debugPrint('📲 Navigation → offres reçues (clientRecoId=$clientRecoId)');
      onOpenOffer!(clientId, clientRecoId);
      return;
    }

    // Comportement original pour les autres types
    if (clientId.isNotEmpty && onOpenPrevision != null) {
      onOpenPrevision!(clientId);
    }
  }

  // ══════════════════════════════════════════════════════════════
  //  Affichage de notification Firebase en foreground
  // ══════════════════════════════════════════════════════════════
  void _showFirebaseNotification(RemoteMessage message) {
    debugPrint('📩 Message Firebase foreground: ${message.messageId}');
    try {
      final notification = message.notification;
      final data = message.data;
      final type = data['type'] as String? ?? '';
      final clientId = data['client_id'] as String? ?? '';

      if (notification != null) {
        _showLocalNotification(
          title: notification.title ?? 'SmartWallet',
          body: notification.body ?? '',
          niveau: type == 'offer_received' ? 'OFFRE' : (data['niveau'] ?? 'ATTENTION'),
          payload: jsonEncode(data),
        );
      }
    } catch (e) {
      debugPrint('⚠️ Erreur affichage notification: $e');
    }
  }

  // ══════════════════════════════════════════════════════════════
  //  Notification locale
  // ══════════════════════════════════════════════════════════════
  void _showLocalNotification({
    required String title,
    required String body,
    required String niveau,
    String? payload,
  }) {
    final color = niveau == 'OFFRE'
        ? const Color(0xFF6C5CE7)      // violet pour les offres
        : niveau == 'CRITIQUE'
        ? const Color(0xFFFF6B6B)  // rouge pour alerte critique
        : const Color(0xFF00E5A0); // vert sinon

    _local.show(
      DateTime.now().millisecondsSinceEpoch ~/ 1000,
      title,
      body,
      NotificationDetails(
        android: AndroidNotificationDetails(
          _channel.id, _channel.name,
          channelDescription: _channel.description,
          importance: Importance.max,
          priority: Priority.high,
          playSound: true,
          enableVibration: true,
          icon: 'ic_launcher',
          color: color,
          styleInformation: BigTextStyleInformation(body, contentTitle: title),
        ),
        iOS: const DarwinNotificationDetails(
          presentAlert: true, presentSound: true, presentBadge: true,
        ),
      ),
      payload: payload,
    );
  }

  // ══════════════════════════════════════════════════════════════
  //  Tap sur notification locale → navigation
  // ══════════════════════════════════════════════════════════════
  void _onTap(String? payload) {
    if (payload == null) return;
    try {
      final data = jsonDecode(payload) as Map<String, dynamic>;
      _navigate(data);
    } catch (_) {}
  }

  // ══════════════════════════════════════════════════════════════
  //  Enregistrement du token FCM
  // ══════════════════════════════════════════════════════════════
  Future<void> registerAfterLogin() async {
    if (_fcmToken == null) {
      try {
        _fcmToken = await FirebaseMessaging.instance.getToken();
      } catch (e) {
        debugPrint('❌ Token FCM: $e');
      }
    }
    if (_fcmToken != null) await _trySendToken();
    _startPolling();
  }

  Future<void> _trySendToken() async {
    if (_fcmToken == null) return;
    try {
      final cid = await ApiService.getClientId() ?? '';
      final jwt = await ApiService.getToken() ?? '';
      if (cid.isEmpty) return;

      await http.post(
        Uri.parse('${AppConfig.baseUrl}/api/notifications/register-token'),
        headers: {
          HttpHeaders.contentTypeHeader: 'application/json',
          if (jwt.isNotEmpty) HttpHeaders.authorizationHeader: 'Bearer $jwt',
        },
        body: jsonEncode({
          'client_id': cid,
          'fcm_token': _fcmToken,
          'platform': Platform.isAndroid ? 'android' : 'ios',
        }),
      ).timeout(const Duration(seconds: 5));
      debugPrint('✅ Token FCM enregistré');
    } catch (e) {
      debugPrint('❌ Envoi token: $e');
    }
  }

  // ══════════════════════════════════════════════════════════════
  //  Polling (fallback sans Google Play)
  // ══════════════════════════════════════════════════════════════
  void _startPolling() {
    _pollTimer?.cancel();
    _pollTimer = Timer.periodic(const Duration(seconds: 60), (_) => _poll());
  }

  Future<void> _poll() async {
    try {
      final cid = await ApiService.getClientId() ?? '';
      final jwt = await ApiService.getToken() ?? '';
      if (cid.isEmpty || jwt.isEmpty) return;

      final response = await http.get(
        Uri.parse('${AppConfig.baseUrl}/api/ia/solde/$cid'),
        headers: {HttpHeaders.authorizationHeader: 'Bearer $jwt'},
      ).timeout(const Duration(seconds: 10));

      if (response.statusCode != 200) return;
      final data = jsonDecode(response.body);
      final solde = (data['solde_TND'] as num?)?.toDouble() ?? 0;
      final niveau = data['niveau'] as String? ?? 'OK';

      if (_lastSolde != null && (solde - _lastSolde!).abs() > 1) {
        if (niveau == 'NEGATIF' || niveau == 'FAIBLE') {
          _showLocalNotification(
            title: solde < 0 ? '💳 Solde négatif !' : '👀 Solde faible',
            body: solde < 0
                ? 'Votre solde est de ${solde.toStringAsFixed(0)} TND. Rechargez votre wallet.'
                : 'Il vous reste ${solde.toStringAsFixed(0)} TND. Pensez à recharger.',
            niveau: solde < 0 ? 'CRITIQUE' : 'ATTENTION',
            payload: jsonEncode({'client_id': cid, 'action': 'open_prevision'}),
          );
        }
      }
      _lastSolde = solde;
    } catch (_) {}
  }

  Future<void> subscribeToAdminTopic() async {
    try {
      await FirebaseMessaging.instance.subscribeToTopic('admin');
    } catch (_) {}
  }

  // ══════════════════════════════════════════════════════════════
  //  TOPIC PROFIL — recommandations marketing ciblées
  //  Le client s'abonne à `profile_{clusterId}` à la connexion pour
  //  recevoir les recommandations marketing diffusées par l'admin.
  //  Le clusterId est retourné par le backend Spring lors du POST
  //  /api/notifications/register-token (champ `cluster_id`).
  // ══════════════════════════════════════════════════════════════

  /// Abonne l'appareil au topic `profile_$clusterId`.
  /// Appelé après la réponse de register-token quand le backend nous
  /// indique le cluster du client. Idempotent côté FCM.
  Future<bool> subscribeToProfileTopic(int clusterId) async {
    try {
      final topic = 'profile_$clusterId';
      await FirebaseMessaging.instance.subscribeToTopic(topic);
      debugPrint('📡 Abonné au topic FCM "$topic"');
      // Persister localement pour pouvoir se désabonner au logout
      final prefs = await SharedPreferences.getInstance();
      await prefs.setInt('subscribed_cluster_id', clusterId);
      return true;
    } catch (e) {
      debugPrint('⚠️ subscribeToProfileTopic échoué : $e');
      return false;
    }
  }

  /// Désabonne l'appareil du topic profil (logout, changement de cluster).
  Future<void> unsubscribeFromProfileTopic() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final cluster = prefs.getInt('subscribed_cluster_id');
      if (cluster == null) return;
      final topic = 'profile_$cluster';
      await FirebaseMessaging.instance.unsubscribeFromTopic(topic);
      await prefs.remove('subscribed_cluster_id');
      debugPrint('📡 Désabonné du topic FCM "$topic"');
    } catch (e) {
      debugPrint('⚠️ unsubscribeFromProfileTopic échoué : $e');
    }
  }

  /// Enregistre le token FCM auprès du backend Spring.
  /// Le backend retourne le clusterId du client → on s'abonne au topic profil.
  /// À appeler une fois après la connexion réussie.
  Future<void> registerTokenAndSubscribe(String clientId) async {
    try {
      if (_fcmToken == null) {
        _fcmToken = await FirebaseMessaging.instance.getToken();
      }
      if (_fcmToken == null) {
        debugPrint('⚠️ Aucun token FCM disponible');
        return;
      }
      final url = Uri.parse('${AppConfig.baseUrl}/api/notifications/register-token');
      final body = jsonEncode({
        'client_id': clientId,
        'fcm_token': _fcmToken,
        'platform': Platform.isAndroid ? 'android' : 'ios',
      });
      final resp = await http.post(url,
          headers: {HttpHeaders.contentTypeHeader: 'application/json'},
          body: body).timeout(const Duration(seconds: 10));
      if (resp.statusCode == 200) {
        final data = jsonDecode(resp.body) as Map<String, dynamic>;
        final clusterId = data['cluster_id'];
        if (clusterId is int) {
          await subscribeToProfileTopic(clusterId);
        } else {
          debugPrint('ℹ️ Pas de clusterId retourné — pas d\'abonnement profil');
        }
      } else {
        debugPrint('⚠️ register-token statut ${resp.statusCode}');
      }
    } catch (e) {
      debugPrint('⚠️ registerTokenAndSubscribe échoué : $e');
    }
  }

  void dispose() {
    _pollTimer?.cancel();
  }
}