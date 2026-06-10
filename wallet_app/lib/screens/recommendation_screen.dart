import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../theme/app_theme.dart';
import '../services/reco_provider.dart';

// ════════════════════════════════════════════════════════════
//  SmartWallet — Écran Recommandations personnalisées (Module 6)
//  3 sections : Finances · Défis · Alertes
//  Navigation : bottom nav bar flottante moderne
//  Palette et logique identiques — design réorganisé
// ════════════════════════════════════════════════════════════

class RecommendationScreen extends StatelessWidget {
  final String clientId;
  const RecommendationScreen({super.key, required this.clientId});

  @override
  Widget build(BuildContext ctx) => ChangeNotifierProvider(
      create: (_) => RecoProvider()..load(clientId),
      child: _Body(clientId: clientId));
}

class _Body extends StatefulWidget {
  final String clientId;
  const _Body({required this.clientId});
  @override
  State<_Body> createState() => _BodyState();
}

class _BodyState extends State<_Body> {
  int _tab = 0;

  @override
  Widget build(BuildContext ctx) {
    return Consumer<RecoProvider>(
      builder: (_, p, __) => Scaffold(
        backgroundColor: AppTheme.background,
        extendBody: true,
        appBar: _buildAppBar(p),
        body: p.loading
            ? _LoadingView()
            : p.error != null
            ? _ErrView(p)
            : _buildBody(p),
        bottomNavigationBar: _buildBottomNav(p),
      ),
    );
  }

  PreferredSizeWidget _buildAppBar(RecoProvider p) => AppBar(
    backgroundColor: AppTheme.background,
    elevation: 0,
    scrolledUnderElevation: 0,
    leading: IconButton(
      icon: Container(
        width: 36,
        height: 36,
        decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: AppTheme.border),
        ),
        child: const Icon(Icons.arrow_back_ios_new_rounded,
            size: 15, color: AppTheme.textPrimary),
      ),
      onPressed: () => Navigator.pop(context),
    ),
    title: Column(
      children: const [
        Text('Mes conseils',
            style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w800,
                color: AppTheme.textPrimary)),
        Text('Analyse personnalisée',
            style:
            TextStyle(fontSize: 10, color: AppTheme.textMuted)),
      ],
    ),
    centerTitle: true,
    actions: [
      Padding(
        padding: const EdgeInsets.only(right: 12),
        child: GestureDetector(
          onTap: p.reload,
          child: Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: AppTheme.surface,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: AppTheme.border),
            ),
            child: p.loading
                ? const Padding(
                padding: EdgeInsets.all(9),
                child: CircularProgressIndicator(
                    color: AppTheme.primary, strokeWidth: 2))
                : const Icon(Icons.refresh_rounded,
                color: AppTheme.textSecondary, size: 18),
          ),
        ),
      ),
    ],
  );

  Widget _buildBody(RecoProvider p) {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 250),
      child: switch (_tab) {
        0 => _FinancesTab(p, key: const ValueKey(0)),
        1 => _DefisTab(p, key: const ValueKey(1)),
        _ => _AlertesTab(p, key: const ValueKey(2)),
      },
    );
  }

  Widget _buildBottomNav(RecoProvider p) => Padding(
    padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
    child: Container(
      height: 64,
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: AppTheme.border),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withOpacity(0.08),
              blurRadius: 20,
              offset: const Offset(0, 4)),
        ],
      ),
      child: Row(
        children: [
          _NavItem(
              icon: Icons.account_balance_wallet_rounded,
              label: 'Finances',
              badge: p.nbConseils,
              badgeColor: AppTheme.primary,
              selected: _tab == 0,
              onTap: () => setState(() => _tab = 0)),
          _NavItem(
              icon: Icons.emoji_events_rounded,
              label: 'Défis',
              badge: p.nbChallenges,
              badgeColor: const Color(0xFFFFD700),
              selected: _tab == 1,
              onTap: () => setState(() => _tab = 1)),
          _NavItem(
              icon: Icons.warning_amber_rounded,
              label: 'Alertes',
              badge: p.nbAlertes,
              badgeColor: AppTheme.debit,
              selected: _tab == 2,
              onTap: () => setState(() => _tab = 2)),
        ],
      ),
    ),
  );
}

