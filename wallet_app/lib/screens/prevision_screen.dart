// ════════════════════════════════════════════════════════════════
//  SmartWallet — PrevisionScreen (refonte v2)
//  Layout HORIZONTAL : PageView de 5 cartes ML swipeables
//   • Page 1 — Solde prévisionnel + alerte (Module 5)
//   • Page 2 — Prochaines factures (Module 1)
//   • Page 3 — Recharge télécom (Module 2)
//   • Page 4 — Budget par catégorie (Module 3)
//   • Page 5 — Prochaine transaction prédite (Module 4)
//
//  Garde toute la logique AiProvider existante.
// ════════════════════════════════════════════════════════════════
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../theme/app_theme.dart';
import '../models/ai_models.dart';
import '../services/ai_provider.dart';
import '../widgets/sw_widgets.dart';
import 'new_transaction_screen.dart';
import 'scan_screen.dart';

class PrevisionScreen extends StatelessWidget {
  final String clientId;
  const PrevisionScreen({super.key, required this.clientId});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => AiProvider()..init(clientId),
      child: const _PrevisionBody(),
    );
  }
}

class _PrevisionBody extends StatefulWidget {
  const _PrevisionBody();
  @override
  State<_PrevisionBody> createState() => _PrevisionBodyState();
}

class _PrevisionBodyState extends State<_PrevisionBody> {
  final PageController _pageCtrl = PageController(viewportFraction: 0.92);
  int _currentPage = 0;

  @override
  void dispose() {
    _pageCtrl.dispose();
    super.dispose();
  }

  Future<void> _payerFacture(BuildContext context, AiProvider ai, Facture facture) async {
    final typeMapping = {
      'TOPNET': 'PAIEMENT TOPNET',
      'BEE': 'PAIEMENT BEE',
      'SONEDE': 'PAIEMENT SONEDE',
      'STEG': 'PAIEMENT STEG',
      'TT': 'PAIEMENT TT',
      'OOREDOO': 'PAIEMENT ORREDOO',
    };
    final prefillType = typeMapping[facture.label] ?? facture.fournisseur;

    final result = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => NewTransactionScreen(
          clientId: ai.data!.clientId,
          prefillAmount: facture.montantPrevu,
          prefillCategory: 'Factures & Services',
          prefillType: prefillType,
        ),
      ),
    );
    if (result == true) await ai.load();
  }

  /// Paiement d'une facture prédictive SONEDE/STEG VIA OCR : on ouvre le scan,
  /// qui fait l'analyse complète (vrai montant) puis enregistre le paiement
  /// exactement comme un paiement de facture prédictive (via NewTransactionScreen).
  Future<void> _payerFactureOcr(BuildContext context, AiProvider ai, Facture facture) async {
    final result = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ScanScreen(
          clientId: ai.data!.clientId,
        ),
      ),
    );
    if (result == true) await ai.load();
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<AiProvider>(
      builder: (_, ai, __) {
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
            title: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Prévisions',
                    style: TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w700,
                        color: AppTheme.textPrimary)),
                Padding(
                  padding: const EdgeInsets.only(top: 2),
                  child: Text(
                      ai.loading
                          ? 'Analyse en cours...'
                          : 'Swipe pour explorer ${_pageCount(ai)} prévisions',
                      style: const TextStyle(
                          fontSize: 11, color: AppTheme.textMuted)),
                ),
              ],
            ),
            actions: [
              IconButton(
                onPressed: ai.load,
                icon: ai.loading
                    ? const SizedBox(
                    width: 18, height: 18,
                    child: CircularProgressIndicator(
                        color: AppTheme.primary, strokeWidth: 2))
                    : const Icon(Icons.refresh_rounded,
                    color: AppTheme.textSecondary, size: 22),
              ),
            ],
          ),
          body: ai.loading && ai.data == null
              ? const SwLoader(label: 'Calcul des prévisions...')
              : ai.error != null
              ? _ErrorView(error: ai.error!, onRetry: ai.load)
              : _Content(
            ai: ai,
            pageCtrl: _pageCtrl,
            currentPage: _currentPage,
            onPageChanged: (i) => setState(() => _currentPage = i),
            onPayFacture: (f) => _payerFacture(context, ai, f),
            onPayFactureOcr: (f) => _payerFactureOcr(context, ai, f),
          ),
        );
      },
    );
  }

  int _pageCount(AiProvider ai) {
    int n = 1; // solde toujours présent
    if (ai.m1 != null && (ai.m1!.factures.isNotEmpty)) n++;
    if (ai.m2 != null && ai.m2!.disponible) n++;
    if (ai.m3 != null && ai.m3!.parCategorie.isNotEmpty) n++;
    if (ai.m4 != null && ai.m4!.disponible) n++;
    if (_buildInsights(ai).isNotEmpty) n++;
    return n;
  }
}

