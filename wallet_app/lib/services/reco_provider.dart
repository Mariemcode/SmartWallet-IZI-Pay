import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'api_service.dart';
import '../config/app_config.dart';

// ════════════════════════════════════════════════════════════
//  SmartWallet — RecoService + RecoProvider
//
//  VERSION FUSIONNÉE v8 + v9
//
//  • v9 : architecture live (conseils, budgetLive, challenges,
//          alertes, resumeMois)
//  • v8 : getters de compatibilité (recommandations, budgetCible,
//          forfaitTelecom, segmentComportemental, nbRecosFraiches,
//          isLive) pour tout code qui utilisait l'ancien provider
//
//  Le JSON reçu varie selon l'endpoint appelé par Spring Boot :
//    - /recommendations/live/ → structure v9
//    - /recommendations/      → structure v8 (fallback)
//  Le provider gère les deux transparentement.
// ════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────
//  SERVICE — appels réseau
// ─────────────────────────────────────────────────────────────
class RecoService {
  static Future<Map<String, dynamic>> get(String clientId) async {
    final token = await ApiService.getToken();
    final url = Uri.parse('${AppConfig.baseUrl}/api/ia/recommendations/$clientId');
    final r = await http.get(url, headers: {
      HttpHeaders.contentTypeHeader: 'application/json',
      if (token != null) HttpHeaders.authorizationHeader: 'Bearer $token',
    }).timeout(AppConfig.aiTimeout);
    if (r.statusCode == 401) throw Exception('SESSION_EXPIRED');
    if (r.statusCode == 200) return jsonDecode(r.body);
    if (r.statusCode == 404) throw Exception('Profil introuvable');
    if (r.statusCode == 503) throw Exception('Service indisponible');
    throw Exception('Erreur ${r.statusCode}');
  }
}

// ─────────────────────────────────────────────────────────────
//  PROVIDER — état + parsing
// ─────────────────────────────────────────────────────────────
class RecoProvider extends ChangeNotifier {
  String? _clientId;
  Map<String, dynamic>? _data;
  bool _loading = false;
  String? _error;

  bool get loading => _loading;
  String? get error => _error;

  // ── Données v9 (structure live) ───────────────────────────
  String _segment = 'Client';
  List<Map<String, dynamic>> _conseils = [];
  List<Map<String, dynamic>> _budgetLive = [];
  List<Map<String, dynamic>> _challenges = [];
  List<Map<String, dynamic>> _alertes = [];
  // Gamification SUPPRIMÉE : remplacée par les offres marketing
  // réellement appliquées en base (cf. ActiveOfferApplication côté Spring).
  Map<String, dynamic> _resumeMois = {};
  double _economiePotentielle = 0;
  List<Map<String, dynamic>> _newlyCompletedChallenges = [];
  List<Map<String, dynamic>> get newlyCompletedChallenges => _newlyCompletedChallenges;

  // ✅ SPRINT 2 : Getter pour savoir s'il y a des célébrations à afficher
  bool get hasNewlyCompleted => _newlyCompletedChallenges.isNotEmpty;

  // Getters v9
  String get segment => _segment;
  List<Map<String, dynamic>> get conseils => _conseils;
  List<Map<String, dynamic>> get budgetLive => _budgetLive;
  List<Map<String, dynamic>> get challenges => _challenges;
  List<Map<String, dynamic>> get alertes => _alertes;
  Map<String, dynamic> get resumeMois => _resumeMois;
  double get economiePotentielle => _economiePotentielle;
  int get nbConseils => _conseils.length;
  int get nbChallenges => _challenges.length;
  int get nbAlertes => _alertes.length;

  // ── Getters v8 (compatibilité ascendante) ─────────────────
  // Utilisés par RecommendationScreen v8 (onglet Conseils / Mon budget)
  // Mappage transparent depuis les données v9 ou v8 selon la réponse reçue.

  /// Recommandations (v8 : 'recommandations', v9 : champ 'conseils')
  List<dynamic> get recommandations {
    // Priorité : champ 'recommandations' direct (réponse v8/live-fallback)
    final raw = _data?['recommandations'];
    if (raw is List && raw.isNotEmpty) {
      return raw.whereType<Map<String, dynamic>>().toList();
    }
    // Sinon : réutiliser les conseils v9
    return _conseils;
  }

  /// Budget cible (v8 : 'budget_cible', absent en v9 live → liste vide)
  List<dynamic> get budgetCible {
    final raw = _data?['budget_cible'];
    if (raw is List) return raw.whereType<Map<String, dynamic>>().toList();
    return const [];
  }

