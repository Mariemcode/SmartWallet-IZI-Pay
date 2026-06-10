import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:shimmer/shimmer.dart';
import '../models/dashboard_models.dart';
import '../theme/app_theme.dart';
import '../utils/formatters.dart';

class CategoryDonut extends StatefulWidget {
  final List<CategoryBreakdown> data;
  final bool loading;

  const CategoryDonut({super.key, required this.data, this.loading = false});

  @override
  State<CategoryDonut> createState() => _CategoryDonutState();
}

class _CategoryDonutState extends State<CategoryDonut> {
  int _touchedIndex = -1;

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
          Text('Répartition dépenses',
              style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 16),

          if (widget.loading)
            _buildSkeleton()
          else if (widget.data.isEmpty)
            const _EmptyState()
          else
            _buildContent(),
        ],
      ),
    );
  }

  Widget _buildContent() {
    final highlighted = _touchedIndex >= 0 && _touchedIndex < widget.data.length
        ? widget.data[_touchedIndex]
        : null;

    return Row(
      children: [
        // ── Donut chart ──────────────────────────────────────────
        SizedBox(
          width: 150,
          height: 150,
          child: Stack(
            alignment: Alignment.center,
            children: [
              PieChart(
                PieChartData(
                  sectionsSpace: 2,
                  centerSpaceRadius: 42,
                  pieTouchData: PieTouchData(
                    touchCallback: (event, response) {
                      setState(() {
                        _touchedIndex = response?.touchedSection
                            ?.touchedSectionIndex ?? -1;
                      });
                    },
                  ),
                  sections: widget.data.asMap().entries.map((entry) {
                    final idx = entry.key;
                    final item = entry.value;
                    final isTouched = idx == _touchedIndex;
                    final color = AppTheme.categoryColor(item.category);

                    return PieChartSectionData(
                      value: item.percentage,
                      color: isTouched ? color : color.withOpacity(0.8),
                      radius: isTouched ? 38 : 32,
                      title: item.percentage >= 8
                          ? '${item.percentage.toStringAsFixed(0)}%'
                          : '',
                      titleStyle: const TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                        color: Colors.white,
                      ),
                    );
                  }).toList(),
                ),
              ),
              // Label central
              if (highlighted != null)
                Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      '${highlighted.percentage.toStringAsFixed(1)}%',
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                        color: AppTheme.textPrimary,
                      ),
                    ),
                  ],
                ),
            ],
          ),
        ),
        const SizedBox(width: 16),

        // ── Légende ───────────────────────────────────────────────
        Expanded(
          child: Column(
            children: widget.data.take(6).map((item) => _LegendItem(
              item: item,
              highlighted: item == highlighted,
            )).toList(),
          ),
        ),
      ],
    );
  }

  Widget _buildSkeleton() => Shimmer.fromColors(
    baseColor: AppTheme.surfaceLight,
    highlightColor: AppTheme.border,
    child: Row(
      children: [
        Container(
          width: 150, height: 150,
          decoration: const BoxDecoration(
            color: AppTheme.surfaceLight, shape: BoxShape.circle,
          ),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            children: List.generate(4, (_) => Container(
              height: 20, margin: const EdgeInsets.symmetric(vertical: 4),
              decoration: BoxDecoration(
                color: AppTheme.surfaceLight,
                borderRadius: BorderRadius.circular(8),
              ),
            )),
          ),
        ),
      ],
    ),
  );
}

class _LegendItem extends StatelessWidget {
  final CategoryBreakdown item;
  final bool highlighted;

  const _LegendItem({required this.item, required this.highlighted});

  @override
  Widget build(BuildContext context) {
    final color = AppTheme.categoryColor(item.category);

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          Container(
            width: highlighted ? 10 : 8,
            height: highlighted ? 10 : 8,
            decoration: BoxDecoration(
              color: color,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              item.category,
              style: TextStyle(
                fontSize: 11,
                color: highlighted ? AppTheme.textPrimary : AppTheme.textSecondary,
                fontWeight: highlighted ? FontWeight.w600 : FontWeight.w400,
              ),
              overflow: TextOverflow.ellipsis,
            ),
          ),
          Text(
            Formatters.amountCompact(item.totalAmount),
            style: TextStyle(
              fontSize: 10,
              color: highlighted ? color : AppTheme.textMuted,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) => const SizedBox(
    height: 100,
    child: Center(
      child: Text(
        'Aucune dépense pour cette période',
        style: TextStyle(color: AppTheme.textMuted, fontSize: 13),
      ),
    ),
  );
}
