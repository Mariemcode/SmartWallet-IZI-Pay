import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:smartwallet_dashboard/models/ai_models.dart';
import 'package:smartwallet_dashboard/services/api_service.dart';

import '../config/app_config.dart';

// ═══════════════════════════════════════════════════════════════
//  SmartWallet — AiApiService + AiProvider
//
//  CORRECTION : _baseUrl utilise AppConfig.baseUrl
//  Plus d'IP hardcodée dans ce fichier
//  Source unique de vérité : lib2/config/app_config.dart
// ═══════════════════════════════════════════════════════════════
class AiApiService {

  // ✅ Utilise AppConfig — plus d'IP hardcodée ici
  static String get _baseUrl => AppConfig.baseUrl;
  static Duration get _timeout => AppConfig.aiTimeout;

  static Future<String?> _getToken() async {
    return ApiService.getToken();
  }

  static Future<Map<String, String>> _headers() async {
    final token = await _getToken();
    return {
      HttpHeaders.contentTypeHeader: 'application/json',
      if (token != null) HttpHeaders.authorizationHeader: 'Bearer $token',
    };
  }

  /// GET /api/ia/predictions/{clientId}
  /// Spring Boot calcule le solde depuis PostgreSQL et appelle FastAPI
  static Future<PredictionResponse> getPredictions(String clientId) async {
    var response = await http.get(
      Uri.parse('$_baseUrl/api/ia/predictions/$clientId'),
      headers: await _headers(),
    ).timeout(_timeout);

    // 401 : token absent/invalide → session mémoire perdue (app fermée puis rouverte)
    if (response.statusCode == 401) {
      await ApiService.logout();
      throw Exception('SESSION_INVALIDE');
    }

    if (response.statusCode == 200) {
      return PredictionResponse.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
    }
    if (response.statusCode == 404) throw Exception('Profil introuvable');
    if (response.statusCode == 401) throw Exception('Votre session a expiré. Reconnectez-vous.');
    if (response.statusCode == 503) throw Exception('Service indisponible');
    throw Exception('Erreur ${response.statusCode}');
  }
}

// ═══════════════════════════════════════════════════════════════
//  AI PROVIDER — état global de l'écran prévision
// ═══════════════════════════════════════════════════════════════
class AiProvider extends ChangeNotifier {
  String? _clientId;

  PredictionResponse? _data;
  PredictionResponse? get data => _data;

  bool    _loading = false;
  bool    get loading => _loading;

  String? _error;
  String? get error => _error;

  // ── Raccourcis directs vers les 5 modules ──────────────────
  Module1Factures?    get m1 => _data?.module1Factures;
  Module2Recharge?    get m2 => _data?.module2Recharge;
  Module3Budget?      get m3 => _data?.module3Budget;
  Module4ProchaineTx? get m4 => _data?.module4ProchaineTx;
  Module5Alerte?      get m5 => _data?.module5Alerte;

  // ── Raccourcis pratiques ────────────────────────────────────
  double  get solde   => _data?.soldeActuelTnd ?? 0.0;
  String  get segment => _data?.segment ?? 'Faible';
  String? get nom     => _data?.clientNom;

  String  get niveauAlerte    => m5?.niveauAlerte ?? 'OK';
  bool    get alerteCritique  => niveauAlerte == 'CRITIQUE';
  bool    get alerteAttention => niveauAlerte == 'ATTENTION';

  double  get budgetVariable => m3?.budgetTotalTnd ?? 0.0;
  double  get totalFactures  => m1?.totalAVenirTnd ?? 0.0;

  int get nbUrgents {
    final fUrgentes = m1?.urgentes.length ?? 0;
    final rUrgente  = (m2?.disponible == true && (m2?.joursRestants ?? 99) <= 3) ? 1 : 0;
    return fUrgentes + rUrgente;
  }

  // ── Init / Load ─────────────────────────────────────────────
  Future<void> init(String clientId) async {
    _clientId = clientId;
    await load();
  }

  Future<void> load() async {
    if (_clientId == null) return;
    _loading = true;
    _error   = null;
    notifyListeners();

    try {
      _data = await AiApiService.getPredictions(_clientId!);
    } catch (e) {
      _error = e.toString();
    } finally {
      _loading = false;
      notifyListeners();
    }
  }
}