  /// Budget targets (cibles par catégorie vs pairs)
  List<Map<String, dynamic>> get budgetTargets {
    final raw = _data?['budget_targets'];
    if (raw is List) return raw.whereType<Map<String, dynamic>>().toList();
    return const [];
  }

  /// Forfait télécom (suggestions de changement de forfait)
  List<dynamic> get forfaitTelecom {
    final raw = _data?['forfait_telecom'] ?? _data?['budget_optim'];
    if (raw is List) return raw.whereType<Map<String, dynamic>>().toList();
    return const [];
  }

  /// Segment comportemental (v8 : 'segment_comportemental', v9 : 'segment')
  String get segmentComportemental =>
      _data?['segment_comportemental'] as String? ?? _segment;

  /// Nombre de recommandations fraîches (v8 live : 'nb_recos_fraiches')
  int get nbRecosFraiches {
    final n = _data?['nb_recos_fraiches'];
    if (n is int) return n;
    // Compter manuellement dans recommandations
    return recommandations
        .whereType<Map<String, dynamic>>()
        .where((r) => r['fresh'] == true)
        .length;
  }

  /// Vrai si la réponse vient d'un endpoint live
  bool get isLive =>
      _data?['module'] == '6_recommandation_live' || nbRecosFraiches > 0;

  // ── Messages d'erreur ──────────────────────────────────────
  String get errorMessage {
    if (_error == null) return '';
    final e = _error!;
    if (e.contains('SESSION_EXPIRED')) {
      return 'Session expirée.\nVeuillez vous reconnecter.';
    }
    if (e.contains('Service indisponible') || e.contains('503')) {
      return 'Service temporairement indisponible.\nRéessayez dans quelques instants.';
    }
    if (e.contains('Profil introuvable') || e.contains('404')) {
      return 'Profil introuvable.\nContactez le support.';
    }
    if (e.contains('SocketException') || e.contains('Timeout')) {
      return 'Pas de connexion internet.\nVérifiez votre réseau.';
    }
    return 'Erreur de chargement.\nTirez vers le bas pour réessayer.';
  }

  // ── Chargement ─────────────────────────────────────────────
  Future<void> load(String clientId) async {
    _clientId = clientId;
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      _data = await RecoService.get(clientId);
      _parse();
    } catch (e) {
      _error = e.toString();
    } finally {
      _loading = false;
      notifyListeners();
    }

  }

  // ── Parsing — supporte structure v8 ET v9 ─────────────────
  void _parse() {
    if (_data == null) return;

    // Segment : v9 utilise 'segment', v8 utilise 'segment_comportemental'
    _segment = _data!['segment'] as String?
        ?? _data!['segment_comportemental'] as String?
        ?? 'Client';

    // Conseils : v9 → 'conseils', v8 → 'recommandations'
    _conseils = _listOf('conseils');
    if (_conseils.isEmpty) {
      _conseils = _listOf('recommandations');
    }

    _budgetLive = _listOf('budget_live');
    _challenges = _listOf('challenges');
    _newlyCompletedChallenges = _challenges
        .where((c) => c['newly_completed'] == true)
        .toList();

    // Alertes : v9 → 'alertes', v8 → 'alertes_comportementales'
    _alertes = _listOf('alertes');
    if (_alertes.isEmpty) {
      _alertes = _listOf('alertes_comportementales');
    }

    _resumeMois = _data!['resume_mois'] as Map<String, dynamic>? ?? {};

    // Économie potentielle
    final resume = _data!['resume'] as Map<String, dynamic>? ?? {};
    _economiePotentielle =
        (resume['economie_potentielle_tnd'] as num?)?.toDouble() ?? 0;

    // Fallback : calculer depuis les recommandations si absent
    if (_economiePotentielle == 0) {
      for (final r in recommandations.whereType<Map<String, dynamic>>()) {
        _economiePotentielle += (r['economie_tnd'] as num?)?.toDouble() ?? 0;
      }
      for (final f in forfaitTelecom.whereType<Map<String, dynamic>>()) {
        _economiePotentielle +=
            (f['economie_mensuelle'] as num?)?.toDouble() ?? 0;
      }
    }
    notifyListeners();
  }
  void clearNewlyCompleted() {
    _newlyCompletedChallenges.clear();
    for (var c in _challenges) {
      c['newly_completed'] = false;
    }
    notifyListeners();
  }

  List<Map<String, dynamic>> _listOf(String key) =>
      (_data![key] as List? ?? []).whereType<Map<String, dynamic>>().toList();

  Future<void> reload() async {
    if (_clientId != null) await load(_clientId!);
  }
}