// ═══════════════════════════════════════════════════════════════
//  MODÈLES IA — mappés exactement sur PredictionResponse.java v4
//  Endpoint : GET /api/ia/predictions/{clientId}
//
//  Structure JSON retournée par Spring Boot (5 modules) :
//  {
//    client_id, client_nom, solde_actuel_TND, generated_at,
//    segment, mois_prevu, version,
//    module1_factures:     { nb_factures, factures[], total_a_venir_TND }
//    module2_recharge:     { operateur, montant_habituel, jours_restants, ... }
//    module3_budget:       { budget_total_TND, par_categorie: {} }
//    module4_prochaine_tx: { cat_dominante, delai_estime_jours, top3[] }
//    module5_alerte:       { niveau_alerte, solde_apres_TND, message, ... }
//  }
// ═══════════════════════════════════════════════════════════════

// ── Réponse complète ──────────────────────────────────────────
class PredictionResponse {
  final String  clientId;
  final String? clientNom;
  final double  soldeActuelTnd;
  final String  generatedAt;
  final String  segment;     // "Gold" / "Moyen" / "Faible"
  final String  moisPrevu;   // "2026-04"
  final String? version;

  // 5 modules
  final Module1Factures?    module1Factures;
  final Module2Recharge?    module2Recharge;
  final Module3Budget?      module3Budget;
  final Module4ProchaineTx? module4ProchaineTx;
  final Module5Alerte?      module5Alerte;

  const PredictionResponse({
    required this.clientId,
    this.clientNom,
    required this.soldeActuelTnd,
    required this.generatedAt,
    required this.segment,
    required this.moisPrevu,
    this.version,
    this.module1Factures,
    this.module2Recharge,
    this.module3Budget,
    this.module4ProchaineTx,
    this.module5Alerte,
  });

  factory PredictionResponse.fromJson(Map<String, dynamic> j) =>
      PredictionResponse(
        clientId:       j['client_id']       as String?  ?? '',
        clientNom:      j['client_nom']       as String?,
        soldeActuelTnd: (j['solde_actuel_TND'] as num?)?.toDouble() ?? 0.0,
        generatedAt:    j['generated_at']     as String?  ?? '',
        segment:        j['segment']          as String?  ?? 'Faible',
        moisPrevu:      j['mois_prevu']       as String?  ?? '',
        version:        j['version']          as String?,
        module1Factures: j['module1_factures'] != null
            ? Module1Factures.fromJson(j['module1_factures'] as Map<String, dynamic>)
            : null,
        module2Recharge: j['module2_recharge'] != null
            ? Module2Recharge.fromJson(j['module2_recharge'] as Map<String, dynamic>)
            : null,
        module3Budget: j['module3_budget'] != null
            ? Module3Budget.fromJson(j['module3_budget'] as Map<String, dynamic>)
            : null,
        module4ProchaineTx: j['module4_prochaine_tx'] != null
            ? Module4ProchaineTx.fromJson(j['module4_prochaine_tx'] as Map<String, dynamic>)
            : null,
        module5Alerte: j['module5_alerte'] != null
            ? Module5Alerte.fromJson(j['module5_alerte'] as Map<String, dynamic>)
            : null,
      );
}

// ════════════════════════════════════════════════════════════
//  MODULE 1 — Prochaines factures (TOPNET/BEE/SONEDE/STEG/TT/OOREDOO)
// ════════════════════════════════════════════════════════════
class Module1Factures {
  final String      clientId;
  final int         nbFactures;
  final List<Facture> factures;
  final double      totalAVenirTnd;

  const Module1Factures({
    required this.clientId,
    required this.nbFactures,
    required this.factures,
    required this.totalAVenirTnd,
  });

  factory Module1Factures.fromJson(Map<String, dynamic> j) =>
      Module1Factures(
        clientId:      j['client_id']        as String?  ?? '',
        nbFactures:    j['nb_factures']       as int?     ?? 0,
        factures:      _parseList(j['factures'], Facture.fromJson),
        totalAVenirTnd:(j['total_a_venir_TND'] as num?)?.toDouble() ?? 0.0,
      );

