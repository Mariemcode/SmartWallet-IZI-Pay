import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../models/dashboard_models.dart';

class ApiService {

  static const String baseUrl = 'http://192.168.1.68:8222';
  static const String authUrl = 'http://192.168.1.68:8222';
  static const Duration timeout = Duration(seconds: 15);

  // ════════════════════════════════════════════════════════════════
  // AUTH
  // ════════════════════════════════════════════════════════════════
  static Future<Map<String, dynamic>> login({
    required String username,
    required String password,
  }) async {
    final url = Uri.parse('$authUrl/api/auth/login');
    debugPrint('🔐 LOGIN → $url');

    final response = await http.post(
      url,
      headers: {HttpHeaders.contentTypeHeader: 'application/json'},
      body: jsonEncode({'username': username, 'password': password}),
    ).timeout(timeout);

    debugPrint('🔐 LOGIN STATUS : ${response.statusCode}');
    debugPrint('🔐 LOGIN BODY   : ${response.body.substring(0, response.body.length.clamp(0, 500))}');

    if (response.statusCode == 200) {
      final data = jsonDecode(response.body) as Map<String, dynamic>;
      debugPrint('🔐 client_id reçu : ${data['client_id']}');
      return data;
    }
    try {
      final body = jsonDecode(response.body);
      throw Exception(body['error'] ?? 'Erreur ${response.statusCode}');
    } catch (_) {
      throw Exception('Erreur ${response.statusCode}');
    }
  }

  // ════════════════════════════════════════════════════════════════
  // REGISTER
  // ════════════════════════════════════════════════════════════════
  static Future<Map<String, dynamic>> register({
    required String phone,
    required String password,
    required String firstName,
    required String lastName,
  }) async {
    final url = Uri.parse('$authUrl/api/auth/register');
    debugPrint('📝 REGISTER → $url');

    final response = await http.post(
      url,
      headers: {HttpHeaders.contentTypeHeader: 'application/json'},
      body: jsonEncode({
        'username': phone,
        'password': password,
        'firstName': firstName,
        'lastName': lastName,
      }),
    ).timeout(timeout);

    debugPrint('📝 REGISTER STATUS : ${response.statusCode}');
    debugPrint('📝 REGISTER BODY   : ${response.body}');

    if (response.statusCode == 200) {
      return jsonDecode(response.body) as Map<String, dynamic>;
    }

    // Gérer les erreurs spécifiques
    try {
      final body = jsonDecode(response.body) as Map<String, dynamic>;
      throw Exception(body['error'] ?? 'Erreur ${response.statusCode}');
    } catch (e) {
      if (e is Exception) rethrow;
      throw Exception('Erreur ${response.statusCode}');
    }
  }

  // ════════════════════════════════════════════════════════════════
  // STORAGE — EN MÉMOIRE UNIQUEMENT (RAM)
  // ─────────────────────────────────────────────────────────────────
  //  Choix d'architecture :
  //  Le token et le client_id sont conservés dans des variables statiques
  //  (mémoire vive). Ils vivent donc EXACTEMENT le temps que l'application
  //  reste ouverte :
  //    • Navigation libre tant que l'app tourne   → token présent en RAM
  //    • Fermeture complète de l'app              → RAM effacée → reconnexion
  //  Aucune écriture sur disque (pas de SharedPreferences) : c'est ce qui
  //  garantit la reconnexion obligatoire au prochain lancement.
  // ════════════════════════════════════════════════════════════════
  static String? _accessToken;
  static String? _clientId;

  static Future<void> logout() async {
    _accessToken = null;
    _clientId = null;
    debugPrint('🚪 Déconnexion — token et client_id effacés de la mémoire');
  }

  static Future<String?> getToken() async => _accessToken;

  static Future<void> saveToken(String token) async {
    _accessToken = token;
    debugPrint('💾 Token gardé en mémoire : ${token.substring(0, 20)}...');
  }

  static Future<void> saveClientId(String clientId) async {
    _clientId = clientId;
    debugPrint('💾 ClientId gardé en mémoire : $clientId');
  }

  static Future<String?> getClientId() async => _clientId;

  // ════════════════════════════════════════════════════════════════
  // HEADERS
  // ════════════════════════════════════════════════════════════════
  static Future<Map<String, String>> _headers() async {
    final token = await getToken();
    debugPrint('🔑 Token dans headers : ${token != null ? "${token.substring(0, 20)}..." : "NULL ⚠️"}');
    return {
      HttpHeaders.contentTypeHeader: 'application/json',
      if (token != null)
        HttpHeaders.authorizationHeader: 'Bearer $token',
    };
  }

