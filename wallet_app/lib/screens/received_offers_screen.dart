// ════════════════════════════════════════════════════════════════
//  SmartWallet — Offres reçues (refonte minimaliste)
//  3 onglets : Nouvelles • Acceptées • Refusées
//  Carte d'offre minimaliste + bottom sheet pour les détails
// ════════════════════════════════════════════════════════════════
import 'package:flutter/material.dart';
import '../services/client_offer_service.dart';
import '../theme/app_theme.dart';
import '../utils/session_guard.dart';
import '../widgets/sw_widgets.dart';

class ReceivedOffersScreen extends StatefulWidget {
  final String clientId;
  final int? highlightOfferId;
  const ReceivedOffersScreen({super.key, required this.clientId, this.highlightOfferId});
  @override
  State<ReceivedOffersScreen> createState() => _ReceivedOffersScreenState();
}

class _ReceivedOffersScreenState extends State<ReceivedOffersScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tab;
  List<ReceivedOffer> _offers = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _tab = TabController(length: 3, vsync: this);
    _load();
  }

  @override
  void dispose() {
    _tab.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try {
      final offers = await ClientOfferService.getOffers(widget.clientId);
      offers.sort((a, b) {
        final aT = a.sentAt ?? DateTime(2000);
        final bT = b.sentAt ?? DateTime(2000);
        return bT.compareTo(aT);
      });
      if (!mounted) return;
      setState(() { _offers = offers; _loading = false; });

      // Auto-ouverture de l'offre venue d'une notif push
      if (widget.highlightOfferId != null) {
        final target = offers.where((o) => o.id == widget.highlightOfferId).toList();
        if (target.isNotEmpty) {
          WidgetsBinding.instance.addPostFrameCallback((_) => _openOffer(target.first));
        }
      }
    } catch (e) {
      if (!mounted) return;
      if (await SessionGuard.handle(context, e)) return;
      setState(() {
        _loading = false;
        _error = 'Impossible de charger les offres';
      });
    }
  }

  List<ReceivedOffer> get _pending  => _offers.where((o) => o.isPending).toList();
  List<ReceivedOffer> get _accepted => _offers.where((o) => o.isAccepted).toList();
  List<ReceivedOffer> get _rejected => _offers.where((o) => o.isRejected).toList();

  // ── Ouvrir le détail d'une offre dans un BottomSheet ─────────
  void _openOffer(ReceivedOffer offer) {
    if (offer.isPending) ClientOfferService.markAsOpened(offer.id);
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.surface,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(28))),
      builder: (_) => _OfferDetailSheet(
        offer: offer,
        onRespond: (accept) async {
          Navigator.pop(context);
          await _respond(offer, accept);
        },
      ),
    );
  }

  // ── Accepter / refuser une offre ─────────────────────────────
  Future<void> _respond(ReceivedOffer offer, bool accept) async {
    // Optimistic UI
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => const Center(
        child: CircularProgressIndicator(color: AppTheme.primary),
      ),
    );

    try {
      final updated = await ClientOfferService.respondToOffer(offer.id, accept);
      Navigator.pop(context); // close loader
      if (!mounted) return;

      final idx = _offers.indexWhere((o) => o.id == offer.id);
      if (idx >= 0) {
        setState(() => _offers[idx] = updated);
      }

      _showFeedback(accept, offer.title);
    } catch (e) {
      Navigator.pop(context);
      if (!mounted) return;
      if (await SessionGuard.handle(context, e)) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Erreur : ${e.toString().replaceAll('Exception: ', '')}')),
      );
    }
  }

  void _showFeedback(bool accepted, String title) {
    final color = accepted ? AppTheme.credit : AppTheme.debit;
    final detail = accepted
        ? 'L\'offre est active jusqu\'à la fin du mois.'
        : 'Cette offre ne sera plus proposée.';
    showDialog(
      context: context,
      builder: (_) => Dialog(
        backgroundColor: AppTheme.surface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 64, height: 64,
                decoration: BoxDecoration(
                    color: color.withOpacity(0.15), shape: BoxShape.circle),
                child: Icon(
                    accepted ? Icons.check_rounded : Icons.close_rounded,
                    color: color, size: 32),
              ),
              const SizedBox(height: 16),
              Text(accepted ? 'Offre acceptée !' : 'Offre refusée',
                  style: const TextStyle(
                      color: AppTheme.textPrimary,
                      fontSize: 17,
                      fontWeight: FontWeight.w700)),
              const SizedBox(height: 6),
              Text(detail,
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: AppTheme.textMuted, fontSize: 12)),
              const SizedBox(height: 20),
              SwPrimaryButton(label: 'OK', onPressed: () => Navigator.pop(context)),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.background,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new_rounded,
              size: 18, color: AppTheme.textPrimary),
          onPressed: () => Navigator.maybePop(context),
        ),
        title: const Text('Mes offres',
            style: TextStyle(
                fontSize: 17, fontWeight: FontWeight.w700, color: AppTheme.textPrimary)),
        actions: [
          IconButton(
            onPressed: _load,
            icon: _loading
                ? const SizedBox(
                width: 18, height: 18,
                child: CircularProgressIndicator(
                    color: AppTheme.primary, strokeWidth: 2))
                : const Icon(Icons.refresh_rounded,
                color: AppTheme.textSecondary, size: 22),
          ),
        ],
        bottom: TabBar(
          controller: _tab,
          indicatorColor: AppTheme.primary,
          indicatorWeight: 3,
          labelColor: AppTheme.primary,
          unselectedLabelColor: AppTheme.textMuted,
          labelStyle: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700),
          tabs: [
            _TabLabel('Nouvelles', _pending.length, AppTheme.primary),
            _TabLabel('Acceptées', _accepted.length, AppTheme.credit),
            _TabLabel('Refusées',  _rejected.length, AppTheme.debit),
          ],
        ),
      ),
      body: _loading
          ? const SwLoader(label: 'Chargement des offres...')
          : _error != null
          ? _ErrorView(message: _error!, onRetry: _load)
          : TabBarView(
        controller: _tab,
        children: [
          _OffersList(offers: _pending, onTap: _openOffer,
              emptyIcon: Icons.mark_email_unread_outlined,
              emptyText: 'Pas de nouvelle offre'),
          _OffersList(offers: _accepted, onTap: _openOffer,
              emptyIcon: Icons.check_circle_outline_rounded,
              emptyText: 'Aucune offre acceptée'),
          _OffersList(offers: _rejected, onTap: _openOffer,
              emptyIcon: Icons.cancel_outlined,
              emptyText: 'Aucune offre refusée'),
        ],
      ),
    );
  }
}