  // Factures à afficher (exclure payées et expirées)
  List<Facture> get prochaines =>
      factures.where((f) => f.statut != 'payée' && f.statut != 'expirée').toList();

  // Factures urgentes ou en retard
  List<Facture> get urgentes =>
      factures.where((f) =>
        f.statut == 'urgente' || f.statut == 'due_maintenant' ||
        f.statut == 'en_retard' || f.statut == 'en_retard_grave').toList();
}

class Facture {
  final String fournisseur;
  final String label;
  final double montantPrevu;
  final double icBas;
  final double icHaut;
  final String datePrevue;
  final int    joursRestants;
  final String statut;      // "urgente"/"prochaine"/"planifiée"/"due_maintenant"/"en_retard"/"expirée"/"payée"
  final String confiance;
  final int    couche;
  final double? r2Modele;
  final bool   montantApproximatif;
  final int?   intervalleMedian;

  const Facture({
    required this.fournisseur, required this.label,
    required this.montantPrevu, required this.icBas, required this.icHaut,
    required this.datePrevue,   required this.joursRestants,
    required this.statut,       required this.confiance,
    required this.couche,
    this.r2Modele, this.montantApproximatif = false, this.intervalleMedian,
  });

  factory Facture.fromJson(Map<String, dynamic> j) => Facture(
    fournisseur:   j['fournisseur']    as String? ?? '',
    label:         j['label']          as String? ?? '',
    montantPrevu:  (j['montant_prevu'] as num?)?.toDouble() ?? 0.0,
    icBas:         (j['ic_bas']        as num?)?.toDouble() ?? 0.0,
    icHaut:        (j['ic_haut']       as num?)?.toDouble() ?? 0.0,
    datePrevue:    j['date_prevue']    as String? ?? '',
    joursRestants: j['jours_restants'] as int?    ?? 0,
    statut:        j['statut']         as String? ?? '',
    confiance:     j['confiance']      as String? ?? 'Faible',
    couche:        j['couche']         as int?    ?? 1,
    r2Modele:      (j['r2_modele'] as num?)?.toDouble(),
    montantApproximatif: j['montant_approximatif'] as bool? ?? false,
    intervalleMedian: j['intervalle_median'] as int?,
  );

  String get nomCourt => fournisseur
      .replaceAll('PAIEMENT ', '').replaceAll('RECHARGE ', '').trim();
  bool get estUrgent  => statut == 'urgente' || statut == 'due_maintenant';
  bool get estEnRetard => statut == 'en_retard' || statut == 'en_retard_grave';
  bool get estPassee  => joursRestants < 0;
  bool get estApproximatif => montantApproximatif || (r2Modele != null && r2Modele! < 0.5);
}

// ════════════════════════════════════════════════════════════
//  MODULE 2 — Prochain rechargement téléphonique
// ════════════════════════════════════════════════════════════
class Module2Recharge {
  final String  clientId;
  final String? erreur;          // null si OK
  final String  operateur;       // "TT" / "Ooredoo" / "Orange"
  final double  montantHabituel;
  final int     intervalleMedian;
  final String  datePrevue;
  final int     joursRestants;
  final String  statut;          // "imminent"/"prochaine"/"planifiée"/"en_retard"/"à recharger"/"inactif"/"rechargé_ce_mois"
  final String  fiabilite;       // "Haute" / "Moyenne" / "Faible"
  final int     nHistorique;

  const Module2Recharge({
    required this.clientId,
    this.erreur,
    required this.operateur,
    required this.montantHabituel,
    required this.intervalleMedian,
    required this.datePrevue,
    required this.joursRestants,
    required this.statut,
    required this.fiabilite,
    required this.nHistorique,
  });

