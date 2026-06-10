import 'package:flutter/material.dart';
import 'package:shimmer/shimmer.dart';
import '../models/dashboard_models.dart';
import '../theme/app_theme.dart';
import '../utils/formatters.dart';

class KpiRow extends StatelessWidget {
  final WalletSummary? summary;
  final PeriodStats? stats;
  final bool loading;

  const KpiRow({
    super.key,
    this.summary,
    this.stats,
    this.loading = false,
  });

  @override
  Widget build(BuildContext context) {
    if (loading) return _buildSkeleton();

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Row(
        children: [
          _KpiCard(
            icon: Icons.receipt_long_rounded,
            label: 'Transactions',
            value: '${summary?.transactionsThisMonth ?? 0}',
            sub: 'ce mois',
            color: AppTheme.accent,
          ),
          const SizedBox(width: 10),
          _KpiCard(
            icon: Icons.calendar_today_rounded,
            label: 'Jours actifs',
            value: '${stats?.activeDays ?? 0}',
            sub: 'avec activité',
            color: const Color(0xFF74B9FF),
          ),
          const SizedBox(width: 10),
          _KpiCard(
            icon: Icons.trending_flat_rounded,
            label: 'Moy/tx',
            value: stats != null
                ? Formatters.amountCompact(stats!.avgTransactionAmount)
                : '—',
            sub: 'par débit',
            color: const Color(0xFFA29BFE),
          ),
        ],
      ),
    );
  }

  Widget _buildSkeleton() => Shimmer.fromColors(
    baseColor: AppTheme.surfaceLight,
    highlightColor: AppTheme.border,
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Row(
        children: List.generate(3, (_) => Expanded(
          child: Container(
            height: 80,
            margin: const EdgeInsets.symmetric(horizontal: 4),
            decoration: BoxDecoration(
              color: AppTheme.surfaceLight,
              borderRadius: BorderRadius.circular(16),
            ),
          ),
        )),
      ),
    ),
  );
}

class _KpiCard extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final String sub;
  final Color color;

  const _KpiCard({
    required this.icon,
    required this.label,
    required this.value,
    required this.sub,
    required this.color,
  });

  @override
  Widget build(BuildContext context) => Expanded(
    child: Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.border, width: 1),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 14, color: color),
              const SizedBox(width: 5),
              Flexible(
                child: Text(
                  label,
                  style: TextStyle(
                    fontSize: 10,
                    color: AppTheme.textSecondary,
                    fontWeight: FontWeight.w500,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            value,
            style: TextStyle(
              fontSize: value.length > 8 ? 12 : 16,
              fontWeight: FontWeight.w800,
              color: color,
            ),
          ),
          Text(
            sub,
            style: const TextStyle(
              fontSize: 9,
              color: AppTheme.textMuted,
            ),
          ),
        ],
      ),
    ),
  );
}
