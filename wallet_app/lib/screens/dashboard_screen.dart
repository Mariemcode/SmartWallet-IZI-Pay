// ════════════════════════════════════════════════════════════════
//  SmartWallet — DashboardScreen (refonte minimaliste)
//  4 onglets : Accueil • Stats • Actions • Profil
//  Utilise les noms RÉELS du DashboardProvider et des widgets.
// ════════════════════════════════════════════════════════════════
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/dashboard_provider.dart';
import '../services/notification_service.dart';
import '../theme/app_theme.dart';
import '../widgets/balance_card.dart';
import '../widgets/transaction_tile.dart';
import '../widgets/chart_section.dart';
import '../widgets/category_donut.dart';
import '../widgets/kpi_row.dart';
import '../widgets/period_selector.dart';
import '../widgets/top_categories_card.dart';
import '../widgets/sw_widgets.dart';
import 'profile_screen.dart';
import 'history_screen.dart';
import 'transaction_detail_screen.dart';
import 'new_transaction_screen.dart';
import 'prevision_screen.dart';
import 'recommendation_screen.dart';
import 'assistant_screen.dart';
import 'received_offers_screen.dart';

// ════════════════════════════════════════════════════════════════
//  Point d'entrée
// ════════════════════════════════════════════════════════════════
class DashboardScreen extends StatelessWidget {
  final String clientId;
  const DashboardScreen({super.key, required this.clientId});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => DashboardProvider()..init(clientId),
      child: _MainShell(clientId: clientId),
    );
  }
}

class _MainShell extends StatefulWidget {
  final String clientId;
  const _MainShell({required this.clientId});
  @override
  State<_MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<_MainShell> {
  int _currentIndex = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      NotificationService().registerTokenAndSubscribe(widget.clientId);
    });
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      _HomePage(clientId: widget.clientId),
      _StatsPage(clientId: widget.clientId),
      _ActionsPage(clientId: widget.clientId),
      ProfileScreen(clientId: widget.clientId),
    ];
    return Scaffold(
      backgroundColor: AppTheme.background,
      body: AnimatedSwitcher(
        duration: const Duration(milliseconds: 200),
        child: KeyedSubtree(
            key: ValueKey(_currentIndex), child: pages[_currentIndex]),
      ),
      bottomNavigationBar: _BottomNav(
        currentIndex: _currentIndex,
        onTap: (i) => setState(() => _currentIndex = i),
      ),
    );
  }
}

// ════════════════════════════════════════════════════════════════
//  Barre de navigation basse
// ════════════════════════════════════════════════════════════════
class _BottomNav extends StatelessWidget {
  final int currentIndex;
  final ValueChanged<int> onTap;
  const _BottomNav({required this.currentIndex, required this.onTap});

  @override
  Widget build(BuildContext context) {
    const items = [
      _NavItem('Accueil', Icons.home_rounded),
      _NavItem('Stats',   Icons.insert_chart_rounded),
      _NavItem('Actions', Icons.flash_on_rounded),
      _NavItem('Profil',  Icons.person_rounded),
    ];
    return Container(
      decoration: const BoxDecoration(
        color: AppTheme.surface,
        border: Border(top: BorderSide(color: AppTheme.border, width: 0.5)),
      ),
      padding: const EdgeInsets.fromLTRB(8, 8, 8, 16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: List.generate(items.length, (i) {
          final it = items[i];
          final selected = i == currentIndex;
          return GestureDetector(
            onTap: () => onTap(i),
            behavior: HitTestBehavior.opaque,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              padding:
              const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              decoration: BoxDecoration(
                color: selected
                    ? AppTheme.primary.withOpacity(0.12)
                    : Colors.transparent,
                borderRadius: BorderRadius.circular(14),
              ),
              child: Row(
                children: [
                  Icon(it.icon,
                      size: 20,
                      color: selected ? AppTheme.primary : AppTheme.textMuted),
                  if (selected) ...[
                    const SizedBox(width: 6),
                    Text(it.label,
                        style: const TextStyle(
                            color: AppTheme.primary,
                            fontSize: 12,
                            fontWeight: FontWeight.w700)),
                  ],
                ],
              ),
            ),
          );
        }),
      ),
    );
  }
}

class _NavItem {
  final String label;
  final IconData icon;
  const _NavItem(this.label, this.icon);
}

// ════════════════════════════════════════════════════════════════
//  ONGLET 1 — Accueil (header + solde + 5 dernières txs)
// ════════════════════════════════════════════════════════════════
class _HomePage extends StatelessWidget {
  final String clientId;
  const _HomePage({required this.clientId});