  factory Module2Recharge.fromJson(Map<String, dynamic> j) =>
      Module2Recharge(
        clientId:        j['client_id']         as String? ?? '',
        erreur:          j['erreur']             as String?,
        operateur:       j['operateur']          as String? ?? '',
        montantHabituel: (j['montant_habituel']  as num?)?.toDouble() ?? 0.0,
        intervalleMedian:j['intervalle_median']  as int?    ?? 14,
        datePrevue:      j['date_prevue']        as String? ?? '',
        joursRestants:   j['jours_restants']     as int?    ?? 0,
        statut:          j['statut']             as String? ?? '',
        fiabilite:       j['fiabilite']          as String? ?? 'Faible',
        nHistorique:     j['n_historique']       as int?    ?? 0,
      );

  bool get disponible => erreur == null && operateur.isNotEmpty && statut != 'inactif';
  bool get estImminente => joursRestants >= 0 && joursRestants <= 3;
  bool get estEnRetard  => statut == 'en_retard' || statut == 'à recharger';
  bool get estInactif   => statut == 'inactif';
  bool get estRecharge  => statut == 'rechargé_ce_mois';
}

// ════════════════════════════════════════════════════════════
//  MODULE 3 — Budget mensuel (4 catégories)
// ════════════════════════════════════════════════════════════
class Module3Budget {
  final String    clientId;
  final String    segment;
  final String    moisPrevu;
  final double    budgetTotalTnd;   // max 600 TND — hors factures et transferts
  final Map<String, DetailBudget> parCategorie;

  const Module3Budget({
    required this.clientId,
    required this.segment,
    required this.moisPrevu,
    required this.budgetTotalTnd,
    required this.parCategorie,
  });

  factory Module3Budget.fromJson(Map<String, dynamic> j) => Module3Budget(
    clientId:       j['client_id']        as String? ?? '',
    segment:        j['segment']          as String? ?? 'Faible',
    moisPrevu:      j['mois_prevu']       as String? ?? '',
    budgetTotalTnd: (j['budget_total_TND'] as num?)?.toDouble() ?? 0.0,
    parCategorie:   _parseMap(
        j['par_categorie'], DetailBudget.fromJson),
  );
}

class DetailBudget {
  final double predit;
  final double icBas;
  final double icHaut;
  final String confiance;

  const DetailBudget({
    required this.predit, required this.icBas,
    required this.icHaut, required this.confiance,
  });

  factory DetailBudget.fromJson(Map<String, dynamic> j) => DetailBudget(
    predit:    (j['predit']    as num?)?.toDouble() ?? 0.0,
    icBas:     (j['ic_bas']    as num?)?.toDouble() ?? 0.0,
    icHaut:    (j['ic_haut']   as num?)?.toDouble() ?? 0.0,
    confiance: j['confiance']  as String? ?? 'Faible',
  );
}

// ════════════════════════════════════════════════════════════
//  MODULE 4 — Prochaine transaction probable
// ════════════════════════════════════════════════════════════
class Module4ProchaineTx {
  final String      clientId;
  final String?     erreur;
  final String      catDominante;        // catégorie la plus fréquente
  final int         delaiEstimeJours;    // intervalle médian entre tx
  final int         joursDepuisTx;       // depuis la dernière tx
  final String      confiance;
  final List<ProbaTx> top3;

  const Module4ProchaineTx({
    required this.clientId,
    this.erreur,
    required this.catDominante,
    required this.delaiEstimeJours,
    required this.joursDepuisTx,
    required this.confiance,
    required this.top3,
  });

  factory Module4ProchaineTx.fromJson(Map<String, dynamic> j) =>
      Module4ProchaineTx(
        clientId:          j['client_id']            as String? ?? '',
        erreur:            j['erreur']               as String?,
        catDominante:      j['cat_dominante']         as String? ?? '',
        delaiEstimeJours:  j['delai_estime_jours']   as int?    ?? 3,
        joursDepuisTx:     j['jours_depuis_tx']      as int?    ?? 0,
        confiance:         j['confiance']            as String? ?? 'Faible',
        top3:              _parseList(j['top3'], ProbaTx.fromJson),
      );

