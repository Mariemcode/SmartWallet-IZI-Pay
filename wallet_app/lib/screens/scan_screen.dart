import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:http/http.dart' as http;
import '../services/api_service.dart';

import '../config/app_config.dart';
import '../theme/app_theme.dart';
import 'new_transaction_screen.dart';

// ════════════════════════════════════════════════════════════════
//  SmartWallet — ScanScreen (Computer Vision)
//  ============================================================
//  Fonctionnalité 1 : Scan de facture (OCR)
//    → Photo → FastAPI → champs pré-remplis → paiement
//
//  Fonctionnalité 2 : Rappel intelligent
//    → Programmation automatique après scan
//
//  Fonctionnalité 3 : Détective de factures
//    → Alerte si montant anormal vs historique
//
//  pubspec.yaml — packages requis :
//    image_picker: ^1.0.7
//    http: ^1.2.0   (déjà présent)
// ════════════════════════════════════════════════════════════════

class ScanScreen extends StatefulWidget {
  final String clientId;
  final double? soldeActuel;

  const ScanScreen({
    super.key,
    required this.clientId,
    this.soldeActuel,
  });

  @override
  State<ScanScreen> createState() => _ScanScreenState();
}

class _ScanScreenState extends State<ScanScreen>
    with SingleTickerProviderStateMixin {
  // ── État du scan ────────────────────────────────────────────
  File?   _imageFile;
  bool    _scanning  = false;
  bool    _scanned   = false;
  String? _error;

  // ── Résultats OCR ───────────────────────────────────────────
  String? _fournisseurLabel;
  String? _fournisseurNom;
  double? _montant;
  String? _dateEcheance;
  String? _reference;
  String  _confiance      = 'faible';
  List    _chamsManquants = [];

  // ── Impact solde ────────────────────────────────────────────
  Map<String, dynamic>? _impactSolde;

  // ── Anomalie ────────────────────────────────────────────────
  Map<String, dynamic>? _anomalie;

  // ── Feedback Loop — valeurs originales OCR (pour détecter les corrections) ──
  String? _ocrFournisseurLabel;  // valeur brute retournée par OCR
  double? _ocrMontant;           // valeur brute retournée par OCR
  String? _ocrDateEcheance;      // valeur brute retournée par OCR
  String? _ocrReference;         // valeur brute retournée par OCR
  bool    _feedbackEnvoye = false; // éviter le double envoi

  // ── Animation ───────────────────────────────────────────────
  late AnimationController _pulseCtrl;
  late Animation<double>   _pulseAnim;

  final _picker = ImagePicker();

  @override
  void initState() {
    super.initState();
    _pulseCtrl = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 1200))
      ..repeat(reverse: true);
    _pulseAnim = Tween(begin: 0.85, end: 1.0).animate(
        CurvedAnimation(parent: _pulseCtrl, curve: Curves.easeInOut));
  }

  @override
  void dispose() {
    _pulseCtrl.dispose();
    super.dispose();
  }

  // ════════════════════════════════════════════════════════════
  //  SCAN — Prise de photo + envoi à FastAPI
  // ════════════════════════════════════════════════════════════

  Future<void> _prisePhoto(ImageSource source) async {
    try {
      final picked = await _picker.pickImage(
        source: source,
        imageQuality: 85,
        maxWidth: 1920,
        maxHeight: 2560,
      );
      if (picked == null) return;

      setState(() {
        _imageFile = File(picked.path);
        _scanning  = true;
        _scanned   = false;
        _error     = null;
        _resetResultats();
      });

      await _analyserImage(_imageFile!);
    } catch (e) {
      setState(() {
        _scanning = false;
        _error    = 'Erreur caméra : ${e.toString()}';
      });
    }
  }

  void _resetResultats() {
    _fournisseurLabel = null;
    _fournisseurNom   = null;
    _montant          = null;
    _dateEcheance     = null;
    _reference        = null;
    _confiance        = 'faible';
    _chamsManquants   = [];
    _impactSolde      = null;
    _anomalie         = null;
    _ocrFournisseurLabel = null;
    _ocrMontant          = null;
    _ocrDateEcheance     = null;
    _ocrReference        = null;
    _feedbackEnvoye      = false;
  }

  Future<void> _analyserImage(File image) async {
    try {
      final token = await ApiService.getToken();

      print('🔑 Token présent : ${token != null}');
      print('📡 URL : ${AppConfig.baseUrl}/api/ia/ocr/scan-facture');
      print('👤 Client ID : ${widget.clientId}');
      print('💰 Solde : ${widget.soldeActuel}');

      // Construire le multipart/form-data
      final uri = Uri.parse(
        '${AppConfig.baseUrl}/api/ia/ocr/scan-facture',
      );
      final request = http.MultipartRequest('POST', uri)
        ..headers.addAll({
          if (token != null) 'Authorization': 'Bearer $token',
        })
        ..fields['client_id'] = widget.clientId
        ..fields['solde']     = (widget.soldeActuel ?? 0).toString()
        ..files.add(await http.MultipartFile.fromPath('image', image.path));

      final response = await request.send().timeout(AppConfig.aiTimeout);
      final body     = await response.stream.bytesToString();

      if (response.statusCode != 200) {
        throw Exception('Erreur serveur ${response.statusCode}');
      }

      final data = jsonDecode(body) as Map<String, dynamic>;

      setState(() {
        _fournisseurLabel = data['fournisseur_label'] as String?;
        _fournisseurNom   = data['fournisseur_nom']   as String?;
        _montant          = (data['montant']          as num?)?.toDouble();
        _dateEcheance     = data['date_echeance']     as String?;
        _reference        = data['reference']         as String?;
        _confiance        = data['confiance_globale'] as String? ?? 'faible';
        _chamsManquants   = (data['champs_manquants'] as List?) ?? [];
        _impactSolde      = data['impact_solde']      as Map<String, dynamic>?;
        _anomalie         = data['anomalie']           as Map<String, dynamic>?;
        _scanning         = false;
        _scanned          = true;
      });
      // Sauvegarder les valeurs OCR brutes pour le feedback
      _ocrFournisseurLabel = data['fournisseur_label'] as String?;
      _ocrMontant          = (data['montant'] as num?)?.toDouble();
      _ocrDateEcheance     = data['date_echeance'] as String?;
      _ocrReference        = data['reference'] as String?;

      // ✅ CORRECTION : si l'OCR n'est pas sûr du montant (confiance != haute
      // ou montant absent), on ouvre la confirmation/correction manuelle pour
      // que l'utilisateur valide ou saisisse le montant avant d'enregistrer.
      final bool needConfirm = (data['confirmation_requise'] == true) ||
          _montant == null || _confiance != 'haute';
      if (needConfirm && mounted) {
        await Future.delayed(const Duration(milliseconds: 300));
        _showCorrectionDialog();
      }

      // Si anomalie haute → afficher popup immédiatement
      if (_anomalie?['anomalie'] == true &&
          _anomalie?['severite'] == 'haute' && mounted) {
        await Future.delayed(const Duration(milliseconds: 400));
        _showAnomalieDialog();
      }
    } catch (e) {
      setState(() {
        _scanning = false;
        _error    = 'Analyse échouée : ${e.toString().replaceAll('Exception: ', '')}';
      });
    }
  }

  // ════════════════════════════════════════════════════════════
  //  RAPPEL INTELLIGENT — Programmer via FastAPI → Spring Boot
  // ════════════════════════════════════════════════════════════

  Future<void> _programmerRappel() async {
    if (_montant == null || _dateEcheance == null) return;

    try {
      final token = await ApiService.getToken();

      final uri = Uri.parse('${AppConfig.baseUrl}/api/ia/ocr/programmer-rappel');
      final resp = await http.post(
        uri,
        headers: {
          'Content-Type': 'application/json',
          if (token != null) 'Authorization': 'Bearer $token',
        },
        body: jsonEncode({
          'client_id':         widget.clientId,
          'fournisseur_label': _fournisseurLabel,
          'fournisseur_nom':   _fournisseurNom,
          'montant':           _montant,
          'date_echeance':     _dateEcheance,
          'reference':         _reference,
        }),
      ).timeout(AppConfig.defaultTimeout);

      if (resp.statusCode == 200) {
        final data    = jsonDecode(resp.body) as Map<String, dynamic>;
        final rappels = (data['rappels'] as List?) ?? [];

        if (mounted) _showRappelConfirmation(rappels);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Rappel non enregistré : $e'),
          backgroundColor: AppTheme.debit,
          behavior: SnackBarBehavior.floating,
        ));
      }
    }
  }

  // ════════════════════════════════════════════════════════════
  //  NAVIGATION — Payer maintenant
  // ════════════════════════════════════════════════════════════

  void _payerMaintenant() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => NewTransactionScreen(
          clientId:        widget.clientId,
          prefillAmount:   _montant,
          prefillCategory: 'Factures & Services',
          prefillType:     _fournisseurNom,
        ),
      ),
    );
  }

  // ════════════════════════════════════════════════════════════
  //  FEEDBACK LOOP — Envoyer les corrections à FastAPI
  // ════════════════════════════════════════════════════════════

  Future<void> _envoyerFeedback({String actionFinale = 'paye'}) async {
    if (_feedbackEnvoye) return;
    _feedbackEnvoye = true;

    try {
      final token = await ApiService.getToken();

      final uri = Uri.parse('${AppConfig.baseUrl}/api/ia/ocr/feedback');

      // 🔥 LOG VISIBLE DANS LA CONSOLE FLUTTER
      print('🚀🚀🚀 ENVOI FEEDBACK VERS : $uri');

      final response = await http.post(
        uri,
        headers: {
          'Content-Type': 'application/json',
          if (token != null) 'Authorization': 'Bearer $token',
        },
        body: jsonEncode({
          'client_id':          widget.clientId,
          'ocr_fournisseur':    _ocrFournisseurLabel,
          'ocr_montant':        _ocrMontant,
          'ocr_date_echeance':  _ocrDateEcheance,
          'ocr_reference':      _ocrReference,
          'ocr_confiance':      _confiance,
          'user_fournisseur':   _fournisseurLabel,
          'user_montant':       _montant,
          'user_date_echeance': _dateEcheance,
          'user_reference':     _reference,
          'action_finale':      actionFinale,
        }),
      ).timeout(const Duration(seconds: 5)); // Augmente le timeout

      // 🔥 LOG DE LA RÉPONSE
      print('✅ Feedback response: ${response.statusCode} - ${response.body}');

    } catch (e) {
      // 🔥 LOG DE L'ERREUR EXACTE
      print('❌❌❌ ERREUR FEEDBACK: $e');

      // Afficher dans l'UI pour être sûr de le voir
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Erreur Feedback: $e', style: TextStyle(color: Colors.white)),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 5),
          ),
        );
      }
    }
  }

  // ════════════════════════════════════════════════════════════
  //  DIALOGS
  // ════════════════════════════════════════════════════════════
  void _showAnomalieDialog() {
    final ecart   = _anomalie!['pourcentage_ecart'] as num? ?? 0;
    final moy     = _anomalie!['montant_moyen']      as num? ?? 0;
    final causes  = (_anomalie!['causes_possibles']  as List?) ?? [];
    final hist    = (_anomalie!['historique']        as List?) ?? [];

    showDialog(
      context: context,
      builder: (_) => Dialog(
        backgroundColor: Colors.transparent,
        child: Container(
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            color: const Color(0xFF1E1E2E),
            borderRadius: BorderRadius.circular(24),
            border: Border.all(color: AppTheme.debit.withOpacity(0.4), width: 1.5),
          ),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            // Header
            Row(children: [
              Container(
                width: 48, height: 48,
                decoration: BoxDecoration(
                    color: AppTheme.debit.withOpacity(0.12),
                    borderRadius: BorderRadius.circular(14)),
                child: const Icon(Icons.search_rounded, color: AppTheme.debit, size: 26),
              ),
              const SizedBox(width: 12),
              const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('🕵️ Alerte Détective !',
                    style: TextStyle(color: AppTheme.debit, fontSize: 16, fontWeight: FontWeight.w800)),
                Text('Facture anormalement élevée',
                    style: TextStyle(color: AppTheme.textSecondary, fontSize: 12)),
              ])),
            ]),
            const SizedBox(height: 18),

            // Montant scanné vs moyenne
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppTheme.debit.withOpacity(0.06),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: AppTheme.debit.withOpacity(0.2)),
              ),
              child: Column(children: [
                Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                  const Text('Facture actuelle',
                      style: TextStyle(color: AppTheme.textSecondary, fontSize: 12)),
                  Text('${_montant?.toStringAsFixed(3) ?? '?'} TND',
                      style: const TextStyle(color: AppTheme.debit, fontSize: 16, fontWeight: FontWeight.w800)),
                ]),
                const Divider(color: Color(0xFF2D2D44), height: 16),
                Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                  const Text('Votre moyenne',
                      style: TextStyle(color: AppTheme.textSecondary, fontSize: 12)),
                  Text('${moy.toStringAsFixed(0)} TND',
                      style: const TextStyle(color: AppTheme.textPrimary, fontSize: 14, fontWeight: FontWeight.w600)),
                ]),
                const SizedBox(height: 8),
                // Barre de comparaison
                if (hist.isNotEmpty) _buildHistoriqueBar(hist, _montant ?? 0),
                const SizedBox(height: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
                  decoration: BoxDecoration(
                      color: AppTheme.debit.withOpacity(0.12),
                      borderRadius: BorderRadius.circular(20)),
                  child: Text(
                      '📈 Augmentation de ${ecart.toStringAsFixed(0)}% !',
                      style: const TextStyle(color: AppTheme.debit, fontSize: 12, fontWeight: FontWeight.w700)),
                ),
              ]),
            ),

            // Causes possibles
            if (causes.isNotEmpty) ...[
              const SizedBox(height: 14),
              Align(
                alignment: Alignment.centerLeft,
                child: const Text('🤔 Causes possibles',
                    style: TextStyle(color: AppTheme.textPrimary, fontSize: 13, fontWeight: FontWeight.w700)),
              ),
              const SizedBox(height: 8),
              ...causes.map((c) => Padding(
                padding: const EdgeInsets.only(bottom: 4),
                child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const Text('•  ', style: TextStyle(color: AppTheme.textMuted)),
                  Expanded(child: Text(c.toString(),
                      style: const TextStyle(color: AppTheme.textSecondary, fontSize: 12))),
                ]),
              )),
            ],

            const SizedBox(height: 20),
            // Boutons
            Row(children: [
              Expanded(child: OutlinedButton.icon(
                onPressed: () => Navigator.pop(context),
                icon: const Icon(Icons.arrow_back_rounded, size: 16),
                label: const Text('Retour', style: TextStyle(fontSize: 13)),
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppTheme.textSecondary,
                  side: const BorderSide(color: Color(0xFF2D2D44)),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  padding: const EdgeInsets.symmetric(vertical: 12),
                ),
              )),
              const SizedBox(width: 10),
              Expanded(child: ElevatedButton.icon(
                onPressed: () { Navigator.pop(context); _payerMaintenant(); },
                icon: const Icon(Icons.payment_rounded, size: 16),
                label: const Text('Payer quand même', style: TextStyle(fontSize: 12)),
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTheme.debit,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  padding: const EdgeInsets.symmetric(vertical: 12),
                ),
              )),
            ]),
          ]),
        ),
      ),
    );
  }

  void _showCorrectionDialog() {
    // Contrôleurs pré-remplis avec les valeurs actuelles
    final ctrlFournisseur = TextEditingController(text: _fournisseurNom ?? '');
    final ctrlMontant     = TextEditingController(
        text: _montant != null ? _montant!.toStringAsFixed(3) : '');
    final ctrlDate        = TextEditingController(text: _dateEcheance ?? '');
    final ctrlReference   = TextEditingController(text: _reference ?? '');

    // Mapping nom affiché → label interne
    const fourLabels = {
      'STEG': 'STEG', 'SONEDE': 'SONEDE', 'TOPNET': 'TOPNET',
      'BeeConnect': 'BEE', 'Tunisie Telecom': 'TT', 'Ooredoo': 'OOREDOO',
    };

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => Dialog(
        backgroundColor: Colors.transparent,
        child: Container(
          constraints: const BoxConstraints(maxWidth: 400),
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            color: const Color(0xFF1E1E2E),
            borderRadius: BorderRadius.circular(24),
            border: Border.all(color: AppTheme.primary.withOpacity(0.35), width: 1.5),
          ),
          child: SingleChildScrollView(
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              // ── Header ──────────────────────────────────────
              Row(children: [
                Container(
                    width: 44, height: 44,
                    decoration: BoxDecoration(
                        color: AppTheme.primary.withOpacity(0.12),
                        borderRadius: BorderRadius.circular(12)),
                    child: const Icon(Icons.edit_note_rounded,
                        color: AppTheme.primary, size: 24)),
                const SizedBox(width: 12),
                const Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text('✏️ Corriger les champs',
                        style: TextStyle(color: AppTheme.primary,
                            fontSize: 15, fontWeight: FontWeight.w800)),
                    Text('Vos corrections améliorent l\'IA',
                        style: TextStyle(color: AppTheme.textSecondary, fontSize: 11)),
                  ]),
                ),
              ]),
              const SizedBox(height: 4),
              // ── Badge apprentissage ──────────────────────────
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                margin: const EdgeInsets.only(top: 12),
                decoration: BoxDecoration(
                  color: const Color(0xFF1A3A2A),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: AppTheme.credit.withOpacity(0.3)),
                ),
                child: const Row(children: [
                  Icon(Icons.psychology_rounded, color: AppTheme.credit, size: 14),
                  SizedBox(width: 6),
                  Expanded(child: Text(
                      '🧠 Chaque correction entraîne l\'IA à mieux lire vos factures',
                      style: TextStyle(color: AppTheme.credit, fontSize: 10.5))),
                ]),
              ),
              const SizedBox(height: 20),

              // ── Champ Fournisseur (dropdown) ─────────────────
              Align(alignment: Alignment.centerLeft,
                  child: Text('Fournisseur',
                      style: const TextStyle(color: AppTheme.textSecondary,
                          fontSize: 12, fontWeight: FontWeight.w600))),
              const SizedBox(height: 6),
              StatefulBuilder(
                builder: (ctx, setStateLocal) {
                  String? selected = fourLabels.entries
                      .firstWhere(
                        (e) => e.key == ctrlFournisseur.text || e.value == _fournisseurLabel,
                    orElse: () => const MapEntry('', ''),
                  ).key;
                  if (selected!.isEmpty) selected = null;

                  return DropdownButtonFormField<String>(
                    value: selected,
                    dropdownColor: const Color(0xFF1E1E2E),
                    style: const TextStyle(color: AppTheme.textPrimary, fontSize: 13),
                    decoration: InputDecoration(
                      filled: true,
                      fillColor: const Color(0xFF151C28),
                      border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide(color: AppTheme.border)),
                      enabledBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide(color: AppTheme.border)),
                      focusedBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide(color: AppTheme.primary)),
                      contentPadding: const EdgeInsets.symmetric(
                          horizontal: 14, vertical: 12),
                    ),
                    hint: const Text('Choisir le fournisseur',
                        style: TextStyle(color: AppTheme.textMuted)),
                    items: fourLabels.entries.map((e) => DropdownMenuItem(
                        value: e.key,
                        child: Text(e.key))).toList(),
                    onChanged: (val) {
                      if (val != null) ctrlFournisseur.text = val;
                    },
                  );
                },
              ),
              const SizedBox(height: 14),

              // ── Champ Montant ────────────────────────────────
              _CorrectionField(
                controller: ctrlMontant,
                label: 'Montant (TND)',
                icon: Icons.payments_rounded,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                hint: 'ex: 141.398',
              ),
              const SizedBox(height: 14),

              // ── Champ Date ───────────────────────────────────
              _CorrectionField(
                controller: ctrlDate,
                label: 'Date d\'échéance',
                icon: Icons.calendar_today_rounded,
                hint: 'DD/MM/YYYY',
              ),
              const SizedBox(height: 14),

              // ── Champ Référence ──────────────────────────────
              _CorrectionField(
                controller: ctrlReference,
                label: 'Référence client',
                icon: Icons.tag_rounded,
                hint: 'ex: 20260415001',
                keyboardType: TextInputType.number,
              ),
              const SizedBox(height: 24),

              // ── Boutons ──────────────────────────────────────
              Row(children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => Navigator.pop(context),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppTheme.textSecondary,
                      side: BorderSide(color: AppTheme.border),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12)),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                    child: const Text('Annuler'),
                  ),
                ),
                const SizedBox(width: 12),

                Expanded(
                  child: ElevatedButton(
                    onPressed: () {
                      // Appliquer les corrections
                      setState(() {
                        final nomFour = ctrlFournisseur.text.trim();
                        _fournisseurNom   = nomFour.isNotEmpty ? nomFour : _fournisseurNom;
                        _fournisseurLabel = fourLabels[nomFour] ?? _fournisseurLabel;

                        final montantStr = ctrlMontant.text.trim().replaceAll(',', '.');
                        _montant = double.tryParse(montantStr) ?? _montant;

                        final dateStr = ctrlDate.text.trim();
                        if (dateStr.isNotEmpty) _dateEcheance = dateStr;

                        final refStr = ctrlReference.text.trim();
                        if (refStr.isNotEmpty) _reference = refStr;
                      });

                      Navigator.pop(context);

                      // ✅✅✅ AJOUTEZ CES 2 LIGNES CI-DESSOUS ✅✅✅
                      _feedbackEnvoye = false; // Réinitialiser pour permettre l'envoi
                      _envoyerFeedback(actionFinale: 'corrige');

                      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                        content: const Row(children: [
                          Icon(Icons.check_circle_rounded,
                              color: Colors.white, size: 16),
                          SizedBox(width: 8),
                          Text('Corrections enregistrées — Merci ! 🧠'),
                        ]),
                        backgroundColor: AppTheme.credit,
                        behavior: SnackBarBehavior.floating,
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12)),
                        duration: const Duration(seconds: 2),
                      ));
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppTheme.primary,
                      foregroundColor: AppTheme.background,
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12)),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      elevation: 0,
                    ),
                    child: const Text('✅ Valider',
                        style: TextStyle(fontWeight: FontWeight.w700)),
                  ),
                ),
              ]),
            ]),
          ),
        ),
      ),
    );
  }

  Widget _buildHistoriqueBar(List hist, double montantActuel) {
    final maxVal = [
      ...hist.map((e) => (e as num).toDouble()),
      montantActuel,
    ].reduce((a, b) => a > b ? a : b);

    return Column(
      children: [
        ...hist.asMap().entries.take(4).map((entry) {
          final val  = (entry.value as num).toDouble();
          final pct  = maxVal > 0 ? val / maxVal : 0.0;
          final mois = ['Jan', 'Fév', 'Mar', 'Avr', 'Mai', 'Juin',
            'Juil', 'Aoû', 'Sep', 'Oct', 'Nov', 'Déc'];
          final now  = DateTime.now();
          final idx  = (now.month - 1 - (hist.length - 1 - entry.key)) % 12;
          final nom  = mois[idx < 0 ? idx + 12 : idx];
          return Padding(
            padding: const EdgeInsets.only(bottom: 3),
            child: Row(children: [
              SizedBox(width: 28,
                  child: Text(nom, style: const TextStyle(color: AppTheme.textMuted, fontSize: 9))),
              const SizedBox(width: 4),
              Expanded(child: ClipRRect(
                borderRadius: BorderRadius.circular(3),
                child: LinearProgressIndicator(
                  value: pct.clamp(0.0, 1.0),
                  minHeight: 8,
                  backgroundColor: AppTheme.surface,
                  valueColor: const AlwaysStoppedAnimation(AppTheme.textMuted),
                ),
              )),
              const SizedBox(width: 4),
              Text('${val.toStringAsFixed(0)} TND',
                  style: const TextStyle(color: AppTheme.textSecondary, fontSize: 9)),
            ]),
          );
        }),
        // Barre mois actuel (rouge)
        Padding(
          padding: const EdgeInsets.only(bottom: 3),
          child: Row(children: [
            const SizedBox(width: 28,
                child: Text('Actuel', style: TextStyle(color: AppTheme.debit, fontSize: 9))),
            const SizedBox(width: 4),
            Expanded(child: ClipRRect(
              borderRadius: BorderRadius.circular(3),
              child: LinearProgressIndicator(
                value: (maxVal > 0 ? montantActuel / maxVal : 0)
                    .clamp(0.0, 1.0)
                    .toDouble(),
                minHeight: 8,
                backgroundColor: AppTheme.surface,
                valueColor: const AlwaysStoppedAnimation(AppTheme.debit),
              ),
            )),
            const SizedBox(width: 4),
            Text('${montantActuel.toStringAsFixed(0)} TND ⚠️',
                style: const TextStyle(color: AppTheme.debit, fontSize: 9, fontWeight: FontWeight.w700)),
          ]),
        ),
      ],
    );
  }

  void _showRappelConfirmation(List rappels) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (_) => Container(
        margin: const EdgeInsets.fromLTRB(12, 0, 12, 12),
        padding: const EdgeInsets.all(24),
        decoration: const BoxDecoration(
          color: Color(0xFF1A1A2E),
          borderRadius: BorderRadius.all(Radius.circular(24)),
        ),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Container(width: 40, height: 4,
              decoration: BoxDecoration(color: AppTheme.border,
                  borderRadius: BorderRadius.circular(2))),
          const SizedBox(height: 20),
          // Header
          Row(children: [
            Container(
                width: 48, height: 48,
                decoration: BoxDecoration(
                    color: AppTheme.primary.withOpacity(0.12),
                    borderRadius: BorderRadius.circular(14)),
                child: const Icon(Icons.notifications_active_rounded,
                    color: AppTheme.primary, size: 26)),
            const SizedBox(width: 14),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('Facture $_fournisseurNom programmée',
                  style: const TextStyle(color: AppTheme.textPrimary,
                      fontSize: 15, fontWeight: FontWeight.w800)),
              Text('${_montant?.toStringAsFixed(3) ?? '?'} TND — $_dateEcheance',
                  style: const TextStyle(color: AppTheme.textSecondary, fontSize: 12)),
            ])),
          ]),
          const SizedBox(height: 20),

          // Rappels prévus
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppTheme.primary.withOpacity(0.05),
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: AppTheme.primary.withOpacity(0.2)),
            ),
            child: Column(children: [
              const Align(
                alignment: Alignment.centerLeft,
                child: Text('🔔 Rappels automatiques activés',
                    style: TextStyle(color: AppTheme.primary,
                        fontSize: 13, fontWeight: FontWeight.w700)),
              ),
              const SizedBox(height: 12),
              ...rappels.map((r) => _RappelRow(rappel: r as Map<String, dynamic>)),
            ]),
          ),
          const SizedBox(height: 20),

          // Bouton confirmer
          SizedBox(width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: () => Navigator.pop(context),
              icon: const Icon(Icons.check_rounded, size: 18),
              label: const Text('Parfait !',
                  style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
              style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.primary,
                foregroundColor: AppTheme.background,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
              ),
            ),
          ),
        ]),
      ),
    );
  }

  // ════════════════════════════════════════════════════════════
  //  BUILD
  // ════════════════════════════════════════════════════════════

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.background, elevation: 0,
        leading: IconButton(
            icon: const Icon(Icons.arrow_back_ios_new_rounded,
                size: 18, color: AppTheme.textPrimary),
            onPressed: () => Navigator.pop(context)),
        title: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Scanner une facture',
                style: TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w700,
                    color: AppTheme.textPrimary)),
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Text(_scanned ? 'Vérifie et confirme' : 'OCR + IA',
                  style: const TextStyle(
                      fontSize: 11, color: AppTheme.textMuted)),
            ),
          ],
        ),
      ),
      body: SafeArea(
        child: _scanned ? _buildResultView() : _buildScanView(),
      ),
    );
  }

  // ════════════════════════════════════════════════════════════
  //  VUE 1 — Avant scan (choix caméra/galerie)
  // ════════════════════════════════════════════════════════════

  Widget _buildScanView() {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(children: [
        const Spacer(flex: 1),

        // Icône animée
        AnimatedBuilder(
          animation: _pulseAnim,
          builder: (_, __) => Transform.scale(
            scale: _scanning ? _pulseAnim.value : 1.0,
            child: Container(
              width: 160, height: 160,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: RadialGradient(colors: [
                  AppTheme.primary.withOpacity(0.15),
                  AppTheme.primary.withOpacity(0.03),
                ]),
                border: Border.all(
                    color: AppTheme.primary.withOpacity(_scanning ? 0.8 : 0.3),
                    width: 2),
              ),
              child: _scanning
                  ? const Center(child: CircularProgressIndicator(
                  color: AppTheme.primary, strokeWidth: 3))
                  : _imageFile != null
                  ? ClipOval(child: Image.file(_imageFile!, fit: BoxFit.cover))
                  : const Icon(Icons.document_scanner_rounded,
                  color: AppTheme.primary, size: 72),
            ),
          ),
        ),
        const SizedBox(height: 28),

        Text(
          _scanning ? 'Analyse en cours...' : 'Scannez votre facture',
          style: const TextStyle(color: AppTheme.textPrimary,
              fontSize: 22, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 8),
        Text(
          _scanning
              ? 'L\'IA extrait les informations de votre facture'
              : 'STEG · SONEDE · TOPNET · BEE · TT · Ooredoo',
          style: const TextStyle(color: AppTheme.textSecondary, fontSize: 13),
          textAlign: TextAlign.center,
        ),

        if (_error != null) ...[
          const SizedBox(height: 16),
          Container(
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
              Expanded(child: Text(_error!,
                  style: const TextStyle(color: AppTheme.debit, fontSize: 12))),
            ]),
          ),
        ],

        const Spacer(flex: 2),

        if (!_scanning) ...[
          // Bouton principale : Caméra
          SizedBox(
            width: double.infinity, height: 54,
            child: ElevatedButton.icon(
              onPressed: () => _prisePhoto(ImageSource.camera),
              icon: const Icon(Icons.camera_alt_rounded, size: 22),
              label: const Text('📸 Prendre une photo',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
              style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.primary,
                foregroundColor: AppTheme.background,
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16)),
                elevation: 0,
              ),
            ),
          ),
          const SizedBox(height: 12),
          // Bouton secondaire : Galerie
          SizedBox(
            width: double.infinity, height: 50,
            child: OutlinedButton.icon(
              onPressed: () => _prisePhoto(ImageSource.gallery),
              icon: const Icon(Icons.photo_library_rounded, size: 20),
              label: const Text('📁 Choisir depuis la galerie',
                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
              style: OutlinedButton.styleFrom(
                foregroundColor: AppTheme.textSecondary,
                side: const BorderSide(color: AppTheme.border),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14)),
              ),
            ),
          ),
          const SizedBox(height: 12),
          // Saisie manuelle
          TextButton.icon(
            onPressed: () => Navigator.push(context, MaterialPageRoute(
              builder: (_) => NewTransactionScreen(
                clientId: widget.clientId,
                prefillCategory: 'Factures & Services',
              ),
            )),
            icon: const Icon(Icons.edit_rounded,
                size: 16, color: AppTheme.textMuted),
            label: const Text('📋 Saisie manuelle',
                style: TextStyle(color: AppTheme.textMuted, fontSize: 13)),
          ),
        ],

        const SizedBox(height: 16),
      ]),
    );
  }

  // ════════════════════════════════════════════════════════════
  //  VUE 2 — Après scan (résultats + actions)
  // ════════════════════════════════════════════════════════════

  Widget _buildResultView() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [

        // ── Badge confiance ──────────────────────────────────
        _ConfianceBadge(confiance: _confiance),
        const SizedBox(height: 16),

        // ── Carte principale : champs extraits ──────────────
        _buildCarteFacture(),
        const SizedBox(height: 16),

        // ── Impact solde ─────────────────────────────────────
        if (_impactSolde != null) _buildImpactSolde(),
        if (_impactSolde != null) const SizedBox(height: 16),

        // ── Champs manquants ─────────────────────────────────
        if (_chamsManquants.isNotEmpty) _buildChamsManquants(),
        if (_chamsManquants.isNotEmpty) const SizedBox(height: 16),

        // ── Boutons d'action ─────────────────────────────────
        _buildActions(),
        const SizedBox(height: 16),

        // ── Recommencer le scan ──────────────────────────────
        Center(
          child: TextButton.icon(
            onPressed: () => setState(() {
              _scanned = false;
              _imageFile = null;
              _resetResultats();
            }),
            icon: const Icon(Icons.refresh_rounded,
                size: 16, color: AppTheme.textMuted),
            label: const Text('Scanner à nouveau',
                style: TextStyle(color: AppTheme.textMuted, fontSize: 13)),
          ),
        ),
      ]),
    );
  }

  Widget _buildCarteFacture() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppTheme.primary.withOpacity(0.3)),
        boxShadow: [
          BoxShadow(color: AppTheme.primary.withOpacity(0.06),
              blurRadius: 20, spreadRadius: 0),
        ],
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(
              width: 44, height: 44,
              decoration: BoxDecoration(
                  color: AppTheme.primary.withOpacity(0.12),
                  borderRadius: BorderRadius.circular(12)),
              child: const Icon(Icons.flash_on_rounded,
                  color: AppTheme.primary, size: 24)),
          const SizedBox(width: 12),
          const Text('⚡ Facture détectée !',
              style: TextStyle(color: AppTheme.primary,
                  fontSize: 16, fontWeight: FontWeight.w800)),
        ]),
        const SizedBox(height: 16),
        const Divider(color: Color(0xFF1E2D45), height: 1),
        const SizedBox(height: 16),
        _ChampRow(icon: Icons.business_rounded,  label: 'Fournisseur', value: _fournisseurNom   ?? '—'),
        _ChampRow(icon: Icons.payments_rounded,   label: 'Montant',     value: _montant != null ? '${_montant!.toStringAsFixed(3)} TND' : '—'),
        _ChampRow(icon: Icons.calendar_today_rounded, label: 'Échéance', value: _dateEcheance ?? '—'),
        _ChampRow(icon: Icons.tag_rounded,        label: 'Référence',   value: _reference ?? '—', isLast: true),
      ]),
    );
  }

  Widget _buildImpactSolde() {
    final niveau   = _impactSolde!['niveau_alerte'] as String? ?? 'ok';
    final soldeApr = (_impactSolde!['solde_apres']        as num?)?.toDouble() ?? 0;
    final pct      = (_impactSolde!['pct_budget_utilise'] as num?)?.toDouble() ?? 0;
    final message  = _impactSolde!['message'] as String? ?? '';
    // ✅ Solde actuel = solde après facture + montant de la facture.
    // (le backend calcule solde_apres à partir du VRAI solde en base ; on ne
    //  dépend donc plus de widget.soldeActuel qui pouvait valoir 0).
    final soldeAvant = soldeApr + (_montant ?? 0);

    final color = switch (niveau) {
      'critique'  => AppTheme.debit,
      'attention' => AppTheme.accent,
      _           => AppTheme.credit,
    };

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withOpacity(0.06),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: color.withOpacity(0.25)),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Icon(Icons.account_balance_wallet_rounded, color: color, size: 18),
          const SizedBox(width: 8),
          Text('🔮 Impact sur votre solde',
              style: TextStyle(color: color, fontSize: 13, fontWeight: FontWeight.w700)),
        ]),
        const SizedBox(height: 12),
        Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          const Text('Solde actuel',
              style: TextStyle(color: AppTheme.textSecondary, fontSize: 12)),
          Text('${soldeAvant.toStringAsFixed(3)} TND',
              style: const TextStyle(color: AppTheme.textPrimary, fontSize: 13, fontWeight: FontWeight.w600)),
        ]),
        const SizedBox(height: 4),
        Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          const Text('Après cette facture',
              style: TextStyle(color: AppTheme.textSecondary, fontSize: 12)),
          Text('${soldeApr.toStringAsFixed(3)} TND',
              style: TextStyle(color: color, fontSize: 13, fontWeight: FontWeight.w700)),
        ]),
        const SizedBox(height: 10),
        // Barre progression budget
        ClipRRect(
          borderRadius: BorderRadius.circular(6),
          child: LinearProgressIndicator(
            value: (pct / 100).clamp(0.0, 1.0),
            minHeight: 10,
            backgroundColor: color.withOpacity(0.12),
            valueColor: AlwaysStoppedAnimation(color),
          ),
        ),
        const SizedBox(height: 6),
        Text(message, style: TextStyle(color: color, fontSize: 11)),
      ]),
    );
  }

  Widget _buildChamsManquants() {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppTheme.accent.withOpacity(0.06),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppTheme.accent.withOpacity(0.2)),
      ),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Icon(Icons.info_outline_rounded,
            color: AppTheme.accent, size: 16),
        const SizedBox(width: 8),
        Expanded(child: Text(
            'Champs non détectés : ${_chamsManquants.join(', ')}. '
                'Vous pouvez les saisir manuellement.',
            style: const TextStyle(color: AppTheme.accent, fontSize: 11.5))),
      ]),
    );
  }

  Widget _buildActions() {
    return Column(children: [
      // Payer maintenant
      SizedBox(
        width: double.infinity, height: 52,
        child: ElevatedButton.icon(
          onPressed: () async {await _envoyerFeedback(actionFinale: 'paye');_payerMaintenant();},
          icon: const Icon(Icons.credit_card_rounded, size: 20),
          label: const Text('💳 Payer maintenant',
              style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
          style: ElevatedButton.styleFrom(
            backgroundColor: AppTheme.primary,
            foregroundColor: AppTheme.background,
            shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(14)),
            elevation: 0,
          ),
        ),
      ),
      const SizedBox(height: 10),
      const SizedBox(height: 10),
      SizedBox(
        width: double.infinity, height: 46,
        child: OutlinedButton.icon(
          onPressed: _showCorrectionDialog,
          icon: const Icon(Icons.edit_note_rounded, size: 18),
          label: const Text('✏️ Corriger les champs',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
          style: OutlinedButton.styleFrom(
            foregroundColor: AppTheme.primary,
            side: BorderSide(color: AppTheme.primary.withOpacity(0.5)),
            shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12)),
          ),
        ),
      ),
      const SizedBox(height: 4),
      // Micro-texte d'encouragement
      const Center(
        child: Text(
          '🧠 Vos corrections améliorent l\'IA pour tous',
          style: TextStyle(color: AppTheme.textMuted, fontSize: 10.5),
        ),
      ),
      // Programmer rappel
      SizedBox(
        width: double.infinity, height: 48,
        child: ElevatedButton.icon(
          onPressed: () async {await _envoyerFeedback(actionFinale: 'rappel');_programmerRappel();},
          icon: const Icon(Icons.notifications_rounded, size: 18),
          label: Text(
              _dateEcheance != null
                  ? '📅 Programmer les rappels'
                  : '📅 Rappel (date manquante)',
              style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
          style: ElevatedButton.styleFrom(
            backgroundColor: const Color(0xFF6C5CE7),
            foregroundColor: Colors.white,
            disabledBackgroundColor: AppTheme.surface,
            shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(14)),
            elevation: 0,
          ),
        ),
      ),
      const SizedBox(height: 10),
      // Voir détail anomalie si présente
      if (_anomalie?['anomalie'] == true)
        SizedBox(
          width: double.infinity, height: 46,
          child: OutlinedButton.icon(
            onPressed: _showAnomalieDialog,
            icon: const Icon(Icons.search_rounded,
                size: 18, color: AppTheme.debit),
            label: const Text('🕵️ Voir l\'alerte détective',
                style: TextStyle(color: AppTheme.debit,
                    fontSize: 13, fontWeight: FontWeight.w600)),
            style: OutlinedButton.styleFrom(
              side: BorderSide(color: AppTheme.debit.withOpacity(0.4)),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14)),
            ),
          ),
        ),
    ]);
  }
}

