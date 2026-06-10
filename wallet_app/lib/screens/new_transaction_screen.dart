import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:smartwallet_dashboard/screens/scan_screen.dart';
import 'package:smartwallet_dashboard/services/ai_provider.dart';
import '../services/api_service.dart';
import '../theme/app_theme.dart';
import '../utils/session_guard.dart';
import '../widgets/sw_widgets.dart';

/// Écran Wallet — Nouvelle Transaction
/// =====================================
/// Catégories système complètement masquées côté Flutter :
///   - Frais & Commissions
///   - Annulation & Correction
///   - Argent Recu
///   - Depot & Retrait (géré uniquement en agence)
///
/// Le backend filtre déjà ces catégories dans /transaction-types,
/// mais on ajoute un second filtre ici pour garantir qu'elles
/// n'apparaissent jamais à l'écran même en cas de bug serveur.
class NewTransactionScreen extends StatefulWidget {
  final String clientId;
  final double? prefillAmount;      // 🌟 AJOUTÉ
  final String? prefillCategory;    // 🌟 AJOUTÉ
  final String? prefillType;        // 🌟 AJOUTÉ
  final double soldeActuel;

  const NewTransactionScreen({
    super.key,
    required this.clientId,
    this.prefillAmount,
    this.prefillCategory,
    this.prefillType,
    this.soldeActuel = 0.0,
  });

  @override
  State<NewTransactionScreen> createState() => _NewTransactionScreenState();
}

class _NewTransactionScreenState extends State<NewTransactionScreen> {
  // ── Catégories à masquer absolument côté client ────────────────
  static const Set<String> _hiddenCategories = {
    'Frais & Commissions',
    'Annulation & Correction',
    'Argent Recu',
    'Depot & Retrait',
  };

  int _step = 0;
  bool _loading    = true;
  bool _submitting = false;
  String? _error;

  List<Map<String, dynamic>> _allTypes = [];
  List<String> _categories = [];

  String? _selectedCategory;
  Map<String, dynamic>? _selectedType;
  final _amountCtrl   = TextEditingController();
  final _receiverCtrl = TextEditingController();
  String _searchQuery = '';

  @override
  void initState() {
    super.initState();
    // 🌟 Pré-remplir le montant
    _amountCtrl.text = widget.prefillAmount?.toStringAsFixed(3) ?? '';
    _loadTypes();
  }