  @override
  Widget build(BuildContext context) {
    return Consumer<DashboardProvider>(
      builder: (_, prov, __) {
        if (prov.loading && prov.walletSummary == null) {
          return const Center(child: SwLoader(label: 'Chargement...'));
        }
        return SafeArea(
          child: RefreshIndicator(
            color: AppTheme.primary,
            onRefresh: () => prov.loadDashboard(),
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 20),
              children: [
                _HomeHeader(clientId: clientId, prov: prov),
                const SizedBox(height: 18),
                BalanceCard(
                  summary: prov.walletSummary,
                  profile: prov.clientProfile,
                  loading: prov.loading && prov.walletSummary == null,
                ),
                const SizedBox(height: 22),
                SwSectionHeader(
                  title: 'TRANSACTIONS RÉCENTES',
                  icon: Icons.history_rounded,
                  action: 'Voir tout',
                  onAction: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => ChangeNotifierProvider.value(
                        value: prov,
                        child: HistoryScreen(clientId: clientId),
                      ),
                    ),
                  ),
                ),
                _RecentList(prov: prov, clientId: clientId),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _HomeHeader extends StatelessWidget {
  final String clientId;
  final DashboardProvider prov;
  const _HomeHeader({required this.clientId, required this.prov});

  @override
  Widget build(BuildContext context) {
    final hour = DateTime.now().hour;
    final salut =
    hour < 12 ? 'Bonjour' : hour < 19 ? 'Bon après-midi' : 'Bonsoir';
    final first = prov.clientProfile?.firstName ?? '';

    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(salut,
                  style:
                  const TextStyle(color: AppTheme.textMuted, fontSize: 12)),
              const SizedBox(height: 2),
              Text(first.isEmpty ? 'SmartWallet' : first,
                  style: const TextStyle(
                      color: AppTheme.textPrimary,
                      fontSize: 22,
                      fontWeight: FontWeight.w800)),
            ],
          ),
        ),
        _HeaderIcon(
          icon: Icons.local_offer_rounded,
          color: AppTheme.accent,
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(
                builder: (_) => ReceivedOffersScreen(clientId: clientId)),
          ),
        ),
        const SizedBox(width: 8),
        _HeaderIcon(
          icon: Icons.smart_toy_outlined,
          color: AppTheme.primary,
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(
                builder: (_) => AssistantScreen(clientId: clientId)),
          ),
        ),
      ],
    );
  }
}

class _HeaderIcon extends StatelessWidget {
  final IconData icon;
  final Color color;
  final VoidCallback onTap;
  const _HeaderIcon(
      {required this.icon, required this.color, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 42, height: 42,
        decoration: BoxDecoration(
          color: color.withOpacity(0.12),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Icon(icon, color: color, size: 20),
      ),
    );
  }
}

class _RecentList extends StatelessWidget {
  final DashboardProvider prov;
  final String clientId;
  const _RecentList({required this.prov, required this.clientId});

  @override
  Widget build(BuildContext context) {
    final txs = prov.transactions;
    if (txs.isEmpty) {
      return SwCard(
        padding: const EdgeInsets.symmetric(vertical: 36),
        child: Center(
          child: Column(
            children: [
              Icon(Icons.receipt_long_rounded,
                  color: AppTheme.textMuted.withOpacity(0.5), size: 40),
              const SizedBox(height: 10),
              const Text('Aucune transaction',
                  style:
                  TextStyle(color: AppTheme.textMuted, fontSize: 13)),
            ],
          ),
        ),
      );
    }
    return Column(
      children: txs.take(5).map((tx) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: TransactionTile(
          transaction: tx,
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(
                builder: (_) => TransactionDetailScreen(
                  clientId: clientId,
                  transactionId: tx.id,
                )),
          ),
        ),
      )).toList(),
    );
  }
}

// ════════════════════════════════════════════════════════════════
//  ONGLET 2 — Stats (graphes conservés intégralement)
// ════════════════════════════════════════════════════════════════
class _StatsPage extends StatelessWidget {
  final String clientId;
  const _StatsPage({required this.clientId});