// ═══════════════════════════════════════════════════════
//  BOTTOM NAV ITEM
// ═══════════════════════════════════════════════════════
class _NavItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final int badge;
  final Color badgeColor;
  final bool selected;
  final VoidCallback onTap;
  const _NavItem(
      {required this.icon,
        required this.label,
        required this.badge,
        required this.badgeColor,
        required this.selected,
        required this.onTap});

  @override
  Widget build(BuildContext ctx) => Expanded(
    child: GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        margin: const EdgeInsets.all(6),
        padding: const EdgeInsets.symmetric(vertical: 4),
        decoration: BoxDecoration(
          color: selected
              ? AppTheme.primary.withOpacity(0.1)
              : Colors.transparent,
          borderRadius: BorderRadius.circular(18),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Stack(clipBehavior: Clip.none, children: [
              Icon(icon,
                  size: 20,
                  color: selected
                      ? AppTheme.primary
                      : AppTheme.textMuted),
              if (badge > 0)
                Positioned(
                  right: -8,
                  top: -4,
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 4, vertical: 1),
                    decoration: BoxDecoration(
                        color: badgeColor,
                        borderRadius: BorderRadius.circular(8)),
                    child: Text('$badge',
                        style: const TextStyle(
                            color: Colors.white,
                            fontSize: 8,
                            fontWeight: FontWeight.w800)),
                  ),
                ),
            ]),
            Text(label,
                style: TextStyle(
                    fontSize: 9,
                    fontWeight: FontWeight.w600,
                    color: selected
                        ? AppTheme.primary
                        : AppTheme.textMuted)),
          ],
        ),
      ),
    ),
  );
}

// ═══════════════════════════════════════════════════════
//  TAB 1 — FINANCES
// ═══════════════════════════════════════════════════════
class _FinancesTab extends StatelessWidget {
  final RecoProvider p;
  const _FinancesTab(this.p, {super.key});

  @override
  Widget build(BuildContext ctx) => ListView(
    padding:
    const EdgeInsets.fromLTRB(16, 8, 16, 100),
    children: [
      _ResumeCard(p),
      const SizedBox(height: 20),

      // Budget par catégorie (LIVE)
      if (p.budgetLive.isNotEmpty) ...[
        _SectionHeader(
            'Par catégorie', 'Ce mois vs le mois dernier',
            icon: Icons.bar_chart_rounded,
            color: AppTheme.primary),
        const SizedBox(height: 10),
        ...p.budgetLive.map((b) => _BudgetCard(b)),
        const SizedBox(height: 8),
      ],

      // Budget targets PKL
      if (p.budgetTargets.isNotEmpty) ...[
        const SizedBox(height: 4),
        _SectionHeader(
            'Objectifs budget', 'Comparé aux clients similaires',
            icon: Icons.people_alt_rounded,
            color: const Color(0xFF6366F1)),
        const SizedBox(height: 10),
        ...p.budgetTargets.map((t) => _TargetCard(t)),
        const SizedBox(height: 8),
      ],

      // Conseils
      if (p.conseils.isNotEmpty) ...[
        const SizedBox(height: 4),
        Row(children: [
          _SectionHeader('Conseils', 'Recommandations IA',
              icon: Icons.lightbulb_outline_rounded,
              color: AppTheme.accent),
          const Spacer(),
          if (p.economiePotentielle > 0)
            Container(
              padding: const EdgeInsets.symmetric(
                  horizontal: 10, vertical: 5),
              decoration: BoxDecoration(
                  color: AppTheme.credit.withOpacity(0.12),
                  borderRadius: BorderRadius.circular(20)),
              child: Row(children: [
                const Icon(Icons.savings_rounded,
                    size: 12, color: AppTheme.credit),
                const SizedBox(width: 4),
                Text(
                    '~${p.economiePotentielle.toStringAsFixed(0)} TND',
                    style: const TextStyle(
                        color: AppTheme.credit,
                        fontSize: 11,
                        fontWeight: FontWeight.w700)),
              ]),
            ),
        ]),
        const SizedBox(height: 10),
        ...p.conseils.map((c) => _ConseilCard(c)),
        const SizedBox(height: 8),
      ],

      // Forfait télécom PKL
      if (p.forfaitTelecom.isNotEmpty) ...[
        const SizedBox(height: 4),
        _SectionHeader(
            'Optimisation forfait',
            'Suggestion basée sur vos habitudes',
            icon: Icons.phone_android_rounded,
            color: const Color(0xFF6366F1)),
        const SizedBox(height: 10),
        ...p.forfaitTelecom
            .whereType<Map<String, dynamic>>()
            .map((f) => _ForfaitCard(f)),
      ],

      if (p.budgetLive.isEmpty && p.conseils.isEmpty)
        _Empty(Icons.check_circle_rounded, 'Pas encore de données',
            'Faites des transactions pour voir vos stats'),
    ],
  );
}