  bool get disponible => erreur == null && catDominante.isNotEmpty;
}

class ProbaTx {
  final String categorie;
  final double probabilite;  // 0.0 → 1.0
  final int    dansJours;

  const ProbaTx({
    required this.categorie,
    required this.probabilite,
    required this.dansJours,
  });

  factory ProbaTx.fromJson(Map<String, dynamic> j) => ProbaTx(
    categorie:    j['categorie']    as String? ?? '',
    probabilite:  (j['probabilite'] as num?)?.toDouble() ?? 0.0,
    dansJours:    j['dans_jours']   as int?    ?? 0,
  );

  String get categorieCoure {
    final c = categorie;
    if (c.contains('Shopping'))    return 'Shopping';
    if (c.contains('Restaurant') || c.contains('Livraison')) return 'Restos';
    if (c.contains('Recharge'))    return 'Recharge';
    if (c.contains('Facture'))     return 'Factures';
    if (c.contains('Voyage'))      return 'Voyages';
    if (c.contains('Transfert'))   return 'Transferts';
    return c.split(' ').first;
  }
}

// ════════════════════════════════════════════════════════════
//  MODULE 5 — Alerte solde intelligente
// ════════════════════════════════════════════════════════════
class Module5Alerte {
  final String  clientId;
  final double  soldeActuelTnd;
  final double  totalUrgentTnd;
  final double  budgetVariableTnd;
  final double  totalPrevuTnd;
  final double  soldeApresTnd;
  final String  niveauAlerte;        // "OK" / "ATTENTION" / "CRITIQUE"
  final int     nbUrgents;
  final String  message;
  final String  recommandation;

  const Module5Alerte({
    required this.clientId,
    required this.soldeActuelTnd,
    required this.totalUrgentTnd,
    required this.budgetVariableTnd,
    required this.totalPrevuTnd,
    required this.soldeApresTnd,
    required this.niveauAlerte,
    required this.nbUrgents,
    required this.message,
    required this.recommandation,
  });

  factory Module5Alerte.fromJson(Map<String, dynamic> j) => Module5Alerte(
    clientId:           j['client_id']            as String? ?? '',
    soldeActuelTnd:     (j['solde_actuel_TND']     as num?)?.toDouble() ?? 0.0,
    totalUrgentTnd:     (j['total_urgent_TND']     as num?)?.toDouble() ?? 0.0,
    budgetVariableTnd:  (j['budget_variable_TND']  as num?)?.toDouble() ?? 0.0,
    totalPrevuTnd:      (j['total_prevu_TND']      as num?)?.toDouble() ?? 0.0,
    soldeApresTnd:      (j['solde_apres_TND']      as num?)?.toDouble() ?? 0.0,
    niveauAlerte:       j['niveau_alerte']         as String? ?? 'OK',
    nbUrgents:          j['nb_urgents']            as int?    ?? 0,
    message:            j['message']               as String? ?? '',
    recommandation:     j['recommandation']        as String? ?? '',
  );

  bool get estCritique  => niveauAlerte == 'CRITIQUE';
  bool get estAttention => niveauAlerte == 'ATTENTION';
  bool get estOk        => niveauAlerte == 'OK';
}

// ════════════════════════════════════════════════════════════
//  HELPERS GÉNÉRIQUES
// ════════════════════════════════════════════════════════════

List<T> _parseList<T>(dynamic raw, T Function(Map<String, dynamic>) fromJson) {
  if (raw is List) {
    return raw.whereType<Map<String, dynamic>>().map(fromJson).toList();
  }
  return [];
}

Map<String, T> _parseMap<T>(dynamic raw, T Function(Map<String, dynamic>) fromJson) {
  if (raw is Map<String, dynamic>) {
    return raw.map((k, v) {
      if (v is Map<String, dynamic>) return MapEntry(k, fromJson(v));
      return MapEntry(k, fromJson({}));
    });
  }
  return {};
}