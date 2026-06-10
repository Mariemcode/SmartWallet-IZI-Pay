import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'api_service.dart';
import '../config/app_config.dart';

// ──────────────────────────────────────────────────────────────
//  Modèle d'offre reçue (côté client mobile)
// ──────────────────────────────────────────────────────────────
class ReceivedOffer {
  final int id;                   // ClientRecommendation.id
  final String offerCode;
  final String title;
  final String? type;
  final String? providerName;
  final String? category;
  final double cashbackPct;
  final double discountPct;
  final double minAmount;
  final String? description;
  final String status;            // SENT | OPENED | ACCEPTED | REJECTED
  final DateTime? sentAt;
  final DateTime? acceptedAt;
  final DateTime? rejectedAt;

  const ReceivedOffer({
    required this.id,
    required this.offerCode,
    required this.title,
    this.type,
    this.providerName,
    this.category,
    required this.cashbackPct,
    required this.discountPct,
    required this.minAmount,
    this.description,
    required this.status,
    this.sentAt,
    this.acceptedAt,
    this.rejectedAt,
  });

  factory ReceivedOffer.fromJson(Map<String, dynamic> j) => ReceivedOffer(
    id:           j['id'] as int,
    offerCode:    j['offerCode'] as String? ?? '',
    title:        j['title'] as String? ?? 'Offre SmartWallet',
    type:         j['type'] as String?,
    providerName: j['providerName'] as String?,
    category:     j['category'] as String?,
    cashbackPct:  (j['cashbackPct'] as num?)?.toDouble() ?? 0,
    discountPct:  (j['discountPct'] as num?)?.toDouble() ?? 0,
    minAmount:    (j['minAmount'] as num?)?.toDouble() ?? 0,
    description:  j['description'] as String?,
    status:       j['status'] as String? ?? 'SENT',
    sentAt:       j['sentAt'] != null ? DateTime.tryParse(j['sentAt'] as String) : null,
    acceptedAt:   j['acceptedAt'] != null ? DateTime.tryParse(j['acceptedAt'] as String) : null,
    rejectedAt:   j['rejectedAt'] != null ? DateTime.tryParse(j['rejectedAt'] as String) : null,
  );

  bool get isPending  => status == 'SENT' || status == 'OPENED';
  bool get isAccepted => status == 'ACCEPTED';
  bool get isRejected => status == 'REJECTED';
}

// ──────────────────────────────────────────────────────────────
//  Service API pour les offres reçues
// ──────────────────────────────────────────────────────────────
class ClientOfferService {

  static Future<Map<String, String>> _headers() async {
    final jwt = await ApiService.getToken() ?? '';
    return {
      HttpHeaders.contentTypeHeader: 'application/json',
      if (jwt.isNotEmpty) HttpHeaders.authorizationHeader: 'Bearer $jwt',
    };
  }

  /// GET /api/client-offers/{clientId}
  /// Retourne toutes les offres envoyées au client.
  static Future<List<ReceivedOffer>> getOffers(String clientId) async {
    final url = Uri.parse('${AppConfig.baseUrl}/api/client-offers/$clientId');
    final headers = await _headers();

    debugPrint('📥 GET offres client → $url');
    final response = await http.get(url, headers: headers)
        .timeout(AppConfig.defaultTimeout);

    debugPrint('📥 Status : ${response.statusCode}');

    if (response.statusCode == 200) {
      final body = jsonDecode(response.body) as Map<String, dynamic>;
      final data = body['data'] as List? ?? [];
      return data
          .whereType<Map<String, dynamic>>()
          .map(ReceivedOffer.fromJson)
          .toList();
    }
    if (response.statusCode == 401) throw Exception('SESSION_EXPIRED');
    throw Exception('Erreur ${response.statusCode}');
  }

  /// PATCH /api/client-offers/{clientRecoId}/open
  /// Marque l'offre comme ouverte (appelé à l'ouverture du détail).
  static Future<void> markAsOpened(int clientRecoId) async {
    final url = Uri.parse('${AppConfig.baseUrl}/api/client-offers/$clientRecoId/open');
    final headers = await _headers();
    try {
      await http.patch(url, headers: headers).timeout(AppConfig.defaultTimeout);
    } catch (e) {
      debugPrint('⚠️ markAsOpened ignoré : $e');
    }
  }

  /// PATCH /api/client-offers/{clientRecoId}/respond
  /// Le client accepte (accept=true) ou refuse (accept=false) l'offre.
  static Future<ReceivedOffer> respondToOffer(int clientRecoId, bool accept) async {
    final url = Uri.parse('${AppConfig.baseUrl}/api/client-offers/$clientRecoId/respond');
    final headers = await _headers();

    final response = await http.patch(
      url,
      headers: headers,
      body: jsonEncode({'accept': accept}),
    ).timeout(AppConfig.defaultTimeout);

    if (response.statusCode == 200) {
      final body = jsonDecode(response.body) as Map<String, dynamic>;
      return ReceivedOffer.fromJson(body['data'] as Map<String, dynamic>);
    }
    if (response.statusCode == 401) throw Exception('SESSION_EXPIRED');
    throw Exception('Erreur ${response.statusCode}');
  }
}