// ═══════════════════════════════════════════════════════
//  FORFAIT CARD
// ═══════════════════════════════════════════════════════
class _ForfaitCard extends StatelessWidget {
  final Map<String, dynamic> f;
  const _ForfaitCard(this.f);

  @override
  Widget build(BuildContext ctx) => Container(
    margin: const EdgeInsets.only(bottom: 8),
    padding: const EdgeInsets.all(16),
    decoration: BoxDecoration(
      gradient: LinearGradient(
        colors: [
          const Color(0xFF6366F1).withOpacity(0.08),
          const Color(0xFF6366F1).withOpacity(0.03),
        ],
      ),
      borderRadius: BorderRadius.circular(16),
      border:
      Border.all(color: const Color(0xFF6366F1).withOpacity(0.2)),
    ),
    child: Row(children: [
      Container(
        width: 44,
        height: 44,
        decoration: BoxDecoration(
          color: const Color(0xFF6366F1).withOpacity(0.1),
          borderRadius: BorderRadius.circular(14),
        ),
        child: const Icon(Icons.phone_android_rounded,
            color: Color(0xFF6366F1), size: 22),
      ),
      const SizedBox(width: 12),
      Expanded(
          child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                    f['conseil']?.toString() ??
                        f['message']?.toString() ??
                        '',
                    style: const TextStyle(
                        color: AppTheme.textPrimary,
                        fontSize: 13,
                        fontWeight: FontWeight.w600)),
                if (f['economie_mensuelle'] != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 3),
                    child: Text(
                        'Économie : ${(f['economie_mensuelle'] as num).toStringAsFixed(1)} TND/mois',
                        style: const TextStyle(
                            color: AppTheme.credit, fontSize: 11)),
                  ),
              ])),
    ]),
  );
}

// ═══════════════════════════════════════════════════════
//  RESUME CARD (hero card top)
// ═══════════════════════════════════════════════════════
class _ResumeCard extends StatelessWidget {
  final RecoProvider p;
  const _ResumeCard(this.p);