// ════════════════════════════════════════════════════════════════
//  WIDGETS HELPERS
// ════════════════════════════════════════════════════════════════

class _ChampRow extends StatelessWidget {
  final IconData icon;
  final String label, value;
  final bool isLast;

  const _ChampRow({
    required this.icon,
    required this.label,
    required this.value,
    this.isLast = false,
  });


  @override
  Widget build(BuildContext context) {
    final ok = value != '—';
    return Padding(
      padding: EdgeInsets.only(bottom: isLast ? 0 : 12),
      child: Row(children: [
        Icon(icon, color: AppTheme.textSecondary, size: 16),
        const SizedBox(width: 10),
        Expanded(child: Text(label,
            style: const TextStyle(color: AppTheme.textSecondary, fontSize: 13))),
        Row(children: [
          Text(value,
              style: TextStyle(
                  color: ok ? AppTheme.textPrimary : AppTheme.textMuted,
                  fontSize: 13, fontWeight: ok ? FontWeight.w700 : FontWeight.w400)),
          if (ok) ...[
            const SizedBox(width: 4),
            const Icon(Icons.check_circle_rounded,
                color: AppTheme.credit, size: 14),
          ],
        ]),
      ]),
    );
  }
}

class _ConfianceBadge extends StatelessWidget {
  final String confiance;
  const _ConfianceBadge({required this.confiance});