// ════════════════════════════════════════════════════════════════
//  Contenu — PageView horizontal des modules
// ════════════════════════════════════════════════════════════════
class _Content extends StatelessWidget {
  final AiProvider ai;
  final PageController pageCtrl;
  final int currentPage;
  final ValueChanged<int> onPageChanged;
  final void Function(Facture) onPayFacture;
  final void Function(Facture) onPayFactureOcr;

  const _Content({
    required this.ai,
    required this.pageCtrl,
    required this.currentPage,
    required this.onPageChanged,
    required this.onPayFacture,
    required this.onPayFactureOcr,
  });

  @override
  Widget build(BuildContext context) {
    // Construit la liste de pages selon les modules disponibles
    final pages = <Widget>[];
    final labels = <String>[];

    // Page 1 — Solde prévisionnel (toujours présent)
    pages.add(_SoldePage(ai: ai));
    labels.add('Solde');

    if (ai.m1 != null && ai.m1!.factures.isNotEmpty) {
      pages.add(_FacturesPage(m1: ai.m1!, onPay: onPayFacture, onPayOcr: onPayFactureOcr));
      labels.add('Factures');
    }
    if (ai.m2 != null && ai.m2!.disponible) {
      pages.add(_RechargePage(m2: ai.m2!));
      labels.add('Recharge');
    }
    if (ai.m3 != null && ai.m3!.parCategorie.isNotEmpty) {
      pages.add(_BudgetPage(m3: ai.m3!));
      labels.add('Budget');
    }
    if (ai.m4 != null && ai.m4!.disponible) {
      pages.add(_ProchaineTxPage(m4: ai.m4!));
      labels.add('Activité');
    }

    // Page Conseils — toujours affichée si données disponibles
    final insights = _buildInsights(ai);
    if (insights.isNotEmpty) {
      pages.add(_ConseilsPage(insights: insights));
      labels.add('Conseils');
    }

    return Column(
      children: [
        // ── Indicateur tabs horizontal ──
        SizedBox(
          height: 44,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            itemCount: labels.length,
            itemBuilder: (_, i) {
              final selected = i == currentPage;
              return GestureDetector(
                onTap: () => pageCtrl.animateToPage(i,
                    duration: const Duration(milliseconds: 280),
                    curve: Curves.easeOut),
                child: Container(
                  margin: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                  decoration: BoxDecoration(
                    color: selected
                        ? AppTheme.primary
                        : AppTheme.surface,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(
                        color: selected
                            ? AppTheme.primary
                            : AppTheme.border),
                  ),
                  child: Center(
                    child: Text(labels[i],
                        style: TextStyle(
                            color: selected
                                ? AppTheme.background
                                : AppTheme.textSecondary,
                            fontSize: 12,
                            fontWeight: FontWeight.w700)),
                  ),
                ),
              );
            },
          ),
        ),

        // ── PageView horizontal ──
        Expanded(
          child: PageView.builder(
            controller: pageCtrl,
            onPageChanged: onPageChanged,
            itemCount: pages.length,
            itemBuilder: (_, i) => Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
              child: pages[i],
            ),
          ),
        ),

        // ── Indicateur de points ──
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 12),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: List.generate(pages.length, (i) {
              final selected = i == currentPage;
              return AnimatedContainer(
                duration: const Duration(milliseconds: 250),
                margin: const EdgeInsets.symmetric(horizontal: 3),
                width: selected ? 22 : 7,
                height: 7,
                decoration: BoxDecoration(
                  color: selected
                      ? AppTheme.primary
                      : AppTheme.border,
                  borderRadius: BorderRadius.circular(4),
                ),
              );
            }),
          ),
        ),
      ],
    );
  }
}

// ════════════════════════════════════════════════════════════════
//  PAGE 1 — Solde prévisionnel + alerte
// ════════════════════════════════════════════════════════════════
class _SoldePage extends StatelessWidget {
  final AiProvider ai;
  const _SoldePage({required this.ai});