  // ════════════════════════════════════════════════════════════════
  // PARSE avec logs
  // ════════════════════════════════════════════════════════════════
  static Future<T> _parse<T>(http.Response response, T Function(dynamic json) parser) async {
    debugPrint('═══════════════════════════════');
    debugPrint('📡 URL    : ${response.request?.url}');
    debugPrint('📡 STATUS : ${response.statusCode}');
    debugPrint('📡 BODY   : ${response.body.substring(0, response.body.length.clamp(0, 400))}');
    debugPrint('═══════════════════════════════');

    if (response.statusCode == 200) {
      return parser(jsonDecode(response.body));
    }
    if (response.statusCode == 401) {
      // Token absent / invalide : la session mémoire n'est plus valable.
      // (Cas normal uniquement après fermeture-réouverture de l'app.)
      await logout();
      throw Exception('SESSION_INVALIDE');
    }
    if (response.statusCode == 404) {
      throw Exception('Ressource introuvable');
    }
    throw Exception('Erreur serveur ${response.statusCode}');
  }

  // ════════════════════════════════════════════════════════════════
  //  1. RÉSUMÉ WALLET
  // ════════════════════════════════════════════════════════════════
  static Future<WalletSummary> getWalletSummary(String clientId) async {
    debugPrint('📊 getWalletSummary → clientId: $clientId');
    final response = await http.get(
      Uri.parse('$baseUrl/dashboard/$clientId/summary'),
      headers: await _headers(),
    ).timeout(timeout);
    return _parse(response, (json) => WalletSummary.fromJson(json));
  }

  // ════════════════════════════════════════════════════════════════
  //  2. PROFIL CLIENT
  // ════════════════════════════════════════════════════════════════
  static Future<ClientProfile> getClientProfile(String clientId) async {
    final response = await http.get(
      Uri.parse('$baseUrl/dashboard/$clientId/profile'),
      headers: await _headers(),
    ).timeout(timeout);
    return _parse(response, (json) => ClientProfile.fromJson(json));
  }

  // ════════════════════════════════════════════════════════════════
  //  3. HISTORIQUE
  // ════════════════════════════════════════════════════════════════
  static Future<TransactionPage> getHistory(
      String clientId, {
        String? category,
        String? flowType,
        int page = 0,
        int size = 20,
      }) async {
    final params = <String, String>{
      'page': page.toString(),
      'size': size.toString(),
      if (category != null && category.isNotEmpty) 'category': category,
      if (flowType != null && flowType.isNotEmpty) 'flowType': flowType,
    };
    final response = await http.get(
      Uri.parse('$baseUrl/dashboard/$clientId/history')
          .replace(queryParameters: params),
      headers: await _headers(),
    ).timeout(timeout);
    return _parse(response, (json) => TransactionPage.fromJson(json));
  }

  // ════════════════════════════════════════════════════════════════
  //  4. DÉTAIL TRANSACTION
  // ════════════════════════════════════════════════════════════════
  static Future<TransactionDetail> getTransactionDetail(
      String clientId, String txId) async {
    final response = await http.get(
      Uri.parse('$baseUrl/dashboard/$clientId/transactions/$txId'),
      headers: await _headers(),
    ).timeout(timeout);
    return _parse(response, (json) => TransactionDetail.fromJson(json));
  }

  // ════════════════════════════════════════════════════════════════
  //  5. CATÉGORIES
  // ════════════════════════════════════════════════════════════════
  static Future<List<String>> getCategories(String clientId) async {
    final response = await http.get(
      Uri.parse('$baseUrl/dashboard/$clientId/categories/list'),
      headers: await _headers(),
    ).timeout(timeout);
    return _parse(response,
            (json) => (json as List).map((e) => e as String).toList());
  }

  // ════════════════════════════════════════════════════════════════
  //  6. RÉPARTITION CATÉGORIES
  // ════════════════════════════════════════════════════════════════
  static Future<List<CategoryBreakdown>> getCategoryBreakdown(
      String clientId, {
        String flowType = 'D',
        String period = 'THIS_MONTH',
      }) async {
    final response = await http.get(
      Uri.parse('$baseUrl/dashboard/$clientId/categories/breakdown')
          .replace(queryParameters: {'flowType': flowType, 'period': period}),
      headers: await _headers(),
    ).timeout(timeout);
    return _parse(response, (json) =>
        (json as List).map((e) => CategoryBreakdown.fromJson(e)).toList());
  }

  // ════════════════════════════════════════════════════════════════
  //  7. TOP CATÉGORIES
  // ════════════════════════════════════════════════════════════════
  static Future<List<TopCategory>> getTopCategories(
      String clientId, {
        String period = 'THIS_MONTH',
      }) async {
    final response = await http.get(
      Uri.parse('$baseUrl/dashboard/$clientId/categories/top')
          .replace(queryParameters: {'period': period}),
      headers: await _headers(),
    ).timeout(timeout);
    return _parse(response,
            (json) => (json as List).map((e) => TopCategory.fromJson(e)).toList());
  }

  // ════════════════════════════════════════════════════════════════
  //  8. FLUX CATÉGORIES
  // ════════════════════════════════════════════════════════════════
  static Future<List<CategoryFlow>> getCategoryFlow(
      String clientId, {
        String period = 'THIS_MONTH',
      }) async {
    final response = await http.get(
      Uri.parse('$baseUrl/dashboard/$clientId/categories/flow')
          .replace(queryParameters: {'period': period}),
      headers: await _headers(),
    ).timeout(timeout);
    return _parse(response,
            (json) => (json as List).map((e) => CategoryFlow.fromJson(e)).toList());
  }

