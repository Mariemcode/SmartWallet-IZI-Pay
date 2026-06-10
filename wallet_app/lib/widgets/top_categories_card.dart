import 'package:flutter/material.dart';
import 'package:shimmer/shimmer.dart';
import '../models/dashboard_models.dart';
import '../theme/app_theme.dart';
import '../utils/formatters.dart';

class TopCategoriesCard extends StatelessWidget {
  final List<TopCategory> data;
  final bool loading;

  const TopCategoriesCard({super.key, required this.data, this.loading = false});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppTheme.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Top dépenses',
                  style: Theme.of(context).textTheme.titleLarge),
              Container(
                padding: const EdgeInsets.symmetric(
                    horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: AppTheme.primary.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Text(
                  'Top 5',
                  style: TextStyle(
                    fontSize: 11,
                    color: AppTheme.primary,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),

          if (loading)
            _buildSkeleton()
          else if (data.isEmpty)
            const Center(
              child: Padding(
                padding: EdgeInsets.symmetric(vertical: 20),
                child: Text('Aucune donnée',
                    style: TextStyle(color: AppTheme.textMuted)),
              ),
            )
          else
            ...data.map((item) => _CategoryRow(item: item,
                maxAmount: data.first.totalAmount)),
        ],
      ),
    );
  }

  Widget _buildSkeleton() => Column(
    children: List.generate(4, (i) => Shimmer.fromColors(
      baseColor: AppTheme.surfaceLight,
      highlightColor: AppTheme.border,
      child: Container(
        height: 44,
        margin: const EdgeInsets.symmetric(vertical: 4),
        decoration: BoxDecoration(
          color: AppTheme.surfaceLight,
          borderRadius: BorderRadius.circular(10),
        ),
      ),
    )),
  );
}

class _CategoryRow extends StatelessWidget {
  final TopCategory item;
  final double maxAmount;

  const _CategoryRow({required this.item, required this.maxAmount});

  @override
  Widget build(BuildContext context) {
    final color = AppTheme.categoryColor(item.category);
    final barWidth = maxAmount > 0 ? item.totalAmount / maxAmount : 0.0;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Column(
        children: [
          Row(
            children: [
              // Rang
              Container(
                width: 24, height: 24,
                decoration: BoxDecoration(
                  color: color.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(6),
                ),
                alignment: Alignment.center,
                child: Text(
                  '${item.rank}',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w800,
                    color: color,
                  ),
                ),
              ),
              const SizedBox(width: 10),

              // Icône + nom
              Icon(
                AppTheme.categoryIcon(item.category),
                size: 14, color: color,
              ),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  item.category,
                  style: Theme.of(context).textTheme.bodyLarge!.copyWith(
                    fontWeight: FontWeight.w500,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),

              // Montant + nb tx
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    Formatters.amountCompact(item.totalAmount),
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: color,
                    ),
                  ),
                  Text(
                    '${item.transactionCount} tx',
                    style: const TextStyle(
                      fontSize: 10,
                      color: AppTheme.textMuted,
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 6),

          // Barre de progression
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: barWidth.clamp(0.0, 1.0),
              backgroundColor: AppTheme.surfaceLight,
              valueColor: AlwaysStoppedAnimation<Color>(
                color.withOpacity(0.7),
              ),
              minHeight: 4,
            ),
          ),
        ],
      ),
    );
  }
}