  @override
  Widget build(BuildContext context) {
    final m5 = ai.m5;
    final isCritique = ai.alerteCritique;
    final isAttention = ai.alerteAttention;
    final color = isCritique
        ? AppTheme.debit
        : isAttention
        ? AppTheme.accentOrange
        : AppTheme.credit;
    final emoji = isCritique ? '🚨' : isAttention ? '⚠️' : '✓';

    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      children: [
        // ── Hero solde ──
        Container(
          padding: const EdgeInsets.all(22),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [color, color.withOpacity(0.7)],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Text(emoji, style: const TextStyle(fontSize: 28)),
                  const SizedBox(width: 8),
                  Text(ai.niveauAlerte,
                      style: const TextStyle(
                          color: Colors.white,
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 0.5)),
                ],
              ),
              const SizedBox(height: 18),
              const Text('Solde actuel',
                  style: TextStyle(
                      color: Colors.white70,
                      fontSize: 11,
                      fontWeight: FontWeight.w600)),
              Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(ai.solde.toStringAsFixed(2),
                      style: const TextStyle(
                          color: Colors.white,
                          fontSize: 34,
                          fontWeight: FontWeight.w900,
                          height: 1.1)),
                  const Padding(
                    padding: EdgeInsets.only(left: 5, bottom: 4),
                    child: Text('TND',
                        style: TextStyle(
                            color: Colors.white70, fontSize: 14)),
                  ),
                ],
              ),
              if (m5 != null) ...[
                const SizedBox(height: 18),
                Container(height: 1, color: Colors.white24),
                const SizedBox(height: 14),
                _SoldeRow('Total prévu',
                    '-${m5.totalPrevuTnd.toStringAsFixed(0)} TND'),
                const SizedBox(height: 6),
                _SoldeRow('Solde après',
                    '${m5.soldeApresTnd.toStringAsFixed(0)} TND',
                    bold: true),
                if (m5.totalUrgentTnd > 0) ...[
                  const SizedBox(height: 6),
                  _SoldeRow(
                      'Urgent (≤7 jours)',
                      '${m5.totalUrgentTnd.toStringAsFixed(0)} TND'),
                ],
              ],
            ],
          ),
        ),

        const SizedBox(height: 14),

        // ── Stats compact ──
        Row(
          children: [
            Expanded(
              child: _StatBox(
                icon: Icons.receipt_long_rounded,
                label: 'Factures à venir',
                value: '${ai.totalFactures.toStringAsFixed(0)} TND',
                color: AppTheme.accentOrange,
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: _StatBox(
                icon: Icons.shopping_cart_rounded,
                label: 'Budget variable',
                value: '${ai.budgetVariable.toStringAsFixed(0)} TND',
                color: AppTheme.primary,
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),
        Row(
          children: [
            Expanded(
              child: _StatBox(
                icon: Icons.priority_high_rounded,
                label: 'Items urgents',
                value: '${ai.nbUrgents}',
                color: AppTheme.debit,
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: _StatBox(
                icon: Icons.account_circle_rounded,
                label: 'Segment',
                value: ai.segment,
                color: AppTheme.accent,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _SoldeRow extends StatelessWidget {
  final String label;
  final String value;
  final bool bold;
  const _SoldeRow(this.label, this.value, {this.bold = false});
  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Text(label,
            style: const TextStyle(color: Colors.white70, fontSize: 12)),
        const Spacer(),
        Text(value,
            style: TextStyle(
                color: Colors.white,
                fontSize: bold ? 15 : 13,
                fontWeight: bold ? FontWeight.w800 : FontWeight.w600)),
      ],
    );
  }
}

class _StatBox extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final Color color;
  const _StatBox({
    required this.icon,
    required this.label,
    required this.value,
    required this.color,
  });
  @override
  Widget build(BuildContext context) {
    return SwCard(
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: color, size: 20),
          const SizedBox(height: 8),
          Text(value,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                  color: color,
                  fontSize: 16,
                  fontWeight: FontWeight.w800)),
          const SizedBox(height: 2),
          Text(label,
              style: const TextStyle(
                  color: AppTheme.textMuted, fontSize: 10.5)),
        ],
      ),
    );
  }
}

// ════════════════════════════════════════════════════════════════
//  PAGE 2 — Factures à venir (Module 1) + Calendrier
// ════════════════════════════════════════════════════════════════
class _FacturesPage extends StatefulWidget {
  final Module1Factures m1;
  final void Function(Facture) onPay;
  final void Function(Facture) onPayOcr;
  const _FacturesPage({required this.m1, required this.onPay, required this.onPayOcr});
  @override
  State<_FacturesPage> createState() => _FacturesPageState();
}

class _FacturesPageState extends State<_FacturesPage> {
  DateTime _mois = DateTime.now();

  Map<int, _CalEvent> _events() {
    final result = <int, _CalEvent>{};
    for (final f in widget.m1.factures) {
      if (f.datePrevue.isEmpty) continue;
      try {
        final d = DateTime.parse(f.datePrevue);
        if (d.year == _mois.year && d.month == _mois.month) {
          result[d.day] = _CalEvent(
            label: f.nomCourt,
            montant: f.montantPrevu,
            isUrgent: f.estUrgent,
          );
        }
      } catch (_) {}
    }
    return result;
  }

  @override
  Widget build(BuildContext context) {
    final factures = widget.m1.prochaines;
    if (factures.isEmpty) {
      return const SwEmpty(
        icon: Icons.receipt_long_rounded,
        title: 'Pas de factures à venir',
        description: 'Tes factures prévues apparaîtront ici.',
      );
    }

    final events  = _events();
    final premier = DateTime(_mois.year, _mois.month, 1);
    final nbJours = DateUtils.getDaysInMonth(_mois.year, _mois.month);
    final debut   = premier.weekday;
    final moisLbl = DateFormat('MMMM yyyy', 'fr').format(_mois);

    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      children: [
        // ── Hero total ──
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [
                AppTheme.accentOrange.withOpacity(0.2),
                AppTheme.accentOrange.withOpacity(0.05),
              ],
            ),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: AppTheme.accentOrange.withOpacity(0.3)),
          ),
          child: Row(
            children: [
              Container(
                width: 48, height: 48,
                decoration: BoxDecoration(
                  color: AppTheme.accentOrange.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: const Icon(Icons.receipt_long_rounded,
                    color: AppTheme.accentOrange, size: 24),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('${factures.length} facture${factures.length > 1 ? 's' : ''} à venir',
                        style: const TextStyle(
                            color: AppTheme.textPrimary,
                            fontSize: 13,
                            fontWeight: FontWeight.w700)),
                    const SizedBox(height: 2),
                    Text('Total : ${widget.m1.totalAVenirTnd.toStringAsFixed(0)} TND',
                        style: const TextStyle(
                            color: AppTheme.accentOrange,
                            fontSize: 18,
                            fontWeight: FontWeight.w900)),
                  ],
                ),
              ),
            ],
          ),
        ),

        const SizedBox(height: 14),

        // ── Calendrier ──
        SwCard(
          padding: const EdgeInsets.all(14),
          child: Column(children: [
            // Navigation mois
            Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              IconButton(
                icon: const Icon(Icons.chevron_left,
                    color: AppTheme.primary, size: 22),
                onPressed: () => setState(() =>
                _mois = DateTime(_mois.year, _mois.month - 1)),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(),
              ),
              Text(
                moisLbl[0].toUpperCase() + moisLbl.substring(1),
                style: const TextStyle(
                    color: AppTheme.textPrimary,
                    fontSize: 13.5,
                    fontWeight: FontWeight.w600),
              ),
              IconButton(
                icon: const Icon(Icons.chevron_right,
                    color: AppTheme.primary, size: 22),
                onPressed: () => setState(() =>
                _mois = DateTime(_mois.year, _mois.month + 1)),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(),
              ),
            ]),
            const SizedBox(height: 8),

            // En-têtes jours
            Row(
              children: ['L', 'M', 'M', 'J', 'V', 'S', 'D'].map((d) =>
                  Expanded(
                    child: Text(d,
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                            color: AppTheme.textMuted,
                            fontSize: 10.5,
                            fontWeight: FontWeight.w600)),
                  )).toList(),
            ),
            const SizedBox(height: 6),

            // Grille des jours
            GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 7,
                mainAxisSpacing: 4,
                crossAxisSpacing: 4,
                childAspectRatio: 0.9,
              ),
              itemCount: (debut - 1) + nbJours,
              itemBuilder: (_, i) {
                if (i < debut - 1) return const SizedBox();
                final day = i - (debut - 2);
                final today = DateTime.now();
                final isToday = today.day == day &&
                    today.month == _mois.month &&
                    today.year == _mois.year;
                return _CalendarDay(
                    day: day, isToday: isToday, event: events[day]);
              },
            ),
            const SizedBox(height: 10),

            // Légende
            Row(mainAxisAlignment: MainAxisAlignment.center, children: [
              _CalLeg(color: AppTheme.debit,        label: 'Urgente'),
              const SizedBox(width: 14),
              _CalLeg(color: AppTheme.accentOrange,  label: 'Prévue'),
              const SizedBox(width: 14),
              _CalLeg(color: AppTheme.primary,       label: "Aujourd'hui"),
            ]),
          ]),
        ),

        const SizedBox(height: 14),

        // ── Liste factures ──
        ...factures.map((f) => Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: _FactureCard(facture: f, onPay: () => widget.onPay(f), onPayOcr: () => widget.onPayOcr(f)),
        )),
      ],
    );
  }
}

