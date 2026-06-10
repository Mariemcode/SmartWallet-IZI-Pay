import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:shimmer/shimmer.dart';
import '../models/dashboard_models.dart';
import '../theme/app_theme.dart';
import '../utils/formatters.dart';

class ChartSection extends StatelessWidget {
  final List<WeeklyEvolution> weeklyData;
  final List<MonthlyEvolution> monthlyData;
  final int activeTab;
  final ValueChanged<int> onTabChanged;
  final bool loading;

  const ChartSection({
    super.key,
    required this.weeklyData,
    required this.monthlyData,
    required this.activeTab,
    required this.onTabChanged,
    this.loading = false,
  });

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
          // ── Header + Tabs ────────────────────────────────────────
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Activité',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              _TabToggle(activeTab: activeTab, onChanged: onTabChanged),
            ],
          ),
          const SizedBox(height: 20),

          // ── Graphe ───────────────────────────────────────────────
          if (loading)
            _buildSkeleton()
          else if (activeTab == 0)
            _WeeklyBars(data: weeklyData)
          else
            _MonthlyLine(data: monthlyData),
        ],
      ),
    );
  }

  Widget _buildSkeleton() => Shimmer.fromColors(
    baseColor: AppTheme.surfaceLight,
    highlightColor: AppTheme.border,
    child: Container(
      height: 180,
      decoration: BoxDecoration(
        color: AppTheme.surfaceLight,
        borderRadius: BorderRadius.circular(12),
      ),
    ),
  );
}

// ── Toggle Tabs ──────────────────────────────────────────────────────

class _TabToggle extends StatelessWidget {
  final int activeTab;
  final ValueChanged<int> onChanged;

  const _TabToggle({required this.activeTab, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: AppTheme.surfaceLight,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        children: [
          _Tab(label: '7 jours', active: activeTab == 0,
              onTap: () => onChanged(0)),
          _Tab(label: '12 mois', active: activeTab == 1,
              onTap: () => onChanged(1)),
        ],
      ),
    );
  }
}

class _Tab extends StatelessWidget {
  final String label;
  final bool active;
  final VoidCallback onTap;

  const _Tab({required this.label, required this.active, required this.onTap});

  @override
  Widget build(BuildContext context) => GestureDetector(
    onTap: onTap,
    child: AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: active ? AppTheme.primary : Colors.transparent,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w600,
          color: active ? AppTheme.background : AppTheme.textSecondary,
        ),
      ),
    ),
  );
}

// ── Graphe barres hebdomadaire ────────────────────────────────────────

class _WeeklyBars extends StatefulWidget {
  final List<WeeklyEvolution> data;
  const _WeeklyBars({required this.data});

  @override
  State<_WeeklyBars> createState() => _WeeklyBarsState();
}

class _WeeklyBarsState extends State<_WeeklyBars> {
  int? _touchedIndex;

  @override
  Widget build(BuildContext context) {
    if (widget.data.isEmpty) {
      return const SizedBox(height: 180, child: Center(
        child: Text('Aucune donnée', style: TextStyle(color: AppTheme.textMuted)),
      ));
    }

    final maxVal = widget.data
        .map((e) => [e.totalDebit, e.totalCredit])
        .expand((e) => e)
        .fold(0.0, (a, b) => a > b ? a : b);

    return SizedBox(
      height: 180,
      child: BarChart(
        BarChartData(
          alignment: BarChartAlignment.spaceAround,
          maxY: maxVal * 1.25,
          barTouchData: BarTouchData(
            touchTooltipData: BarTouchTooltipData(
              getTooltipColor: (group) => AppTheme.surfaceLight,
              tooltipRoundedRadius: 10,
              getTooltipItem: (group, gi, rod, ri) => BarTooltipItem(
                '${rod.color == AppTheme.debit ? '↓' : '↑'} '
                '${Formatters.amountCompact(rod.toY)} TND',
                const TextStyle(
                  color: AppTheme.textPrimary,
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            touchCallback: (event, response) {
              setState(() {
                _touchedIndex = response?.spot?.touchedBarGroupIndex;
              });
            },
          ),
          titlesData: FlTitlesData(
            show: true,
            bottomTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                getTitlesWidget: (value, meta) {
                  final idx = value.toInt();
                  if (idx < 0 || idx >= widget.data.length) return const SizedBox();
                  // Abréviation du label "Lun 03 Mar" → "Lun"
                  final label = widget.data[idx].dayLabel.split(' ')[0];
                  return Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(
                      label,
                      style: const TextStyle(
                        fontSize: 10,
                        color: AppTheme.textSecondary,
                      ),
                    ),
                  );
                },
                reservedSize: 28,
              ),
            ),
            leftTitles: const AxisTitles(
              sideTitles: SideTitles(showTitles: false),
            ),
            topTitles: const AxisTitles(
              sideTitles: SideTitles(showTitles: false),
            ),
            rightTitles: const AxisTitles(
              sideTitles: SideTitles(showTitles: false),
            ),
          ),
          gridData: FlGridData(
            show: true,
            getDrawingHorizontalLine: (_) => FlLine(
              color: AppTheme.border,
              strokeWidth: 1,
              dashArray: [4, 4],
            ),
            drawVerticalLine: false,
          ),
          borderData: FlBorderData(show: false),
          barGroups: widget.data.asMap().entries.map((entry) {
            final idx = entry.key;
            final d   = entry.value;
            final isTouched = _touchedIndex == idx;

            return BarChartGroupData(
              x: idx,
              groupVertically: false,
              barRods: [
                BarChartRodData(
                  toY: d.totalDebit,
                  color: isTouched
                      ? AppTheme.debit
                      : AppTheme.debit.withOpacity(0.7),
                  width: 10,
                  borderRadius: const BorderRadius.vertical(
                      top: Radius.circular(5)),
                ),
                BarChartRodData(
                  toY: d.totalCredit,
                  color: isTouched
                      ? AppTheme.credit
                      : AppTheme.credit.withOpacity(0.7),
                  width: 10,
                  borderRadius: const BorderRadius.vertical(
                      top: Radius.circular(5)),
                ),
              ],
              barsSpace: 4,
            );
          }).toList(),
        ),
      ),
    );
  }
}