  @override
  Widget build(BuildContext ctx) {
    final r = p.resumeMois;
    final ce = (r['depense_ce_mois'] as num?)?.toDouble() ?? 0;
    final prec = (r['depense_mois_dernier'] as num?)?.toDouble() ?? 0;
    final diff = (r['difference'] as num?)?.toDouble() ?? 0;
    final tendance = r['tendance'] as String? ?? 'stable';
    final isDown = tendance == 'baisse';
    final isUp = tendance == 'hausse';

    final List<Color> gradColors = isDown
        ? [const Color(0xFF00B894), const Color(0xFF00E5A0)]
        : isUp
        ? [const Color(0xFFFF6B6B), const Color(0xFFFF8E8E)]
        : [const Color(0xFF6C5CE7), const Color(0xFFA29BFE)];

    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        gradient: LinearGradient(
            colors: gradColors, begin: Alignment.topLeft, end: Alignment.bottomRight),
        borderRadius: BorderRadius.circular(24),
        boxShadow: [
          BoxShadow(
              color: gradColors[0].withOpacity(0.35),
              blurRadius: 20,
              offset: const Offset(0, 8)),
        ],
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(
            padding:
            const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.2),
                borderRadius: BorderRadius.circular(20)),
            child: Text(
                isDown ? '📉 En baisse' : isUp ? '📈 En hausse' : '〰 Stable',
                style: const TextStyle(
                    color: Colors.white,
                    fontSize: 11,
                    fontWeight: FontWeight.w600)),
          ),
          const Spacer(),
          if (prec > 0)
            Container(
              padding:
              const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              decoration: BoxDecoration(
                  color: Colors.white.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(14)),
              child: Text(
                  diff >= 0
                      ? '+${diff.toStringAsFixed(0)} TND'
                      : '${diff.toStringAsFixed(0)} TND',
                  style: const TextStyle(
                      color: Colors.white,
                      fontSize: 14,
                      fontWeight: FontWeight.w800)),
            ),
        ]),
        const SizedBox(height: 16),
        Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
          Text('${ce.toStringAsFixed(0)}',
              style: const TextStyle(
                  color: Colors.white,
                  fontSize: 40,
                  fontWeight: FontWeight.w900,
                  height: 1)),
          const Padding(
            padding: EdgeInsets.only(bottom: 6, left: 6),
            child: Text('TND',
                style: TextStyle(
                    color: Colors.white70,
                    fontSize: 15,
                    fontWeight: FontWeight.w500)),
          ),
        ]),
        const SizedBox(height: 8),
        Text(
            isDown
                ? 'Vous avez économisé ${(-diff).toStringAsFixed(0)} TND ce mois 🎉'
                : isUp
                ? 'Dépenses en hausse vs le mois dernier'
                : 'Dépenses stables par rapport au mois dernier',
            style: const TextStyle(
                color: Colors.white,
                fontSize: 12,
                fontWeight: FontWeight.w500)),
      ]),
    );
  }
}

// ═══════════════════════════════════════════════════════
//  BUDGET CARD
// ═══════════════════════════════════════════════════════
class _BudgetCard extends StatelessWidget {
  final Map<String, dynamic> b;
  const _BudgetCard(this.b);

  @override
  Widget build(BuildContext ctx) {
    final ce = (b['ce_mois'] as num?)?.toDouble() ?? 0;
    final prec = (b['mois_dernier'] as num?)?.toDouble() ?? 0;
    final statut = b['statut'] as String? ?? 'stable';
    final msg = b['message'] as String? ?? '';
    final isUp = statut == 'hausse';
    final isDown = statut == 'baisse';
    final color = isUp
        ? AppTheme.debit
        : isDown
        ? AppTheme.credit
        : AppTheme.textMuted;
    final pct =
    prec > 0 ? (ce / (prec == 0 ? 1 : prec)).clamp(0.0, 2.0) : 0.0;

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
              color: isUp
                  ? AppTheme.debit.withOpacity(0.2)
                  : AppTheme.border)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(b['categorie'] ?? '',
                        style: const TextStyle(
                            color: AppTheme.textPrimary,
                            fontSize: 13,
                            fontWeight: FontWeight.w600)),
                    const SizedBox(height: 2),
                    Text(msg,
                        style: TextStyle(color: color, fontSize: 11)),
                  ])),
          const SizedBox(width: 12),
          Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
            Text('${ce.toStringAsFixed(0)} TND',
                style: const TextStyle(
                    color: AppTheme.textPrimary,
                    fontSize: 15,
                    fontWeight: FontWeight.w700)),
            if (prec > 0)
              Text('vs ${prec.toStringAsFixed(0)} TND',
                  style: const TextStyle(
                      color: AppTheme.textMuted, fontSize: 10)),
          ]),
          const SizedBox(width: 8),
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
                color: color.withOpacity(0.1),
                borderRadius: BorderRadius.circular(10)),
            child: Icon(
                isUp
                    ? Icons.trending_up_rounded
                    : isDown
                    ? Icons.trending_down_rounded
                    : Icons.trending_flat_rounded,
                color: color,
                size: 18),
          ),
        ]),
        if (prec > 0) ...[
          const SizedBox(height: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: LinearProgressIndicator(
              value: pct.clamp(0.0, 1.0),
              minHeight: 5,
              backgroundColor: color.withOpacity(0.1),
              valueColor: AlwaysStoppedAnimation(color),
            ),
          ),
        ],
      ]),
    );
  }
}