class _FactureCard extends StatelessWidget {
  final Facture facture;
  final VoidCallback onPay;
  final VoidCallback onPayOcr;
  const _FactureCard({required this.facture, required this.onPay, required this.onPayOcr});

  @override
  Widget build(BuildContext context) {
    final isUrgent = facture.joursRestants <= 3 && facture.joursRestants >= 0;
    final isPasse = facture.joursRestants < 0;
    final color = isPasse
        ? AppTheme.debit
        : isUrgent
        ? AppTheme.accentOrange
        : AppTheme.primary;
    return SwCard(
      borderColor: color.withOpacity(0.3),
      child: Row(
        children: [
          Container(
            width: 44, height: 44,
            decoration: BoxDecoration(
                color: color.withOpacity(0.12),
                borderRadius: BorderRadius.circular(12)),
            child: Icon(Icons.bolt_rounded, color: color, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(facture.label,
                    style: const TextStyle(
                        color: AppTheme.textPrimary,
                        fontSize: 13.5,
                        fontWeight: FontWeight.w700)),
                const SizedBox(height: 2),
                Text(
                    isPasse
                        ? 'En retard de ${-facture.joursRestants} j'
                        : facture.joursRestants == 0
                        ? "Aujourd'hui"
                        : 'Dans ${facture.joursRestants} jours',
                    style: TextStyle(
                        color: color,
                        fontSize: 11,
                        fontWeight: FontWeight.w600)),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text('${facture.montantPrevu.toStringAsFixed(0)} TND',
                  style: const TextStyle(
                      color: AppTheme.textPrimary,
                      fontSize: 14,
                      fontWeight: FontWeight.w800)),
              const SizedBox(height: 4),
              GestureDetector(
                onTap: onPay,
                child: Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                      color: AppTheme.primary,
                      borderRadius: BorderRadius.circular(8)),
                  child: const Text('Payer',
                      style: TextStyle(
                          color: AppTheme.background,
                          fontSize: 10,
                          fontWeight: FontWeight.w800)),
                ),
              ),
              // ── SONEDE / STEG : option de paiement par scan OCR ──
              if (facture.label == 'SONEDE' || facture.label == 'STEG') ...[
                const SizedBox(height: 4),
                GestureDetector(
                  onTap: onPayOcr,
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                        border: Border.all(color: AppTheme.primary, width: 1.2),
                        borderRadius: BorderRadius.circular(8)),
                    child: const Row(mainAxisSize: MainAxisSize.min, children: [
                      Icon(Icons.document_scanner_rounded,
                          color: AppTheme.primary, size: 12),
                      SizedBox(width: 4),
                      Text('Payer par OCR',
                          style: TextStyle(
                              color: AppTheme.primary,
                              fontSize: 10,
                              fontWeight: FontWeight.w800)),
                    ]),
                  ),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }
}

// ════════════════════════════════════════════════════════════════
//  PAGE 3 — Recharge télécom (Module 2)
// ════════════════════════════════════════════════════════════════
class _RechargePage extends StatelessWidget {
  final Module2Recharge m2;
  const _RechargePage({required this.m2});

  @override
  Widget build(BuildContext context) {
    final urgent = m2.joursRestants <= 3 && m2.joursRestants >= 0;
    final color = urgent ? AppTheme.accentOrange : AppTheme.primary;

    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      children: [
        Container(
          padding: const EdgeInsets.all(22),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [color, color.withOpacity(0.7)],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(Icons.sim_card_rounded,
                      color: Colors.white, size: 24),
                  const SizedBox(width: 8),
                  Text(m2.operateur,
                      style: const TextStyle(
                          color: Colors.white,
                          fontSize: 14,
                          fontWeight: FontWeight.w800,
                          letterSpacing: 1)),
                ],
              ),
              const SizedBox(height: 22),
              const Text('Montant habituel',
                  style: TextStyle(color: Colors.white70, fontSize: 11)),
              Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(m2.montantHabituel.toStringAsFixed(0),
                      style: const TextStyle(
                          color: Colors.white,
                          fontSize: 34,
                          fontWeight: FontWeight.w900,
                          height: 1)),
                  const Padding(
                    padding: EdgeInsets.only(left: 5, bottom: 4),
                    child: Text('TND',
                        style:
                        TextStyle(color: Colors.white70, fontSize: 14)),
                  ),
                ],
              ),
              const SizedBox(height: 18),
              Container(height: 1, color: Colors.white24),
              const SizedBox(height: 14),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('Prochaine recharge',
                      style:
                      TextStyle(color: Colors.white70, fontSize: 12)),
                  Text(
                      m2.joursRestants <= 0
                          ? 'Aujourd\'hui'
                          : 'Dans ${m2.joursRestants} j',
                      style: const TextStyle(
                          color: Colors.white,
                          fontSize: 13,
                          fontWeight: FontWeight.w800)),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        SwCard(
          child: Row(
            children: [
              Icon(Icons.lightbulb_rounded, color: color, size: 22),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                    urgent
                        ? 'Ta recharge mensuelle arrive bientôt. Pense à recharger pour éviter une coupure.'
                        : 'Prochaine recharge dans ${m2.joursRestants} jours selon ton historique.',
                    style: const TextStyle(
                        color: AppTheme.textSecondary, fontSize: 12)),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

// ════════════════════════════════════════════════════════════════
//  PAGE 4 — Budget par catégorie (Module 3)
// ════════════════════════════════════════════════════════════════
class _BudgetPage extends StatelessWidget {
  final Module3Budget m3;
  const _BudgetPage({required this.m3});

  @override
  Widget build(BuildContext context) {
    final entries = m3.parCategorie.entries.toList()
      ..sort((a, b) => b.value.predit.compareTo(a.value.predit));

    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      children: [
        // ── Hero budget total ──
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [
                AppTheme.primary.withOpacity(0.2),
                AppTheme.primary.withOpacity(0.05),
              ],
            ),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: AppTheme.primary.withOpacity(0.3)),
          ),
          child: Row(
            children: [
              Container(
                width: 48, height: 48,
                decoration: BoxDecoration(
                  color: AppTheme.primary.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: const Icon(Icons.shopping_cart_rounded,
                    color: AppTheme.primary, size: 24),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Budget variable prévu',
                        style: TextStyle(
                            color: AppTheme.textPrimary,
                            fontSize: 13,
                            fontWeight: FontWeight.w700)),
                    const SizedBox(height: 2),
                    Text('${m3.budgetTotalTnd.toStringAsFixed(0)} TND',
                        style: const TextStyle(
                            color: AppTheme.primary,
                            fontSize: 22,
                            fontWeight: FontWeight.w900)),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        const SwSectionHeader(
            title: 'PAR CATÉGORIE', icon: Icons.donut_large_rounded),
        ...entries.map((e) => Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: _BudgetCategoryCard(name: e.key, detail: e.value, total: m3.budgetTotalTnd),
        )),
      ],
    );
  }
}

class _BudgetCategoryCard extends StatelessWidget {
  final String name;
  final DetailBudget detail;
  final double total;
  const _BudgetCategoryCard({
    required this.name,
    required this.detail,
    required this.total,
  });

  @override
  Widget build(BuildContext context) {
    final pct = total > 0 ? (detail.predit / total).clamp(0.0, 1.0) : 0.0;
    final confColor = detail.confiance == 'Élevée'
        ? AppTheme.credit
        : detail.confiance == 'Moyenne'
        ? AppTheme.accent
        : AppTheme.textMuted;
    return SwCard(
      padding: const EdgeInsets.all(13),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(name,
                    style: const TextStyle(
                        color: AppTheme.textPrimary,
                        fontSize: 13,
                        fontWeight: FontWeight.w700)),
              ),
              Text('${detail.predit.toStringAsFixed(0)} TND',
                  style: const TextStyle(
                      color: AppTheme.primary,
                      fontSize: 14,
                      fontWeight: FontWeight.w800)),
            ],
          ),
          const SizedBox(height: 8),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: pct,
              minHeight: 6,
              backgroundColor: AppTheme.primary.withOpacity(0.1),
              valueColor: const AlwaysStoppedAnimation(AppTheme.primary),
            ),
          ),
          const SizedBox(height: 6),
          Row(
            children: [
              Text('${detail.icBas.toStringAsFixed(0)} - ${detail.icHaut.toStringAsFixed(0)} TND',
                  style:
                  const TextStyle(color: AppTheme.textMuted, fontSize: 10)),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                    color: confColor.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(6)),
                child: Text(detail.confiance,
                    style: TextStyle(
                        color: confColor,
                        fontSize: 9,
                        fontWeight: FontWeight.w800)),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

// ════════════════════════════════════════════════════════════════
//  PAGE 5 — Prochaine transaction prédite (Module 4)
// ════════════════════════════════════════════════════════════════
class _ProchaineTxPage extends StatelessWidget {
  final Module4ProchaineTx m4;
  const _ProchaineTxPage({required this.m4});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      children: [
        Container(
          padding: const EdgeInsets.all(22),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              colors: [Color(0xFFA855F7), Color(0xFF7C3AED)],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Row(
                children: [
                  Icon(Icons.psychology_rounded,
                      color: Colors.white, size: 24),
                  SizedBox(width: 8),
                  Text('PRÉDICTION COMPORTEMENTALE',
                      style: TextStyle(
                          color: Colors.white,
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 0.8)),
                ],
              ),
              const SizedBox(height: 16),
              const Text('Catégorie dominante',
                  style: TextStyle(color: Colors.white70, fontSize: 11)),
              const SizedBox(height: 2),
              Text(m4.catDominante,
                  style: const TextStyle(
                      color: Colors.white,
                      fontSize: 26,
                      fontWeight: FontWeight.w900,
                      height: 1.1)),
              const SizedBox(height: 16),
              Row(
                children: [
                  Icon(Icons.schedule_rounded,
                      color: Colors.white70, size: 14),
                  const SizedBox(width: 4),
                  Text(
                      m4.joursDepuisTx >= m4.delaiEstimeJours
                          ? 'Imminente'
                          : 'Dans ${m4.delaiEstimeJours - m4.joursDepuisTx} j environ',
                      style: const TextStyle(
                          color: Colors.white,
                          fontSize: 12,
                          fontWeight: FontWeight.w600)),
                  const Spacer(),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                        color: Colors.white.withOpacity(0.22),
                        borderRadius: BorderRadius.circular(8)),
                    child: Text('Confiance : ${m4.confiance}',
                        style: const TextStyle(
                            color: Colors.white,
                            fontSize: 10,
                            fontWeight: FontWeight.w800)),
                  ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        if (m4.top3.isNotEmpty) ...[
          const SwSectionHeader(
              title: 'TOP 3 PROBABILITÉS',
              icon: Icons.leaderboard_rounded),
          ...m4.top3.map((p) => Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: _ProbaTile(probaTx: p),
          )),
        ],
      ],
    );
  }
}

class _ProbaTile extends StatelessWidget {
  final ProbaTx probaTx;
  const _ProbaTile({required this.probaTx});