// ════════════════════════════════════════════════════════════════
//  Label d'onglet avec badge compteur
// ════════════════════════════════════════════════════════════════
class _TabLabel extends StatelessWidget {
  final String label;
  final int count;
  final Color color;
  const _TabLabel(this.label, this.count, this.color);

  @override
  Widget build(BuildContext context) {
    return Tab(
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(label),
          if (count > 0) ...[
            const SizedBox(width: 6),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
              decoration: BoxDecoration(
                  color: color.withOpacity(0.18),
                  borderRadius: BorderRadius.circular(8)),
              child: Text('$count',
                  style: TextStyle(
                      fontSize: 10, color: color, fontWeight: FontWeight.w800)),
            ),
          ],
        ],
      ),
    );
  }
}

// ════════════════════════════════════════════════════════════════
//  Liste d'offres
// ════════════════════════════════════════════════════════════════
class _OffersList extends StatelessWidget {
  final List<ReceivedOffer> offers;
  final void Function(ReceivedOffer) onTap;
  final IconData emptyIcon;
  final String emptyText;
  const _OffersList({
    required this.offers,
    required this.onTap,
    required this.emptyIcon,
    required this.emptyText,
  });

  @override
  Widget build(BuildContext context) {
    if (offers.isEmpty) {
      return SwEmpty(icon: emptyIcon, title: emptyText);
    }
    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: offers.length,
      itemBuilder: (_, i) => Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: _OfferCard(offer: offers[i], onTap: () => onTap(offers[i])),
      ),
    );
  }
}