// ═══════════════════════════════════════════════════════
//  TARGET CARD
// ═══════════════════════════════════════════════════════
class _TargetCard extends StatelessWidget {
  final Map<String, dynamic> t;
  const _TargetCard(this.t);

  @override
  Widget build(BuildContext ctx) {
    final cat = t['categorie'] as String? ?? '';
    final actuel = (t['actuel_tnd'] as num?)?.toDouble() ?? 0;
    final cible = (t['cible_tnd'] as num?)?.toDouble() ?? 0;
    final ecart = (t['ecart_tnd'] as num?)?.toDouble() ?? 0;
    final statut = t['statut'] as String? ?? '';
    final isAbove = statut == 'au-dessus';
    final c = isAbove ? AppTheme.debit : AppTheme.credit;
    final pct = cible > 0 ? (actuel / cible).clamp(0.0, 1.5) : 0.0;

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppTheme.border)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Expanded(
              child: Text(cat,
                  style: const TextStyle(
                      color: AppTheme.textPrimary,
                      fontSize: 13,
                      fontWeight: FontWeight.w600))),
          Container(
            padding:
            const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            decoration: BoxDecoration(
                color: c.withOpacity(0.1),
                borderRadius: BorderRadius.circular(8)),
            child: Text(
                isAbove
                    ? '+${ecart.toStringAsFixed(0)} TND'
                    : '${ecart.toStringAsFixed(0)} TND',
                style: TextStyle(
                    color: c,
                    fontSize: 11,
                    fontWeight: FontWeight.w700)),
          ),
        ]),
        const SizedBox(height: 8),
        ClipRRect(
          borderRadius: BorderRadius.circular(6),
          child: LinearProgressIndicator(
            value: pct.clamp(0.0, 1.0),
            minHeight: 6,
            backgroundColor: c.withOpacity(0.1),
            valueColor: AlwaysStoppedAnimation(c),
          ),
        ),
        const SizedBox(height: 6),
        Row(children: [
          Text('${actuel.toStringAsFixed(0)} TND',
              style: const TextStyle(
                  color: AppTheme.textSecondary, fontSize: 10)),
          const Spacer(),
          Text('Cible : ${cible.toStringAsFixed(0)} TND',
              style:
              const TextStyle(color: AppTheme.textMuted, fontSize: 10)),
        ]),
      ]),
    );
  }
}

// ═══════════════════════════════════════════════════════
//  CONSEIL CARD
// ═══════════════════════════════════════════════════════
class _ConseilCard extends StatelessWidget {
  final Map<String, dynamic> c;
  const _ConseilCard(this.c);