  @override
  Widget build(BuildContext context) {
    final pct = probaTx.probabilite;
    final color = pct >= 0.5
        ? AppTheme.primary
        : pct >= 0.25
        ? AppTheme.accent
        : AppTheme.textMuted;
    return SwCard(
      padding: const EdgeInsets.all(13),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(probaTx.categorie,
                    style: const TextStyle(
                        color: AppTheme.textPrimary,
                        fontSize: 13,
                        fontWeight: FontWeight.w700)),
              ),
              Text('${(pct * 100).toStringAsFixed(0)}%',
                  style: TextStyle(
                      color: color,
                      fontSize: 14,
                      fontWeight: FontWeight.w800)),
            ],
          ),
          const SizedBox(height: 6),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: pct,
              minHeight: 5,
              backgroundColor: color.withOpacity(0.1),
              valueColor: AlwaysStoppedAnimation(color),
            ),
          ),
          const SizedBox(height: 4),
          Text('Dans ${probaTx.dansJours} jours',
              style: const TextStyle(color: AppTheme.textMuted, fontSize: 10)),
        ],
      ),
    );
  }
}

// ════════════════════════════════════════════════════════════════
//  WIDGETS CALENDRIER
// ════════════════════════════════════════════════════════════════
class _CalEvent {
  final String label;
  final double montant;
  final bool isUrgent;
  const _CalEvent({required this.label, required this.montant, required this.isUrgent});
}