// ── Graphe ligne mensuel ──────────────────────────────────────────────

class _MonthlyLine extends StatelessWidget {
  final List<MonthlyEvolution> data;
  const _MonthlyLine({required this.data});

  @override
  Widget build(BuildContext context) {
    if (data.isEmpty) {
      return const SizedBox(height: 180, child: Center(
        child: Text('Aucune donnée', style: TextStyle(color: AppTheme.textMuted)),
      ));
    }

    final maxVal = data
        .map((e) => [e.totalDebit, e.totalCredit])
        .expand((e) => e)
        .fold(0.0, (a, b) => a > b ? a : b);

    return SizedBox(
      height: 180,
      child: LineChart(
        LineChartData(
          maxY: maxVal * 1.2,
          minY: 0,
          lineTouchData: LineTouchData(
            touchTooltipData: LineTouchTooltipData(
              getTooltipColor: (group) => AppTheme.surfaceLight,
              tooltipRoundedRadius: 10,
              getTooltipItems: (spots) => spots.map((s) => LineTooltipItem(
                Formatters.amountCompact(s.y),
                TextStyle(
                  color: s.bar.color ?? AppTheme.textPrimary,
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                ),
              )).toList(),
            ),
          ),
          titlesData: FlTitlesData(
            bottomTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                interval: (data.length / 4).ceil().toDouble(),
                getTitlesWidget: (value, meta) {
                  final idx = value.toInt();
                  if (idx < 0 || idx >= data.length) return const SizedBox();
                  return Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(
                      Formatters.monthLabel(data[idx].monthLabel),
                      style: const TextStyle(
                        fontSize: 10, color: AppTheme.textSecondary,
                      ),
                    ),
                  );
                },
                reservedSize: 28,
              ),
            ),
            leftTitles: const AxisTitles(
              sideTitles: SideTitles(showTitles: false)),
            topTitles: const AxisTitles(
              sideTitles: SideTitles(showTitles: false)),
            rightTitles: const AxisTitles(
              sideTitles: SideTitles(showTitles: false)),
          ),
          gridData: FlGridData(
            show: true,
            getDrawingHorizontalLine: (_) => FlLine(
              color: AppTheme.border,
              strokeWidth: 1,
              dashArray: [4, 4],
            ),
            drawVerticalLine: false,
          ),
          borderData: FlBorderData(show: false),
          lineBarsData: [
            // Ligne débits
            LineChartBarData(
              spots: data.asMap().entries
                  .map((e) => FlSpot(e.key.toDouble(), e.value.totalDebit))
                  .toList(),
              isCurved: true,
              color: AppTheme.debit,
              barWidth: 2.5,
              dotData: FlDotData(
                show: true,
                getDotPainter: (spot, _, bar, idx) => FlDotCirclePainter(
                  radius: 3,
                  color: AppTheme.debit,
                  strokeWidth: 0,
                ),
              ),
              belowBarData: BarAreaData(
                show: true,
                color: AppTheme.debit.withOpacity(0.08),
              ),
            ),
            // Ligne crédits
            LineChartBarData(
              spots: data.asMap().entries
                  .map((e) => FlSpot(e.key.toDouble(), e.value.totalCredit))
                  .toList(),
              isCurved: true,
              color: AppTheme.credit,
              barWidth: 2.5,
              dotData: FlDotData(
                show: true,
                getDotPainter: (spot, _, bar, idx) => FlDotCirclePainter(
                  radius: 3,
                  color: AppTheme.credit,
                  strokeWidth: 0,
                ),
              ),
              belowBarData: BarAreaData(
                show: true,
                color: AppTheme.credit.withOpacity(0.08),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