  @override
  Widget build(BuildContext ctx) {
    final isH = c['priorite'] == 'haute';
    final isBravo = c['priorite'] == 'bravo';
    final eco = (c['economie_tnd'] as num?)?.toDouble() ?? 0;
    final color =
    isBravo ? AppTheme.credit : isH ? AppTheme.debit : AppTheme.accent;
    final icon = isBravo
        ? Icons.celebration_rounded
        : isH
        ? Icons.priority_high_rounded
        : Icons.lightbulb_outline_rounded;

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: color.withOpacity(0.2))),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Container(
          width: 38,
          height: 38,
          decoration: BoxDecoration(
              color: color.withOpacity(0.1),
              borderRadius: BorderRadius.circular(12)),
          child: Icon(icon, color: color, size: 18),
        ),
        const SizedBox(width: 12),
        Expanded(
            child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(c['message'] ?? '',
                      style: const TextStyle(
                          color: AppTheme.textPrimary,
                          fontSize: 13,
                          fontWeight: FontWeight.w600)),
                  if (c['conseil'] != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 3),
                      child: Text(c['conseil'],
                          style: const TextStyle(
                              color: AppTheme.textSecondary,
                              fontSize: 11.5,
                              height: 1.4)),
                    ),
                ])),
        if (eco > 0)
          Container(
            margin: const EdgeInsets.only(left: 8),
            padding:
            const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
                color: AppTheme.credit.withOpacity(0.12),
                borderRadius: BorderRadius.circular(10)),
            child: Text('-${eco.toStringAsFixed(0)}',
                style: const TextStyle(
                    color: AppTheme.credit,
                    fontSize: 11,
                    fontWeight: FontWeight.w700)),
          ),
      ]),
    );
  }
}

// ═══════════════════════════════════════════════════════
//  TAB 2 — DÉFIS
// ═══════════════════════════════════════════════════════
class _DefisTab extends StatelessWidget {
  final RecoProvider p;
  const _DefisTab(this.p, {super.key});

  @override
  Widget build(BuildContext ctx) => ListView(
    padding: const EdgeInsets.fromLTRB(16, 8, 16, 100),
    children: [
      if (p.challenges.isEmpty)
        _Empty(Icons.emoji_events_rounded, 'Pas de défis',
            'Revenez bientôt pour voir vos défis'),
      ...p.challenges.map((c) => _DefiCard(c)),
    ],
  );
}

class _DefiCard extends StatelessWidget {
  final Map<String, dynamic> c;
  const _DefiCard(this.c);

  @override
  Widget build(BuildContext ctx) {
    final prog = (c['progres'] as num?)?.toInt() ?? 0;
    final obj = (c['objectif'] as num?)?.toInt() ?? 1;
    final pct = (prog / obj).clamp(0.0, 1.0);
    final done = c['complete'] == true;
    final diff = c['difficulte'] ?? 'facile';
    final dc = switch (diff) {
      'facile' => AppTheme.credit,
      'moyen' => AppTheme.accent,
      'difficile' => AppTheme.debit,
      _ => AppTheme.textMuted,
    };
    final ic = switch (c['icone'] ?? '') {
      'receipt_long' => Icons.receipt_long_rounded,
      'explore' => Icons.explore_rounded,
      'savings' => Icons.savings_rounded,
      'shield' => Icons.shield_rounded,
      _ => Icons.star_rounded,
    };
    final activeColor = done ? AppTheme.credit : dc;

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
          color: done ? AppTheme.credit.withOpacity(0.05) : AppTheme.surface,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
              color: done
                  ? AppTheme.credit.withOpacity(0.3)
                  : AppTheme.border)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
                color: activeColor.withOpacity(0.12),
                borderRadius: BorderRadius.circular(16)),
            child: Icon(done ? Icons.check_rounded : ic,
                color: activeColor, size: 24),
          ),
          const SizedBox(width: 12),
          Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(c['titre'] ?? '',
                        style: TextStyle(
                            color: done
                                ? AppTheme.credit
                                : AppTheme.textPrimary,
                            fontSize: 14,
                            fontWeight: FontWeight.w700)),
                    const SizedBox(height: 2),
                    Text(c['description'] ?? '',
                        style: const TextStyle(
                            color: AppTheme.textSecondary,
                            fontSize: 11.5,
                            height: 1.3)),
                  ])),
          Container(
            padding:
            const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
                color: activeColor.withOpacity(0.1),
                borderRadius: BorderRadius.circular(10)),
            child: Text(diff,
                style: TextStyle(
                    color: activeColor,
                    fontSize: 10,
                    fontWeight: FontWeight.w700)),
          ),
        ]),
        const SizedBox(height: 14),
        Row(children: [
          Expanded(
              child: ClipRRect(
                borderRadius: BorderRadius.circular(6),
                child: LinearProgressIndicator(
                    value: pct,
                    minHeight: 7,
                    backgroundColor: activeColor.withOpacity(0.1),
                    valueColor: AlwaysStoppedAnimation(activeColor)),
              )),
          const SizedBox(width: 10),
          Text(done ? '✓ Complété' : '$prog / $obj',
              style: TextStyle(
                  color: activeColor,
                  fontSize: 11,
                  fontWeight: FontWeight.w700)),
        ]),
      ]),
    );
  }
}