class _CalendarDay extends StatelessWidget {
  final int day;
  final bool isToday;
  final _CalEvent? event;
  const _CalendarDay({required this.day, required this.isToday, this.event});

  @override
  Widget build(BuildContext context) {
    Color bg = Colors.transparent;
    Color tc = AppTheme.textMuted;
    Color? border;
    Color? dot;

    if (isToday) {
      bg = AppTheme.primary;
      tc = AppTheme.background;
    } else if (event != null) {
      if (event!.isUrgent) {
        bg = AppTheme.debit.withOpacity(0.18);
        tc = AppTheme.debit;
        border = AppTheme.debit;
        dot = AppTheme.debit;
      } else {
        bg = AppTheme.accentOrange.withOpacity(0.15);
        tc = AppTheme.accentOrange;
        border = AppTheme.accentOrange.withOpacity(0.6);
        dot = AppTheme.accentOrange;
      }
    }

    return Container(
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(7),
        border: border != null ? Border.all(color: border, width: 0.8) : null,
      ),
      child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
        Text('$day',
            style: TextStyle(
                color: tc,
                fontSize: 11,
                fontWeight: (isToday || event != null)
                    ? FontWeight.w700
                    : FontWeight.normal)),
        if (dot != null)
          Container(
              width: 4, height: 4,
              margin: const EdgeInsets.only(top: 1),
              decoration: BoxDecoration(color: dot, shape: BoxShape.circle)),
      ]),
    );
  }
}