  @override
  Widget build(BuildContext context) {
    final (color, label, icon) = switch (confiance) {
      'haute'   => (AppTheme.credit,  'Confiance haute',   Icons.verified_rounded),
      'moyenne' => (AppTheme.accent,  'Confiance moyenne', Icons.warning_amber_rounded),
      _         => (AppTheme.debit,   'Confiance faible',  Icons.error_outline_rounded),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(icon, color: color, size: 14),
        const SizedBox(width: 6),
        Text(label, style: TextStyle(
            color: color, fontSize: 12, fontWeight: FontWeight.w700)),
      ]),
    );
  }
}

class _RappelRow extends StatelessWidget {
  final Map<String, dynamic> rappel;
  const _RappelRow({required this.rappel});

  @override
  Widget build(BuildContext context) {
    final jours = rappel['jours_avant'] as int? ?? 0;
    final date  = rappel['date_rappel'] as String? ?? '';
    final titre = rappel['titre']       as String? ?? '';
    final msg   = rappel['message']     as String? ?? '';

    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Container(
          width: 32, height: 32,
          decoration: BoxDecoration(
              color: AppTheme.primary.withOpacity(0.12),
              borderRadius: BorderRadius.circular(8)),
          child: Center(child: Text(
              jours == 0 ? '🚨' : jours == 1 ? '⚠️' : jours <= 3 ? '⏰' : '📋',
              style: const TextStyle(fontSize: 14))),
        ),
        const SizedBox(width: 10),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(titre, style: const TextStyle(
              color: AppTheme.textPrimary, fontSize: 12, fontWeight: FontWeight.w700)),
          Text('$date — $msg',
              style: const TextStyle(color: AppTheme.textSecondary, fontSize: 11)),
        ])),
        const Icon(Icons.check_circle_rounded,
            color: AppTheme.credit, size: 16),
      ]),
    );
  }

}
class _CorrectionField extends StatelessWidget {
  final TextEditingController controller;
  final String label;
  final IconData icon;
  final String? hint;
  final TextInputType? keyboardType;

  const _CorrectionField({
    required this.controller,
    required this.label,
    required this.icon,
    this.hint,
    this.keyboardType,
  });

  @override
  Widget build(BuildContext context) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(label, style: const TextStyle(
          color: AppTheme.textSecondary, fontSize: 12, fontWeight: FontWeight.w600)),
      const SizedBox(height: 6),
      TextField(
        controller:   controller,
        keyboardType: keyboardType ?? TextInputType.text,
        style: const TextStyle(color: AppTheme.textPrimary, fontSize: 13),
        decoration: InputDecoration(
          prefixIcon: Icon(icon, color: AppTheme.textMuted, size: 18),
          hintText: hint,
          hintStyle: const TextStyle(color: AppTheme.textMuted, fontSize: 12),
          filled: true,
          fillColor: const Color(0xFF151C28),
          border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide(color: AppTheme.border)),
          enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide(color: AppTheme.border)),
          focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: const BorderSide(color: AppTheme.primary)),
          contentPadding: const EdgeInsets.symmetric(
              horizontal: 12, vertical: 12),
        ),
      ),
    ]);
  }
}