// ═══════════════════════════════════════════════════════
//  TAB 3 — ALERTES
// ═══════════════════════════════════════════════════════
class _AlertesTab extends StatelessWidget {
  final RecoProvider p;
  const _AlertesTab(this.p, {super.key});

  @override
  Widget build(BuildContext ctx) => ListView(
    padding: const EdgeInsets.fromLTRB(16, 8, 16, 100),
    children: [
      if (p.alertes.isEmpty)
        _Empty(Icons.check_circle_rounded, 'Tout va bien',
            'Aucun changement inhabituel détecté'),
      ...p.alertes.map((a) {
        final isH = a['severite'] == 'haute';
        final isFresh = a['fresh'] == true;
        final c = isH ? AppTheme.debit : AppTheme.accent;
        final label = isFresh ? 'RÉCENT' : isH ? 'IMPORTANT' : 'INFO';

        return Container(
          margin: const EdgeInsets.only(bottom: 10),
          decoration: BoxDecoration(
              color: AppTheme.surface,
              borderRadius: BorderRadius.circular(18),
              border: Border.all(
                  color: isH ? c.withOpacity(0.3) : AppTheme.border)),
          child: Column(children: [
            // Header strip
            Container(
              padding: const EdgeInsets.symmetric(
                  horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: c.withOpacity(0.07),
                borderRadius: const BorderRadius.vertical(
                    top: Radius.circular(18)),
              ),
              child: Row(children: [
                Icon(
                    isH
                        ? Icons.warning_rounded
                        : Icons.info_outline_rounded,
                    color: c,
                    size: 18),
                const SizedBox(width: 6),
                Expanded(
                    child: Text(
                        isH ? 'Alerte importante' : 'Information',
                        style: TextStyle(
                            color: c,
                            fontSize: 11,
                            fontWeight: FontWeight.w700))),
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                      color: c.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(8)),
                  child: Text(label,
                      style: TextStyle(
                          color: c,
                          fontSize: 9,
                          fontWeight: FontWeight.w800)),
                ),
              ]),
            ),
            // Content
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 10, 14, 12),
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(a['message'] ?? '',
                        style: const TextStyle(
                            color: AppTheme.textPrimary,
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            height: 1.4)),
                    if (!isFresh && (a['habituel'] as num?) != null)
                      Padding(
                        padding: const EdgeInsets.only(top: 6),
                        child: Text(
                            'Montant habituel : ${(a['habituel'] as num).toStringAsFixed(0)} TND',
                            style: TextStyle(
                                color: c, fontSize: 11)),
                      ),
                  ]),
            ),
          ]),
        );
      }),
    ],
  );
}

// ═══════════════════════════════════════════════════════
//  SECTION HEADER
// ═══════════════════════════════════════════════════════
class _SectionHeader extends StatelessWidget {
  final String title, sub;
  final IconData icon;
  final Color color;
  const _SectionHeader(this.title, this.sub,
      {required this.icon, required this.color});