  @override
  void dispose() {
    _amountCtrl.dispose();
    _receiverCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadTypes() async {
    try {
      final types = await ApiService.getTransactionTypes(widget.clientId);

      // Double filtre : exclure les catégories système
      final filtered = types.where((t) {
        final cat = t['category'] as String? ?? '';
        return cat.isNotEmpty && !_hiddenCategories.contains(cat);
      }).toList();

      final cats = filtered
          .map((t) => t['category'] as String? ?? '')
          .where((c) => c.isNotEmpty)
          .toSet()
          .toList()
        ..sort();

      setState(() {
        _allTypes   = filtered;
        _categories = cats;
        _loading    = false;
      });

      // 🌟 PRÉ-REMPLISSAGE INTELLIGENT
      if (widget.prefillCategory != null && widget.prefillType != null) {
        _selectedCategory = widget.prefillCategory;

        // Trouver le type exact
        _selectedType = _allTypes.firstWhere(
              (t) => t['category'] == widget.prefillCategory &&
              (t['title'] == widget.prefillType ||
                  t['title']?.toString().contains(widget.prefillType!) == true),
          orElse: () => _allTypes.firstWhere(
                (t) => t['category'] == widget.prefillCategory,
            orElse: () => _allTypes.first,
          ),
        );

        // 🌟 ALLER DIRECTEMENT À L'ÉTAPE 3 (CONFIRMATION)
        _step = 3;
      }
    } catch (e) {
      if (!mounted) return;
      if (await SessionGuard.handle(context, e)) return;
      setState(() { _error = e.toString(); _loading = false; });
    }
  }

  List<Map<String, dynamic>> get _filteredTypes {
    if (_selectedCategory == null) return [];
    var types = _allTypes.where((t) => t['category'] == _selectedCategory);
    if (_searchQuery.isNotEmpty) {
      final q = _searchQuery.toLowerCase();
      types = types.where((t) =>
      (t['title'] ?? '').toLowerCase().contains(q) ||
          (t['subCategory'] ?? '').toLowerCase().contains(q));
    }
    return types.toList();
  }

  bool get _isCredit => _selectedType?['type'] == 'C';

  List<double>? get _quickAmounts {
    final cat   = _selectedCategory ?? '';
    final title = (_selectedType?['title'] ?? '').toLowerCase();
    if (cat == 'Recharge Telephonique') return [5, 10, 15, 20];
    if (title.contains('topnet') || title.contains('bee') ||
        title.contains('orange') || title.contains('ooredoo') ||
        title.contains(' tt'))   return [19.9, 29.9, 39.9, 49.9, 59.9];
    if (cat == 'Restaurants & Livraison') return [15, 25, 35, 50];
    return null;
  }

  void _selectCategory(String cat) => setState(() {
    _selectedCategory = cat;
    _selectedType     = null;
    _searchQuery      = '';
    _step             = 1;
  });

  void _selectType(Map<String, dynamic> type) =>
      setState(() { _selectedType = type; _step = 2; _error = null; });

  void _goToConfirm() {
    final amountText = _amountCtrl.text.trim();
    if (amountText.isEmpty) {
      setState(() => _error = 'Veuillez saisir un montant');
      return;
    }
    final amount = double.tryParse(amountText);
    if (amount == null || amount <= 0) {
      setState(() => _error = 'Le montant doit être supérieur à 0');
      return;
    }
    if (amount > 5000) {
      setState(() => _error = 'Le montant maximum est 5 000 TND');
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() { _error = null; _step = 3; });
  }

  void _goBack() {
    FocusScope.of(context).unfocus();
    setState(() { _error = null; if (_step > 0) _step--; });
  }

  Future<void> _submit() async {
    setState(() { _submitting = true; _error = null; });
    try {
      final result = await ApiService.createTransaction(
        widget.clientId,
        amount: double.parse(_amountCtrl.text.trim()),
        transactionTypeId: _selectedType!['id'] as String,
        receiverId: _receiverCtrl.text.trim().isEmpty
            ? null : _receiverCtrl.text.trim(),
      );
      if (mounted) _showSuccessSheet(result);
    } catch (e) {
      if (!mounted) return;
      if (await SessionGuard.handle(context, e)) return;
      String msg = e.toString().replaceFirst('Exception: ', '');
      if (msg.contains('SocketException') || msg.contains('Connection refused')) {
        msg = 'Impossible de contacter le serveur.';
      } else if (msg.contains('TimeoutException')) {
        msg = 'Le serveur ne répond pas. Réessayez.';
      }
      setState(() { _error = msg; _submitting = false; });
    }
  }

  void _showSuccessSheet(Map<String, dynamic> result) {
    final isCredit = result['credit'] == true;
    showModalBottomSheet(
      context: context,
      isDismissible: false,
      enableDrag: false,
      backgroundColor: AppTheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (_) => Padding(
        padding: const EdgeInsets.all(32),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Container(
            width: 72, height: 72,
            decoration: BoxDecoration(
              color: AppTheme.credit.withOpacity(0.15),
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.check_rounded,
                color: AppTheme.credit, size: 40),
          ),
          const SizedBox(height: 20),
          const Text('Transaction réussie !',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800,
                  color: AppTheme.textPrimary)),
          const SizedBox(height: 10),
          Text(
            '${isCredit ? "+" : "-"} ${_amountCtrl.text.trim()} TND',
            style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800,
                color: isCredit ? AppTheme.credit : AppTheme.debit),
          ),
          const SizedBox(height: 6),
          Text(result['typeTitle'] ?? '',
              style: const TextStyle(fontSize: 14,
                  color: AppTheme.textSecondary)),
          Text(_selectedCategory ?? '',
              style: const TextStyle(fontSize: 12, color: AppTheme.textMuted)),
          const SizedBox(height: 28),
          SizedBox(
            width: double.infinity, height: 50,
            child: ElevatedButton(
              onPressed: () {
                Navigator.of(context).pop();
                Navigator.of(context).pop(true);
              },
              style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.primary,
                foregroundColor: AppTheme.background,
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14)),
                elevation: 0,
              ),
              child: const Text('Retour au dashboard',
                  style: TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
            ),
          ),
          const SizedBox(height: 12),
          TextButton(
            onPressed: () {
              Navigator.of(context).pop();
              setState(() {
                _step = 0; _selectedCategory = null;
                _selectedType = null; _amountCtrl.clear();
                _receiverCtrl.clear(); _submitting = false; _error = null;
              });
            },
            child: const Text('Nouvelle transaction',
                style: TextStyle(color: AppTheme.primary,
                    fontWeight: FontWeight.w600)),
          ),
        ]),
      ),
    );
  }

  // ════════════════════════════════════════════════════════════════
  //  BUILD
  // ════════════════════════════════════════════════════════════════
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.background,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new_rounded,
              size: 18, color: AppTheme.textPrimary),
          onPressed: _step == 0
              ? () => Navigator.of(context).pop()
              : _goBack,
        ),
        title: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(_appBarTitle,
                style: const TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w700,
                    color: AppTheme.textPrimary)),
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Text('Étape ${_step + 1} sur 4',
                  style: const TextStyle(
                      fontSize: 11, color: AppTheme.textMuted)),
            ),
          ],
        ),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(4),
          child: LinearProgressIndicator(
            value: (_step + 1) / 4,
            minHeight: 3,
            backgroundColor: AppTheme.surface,
            valueColor: const AlwaysStoppedAnimation(AppTheme.primary),
          ),
        ),
      ),
      body: _loading
          ? const SwLoader(label: 'Chargement...')
          : SafeArea(child: _buildStep()),
    );
  }

  String get _appBarTitle => switch (_step) {
    0 => 'Nouvelle transaction',
    1 => _selectedCategory ?? 'Type',
    2 => 'Montant',
    3 => 'Confirmation',
    _ => '',
  };

  Widget _buildStep() => switch (_step) {
    0 => _buildCategoryGrid(),
    1 => _buildTypeList(),
    2 => _buildAmountInput(),
    3 => _buildConfirmation(),
    _ => const SizedBox.shrink(),
  };

  // ════════════════════════════════════════════════════════════════
  //  ÉTAPE 0 — Grille des catégories
  // ════════════════════════════════════════════════════════════════
  // ════════════════════════════════════════════════════════════════
