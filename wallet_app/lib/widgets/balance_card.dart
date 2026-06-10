import 'package:flutter/material.dart';
import 'package:shimmer/shimmer.dart';
import '../models/dashboard_models.dart';
import '../theme/app_theme.dart';
import '../utils/formatters.dart';

class BalanceCard extends StatelessWidget {
  final WalletSummary? summary;
  final ClientProfile? profile;
  final bool loading;

  const BalanceCard({
    super.key,
    required this.summary,
    required this.profile,
    this.loading = false,
  });

  @override
  Widget build(BuildContext context) {
    if (loading) return _buildSkeleton();

    return Container(
      margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      decoration: BoxDecoration(
        gradient: AppTheme.balanceGradient,
        borderRadius: BorderRadius.circular(24),
        boxShadow: [
          BoxShadow(
            color: AppTheme.primary.withOpacity(0.35),
            blurRadius: 24,
            spreadRadius: -4,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // ── Header ────────────────────────────────────────────
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Wallet IZI Pay',
                      style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                        color: AppTheme.background.withOpacity(0.7),
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      profile?.phoneNumber ?? '—',
                      style: Theme.of(context).textTheme.titleMedium!.copyWith(
                        color: AppTheme.background,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                ),
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: AppTheme.background.withOpacity(0.2),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    Icons.account_balance_wallet_rounded,
                    color: Colors.white,
                    size: 22,
                  ),
                ),
              ],
            ),

            const SizedBox(height: 20),

            // ── Solde ─────────────────────────────────────────────
            Text(
              'Solde disponible',
              style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                color: AppTheme.background.withOpacity(0.65),
              ),
            ),
            const SizedBox(height: 4),
            Text(
              summary != null
                  ? Formatters.amount(summary!.balance)
                  : '— TND',
              style: TextStyle(
                fontSize: 34,
                fontWeight: FontWeight.w800,
                color: AppTheme.background,
                letterSpacing: -1,
                fontFamily: 'SpaceMono',
              ),
            ),

            const SizedBox(height: 20),

            // ── Stats ce mois ──────────────────────────────────────
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: AppTheme.background.withOpacity(0.15),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  _StatChip(
                    icon: Icons.arrow_downward_rounded,
                    label: 'Dépenses',
                    value: summary != null
                        ? Formatters.amountCompact(summary!.spentThisMonth)
                        : '—',
                    color: AppTheme.debit.withOpacity(0.85),
                  ),
                  Container(
                    height: 32,
                    width: 1,
                    color: AppTheme.background.withOpacity(0.2),
                  ),
                  _StatChip(
                    icon: Icons.arrow_upward_rounded,
                    label: 'Reçu',
                    value: summary != null
                        ? Formatters.amountCompact(summary!.receivedThisMonth)
                        : '—',
                    color: Colors.white,
                  ),
                  if (summary?.spentVariationPercent != null) ...[
                    Container(
                      height: 32,
                      width: 1,
                      color: AppTheme.background.withOpacity(0.2),
                    ),
                    _VariationChip(
                        variation: summary!.spentVariationPercent!),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSkeleton() => Shimmer.fromColors(
    baseColor: AppTheme.surfaceLight,
    highlightColor: AppTheme.border,
    child: Container(
      margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      height: 210,
      decoration: BoxDecoration(
        color: AppTheme.surfaceLight,
        borderRadius: BorderRadius.circular(24),
      ),
    ),
  );
}

class _StatChip extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final Color color;

  const _StatChip({
    required this.icon,
    required this.label,
    required this.value,
    required this.color,
  });

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 12, color: color),
          const SizedBox(width: 4),
          Text(
            label,
            style: TextStyle(
              fontSize: 11,
              color: color.withOpacity(0.8),
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
      const SizedBox(height: 3),
      Text(
        value,
        style: TextStyle(
          fontSize: 13,
          color: color,
          fontWeight: FontWeight.w700,
        ),
      ),
    ],
  );
}

class _VariationChip extends StatelessWidget {
  final double variation;
  const _VariationChip({required this.variation});

  @override
  Widget build(BuildContext context) {
    final isPositive = variation >= 0;
    return Column(
      children: [
        Text(
          'Variation',
          style: TextStyle(
            fontSize: 11,
            color: Colors.white.withOpacity(0.7),
          ),
        ),
        const SizedBox(height: 3),
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              isPositive ? Icons.trending_up : Icons.trending_down,
              size: 13,
              color: isPositive ? AppTheme.debit : AppTheme.credit,
            ),
            const SizedBox(width: 3),
            Text(
              Formatters.variation(variation),
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w700,
                color: isPositive ? AppTheme.debit : AppTheme.credit,
              ),
            ),
          ],
        ),
      ],
    );
  }
}