  @override
  Widget build(BuildContext ctx) => Row(
    crossAxisAlignment: CrossAxisAlignment.center,
    children: [
      Container(
        width: 30,
        height: 30,
        decoration: BoxDecoration(
            color: color.withOpacity(0.1),
            borderRadius: BorderRadius.circular(10)),
        child: Icon(icon, size: 15, color: color),
      ),
      const SizedBox(width: 10),
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title,
            style: const TextStyle(
                color: AppTheme.textPrimary,
                fontSize: 14,
                fontWeight: FontWeight.w700)),
        Text(sub,
            style: const TextStyle(
                color: AppTheme.textMuted, fontSize: 10)),
      ]),
    ],
  );
}

// ═══════════════════════════════════════════════════════
//  LOADING VIEW
// ═══════════════════════════════════════════════════════
class _LoadingView extends StatelessWidget {
  @override
  Widget build(BuildContext ctx) => Center(
    child: Column(mainAxisSize: MainAxisSize.min, children: [
      SizedBox(
        width: 48,
        height: 48,
        child: CircularProgressIndicator(
            color: AppTheme.primary,
            strokeWidth: 3,
            backgroundColor: AppTheme.primary.withOpacity(0.1)),
      ),
      const SizedBox(height: 16),
      const Text('Analyse en cours...',
          style:
          TextStyle(color: AppTheme.textMuted, fontSize: 13)),
    ]),
  );
}

// ═══════════════════════════════════════════════════════
//  EMPTY STATE
// ═══════════════════════════════════════════════════════
class _Empty extends StatelessWidget {
  final IconData icon;
  final String t, d;
  const _Empty(this.icon, this.t, this.d);

  @override
  Widget build(BuildContext ctx) => Center(
    child: Padding(
      padding: const EdgeInsets.symmetric(vertical: 60),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Container(
          width: 72,
          height: 72,
          decoration: BoxDecoration(
              color: AppTheme.primary.withOpacity(0.08),
              shape: BoxShape.circle),
          child: Icon(icon,
              size: 36, color: AppTheme.primary.withOpacity(0.6)),
        ),
        const SizedBox(height: 14),
        Text(t,
            style: const TextStyle(
                color: AppTheme.textPrimary,
                fontSize: 16,
                fontWeight: FontWeight.w700)),
        const SizedBox(height: 6),
        Text(d,
            style: const TextStyle(
                color: AppTheme.textMuted, fontSize: 13),
            textAlign: TextAlign.center),
      ]),
    ),
  );
}

// ═══════════════════════════════════════════════════════
//  ERROR VIEW
// ═══════════════════════════════════════════════════════
class _ErrView extends StatelessWidget {
  final RecoProvider p;
  const _ErrView(this.p);

  @override
  Widget build(BuildContext ctx) => Center(
    child: Padding(
      padding: const EdgeInsets.all(30),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Container(
          width: 72,
          height: 72,
          decoration: BoxDecoration(
              color: AppTheme.textMuted.withOpacity(0.08),
              shape: BoxShape.circle),
          child: const Icon(Icons.cloud_off_rounded,
              size: 36, color: AppTheme.textMuted),
        ),
        const SizedBox(height: 16),
        Text(p.errorMessage,
            style: const TextStyle(
                color: AppTheme.textSecondary,
                fontSize: 13,
                height: 1.5),
            textAlign: TextAlign.center),
        const SizedBox(height: 20),
        ElevatedButton.icon(
          onPressed: p.reload,
          icon: const Icon(Icons.refresh_rounded, size: 18),
          label: const Text('Réessayer'),
          style: ElevatedButton.styleFrom(
              backgroundColor: AppTheme.primary,
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(
                  horizontal: 24, vertical: 12),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14))),
        ),
      ]),
    ),
  );
}