// ════════════════════════════════════════════════════════════════
//  Carte d'offre minimaliste
// ════════════════════════════════════════════════════════════════
class _OfferCard extends StatelessWidget {
  final ReceivedOffer offer;
  final VoidCallback onTap;
  const _OfferCard({required this.offer, required this.onTap});

  String get _benefit {
    if (offer.cashbackPct > 0) return '${offer.cashbackPct.toStringAsFixed(0)}% cashback';
    if (offer.discountPct > 0) return '${offer.discountPct.toStringAsFixed(0)}% remise';
    return 'Offre exclusive';
  }

  Color get _accent {
    if (offer.isAccepted) return AppTheme.credit;
    if (offer.isRejected) return AppTheme.debit;
    return AppTheme.primary;
  }

  IconData get _icon {
    if (offer.isAccepted) return Icons.check_circle_rounded;
    if (offer.isRejected) return Icons.cancel_rounded;
    return Icons.local_offer_rounded;
  }

  @override
  Widget build(BuildContext context) {
    return SwCard(
      onTap: onTap,
      padding: const EdgeInsets.all(14),
      borderColor: offer.isPending
          ? AppTheme.primary.withOpacity(0.4)
          : AppTheme.border,
      child: Row(
        children: [
          Container(
            width: 48, height: 48,
            decoration: BoxDecoration(
                color: _accent.withOpacity(0.12),
                borderRadius: BorderRadius.circular(12)),
            child: Icon(_icon, color: _accent, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(offer.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                              color: AppTheme.textPrimary,
                              fontSize: 14,
                              fontWeight: FontWeight.w700)),
                    ),
                    if (offer.isPending)
                      SwBadge(text: 'NOUVEAU', color: AppTheme.primary),
                  ],
                ),
                const SizedBox(height: 3),
                Text(_benefit,
                    style: TextStyle(
                        color: _accent, fontSize: 12, fontWeight: FontWeight.w600)),
                if (offer.providerName != null) ...[
                  const SizedBox(height: 1),
                  Text(offer.providerName!,
                      style: const TextStyle(color: AppTheme.textMuted, fontSize: 11)),
                ],
              ],
            ),
          ),
          const Icon(Icons.chevron_right_rounded,
              color: AppTheme.textMuted, size: 20),
        ],
      ),
    );
  }
}

// ════════════════════════════════════════════════════════════════
//  Bottom sheet de détail d'offre
// ════════════════════════════════════════════════════════════════
class _OfferDetailSheet extends StatelessWidget {
  final ReceivedOffer offer;
  final void Function(bool accept) onRespond;
  const _OfferDetailSheet({required this.offer, required this.onRespond});

  String get _bigBenefit {
    if (offer.cashbackPct > 0) return '${offer.cashbackPct.toStringAsFixed(0)}%';
    if (offer.discountPct > 0) return '${offer.discountPct.toStringAsFixed(0)}%';
    return '★';
  }

  String get _benefitLabel {
    if (offer.cashbackPct > 0) return 'cashback';
    if (offer.discountPct > 0) return 'de remise';
    return 'exclusif';
  }