class _CalLeg extends StatelessWidget {
  final Color color;
  final String label;
  const _CalLeg({required this.color, required this.label});
  @override
  Widget build(BuildContext context) => Row(mainAxisSize: MainAxisSize.min, children: [
    Container(width: 8, height: 8,
        decoration: BoxDecoration(
            color: color, borderRadius: BorderRadius.circular(2))),
    const SizedBox(width: 5),
    Text(label, style: const TextStyle(color: AppTheme.textMuted, fontSize: 9.5)),
  ]);
}

// ════════════════════════════════════════════════════════════════
//  PAGE CONSEILS PERSONNALISÉS
// ════════════════════════════════════════════════════════════════
class _ConseilsPage extends StatelessWidget {
  final List<_Insight> insights;
  const _ConseilsPage({required this.insights});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      children: [
        // ── Hero ──
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [
                AppTheme.accent.withOpacity(0.25),
                AppTheme.accent.withOpacity(0.05),
              ],
            ),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: AppTheme.accent.withOpacity(0.35)),
          ),
          child: Row(children: [
            Container(
              width: 48, height: 48,
              decoration: BoxDecoration(
                color: AppTheme.accent.withOpacity(0.2),
                borderRadius: BorderRadius.circular(14),
              ),
              child: const Icon(Icons.lightbulb_rounded,
                  color: AppTheme.accent, size: 24),
            ),
            const SizedBox(width: 14),
            const Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('Conseils pour vous',
                    style: TextStyle(
                        color: AppTheme.textPrimary,
                        fontSize: 13,
                        fontWeight: FontWeight.w700)),
                SizedBox(height: 2),
                Text('Basés sur vos habitudes',
                    style: TextStyle(
                        color: AppTheme.textMuted, fontSize: 11.5)),
              ]),
            ),
          ]),
        ),

        const SizedBox(height: 14),

        ...insights.map((ins) => Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: SwCard(
            borderColor: ins.couleur.withOpacity(0.3),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Container(
                width: 44, height: 44,
                decoration: BoxDecoration(
                  color: ins.couleur.withOpacity(0.14),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Center(
                  child: Text(ins.emoji,
                      style: const TextStyle(fontSize: 20)),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(ins.titre,
                    style: const TextStyle(
                        color: AppTheme.textPrimary,
                        fontSize: 13,
                        fontWeight: FontWeight.w700)),
                const SizedBox(height: 4),
                Text(ins.desc,
                    style: const TextStyle(
                        color: AppTheme.textSecondary,
                        fontSize: 12,
                        height: 1.5)),
              ])),
            ]),
          ),
        )),
      ],
    );
  }
}

