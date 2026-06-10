// ════════════════════════════════════════════════════════════════
//  SmartWallet — Détail transaction (refonte minimaliste)
// ════════════════════════════════════════════════════════════════
import 'package:flutter/material.dart';
import '../models/dashboard_models.dart';
import '../services/api_service.dart';
import '../theme/app_theme.dart';
import '../utils/formatters.dart';
import '../utils/session_guard.dart';
import '../widgets/sw_widgets.dart';

class TransactionDetailScreen extends StatefulWidget {
  final String clientId;
  final String transactionId;

  const TransactionDetailScreen({
    super.key,
    required this.clientId,
    required this.transactionId,
  });
  @override
  State<TransactionDetailScreen> createState() => _TransactionDetailScreenState();
}

class _TransactionDetailScreenState extends State<TransactionDetailScreen> {
  TransactionDetail? _detail;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try {
      final d = await ApiService.getTransactionDetail(
          widget.clientId, widget.transactionId);
      if (mounted) setState(() { _detail = d; _loading = false; });
    } catch (e) {
      if (!mounted) return;
      if (await SessionGuard.handle(context, e)) return;
      setState(() {
        _loading = false;
        _error = 'Impossible de charger le détail';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: const SwAppBar(title: 'Détail transaction'),
      body: _loading
          ? const SwLoader(label: 'Chargement...')
          : _error != null
          ? SwEmpty(
        icon: Icons.cloud_off_rounded,
        title: 'Erreur',
        description: _error,
        action: SwPrimaryButton(
            label: 'Réessayer',
            icon: Icons.refresh_rounded,
            onPressed: _load,
            fullWidth: false),
      )
          : _Content(detail: _detail!),
    );
  }
}

class _Content extends StatelessWidget {
  final TransactionDetail detail;
  const _Content({required this.detail});

  @override
  Widget build(BuildContext context) {
    final d = detail;
    final catColor = AppTheme.categoryColor(d.category ?? '');
    final amtColor = d.credit ? AppTheme.credit : AppTheme.debit;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // ── Hero amount ─────────────────────────────
        SwCard(
          padding: const EdgeInsets.symmetric(vertical: 30, horizontal: 24),
          child: Column(
            children: [
              Container(
                width: 64, height: 64,
                decoration: BoxDecoration(
                    color: catColor.withOpacity(0.14),
                    borderRadius: BorderRadius.circular(18)),
                child: Icon(AppTheme.categoryIcon(d.category ?? ''),
                    size: 30, color: catColor),
              ),
              const SizedBox(height: 14),
              SwBadge(
                icon: d.credit
                    ? Icons.arrow_downward_rounded
                    : Icons.arrow_upward_rounded,
                text: d.credit ? 'Argent reçu' : 'Argent envoyé',
                color: amtColor,
              ),
              const SizedBox(height: 14),
              Text('${d.credit ? '+' : '-'} ${Formatters.amount(d.amount)}',
                  style: TextStyle(
                      fontSize: 30,
                      fontWeight: FontWeight.w900,
                      color: amtColor,
                      letterSpacing: -0.5)),
              const SizedBox(height: 6),
              Text(Formatters.dateLong(d.transactionDate),
                  style: const TextStyle(
                      fontSize: 12, color: AppTheme.textMuted)),
            ],
          ),
        ),

        const SizedBox(height: 16),

        // ── Informations opération ─────────────────
        const SwSectionHeader(
            title: 'OPÉRATION', icon: Icons.info_outline_rounded),
        SwCard(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Column(
            children: [
              if (d.typeTitle != null)
                SwKeyValue(
                    label: 'Type',
                    value: d.typeTitle!,
                    icon: Icons.label_outline_rounded),
              if (d.category != null)
                SwKeyValue(
                    label: 'Catégorie',
                    value: d.category!,
                    valueColor: catColor,
                    icon: AppTheme.categoryIcon(d.category!)),
              if (d.subCategory != null)
                SwKeyValue(
                    label: 'Sous-type',
                    value: d.subCategory!,
                    icon: Icons.bookmark_outline_rounded),
            ],
          ),
        ),

        if (d.providerName != null || d.receiverLabel != null) ...[
          const SizedBox(height: 16),
          const SwSectionHeader(
              title: 'CONTREPARTIE', icon: Icons.swap_horiz_rounded),
          SwCard(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Column(
              children: [
                if (d.providerName != null)
                  SwKeyValue(
                      label: 'Prestataire',
                      value: d.providerName!,
                      icon: Icons.business_rounded,
                      valueColor: const Color(0xFF74B9FF)),
                if (d.receiverLabel != null)
                  SwKeyValue(
                      label: d.credit ? 'Expéditeur' : 'Destinataire',
                      value: d.receiverLabel!,
                      icon: d.credit ? Icons.person_rounded : Icons.send_rounded,
                      valueColor: d.credit ? AppTheme.credit : AppTheme.debit),
              ],
            ),
          ),
        ],
        const SizedBox(height: 20),
      ],
    );
  }
}