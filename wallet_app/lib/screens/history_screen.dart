// ════════════════════════════════════════════════════════════════
//  SmartWallet — HistoryScreen (refonte minimaliste)
//  Liste paginée + filtres en bottom sheet
// ════════════════════════════════════════════════════════════════
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/dashboard_provider.dart';
import '../theme/app_theme.dart';
import '../widgets/transaction_tile.dart';
import '../widgets/sw_widgets.dart';
import 'transaction_detail_screen.dart';

class HistoryScreen extends StatelessWidget {
  final String clientId;
  const HistoryScreen({super.key, required this.clientId});

  @override
  Widget build(BuildContext context) {
    return Consumer<DashboardProvider>(
      builder: (context, prov, _) {
        final hasFilters = prov.selectedCategory != null || prov.selectedFlowType != null;
        return Scaffold(
          backgroundColor: AppTheme.background,
          appBar: SwAppBar(
            title: 'Historique',
            subtitle: '${prov.totalElements} transactions',
            actions: [
              if (hasFilters)
                TextButton(
                  onPressed: () => prov.clearHistoryFilters(),
                  child: const Text('Effacer',
                      style: TextStyle(color: AppTheme.primary, fontSize: 12)),
                ),
              IconButton(
                onPressed: () => _showFilterSheet(context, prov),
                icon: Stack(
                  children: [
                    const Icon(Icons.tune_rounded, color: AppTheme.textPrimary),
                    if (hasFilters)
                      Positioned(
                        right: 0, top: 0,
                        child: Container(
                          width: 8, height: 8,
                          decoration: const BoxDecoration(
                              color: AppTheme.primary, shape: BoxShape.circle),
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
          body: Column(
            children: [
              if (hasFilters)
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                  child: Row(
                    children: [
                      if (prov.selectedCategory != null)
                        _FilterChip(
                          label: prov.selectedCategory!,
                          onRemove: () =>
                              prov.filterHistory(flowType: prov.selectedFlowType),
                        ),
                      if (prov.selectedFlowType != null) ...[
                        const SizedBox(width: 6),
                        _FilterChip(
                          label: prov.selectedFlowType == 'C' ? 'Crédits' : 'Débits',
                          onRemove: () =>
                              prov.filterHistory(category: prov.selectedCategory),
                        ),
                      ],
                    ],
                  ),
                ),
              Expanded(
                child: prov.loadingHistory && prov.transactions.isEmpty
                    ? const SwLoader(label: 'Chargement...')
                    : prov.transactions.isEmpty
                    ? const SwEmpty(
                    icon: Icons.receipt_long_rounded,
                    title: 'Aucune transaction',
                    description: 'Vous n\'avez pas encore d\'historique.')
                    : NotificationListener<ScrollNotification>(
                  onNotification: (notif) {
                    if (notif is ScrollEndNotification &&
                        notif.metrics.pixels >=
                            notif.metrics.maxScrollExtent - 200) {
                      prov.loadMoreHistory();
                    }
                    return false;
                  },
                  child: ListView.builder(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    itemCount: prov.transactions.length + 1,
                    itemBuilder: (ctx, i) {
                      if (i == prov.transactions.length) {
                        return prov.loadingHistory
                            ? const Padding(
                            padding: EdgeInsets.all(16),
                            child: Center(
                                child: CircularProgressIndicator(
                                    color: AppTheme.primary,
                                    strokeWidth: 2)))
                            : const SizedBox(height: 80);
                      }
                      final tx = prov.transactions[i];
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: TransactionTile(
                          transaction: tx,
                          onTap: () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => TransactionDetailScreen(
                                clientId: clientId,
                                transactionId: tx.id,
                              ),
                            ),
                          ),
                        ),
                      );
                    },
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  void _showFilterSheet(BuildContext context, DashboardProvider prov) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppTheme.surface,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      builder: (_) => _FilterSheet(provider: prov),
    );
  }
}

// ── Chip filtre actif ─────────────────────────────────────────
class _FilterChip extends StatelessWidget {
  final String label;
  final VoidCallback onRemove;
  const _FilterChip({required this.label, required this.onRemove});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
          color: AppTheme.primary.withOpacity(0.15),
          borderRadius: BorderRadius.circular(20)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(label,
              style: const TextStyle(
                  color: AppTheme.primary,
                  fontSize: 11,
                  fontWeight: FontWeight.w700)),
          const SizedBox(width: 4),
          GestureDetector(
            onTap: onRemove,
            child: const Icon(Icons.close_rounded,
                size: 13, color: AppTheme.primary),
          ),
        ],
      ),
    );
  }
}

// ════════════════════════════════════════════════════════════════
//  Filter sheet
// ════════════════════════════════════════════════════════════════
class _FilterSheet extends StatefulWidget {
  final DashboardProvider provider;
  const _FilterSheet({required this.provider});
  @override
  State<_FilterSheet> createState() => _FilterSheetState();
}

class _FilterSheetState extends State<_FilterSheet> {
  String? _cat;
  String? _flow;

  @override
  void initState() {
    super.initState();
    _cat = widget.provider.selectedCategory;
    _flow = widget.provider.selectedFlowType;
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 16, 20, MediaQuery.of(context).viewInsets.bottom + 20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 36, height: 4,
              decoration: BoxDecoration(
                  color: AppTheme.border,
                  borderRadius: BorderRadius.circular(2)),
            ),
          ),
          const SizedBox(height: 16),
          const Text('Filtrer',
              style: TextStyle(
                  color: AppTheme.textPrimary,
                  fontSize: 17,
                  fontWeight: FontWeight.w800)),
          const SizedBox(height: 20),
          const Text('Type',
              style: TextStyle(
                  color: AppTheme.textMuted,
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.5)),
          const SizedBox(height: 8),
          Row(
            children: [
              _Chip(
                label: 'Tous',
                selected: _flow == null,
                onTap: () => setState(() => _flow = null),
              ),
              const SizedBox(width: 6),
              _Chip(
                label: 'Crédits',
                color: AppTheme.credit,
                selected: _flow == 'C',
                onTap: () => setState(() => _flow = _flow == 'C' ? null : 'C'),
              ),
              const SizedBox(width: 6),
              _Chip(
                label: 'Débits',
                color: AppTheme.debit,
                selected: _flow == 'D',
                onTap: () => setState(() => _flow = _flow == 'D' ? null : 'D'),
              ),
            ],
          ),
          const SizedBox(height: 18),
          const Text('Catégorie',
              style: TextStyle(
                  color: AppTheme.textMuted,
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.5)),
          const SizedBox(height: 8),
          Wrap(
            spacing: 6, runSpacing: 6,
            children: [
              _Chip(
                label: 'Toutes',
                selected: _cat == null,
                onTap: () => setState(() => _cat = null),
              ),
              ...widget.provider.categories.map((c) => _Chip(
                label: c,
                selected: _cat == c,
                onTap: () => setState(() => _cat = _cat == c ? null : c),
              )),
            ],
          ),
          const SizedBox(height: 22),
          SwPrimaryButton(
            label: 'Appliquer',
            icon: Icons.check_rounded,
            onPressed: () {
              widget.provider.filterHistory(category: _cat, flowType: _flow);
              Navigator.pop(context);
            },
          ),
        ],
      ),
    );
  }
}

class _Chip extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;
  final Color? color;
  const _Chip({
    required this.label,
    required this.selected,
    required this.onTap,
    this.color,
  });

  @override
  Widget build(BuildContext context) {
    final c = color ?? AppTheme.primary;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
        decoration: BoxDecoration(
          color: selected ? c.withOpacity(0.18) : AppTheme.background,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
              color: selected ? c : AppTheme.border, width: 1),
        ),
        child: Text(label,
            style: TextStyle(
                color: selected ? c : AppTheme.textSecondary,
                fontSize: 12,
                fontWeight: FontWeight.w700)),
      ),
    );
  }
}