// ── Modèle conseil ──
class _Insight {
  final String emoji, titre, desc;
  final Color couleur;
  const _Insight({
    required this.emoji,
    required this.titre,
    required this.desc,
    required this.couleur,
  });
}

// ── Générateur de conseils (top-level) ──
List<_Insight> _buildInsights(AiProvider ai) {
  final list  = <_Insight>[];
  final m5    = ai.m5;
  final m2    = ai.m2;
  final m3    = ai.m3;
  final m4    = ai.m4;
  final solde = ai.solde;

  // Solde insuffisant
  if (m5 != null && !m5.estOk) {
    list.add(_Insight(
      emoji: '💳',
      titre: 'Rechargez votre wallet',
      desc: 'Il vous manque ${(m5.totalPrevuTnd - solde).toStringAsFixed(0)} TND '
          'pour couvrir toutes vos dépenses du mois.',
      couleur: AppTheme.debit,
    ));
  }

  // Recharge en retard
  if (m2 != null && m2.disponible && m2.joursRestants < 0) {
    list.add(_Insight(
      emoji: '📱',
      titre: 'Recharge ${m2.operateur} en retard',
      desc: 'Votre rechargement habituel de ${m2.montantHabituel.toStringAsFixed(0)} TND '
          'était prévu il y a ${-m2.joursRestants} jour(s).',
      couleur: AppTheme.accentOrange,
    ));
  }

  // Transaction prochaine imminente
  if (m4 != null && m4.disponible && m4.joursDepuisTx >= m4.delaiEstimeJours) {
    final top = m4.top3.isNotEmpty ? m4.top3.first : null;
    if (top != null) {
      list.add(_Insight(
        emoji: '⚡',
        titre: 'Dépense probable bientôt',
        desc: '${top.categorie} — vous effectuez habituellement '
            'ce type de dépense autour de cette période.',
        couleur: AppTheme.primary,
      ));
    }
  }

  // Achats élevés
  final shop = m3?.parCategorie['Shopping & Paiements'];
  if (shop != null && shop.predit > 150) {
    list.add(_Insight(
      emoji: '🛍️',
      titre: 'Achats élevés ce mois',
      desc: 'Vous dépensez habituellement autour de '
          '${shop.predit.toStringAsFixed(0)} TND en achats ce mois-ci.',
      couleur: AppTheme.accent,
    ));
  }

  // Budget maîtrisé
  if (m5 != null && m5.estOk && m5.soldeApresTnd > 50) {
    list.add(_Insight(
      emoji: '✅',
      titre: 'Budget bien maîtrisé',
      desc: 'Après toutes vos dépenses prévues, il vous restera environ '
          '${m5.soldeApresTnd.toStringAsFixed(0)} TND. Continuez ainsi !',
      couleur: AppTheme.credit,
    ));
  }

  return list;
}

// ════════════════════════════════════════════════════════════════
//  Error view
// ════════════════════════════════════════════════════════════════
class _ErrorView extends StatelessWidget {
  final String error;
  final Future<void> Function() onRetry;
  const _ErrorView({required this.error, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return SwEmpty(
      icon: Icons.cloud_off_rounded,
      title: 'Connexion impossible',
      description: error,
      action: SwPrimaryButton(
          label: 'Réessayer',
          icon: Icons.refresh_rounded,
          onPressed: onRetry,
          fullWidth: false),
    );
  }
}