  @override
  Widget build(BuildContext context) {
    return Consumer<DashboardProvider>(
      builder: (_, prov, __) {
        return SafeArea(
          child: Column(
            children: [
              const SwAppBar(
                  title: 'Statistiques',
                  subtitle: 'Vue d\'ensemble',
                  showBack: false),
              Expanded(
                child: RefreshIndicator(
                  onRefresh: () => prov.loadDashboard(),
                  color: AppTheme.primary,
                  child: ListView(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 20),
                    children: [
                      PeriodSelector(
                        selected: prov.selectedPeriod,
                        onChanged: prov.changePeriod,
                      ),
                      const SizedBox(height: 16),
                      KpiRow(
                        summary: prov.walletSummary,
                        stats: prov.periodStats,
                        loading: prov.loading,
                      ),
                      const SizedBox(height: 16),
                      const SwSectionHeader(
                          title: 'ÉVOLUTION',
                          icon: Icons.show_chart_rounded),
                      ChartSection(
                        weeklyData:  prov.weeklyEvolution,
                        monthlyData: prov.monthlyEvolution,
                        activeTab:   prov.activeChartTab,
                        onTabChanged: prov.setChartTab,
                        loading:     prov.loading,
                      ),
                      const SizedBox(height: 16),
                      const SwSectionHeader(
                          title: 'CATÉGORIES',
                          icon: Icons.donut_large_rounded),
                      CategoryDonut(
                        data: prov.categoryBreakdown,
                        loading: prov.loading,
                      ),
                      const SizedBox(height: 16),
                      const SwSectionHeader(
                          title: 'TOP DÉPENSES',
                          icon: Icons.bar_chart_rounded),
                      TopCategoriesCard(
                        data: prov.topCategories,
                        loading: prov.loading,
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

// ════════════════════════════════════════════════════════════════
//  ONGLET 3 — Actions (grille rapide)
// ════════════════════════════════════════════════════════════════
class _ActionsPage extends StatelessWidget {
  final String clientId;
  const _ActionsPage({required this.clientId});

  @override
  Widget build(BuildContext context) {
    return Consumer<DashboardProvider>(
      builder: (_, prov, __) {
        // ── Actions normales (route simple) ────────────────────
        Widget simpleAction({
          required IconData icon,
          required String label,
          required Color color,
          required Widget Function() builder,
        }) {
          return SwCard(
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => builder()),
            ),
            child: _actionCard(icon, label, color),
          );
        }

        // ── Action Historique (besoin du provider) ─────────────
        Widget historyAction() {
          return SwCard(
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => ChangeNotifierProvider.value(
                  value: prov,
                  child: HistoryScreen(clientId: clientId),
                ),
              ),
            ),
            child: _actionCard(
                Icons.history_rounded, 'Historique\ncomplet', AppTheme.textMuted),
          );
        }

        final cards = <Widget>[
          simpleAction(
            icon: Icons.add_circle_outline_rounded,
            label: 'Nouvelle\ntransaction',
            color: AppTheme.primary,
            builder: () => NewTransactionScreen(clientId: clientId),
          ),
          simpleAction(
            icon: Icons.local_offer_rounded,
            label: 'Offres\nreçues',
            color: AppTheme.accent,
            builder: () => ReceivedOffersScreen(clientId: clientId),
          ),
          simpleAction(
            icon: Icons.lightbulb_outline_rounded,
            label: 'Mes\nconseils',
            color: AppTheme.primary,
            builder: () => RecommendationScreen(clientId: clientId),
          ),
          simpleAction(
            icon: Icons.trending_up_rounded,
            label: 'Prévisions\nML',
            color: AppTheme.accentOrange,
            builder: () => PrevisionScreen(clientId: clientId),
          ),
          historyAction(),
          simpleAction(
            icon: Icons.smart_toy_outlined,
            label: 'Assistant\nIA',
            color: AppTheme.primary,
            builder: () => AssistantScreen(clientId: clientId),
          ),
        ];

        return SafeArea(
          child: Column(
            children: [
              const SwAppBar(
                  title: 'Actions',
                  subtitle: 'Tout en un coup d\'œil',
                  showBack: false),
              Expanded(
                child: GridView.count(
                  padding: const EdgeInsets.all(16),
                  crossAxisCount: 2,
                  mainAxisSpacing: 12,
                  crossAxisSpacing: 12,
                  childAspectRatio: 1.05,
                  children: cards,
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _actionCard(IconData icon, String label, Color color) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Container(
          width: 56, height: 56,
          decoration: BoxDecoration(
            color: color.withOpacity(0.12),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Icon(icon, color: color, size: 26),
        ),
        const SizedBox(height: 14),
        Text(label,
            textAlign: TextAlign.center,
            style: const TextStyle(
                color: AppTheme.textPrimary,
                fontSize: 12.5,
                fontWeight: FontWeight.w700,
                height: 1.3)),
      ],
    );
  }
}