  // ════════════════════════════════════════════════════════════════
  //  9. ÉVOLUTION MENSUELLE
  // ════════════════════════════════════════════════════════════════
  static Future<List<MonthlyEvolution>> getMonthlyEvolution(
      String clientId, {
        int months = 12,
      }) async {
    final response = await http.get(
      Uri.parse('$baseUrl/dashboard/$clientId/charts/monthly')
          .replace(queryParameters: {'months': months.toString()}),
      headers: await _headers(),
    ).timeout(timeout);
    return _parse(response, (json) =>
        (json as List).map((e) => MonthlyEvolution.fromJson(e)).toList());
  }

  // ════════════════════════════════════════════════════════════════
  //  10. ÉVOLUTION HEBDOMADAIRE
  // ════════════════════════════════════════════════════════════════
  static Future<List<WeeklyEvolution>> getWeeklyEvolution(
      String clientId) async {
    final response = await http.get(
      Uri.parse('$baseUrl/dashboard/$clientId/charts/weekly'),
      headers: await _headers(),
    ).timeout(timeout);
    return _parse(response, (json) =>
        (json as List).map((e) => WeeklyEvolution.fromJson(e)).toList());
  }

  // ════════════════════════════════════════════════════════════════
  //  11. STATISTIQUES
  // ════════════════════════════════════════════════════════════════
  static Future<PeriodStats> getPeriodStats(
      String clientId, {
        String period = 'THIS_MONTH',
      }) async {
    final response = await http.get(
      Uri.parse('$baseUrl/dashboard/$clientId/stats')
          .replace(queryParameters: {'period': period}),
      headers: await _headers(),
    ).timeout(timeout);
    return _parse(response, (json) => PeriodStats.fromJson(json));
  }

  // ════════════════════════════════════════════════════════════════
  //  12. TYPES DE TRANSACTION (dropdown)
  // ════════════════════════════════════════════════════════════════
  static Future<List<Map<String, dynamic>>> getTransactionTypes(
      String clientId) async {
    final response = await http.get(
      Uri.parse('$baseUrl/dashboard/$clientId/transaction-types'),
      headers: await _headers(),
    ).timeout(timeout);
    return _parse(response, (json) =>
        (json as List).map((e) => Map<String, dynamic>.from(e)).toList());
  }

  // ════════════════════════════════════════════════════════════════
  //  13. PROVIDERS (dropdown)
  // ════════════════════════════════════════════════════════════════
  static Future<List<Map<String, dynamic>>> getProviders(
      String clientId) async {
    final response = await http.get(
      Uri.parse('$baseUrl/dashboard/$clientId/providers'),
      headers: await _headers(),
    ).timeout(timeout);
    return _parse(response, (json) =>
        (json as List).map((e) => Map<String, dynamic>.from(e)).toList());
  }

  // ════════════════════════════════════════════════════════════════
  //  14. CRÉER UNE TRANSACTION
  // ════════════════════════════════════════════════════════════════
  static Future<Map<String, dynamic>> createTransaction(
      String clientId, {
        required double amount,
        required String transactionTypeId,
        String? receiverId,
        String? providerId,
      }) async {
    final url = Uri.parse('$baseUrl/dashboard/$clientId/transactions');
    debugPrint('💸 CREATE TX → $url');

    final bodyMap = <String, dynamic>{
      'amount': amount,
      'transactionTypeId': transactionTypeId,
    };
    if (receiverId != null && receiverId.isNotEmpty) {
      bodyMap['receiverId'] = receiverId;
    }
    if (providerId != null && providerId.isNotEmpty) {
      bodyMap['providerId'] = providerId;
    }

    final response = await http.post(
      url,
      headers: await _headers(),
      body: jsonEncode(bodyMap),
    ).timeout(timeout);

    debugPrint('💸 CREATE TX STATUS : ${response.statusCode}');
    debugPrint('💸 CREATE TX BODY   : ${response.body}');

    if (response.statusCode == 201 || response.statusCode == 200) {
      return jsonDecode(response.body) as Map<String, dynamic>;
    }

    try {
      final body = jsonDecode(response.body) as Map<String, dynamic>;
      throw Exception(body['error'] ?? 'Erreur ${response.statusCode}');
    } catch (e) {
      if (e is Exception) rethrow;
      throw Exception('Erreur ${response.statusCode}');
    }
  }

  /// Profil client complet (Map brute pour l'écran profil)
  static Future<Map<String, dynamic>> getClientProfileRaw(String clientId) async {
    final response = await http.get(
      Uri.parse('$baseUrl/dashboard/$clientId/profile'),
      headers: await _headers(),
    ).timeout(timeout);
    if (response.statusCode == 200) {
      return jsonDecode(response.body) as Map<String, dynamic>;
    }
    throw Exception('Erreur ${response.statusCode}');
  }
}