  @override
  Widget build(BuildContext context) {
    return DraggableScrollableSheet(
      initialChildSize: 0.7,
      minChildSize: 0.5,
      maxChildSize: 0.9,
      expand: false,
      builder: (_, scrollCtrl) => ListView(
        controller: scrollCtrl,
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
        children: [
          Center(
            child: Container(
              width: 36, height: 4,
              margin: const EdgeInsets.only(bottom: 18),
              decoration: BoxDecoration(
                  color: AppTheme.border, borderRadius: BorderRadius.circular(4)),
            ),
          ),
          // Hero benefit
          Container(
            padding: const EdgeInsets.symmetric(vertical: 26),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [AppTheme.primary.withOpacity(0.18), AppTheme.primary.withOpacity(0.05)],
                begin: Alignment.topLeft, end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(20),
            ),
            child: Column(
              children: [
                Text(_bigBenefit,
                    style: const TextStyle(
                        color: AppTheme.primary,
                        fontSize: 56,
                        fontWeight: FontWeight.w900,
                        height: 1)),
                const SizedBox(height: 4),
                Text(_benefitLabel,
                    style: const TextStyle(
                        color: AppTheme.textPrimary,
                        fontSize: 14,
                        letterSpacing: 1.2,
                        fontWeight: FontWeight.w600)),
              ],
            ),
          ),
          const SizedBox(height: 20),
          // Titre + description
          Text(offer.title,
              style: const TextStyle(
                  color: AppTheme.textPrimary,
                  fontSize: 18,
                  fontWeight: FontWeight.w800)),
          if (offer.description != null) ...[
            const SizedBox(height: 8),
            Text(offer.description!,
                style: const TextStyle(
                    color: AppTheme.textSecondary, fontSize: 13, height: 1.4)),
          ],
          const SizedBox(height: 18),
          // Détails clé-valeur
          SwCard(
            child: Column(
              children: [
                if (offer.providerName != null)
                  SwKeyValue(label: 'Partenaire', value: offer.providerName!),
                if (offer.category != null)
                  SwKeyValue(label: 'Catégorie', value: offer.category!),
                if (offer.minAmount > 0)
                  SwKeyValue(label: 'Montant min.', value: formatTnd(offer.minAmount)),
                if (offer.sentAt != null)
                  SwKeyValue(
                      label: 'Reçue le',
                      value: '${offer.sentAt!.day}/${offer.sentAt!.month}/${offer.sentAt!.year}'),
                SwKeyValue(label: 'Code', value: offer.offerCode),
              ],
            ),
          ),
          const SizedBox(height: 22),
          // Boutons
          if (offer.isPending) ...[
            SwPrimaryButton(
              label: 'Accepter cette offre',
              icon: Icons.check_rounded,
              onPressed: () => onRespond(true),
            ),
            const SizedBox(height: 10),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                onPressed: () => onRespond(false),
                icon: const Icon(Icons.close_rounded, size: 18),
                label: const Text('Refuser',
                    style: TextStyle(fontWeight: FontWeight.w700)),
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppTheme.debit,
                  side: BorderSide(color: AppTheme.debit.withOpacity(0.4)),
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14)),
                ),
              ),
            ),
          ] else
            Container(
              padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 18),
              decoration: BoxDecoration(
                color: (offer.isAccepted ? AppTheme.credit : AppTheme.debit).withOpacity(0.08),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Row(
                children: [
                  Icon(
                      offer.isAccepted
                          ? Icons.check_circle_rounded
                          : Icons.cancel_rounded,
                      color: offer.isAccepted ? AppTheme.credit : AppTheme.debit,
                      size: 22),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                        offer.isAccepted
                            ? 'Cette offre est active sur votre compte.'
                            : 'Cette offre a été refusée.',
                        style: TextStyle(
                            color:
                            offer.isAccepted ? AppTheme.credit : AppTheme.debit,
                            fontWeight: FontWeight.w600,
                            fontSize: 13)),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;
  const _ErrorView({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return SwEmpty(
      icon: Icons.cloud_off_rounded,
      title: 'Oups…',
      description: message,
      action: SwPrimaryButton(
          label: 'Réessayer', icon: Icons.refresh_rounded,
          onPressed: onRetry, fullWidth: false),
    );
  }
}