//  ÉTAPE 0 — Grille des catégories
// ════════════════════════════════════════════════════════════════
  Widget _buildCategoryGrid() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Que souhaitez-vous faire ?',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700,
                  color: AppTheme.textPrimary)),
          const SizedBox(height: 6),
          const Text('Choisissez une catégorie',
              style: TextStyle(fontSize: 13,
                  color: AppTheme.textSecondary)),
          const SizedBox(height: 20),

          // ════════════════════════════════════════════════════════════════
          // 🎯 BOUTON SCANNER UNE FACTURE - À PLACER ICI
          // ════════════════════════════════════════════════════════════════
          GestureDetector(
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => ScanScreen(
                  clientId: widget.clientId,
                  soldeActuel: widget.soldeActuel, // À récupérer depuis un provider si disponible
                ),
              ),
            ),
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              margin: const EdgeInsets.only(bottom: 16),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                    colors: [Color(0xFF00E5A0), Color(0xFF00BCD4)]),
                borderRadius: BorderRadius.circular(16),
              ),
              child: Row(children: [
                const Icon(Icons.document_scanner_rounded,
                    color: Colors.black87, size: 28),
                const SizedBox(width: 14),
                const Expanded(child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('📸 Scanner une facture',
                        style: TextStyle(color: Colors.black87,
                            fontSize: 15, fontWeight: FontWeight.w800)),
                    Text('Détection automatique en 2 secondes',
                        style: TextStyle(color: Colors.black54, fontSize: 11)),
                  ],
                )),
                const Icon(Icons.arrow_forward_ios_rounded,
                    color: Colors.black54, size: 16),
              ]),
            ),
          ),

          // Grille des catégories (existante)
          Expanded(
            child: _categories.isEmpty
                ? const Center(child: Text(
                'Aucune catégorie disponible',
                style: TextStyle(color: AppTheme.textMuted)))
                : GridView.builder(
              gridDelegate:
              const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 3,
                mainAxisSpacing: 12,
                crossAxisSpacing: 12,
                childAspectRatio: 0.9,
              ),
              itemCount: _categories.length,
              itemBuilder: (_, i) {
                final cat   = _categories[i];
                final color = AppTheme.categoryColor(cat);
                final icon  = AppTheme.categoryIcon(cat);
                final count = _allTypes
                    .where((t) => t['category'] == cat).length;
                return GestureDetector(
                  onTap: () => _selectCategory(cat),
                  child: Container(
                    decoration: BoxDecoration(
                      color: AppTheme.surface,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: AppTheme.border),
                    ),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Container(
                          width: 46, height: 46,
                          decoration: BoxDecoration(
                            color: color.withOpacity(0.14),
                            borderRadius: BorderRadius.circular(14),
                          ),
                          child: Icon(icon, color: color, size: 24),
                        ),
                        const SizedBox(height: 10),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 6),
                          child: Text(cat,
                              style: const TextStyle(fontSize: 10,
                                  fontWeight: FontWeight.w600,
                                  color: AppTheme.textPrimary),
                              textAlign: TextAlign.center,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis),
                        ),
                        const SizedBox(height: 3),
                        Text('$count services',
                            style: const TextStyle(fontSize: 9,
                                color: AppTheme.textMuted)),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  // ════════════════════════════════════════════════════════════════
  //  ÉTAPE 1 — Liste des types
  // ════════════════════════════════════════════════════════════════
  Widget _buildTypeList() {
    final types = _filteredTypes;
    return Column(children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
        child: TextField(
          onChanged: (q) => setState(() => _searchQuery = q),
          style: const TextStyle(color: AppTheme.textPrimary, fontSize: 13),
          decoration: InputDecoration(
            hintText: 'Rechercher un service...',
            hintStyle: const TextStyle(
                color: AppTheme.textMuted, fontSize: 12),
            prefixIcon: const Icon(Icons.search_rounded,
                size: 18, color: AppTheme.textSecondary),
            filled: true, fillColor: AppTheme.surface,
            contentPadding: const EdgeInsets.symmetric(
                horizontal: 14, vertical: 10),
            border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: const BorderSide(color: AppTheme.border)),
            enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: const BorderSide(color: AppTheme.border)),
            focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: const BorderSide(
                    color: AppTheme.primary, width: 1.5)),
          ),
        ),
      ),
      Expanded(
        child: types.isEmpty
            ? Center(child: Text(
            _searchQuery.isEmpty
                ? 'Aucun service'
                : 'Aucun résultat pour "$_searchQuery"',
            style: const TextStyle(
                color: AppTheme.textMuted, fontSize: 13)))
            : ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: types.length,
          separatorBuilder: (_, __) => const SizedBox(height: 8),
          itemBuilder: (_, i) {
            final t = types[i];
            final isC = t['type'] == 'C';
            final flowColor = isC ? AppTheme.credit : AppTheme.debit;
            return GestureDetector(
              onTap: () => _selectType(t),
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppTheme.surface,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppTheme.border),
                ),
                child: Row(children: [
                  Container(
                    width: 42, height: 42,
                    decoration: BoxDecoration(
                      color: flowColor.withOpacity(0.12),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                        isC ? Icons.arrow_downward_rounded
                            : Icons.arrow_upward_rounded,
                        color: flowColor, size: 20),
                  ),
                  const SizedBox(width: 14),
                  Expanded(child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(t['title'] ?? '',
                          style: const TextStyle(fontSize: 14,
                              fontWeight: FontWeight.w600,
                              color: AppTheme.textPrimary)),
                      if (t['subCategory'] != null) ...[
                        const SizedBox(height: 3),
                        Text(t['subCategory'],
                            style: const TextStyle(fontSize: 11,
                                color: AppTheme.textSecondary)),
                      ],
                    ],
                  )),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: flowColor.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(isC ? 'Crédit' : 'Débit',
                        style: TextStyle(fontSize: 10,
                            fontWeight: FontWeight.w700,
                            color: flowColor)),
                  ),
                  const SizedBox(width: 8),
                  const Icon(Icons.chevron_right_rounded,
                      color: AppTheme.textMuted, size: 20),
                ]),
              ),
            );
          },
        ),
      ),
    ]);
  }

  // ════════════════════════════════════════════════════════════════
  //  ÉTAPE 2 — Montant
  // ════════════════════════════════════════════════════════════════
  Widget _buildAmountInput() {
    final flowColor = _isCredit ? AppTheme.credit : AppTheme.debit;
    final quickAmts = _quickAmounts;
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(children: [
        const Spacer(flex: 1),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
          decoration: BoxDecoration(
            color: flowColor.withOpacity(0.12),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            Icon(_isCredit ? Icons.arrow_downward_rounded
                : Icons.arrow_upward_rounded,
                size: 14, color: flowColor),
            const SizedBox(width: 6),
            Text(_selectedType!['title'] ?? '',
                style: TextStyle(fontSize: 13,
                    fontWeight: FontWeight.w600, color: flowColor)),
          ]),
        ),
        const SizedBox(height: 28),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.baseline,
          textBaseline: TextBaseline.alphabetic,
          children: [
            SizedBox(
              width: 200,
              child: TextField(
                controller: _amountCtrl,
                autofocus: true,
                keyboardType: const TextInputType.numberWithOptions(
                    decimal: true),
                inputFormatters: [
                  FilteringTextInputFormatter.allow(RegExp(r'[\d.]')),
                ],
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 48, fontWeight: FontWeight.w800,
                    color: flowColor, letterSpacing: -2),
                decoration: InputDecoration(
                  hintText: '0.000',
                  hintStyle: TextStyle(fontSize: 48,
                      fontWeight: FontWeight.w800,
                      color: flowColor.withOpacity(0.2)),
                  border: InputBorder.none,
                ),
              ),
            ),
            Text(' TND', style: TextStyle(fontSize: 18,
                fontWeight: FontWeight.w600,
                color: flowColor.withOpacity(0.6))),
          ],
        ),
        if (quickAmts != null) ...[
          const SizedBox(height: 16),
          Wrap(
            spacing: 8, runSpacing: 8,
            alignment: WrapAlignment.center,
            children: quickAmts.map((amt) {
              final label = amt == amt.toInt().toDouble()
                  ? '${amt.toInt()}' : amt.toStringAsFixed(1);
              return GestureDetector(
                onTap: () => setState(() => _amountCtrl.text =
                    amt.toStringAsFixed(
                        amt == amt.toInt().toDouble() ? 0 : 3)),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 16, vertical: 8),
                  decoration: BoxDecoration(
                    color: AppTheme.surface,
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: AppTheme.border),
                  ),
                  child: Text('$label TND',
                      style: const TextStyle(fontSize: 12,
                          fontWeight: FontWeight.w600,
                          color: AppTheme.textPrimary)),
                ),
              );
            }).toList(),
          ),
        ],
        const SizedBox(height: 24),
        if (_selectedCategory == 'Transferts Envoyes') ...[
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            decoration: BoxDecoration(
              color: AppTheme.surface,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: AppTheme.border),
            ),
            child: TextField(
              controller: _receiverCtrl,
              keyboardType: TextInputType.phone,
              style: const TextStyle(
                  color: AppTheme.textPrimary, fontSize: 14),
              decoration: const InputDecoration(
                hintText: 'N° destinataire',
                hintStyle: TextStyle(
                    color: AppTheme.textMuted, fontSize: 12),
                icon: Icon(Icons.person_outline_rounded,
                    size: 18, color: AppTheme.textSecondary),
                border: InputBorder.none,
              ),
            ),
          ),
        ],
        if (_error != null) ...[
          const SizedBox(height: 14),
          _errorBox(_error!),
        ],
        const Spacer(flex: 2),
        SizedBox(
          width: double.infinity, height: 54,
          child: ElevatedButton(
            onPressed: _goToConfirm,
            style: ElevatedButton.styleFrom(
              backgroundColor: flowColor,
              foregroundColor: AppTheme.background,
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16)),
              elevation: 0,
            ),
            child: const Text('Continuer',
                style: TextStyle(
                    fontSize: 16, fontWeight: FontWeight.w700)),
          ),
        ),
      ]),
    );
  }

  // ════════════════════════════════════════════════════════════════
  //  ÉTAPE 3 — Confirmation (sans mention de frais)
  // ════════════════════════════════════════════════════════════════
  Widget _buildConfirmation() {
    final flowColor = _isCredit ? AppTheme.credit : AppTheme.debit;
    final catColor  = AppTheme.categoryColor(_selectedCategory ?? '');
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(children: [
        const Spacer(flex: 1),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            color: AppTheme.surface,
            borderRadius: BorderRadius.circular(24),
            border: Border.all(color: AppTheme.border),
          ),
          child: Column(children: [
            Container(
              width: 56, height: 56,
              decoration: BoxDecoration(
                color: catColor.withOpacity(0.14),
                borderRadius: BorderRadius.circular(16),
              ),
              child: Icon(AppTheme.categoryIcon(_selectedCategory ?? ''),
                  color: catColor, size: 28),
            ),
            const SizedBox(height: 16),
            Text(
              '${_isCredit ? '+' : '-'} ${_amountCtrl.text.trim()} TND',
              style: TextStyle(fontSize: 32, fontWeight: FontWeight.w800,
                  color: flowColor, letterSpacing: -1),
            ),
            const SizedBox(height: 16),
            _confirmRow('Catégorie',  _selectedCategory ?? ''),
            _confirmRow('Opération',  _selectedType!['title'] ?? ''),
            _confirmRow('Flux',
                _isCredit ? 'Crédit (entrant)' : 'Débit (sortant)'),
            if (_receiverCtrl.text.trim().isNotEmpty)
              _confirmRow('Destinataire', _receiverCtrl.text.trim()),
            _confirmRow('Devise', 'TND'),
            // ── Note : les frais IZI Pay sont traités en interne ──
            // Aucune mention des frais à l'utilisateur final.
          ]),
        ),
        if (_error != null) ...[
          const SizedBox(height: 14),
          _errorBox(_error!),
        ],
        const Spacer(flex: 2),
        SizedBox(
          width: double.infinity, height: 54,
          child: ElevatedButton(
            onPressed: _submitting ? null : _submit,
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTheme.primary,
              foregroundColor: AppTheme.background,
              disabledBackgroundColor:
              AppTheme.primary.withOpacity(0.4),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16)),
              elevation: 0,
            ),
            child: _submitting
                ? const SizedBox(width: 22, height: 22,
                child: CircularProgressIndicator(
                    color: AppTheme.background, strokeWidth: 2.5))
                : const Text('Confirmer la transaction',
                style: TextStyle(
                    fontSize: 16, fontWeight: FontWeight.w700)),
          ),
        ),
      ]),
    );
  }

  Widget _confirmRow(String label, String value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 6),
    child: Row(children: [
      Text(label, style: const TextStyle(
          fontSize: 13, color: AppTheme.textSecondary)),
      const Spacer(),
      Text(value, style: const TextStyle(
          fontSize: 13, fontWeight: FontWeight.w600,
          color: AppTheme.textPrimary)),
    ]),
  );

  Widget _errorBox(String text) => Container(
    padding: const EdgeInsets.all(12),
    decoration: BoxDecoration(
      color: AppTheme.debit.withOpacity(0.1),
      borderRadius: BorderRadius.circular(12),
      border: Border.all(color: AppTheme.debit.withOpacity(0.3)),
    ),
    child: Row(children: [
      const Icon(Icons.error_outline_rounded,
          color: AppTheme.debit, size: 16),
      const SizedBox(width: 8),
      Expanded(child: Text(text,
          style: const TextStyle(color: AppTheme.debit,
              fontSize: 12, height: 1.5))),
    ]),
  );
}