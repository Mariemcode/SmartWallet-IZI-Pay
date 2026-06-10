// ═══════════════════════════════════════════════════════════════════
//  SmartWallet — PATCH dashboard_screen.dart
//
//  Ce fichier montre UNIQUEMENT les modifications à apporter dans
//  le header du DashboardScreen pour ajouter le bouton "Mes Offres".
//
//  ➤ Dans _DashboardHeaderWidget.build(), après le bouton "Conseils",
//    ajouter le GestureDetector ci-dessous AVANT la fermeture du Row.
// ═══════════════════════════════════════════════════════════════════

// ────────────────────────────────────────────────────
//  IMPORTS À AJOUTER EN HAUT DE dashboard_screen.dart
// ────────────────────────────────────────────────────
// import 'received_offers_screen.dart';  // ← AJOUTER

// ────────────────────────────────────────────────────
//  BLOC À INSÉRER dans le Row du header du dashboard,
//  APRÈS le GestureDetector du bouton "Conseils"
//  (celui qui navigue vers RecommendationScreen)
// ────────────────────────────────────────────────────

/*
  GestureDetector(
    onTap: () => Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ReceivedOffersScreen(clientId: clientId),
      ),
    ),
    child: Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      margin: const EdgeInsets.only(right: 6),
      decoration: BoxDecoration(
        color: const Color(0xFF6C5CE7).withOpacity(0.15),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: const Color(0xFF6C5CE7).withOpacity(0.5),
        ),
      ),
      child: Row(
        children: const [
          Icon(Icons.local_offer_rounded, color: Color(0xFF6C5CE7), size: 16),
          SizedBox(width: 5),
          Text(
            'Offres',
            style: TextStyle(
              color: Color(0xFF6C5CE7),
              fontWeight: FontWeight.w800,
              fontSize: 13,
            ),
          ),
        ],
      ),
    ),
  ),
*/

// ────────────────────────────────────────────────────
//  ALTERNATIVE — Avec badge de comptage des offres
//  (nécessite de charger le count depuis l'API)
// ────────────────────────────────────────────────────

import 'package:flutter/material.dart';
import '../config/app_config.dart';
import '../services/client_offer_service.dart';
import '../theme/app_theme.dart';
import '../screens/received_offers_screen.dart';

/// Widget autonome que vous pouvez insérer dans le header du dashboard.
/// Il affiche le nombre d'offres en attente en badge rouge.
class OffersHeaderButton extends StatefulWidget {
  final String clientId;
  const OffersHeaderButton({super.key, required this.clientId});

  @override
  State<OffersHeaderButton> createState() => _OffersHeaderButtonState();
}

class _OffersHeaderButtonState extends State<OffersHeaderButton> {
  int _pendingCount = 0;

  @override
  void initState() {
    super.initState();
    _loadCount();
  }

  Future<void> _loadCount() async {
    try {
      final offers = await ClientOfferService.getOffers(widget.clientId);
      if (mounted) {
        setState(() {
          _pendingCount = offers.where((o) => o.isPending).length;
        });
      }
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () async {
        await Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => ReceivedOffersScreen(clientId: widget.clientId),
          ),
        );
        // Rafraîchir le badge au retour
        _loadCount();
      },
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            margin: const EdgeInsets.only(right: 6),
            decoration: BoxDecoration(
              color: const Color(0xFF6C5CE7).withOpacity(0.15),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: const Color(0xFF6C5CE7).withOpacity(0.5),
              ),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: const [
                Icon(Icons.local_offer_rounded, color: Color(0xFF6C5CE7), size: 16),
                SizedBox(width: 5),
                Text(
                  'Offres',
                  style: TextStyle(
                    color: Color(0xFF6C5CE7),
                    fontWeight: FontWeight.w800,
                    fontSize: 13,
                  ),
                ),
              ],
            ),
          ),
          // Badge rouge si offres en attente
          if (_pendingCount > 0)
            Positioned(
              top: -4, right: 2,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                decoration: BoxDecoration(
                  color: AppTheme.debit,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: AppTheme.background, width: 1.5),
                ),
                child: Text(
                  '$_pendingCount',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 9,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
