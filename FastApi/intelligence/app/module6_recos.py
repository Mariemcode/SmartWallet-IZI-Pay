"""
══════════════════════════════════════════════════════════════════════
SmartWallet — Module 6 : Recommandations Live + Peer Comparison
══════════════════════════════════════════════════════════════════════
Utilise (GBM + 43 features +
6 profils) comme source de segmentation client.
Fonctionnalités :
  • Recommandations personnalisées (peer comparison sur le profil GBM)
  • Optimisation budget (forfait télécom)
  • Budget targets (cible par catégorie vs pairs du même profil GBM)
  • Alertes comportementales (Z-score sur catégories)
  • Gamification (challenges + niveaux)
  • Résumé du mois (vs mois dernier)
══════════════════════════════════════════════════════════════════════
"""
from __future__ import annotations

import logging
import os
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional

import joblib
import numpy as np
import pandas as pd

from . import classification as cls

log = logging.getLogger("smartwallet.module6")

# ─────────────────────────────────────────────────────────────────────
# Mappage présentation
# ─────────────────────────────────────────────────────────────────────
CAT_EMOJI = {
    "Factures & Services": "📋",
    "Recharge Telephonique": "📱",
    "Shopping & Paiements": "🛒",
    "Restaurants & Livraison": "🍽️",
    "Transferts Envoyes": "💸",
    "Voyages & Reservations": "✈️",
    "Depot & Retrait": "🏧",
    "Education & Institutions": "🎓",
}
CAT_SIMPLE = {
    "Factures & Services": "Factures",
    "Recharge Telephonique": "Recharge",
    "Shopping & Paiements": "Achats",
    "Restaurants & Livraison": "Restos",
    "Transferts Envoyes": "Transferts",
    "Voyages & Reservations": "Voyages",
    "Depot & Retrait": "Dépôt/Retrait",
    "Education & Institutions": "Éducation",
}

# Mappage feature ratio → catégorie d'affichage
RATIO_TO_CATEGORY = {
    "ratio_factures":      "Factures & Services",
    "ratio_recharges":     "Recharge Telephonique",
    "ratio_shopping":      "Shopping & Paiements",
    "ratio_restaurants":   "Restaurants & Livraison",
    "ratio_transferts":    "Transferts Envoyes",
    "ratio_voyages":       "Voyages & Reservations",
    "ratio_education":     "Education & Institutions",
}

# ─────────────────────────────────────────────────────────────────────
# Gamification — niveaux et challenges
# ─────────────────────────────────────────────────────────────────────
NIVEAUX = [
    {"min": 0,   "nom": "Débutant",   "emoji": "🥉", "couleur": "#CD7F32"},
    {"min": 100, "nom": "Malin",      "emoji": "🥈", "couleur": "#C0C0C0"},
    {"min": 300, "nom": "Expert",     "emoji": "🥇", "couleur": "#FFD700"},
    {"min": 600, "nom": "Champion",   "emoji": "💎", "couleur": "#00E5FF"},
]


def get_niveau(points: int) -> Dict[str, Any]:
    niv = NIVEAUX[0]
    for n in NIVEAUX:
        if points >= n["min"]:
            niv = n
    idx = NIVEAUX.index(niv)
    prochain = NIVEAUX[idx + 1] if idx < len(NIVEAUX) - 1 else None
    return {
        "nom": niv["nom"], "emoji": niv["emoji"], "couleur": niv["couleur"],
        "points_actuels": points,
        "prochain_niveau": prochain["nom"] if prochain else None,
        "points_prochain": prochain["min"] if prochain else None,
        "progres_pct": round((points - niv["min"]) / (prochain["min"] - niv["min"]) * 100, 1)
        if prochain else 100.0,
    }


# ─────────────────────────────────────────────────────────────────────
# Caches : statistiques médianes par profil GBM (pour peer comparison)
# ─────────────────────────────────────────────────────────────────────
# Ces caches sont peuplés au démarrage à partir de reco_features.pkl
# (généré par retrain.py)
_features_cache: Dict[str, Dict[str, Any]] = {}        # client_id -> features
_profile_medians: Dict[int, Dict[str, float]] = {}     # profile_id -> {feature: median}
_profile_p25: Dict[int, Dict[str, float]] = {}         # profile_id -> {feature: p25}
_budget_optim_cache: Dict[str, List[Dict]] = {}        # client_id -> [forfait]
_alerts_cache: Dict[str, List[Dict]] = {}              # client_id -> [alertes]


def load_module6_caches(model_dir: str) -> None:
    """
    Charge les caches pré-calculés du Module 6 (pour accélérer /recommendations).
    Fichiers attendus dans model_dir :
      reco_features.pkl        : dict {client_id: features_dict} avec 'cluster_id' (profil GBM)
      reco_profile_medians.pkl : dict {profile_id: {feature: median_value}}
      reco_profile_p25.pkl     : dict {profile_id: {feature: p25_value}}
      reco_budget_optim.pkl    : dict {client_id: [forfait_options]}
      reco_alerts.pkl          : dict {client_id: [alertes]}
    """
    global _features_cache, _profile_medians, _profile_p25
    global _budget_optim_cache, _alerts_cache

    model_dir_p = Path(model_dir)
    loaded = []

    for name, target in [
        ("reco_features.pkl",        "_features_cache"),
        ("reco_profile_medians.pkl", "_profile_medians"),
        ("reco_profile_p25.pkl",     "_profile_p25"),
        ("reco_budget_optim.pkl",    "_budget_optim_cache"),
        ("reco_alerts.pkl",          "_alerts_cache"),
    ]:
        path = model_dir_p / name
        if path.exists():
            try:
                obj = joblib.load(path)
                globals()[target] = obj
                loaded.append(name)
            except Exception as e:
                log.warning(f"  ⚠️ Impossible de charger {name} : {e}")

    if loaded:
        log.info(f"  ✓ Module 6 caches : {len(loaded)} fichiers chargés")
    else:
        log.info("  ℹ️ Module 6 : pas de cache pré-calculé (mode live uniquement)")


# ─────────────────────────────────────────────────────────────────────
# Peer comparison utilisant le profil GBM
# ─────────────────────────────────────────────────────────────────────
def _peer_comparison_recommendations(
        client_features: Dict[str, float],
        profile_id: int,
        profile_name: str,
) -> List[Dict[str, Any]]:
    """
    Génère des recommandations en comparant le client à la médiane de son
    profil GBM (peer comparison).
    """
    recs: List[Dict[str, Any]] = []

    medians = _profile_medians.get(profile_id) or _profile_medians.get(str(profile_id), {})
    if not medians:
        return recs

    monthly_spend = float(client_features.get("montant_total", 0)) / max(
        float(client_features.get("nb_active_months", 1)), 1
    )

    for ratio_col, cat_name in RATIO_TO_CATEGORY.items():
        client_pct = float(client_features.get(ratio_col, 0) or 0) * 100
        peer_pct = float(medians.get(ratio_col, 0) or 0) * 100

        # Si le client dépense >50% de plus que la médiane de son profil
        if client_pct > 5 and peer_pct > 0 and client_pct > peer_pct * 1.5:
            excess = client_pct - peer_pct
            savings_tnd = monthly_spend * excess / 100
            emoji = CAT_EMOJI.get(cat_name, "📊")
            short_name = CAT_SIMPLE.get(cat_name, cat_name)

            recs.append({
                "type": "peer_comparison",
                "categorie": f"{emoji} {short_name}",
                "message": (
                    f"Vous consacrez {client_pct:.0f}% à {short_name} "
                    f"contre {peer_pct:.0f}% pour les {profile_name}"
                ),
                "conseil": (
                    f"En ramenant à {peer_pct:.0f}%, vous économiseriez "
                    f"~{savings_tnd:.0f} TND/mois"
                ),
                "economie_tnd": round(savings_tnd, 1),
                "priorite": "haute" if savings_tnd > 30 else "moyenne",
                "exces_pct": round(excess, 1),
            })

    # Alerte tendance hausse (momentum_court > 1.3)
    momentum = float(client_features.get("momentum_court", 1.0))
    if momentum > 1.3:
        recs.append({
            "type": "tendance",
            "categorie": "global",
            "message": "Vos dépenses sont en forte hausse récente",
            "conseil": "Attention à la tendance, surveillez vos catégories sensibles",
            "economie_tnd": 0,
            "priorite": "haute",
        })

    # Alerte volatilité (cv_montants > 1.5)
    cv = float(client_features.get("cv_montants", 0))
    if cv > 1.5:
        recs.append({
            "type": "stabilisation",
            "categorie": "global",
            "message": "Vos dépenses sont très variables d'un mois à l'autre",
            "conseil": "Essayez de régulariser votre budget mensuel",
            "economie_tnd": 0,
            "priorite": "moyenne",
        })

    return recs[:5]


# ─────────────────────────────────────────────────────────────────────
# Budget targets utilisant le profil GBM
# ─────────────────────────────────────────────────────────────────────
def _budget_targets_for_profile(
        client_id: str,
        profile_id: int,
        get_db_conn,
) -> List[Dict[str, Any]]:
    """
    Pour chaque catégorie, retourne (actuel_tnd, cible_tnd, ecart_tnd, statut)
    par rapport à la médiane des clients du MÊME profil GBM.
    """
    CATS_BT = [
        "Factures & Services", "Restaurants & Livraison", "Shopping & Paiements",
        "Voyages & Reservations", "Education & Institutions",
    ]

    try:
        conn = get_db_conn()

        # Dépenses moyennes du client par catégorie (sur 6 derniers mois)
        client_avg = pd.read_sql("""
                                 SELECT tt.category, AVG(monthly_total.total_mois) AS moy_mensuel
                                 FROM (
                                          SELECT t.client_id, t.transaction_type_id,
                                                 DATE_TRUNC('month', t.transaction_date) AS mois,
                                                 SUM(t.amount) AS total_mois
                                          FROM transaction t
                                          WHERE t.client_id = %s
                                            AND t.reversal_flag = 'N'
                                            AND t.transaction_date >= NOW() - INTERVAL '6 months'
                                          GROUP BY t.client_id, t.transaction_type_id, mois
                                      ) monthly_total
                                          JOIN type_transaction tt ON monthly_total.transaction_type_id = tt.id
                                 WHERE tt.type = 'D' AND tt.category = ANY(%s)
                                 GROUP BY tt.category
                                 """, conn, params=(client_id, CATS_BT))

        conn.close()
    except Exception as e:
        log.warning(f"⚠️ budget_targets DB error : {e}")
        return []

    # Cible = médiane du profil (si disponible dans le cache)
    # Sinon on prend l'avg du client comme cible (pas de comparaison utile)
    medians_profile = _profile_medians.get(profile_id) or _profile_medians.get(str(profile_id), {})

    targets = []
    avg_dict = dict(zip(client_avg["category"], client_avg["moy_mensuel"])) if not client_avg.empty else {}

    for cat in CATS_BT:
        actuel = float(avg_dict.get(cat, 0))

        # La médiane par profil porte sur les *ratios*, pas les montants.
        # On utilise donc une heuristique : si le profil cible_ratio_X existe,
        # cible = ratio * dépense_totale_moyenne du profil
        cat_key = next(
            (rcol for rcol, cname in RATIO_TO_CATEGORY.items() if cname == cat),
            None
        )
        if cat_key:
            ratio_cible = float(medians_profile.get(cat_key, 0) or 0)
            # Dépense moyenne totale mensuelle de référence (médiane du profil)
            monthly_total_med = float(medians_profile.get("montant_total", 0) or 0) / max(
                float(medians_profile.get("nb_active_months", 1) or 1), 1
            )
            cible = ratio_cible * monthly_total_med
        else:
            cible = actuel

        cible = max(cible, 0)
        ecart = round(actuel - cible, 1)
        if cible > 0:
            statut = ("au-dessus" if ecart > cible * 0.2
                      else "en-dessous" if ecart < -cible * 0.2
            else "dans_la_norme")
        else:
            statut = "dans_la_norme"

        targets.append({
            "categorie": cat,
            "actuel_tnd": round(actuel, 1),
            "cible_tnd": round(cible, 1),
            "ecart_tnd": ecart,
            "statut": statut,
        })

    return targets


# ─────────────────────────────────────────────────────────────────────
# Build challenges dynamiques
# ─────────────────────────────────────────────────────────────────────
def _build_challenges(
        nb_factures_mois: int,
        nb_cats_mois: int,
        total_ce_mois: float,
        total_mois_dernier: float,
        jours_sans_depense: int,
) -> List[Dict[str, Any]]:
    challenges = []

    challenges.append({
        "id": "factures", "titre": "Payeur modèle",
        "description": "Payez 3 factures ce mois via l'app",
        "recompense": "🥉 Badge Bronze + 10 points",
        "points": 10, "icone": "receipt_long",
        "difficulte": "facile",
        "progres": min(nb_factures_mois, 3), "objectif": 3,
        "complete": nb_factures_mois >= 3,
    })

    challenges.append({
        "id": "explorer", "titre": "Explorateur",
        "description": "Utilisez 4 services différents ce mois",
        "recompense": "🔍 Badge Explorateur + 20 points",
        "points": 20, "icone": "explore",
        "difficulte": "facile" if nb_cats_mois >= 3 else "moyen",
        "progres": min(nb_cats_mois, 4), "objectif": 4,
        "complete": nb_cats_mois >= 4,
    })

    if total_mois_dernier > 0:
        objectif_eco = round(total_mois_dernier * 0.9, 0)
        progres_eco = 1 if total_ce_mois < objectif_eco else 0
        challenges.append({
            "id": "economiser", "titre": "Défi du mois",
            "description": f"Dépensez moins de {objectif_eco:.0f} TND ce mois",
            "recompense": "🏆 Badge Champion + 50 points",
            "points": 50, "icone": "savings",
            "difficulte": "difficile",
            "progres": progres_eco, "objectif": 1,
            "complete": progres_eco == 1,
        })

    challenges.append({
        "id": "controle", "titre": "Maître du contrôle",
        "description": "3 jours sans dépense de plus de 50 TND",
        "recompense": "💪 Badge Discipline + 15 points",
        "points": 15, "icone": "shield",
        "difficulte": "moyen",
        "progres": min(jours_sans_depense, 3), "objectif": 3,
        "complete": jours_sans_depense >= 3,
    })

    return challenges


# ─────────────────────────────────────────────────────────────────────
# Endpoint principal : /recommendations/{client_id}
# ─────────────────────────────────────────────────────────────────────
def get_recommendations(
        client_id: str,
        get_db_conn,
        notify_challenge_completed: Optional[callable] = None,
        notified_set: Optional[set] = None,
) -> Dict[str, Any]:
    """
    Génère les recommandations complètes pour un client.

    Args:
        client_id : ID du client
        get_db_conn : callable() -> psycopg2 connection
        notify_challenge_completed : callable(client_id, challenge_id, titre, points)
                                     pour notifier Spring Boot d'un challenge complété
        notified_set : set partagé pour dédupliquer les notifications

    Returns:
        dict avec : client_id, generated_at, segment (profil GBM), resume_mois,
                    budget_live, conseils, budget_optim, budget_targets,
                    challenges, gamification, alertes, resume
    """
    result: Dict[str, Any] = {
        "client_id": client_id,
        "generated_at": datetime.now().isoformat(),
    }

    # ── 1. Classifier le client via la méthode binôme ─────────────
    # Si le client est dans le cache _features_cache (snapshot du dernier retrain),
    # on l'utilise directement. Sinon, on calcule à partir de la DB.
    features_dict: Dict[str, Any] = {}
    cached = _features_cache.get(client_id)
    if cached:
        features_dict = {k: v for k, v in cached.items() if k != "cluster_id"}
        if "cluster_id" in cached:
            # Profil pré-calculé déjà connu — on peut quand même reclassifier
            # pour avoir tous les champs (churn, ltv, ...)
            pass

    classify_result = cls.classify_from_db(client_id, get_db_conn)
    profile_id = classify_result["cluster_id"]
    profile_name = classify_result["profile_name"]
    profile_final = classify_result["profile_final"]
    confidence = classify_result["confidence"]
    churn_score = classify_result["churn_score_30j"]
    churn_seg = classify_result["churn_segment"]

    result["segment"] = profile_final  # nom complet du profil GBM
    result["profil_gbm"] = {
        "cluster_id": profile_id,
        "profile_name": profile_name,
        "profile_final": profile_final,
        "confidence": confidence,
        "is_mixte": classify_result["is_mixte"],
        "churn_score_30j": churn_score,
        "churn_segment": churn_seg,
        "ltv_12m_base": classify_result["ltv_12m_base"],
        "arpu_mensuel": classify_result["arpu_mensuel"],
    }

    # Si pas de features_dict dans le cache, on les reconstruit depuis la DB
    if not features_dict:
        try:
            conn = get_db_conn()
            tx = pd.read_sql("""
                             SELECT t.client_id, t.transaction_date, t.amount, t.reversal_flag,
                                    tt.category, COALESCE(p.provider_name, 'default') AS provider_name
                             FROM transaction t
                                      JOIN type_transaction tt ON t.transaction_type_id = tt.id
                                      LEFT JOIN provider p     ON t.provider_id = p.id
                             WHERE t.client_id = %s
                             """, conn, params=(client_id,))
            conn.close()
            if not tx.empty:
                feats_df = cls.build_features_from_transactions(tx)
                if client_id in feats_df.index:
                    features_dict = feats_df.loc[client_id].to_dict()
        except Exception as e:
            log.warning(f"⚠️ Reconstruction features : {e}")

    # ── 2. Données live du mois courant + mois précédent ───────────
    try:
        conn = get_db_conn()
        ce_mois = pd.read_sql("""
                              SELECT tt.category, SUM(t.amount) as total, COUNT(*) as nb
                              FROM transaction t
                                       JOIN type_transaction tt ON t.transaction_type_id = tt.id
                              WHERE t.client_id = %s AND t.reversal_flag = 'N' AND tt.type = 'D'
                                AND DATE_TRUNC('month', t.transaction_date) = DATE_TRUNC('month', NOW())
                              GROUP BY tt.category
                              """, conn, params=(client_id,))
        mois_dernier = pd.read_sql("""
                                   SELECT tt.category, SUM(t.amount) as total, COUNT(*) as nb
                                   FROM transaction t
                                            JOIN type_transaction tt ON t.transaction_type_id = tt.id
                                   WHERE t.client_id = %s AND t.reversal_flag = 'N' AND tt.type = 'D'
                                     AND DATE_TRUNC('month', t.transaction_date) =
                                         DATE_TRUNC('month', NOW() - INTERVAL '1 month')
                                   GROUP BY tt.category
                                   """, conn, params=(client_id,))
        last_tx = pd.read_sql("""
                              SELECT t.transaction_date, t.amount, tt.category
                              FROM transaction t
                                       JOIN type_transaction tt ON t.transaction_type_id = tt.id
                              WHERE t.client_id = %s AND t.reversal_flag = 'N'
                              ORDER BY t.transaction_date DESC LIMIT 1
                              """, conn, params=(client_id,))
        factures_payees = pd.read_sql("""
                                      SELECT COUNT(*) as nb FROM transaction t
                                                                     JOIN type_transaction tt ON t.transaction_type_id = tt.id
                                      WHERE t.client_id = %s AND t.reversal_flag = 'N'
                                        AND tt.category = 'Factures & Services'
                                        AND DATE_TRUNC('month', t.transaction_date) = DATE_TRUNC('month', NOW())
                                      """, conn, params=(client_id,))
        conn.close()

        cats_ce_mois = set(ce_mois["category"].tolist()) if not ce_mois.empty else set()
        total_ce_mois = ce_mois["total"].sum() if not ce_mois.empty else 0
        total_mois_dernier = mois_dernier["total"].sum() if not mois_dernier.empty else 0
        diff = total_ce_mois - total_mois_dernier

        result["resume_mois"] = {
            "depense_ce_mois": round(float(total_ce_mois), 1),
            "depense_mois_dernier": round(float(total_mois_dernier), 1),
            "difference": round(float(diff), 1),
            "tendance": "hausse" if diff > 10 else "baisse" if diff < -10 else "stable",
            "message": (f"Ce mois : {total_ce_mois:.0f} TND vs {total_mois_dernier:.0f} TND le mois dernier"
                        if total_mois_dernier > 0 else f"Ce mois : {total_ce_mois:.0f} TND dépensés"),
        }

        # ── 3. Budget live (par catégorie ce mois vs mois dernier) ─
        md_dict = dict(zip(mois_dernier["category"], mois_dernier["total"])) if not mois_dernier.empty else {}
        budget_live = []
        for _, row in ce_mois.iterrows():
            cat, actuel = row["category"], float(row["total"])
            precedent = float(md_dict.get(cat, 0))
            diff_cat = actuel - precedent
            emoji = CAT_EMOJI.get(cat, "📊")
            nom = CAT_SIMPLE.get(cat, cat)
            pct_change = diff_cat / precedent * 100 if precedent > 0 else 100
            statut = "hausse" if pct_change > 15 else "baisse" if pct_change < -15 else "stable"
            couleur = "#FF6B6B" if statut == "hausse" else "#00E5A0" if statut == "baisse" else "#A29BFE"
            budget_live.append({
                "categorie": f"{emoji} {nom}", "ce_mois": round(actuel, 1),
                "mois_dernier": round(precedent, 1), "difference": round(diff_cat, 1),
                "statut": statut, "couleur": couleur,
                "message": (f"+{diff_cat:.0f} TND vs le mois dernier" if diff_cat > 0
                            else f"{diff_cat:.0f} TND vs le mois dernier" if diff_cat < 0
                else "Identique au mois dernier"),
            })
        for cat, total in md_dict.items():
            if cat not in cats_ce_mois and total > 10:
                emoji = CAT_EMOJI.get(cat, "📊")
                nom = CAT_SIMPLE.get(cat, cat)
                budget_live.append({
                    "categorie": f"{emoji} {nom}", "ce_mois": 0,
                    "mois_dernier": round(float(total), 1), "difference": -round(float(total), 1),
                    "statut": "baisse", "couleur": "#00E5A0",
                    "message": f"Pas encore dépensé ce mois (vs {total:.0f} TND)",
                })
        budget_live.sort(key=lambda b: abs(b["difference"]), reverse=True)
        result["budget_live"] = budget_live

        # ── 4. Conseils = peer comparison (GBM) + tendances live ───
        conseils = _peer_comparison_recommendations(features_dict, profile_id, profile_final)
        for b in budget_live:
            if b["statut"] == "hausse" and b["difference"] > 20:
                conseils.append({
                    "message": f"{b['categorie']} : {b['message']}",
                    "conseil": f"Vous avez déjà dépensé {b['ce_mois']:.0f} TND — essayez de ralentir",
                    "economie_tnd": round(b["difference"] * 0.5, 1),
                    "priorite": "haute",
                    "type": "live",
                    "categorie": b["categorie"],
                })
        if diff > 50:
            conseils.append({
                "message": f"Attention : +{diff:.0f} TND de plus que le mois dernier",
                "conseil": "Essayez de ne pas dépasser votre budget du mois dernier",
                "economie_tnd": round(diff * 0.3, 1),
                "priorite": "haute",
                "type": "tendance_global",
                "categorie": "global",
            })
        elif diff < -30:
            conseils.append({
                "message": f"Bravo ! Vous avez économisé {abs(diff):.0f} TND ce mois",
                "conseil": "Continuez comme ça",
                "economie_tnd": 0,
                "priorite": "bravo",
                "type": "felicitation",
                "categorie": "global",
            })
        # Alerte churn (du profil GBM)
        if churn_seg in ("A_RISQUE", "CRITIQUE"):
            conseils.insert(0, {
                "message": f"⚠️ Profil à risque : {churn_seg}",
                "conseil": "Réactivez l'usage de votre wallet pour éviter de perdre vos avantages",
                "economie_tnd": 0,
                "priorite": "haute",
                "type": "churn_risk",
                "categorie": "fidelite",
            })
        result["conseils"] = conseils[:6]

        # ── 5. Budget optim (forfait télécom) — depuis cache ───────
        result["budget_optim"] = _budget_optim_cache.get(client_id, [])
        result["forfait_telecom"] = result["budget_optim"]  # alias Flutter

        # ── 6. Budget targets — par profil GBM ─────────────────────
        result["budget_targets"] = _budget_targets_for_profile(client_id, profile_id, get_db_conn)

        # ── 7. Gamification SUPPRIMÉE ──────────────────────────────
        # L'incitation a été remplacée par les offres marketing
        # réellement appliquées en base (cf. ActiveOfferApplication côté Spring).
        # On expose des champs vides pour la rétro-compatibilité du client mobile
        # le temps que toutes les UI rewards soient retirées.
        result["challenges"] = []
        result["gamification"] = {}

        # ── 8. Alertes (cache Z-score + grosse dépense récente) ────
        alertes = []
        cached_alerts = _alerts_cache.get(client_id, [])
        for a in cached_alerts:
            cat = a.get("categorie", "")
            nom = CAT_SIMPLE.get(cat, cat)
            emoji = CAT_EMOJI.get(cat, "📊")
            a_copy = dict(a)
            a_copy["categorie"] = f"{emoji} {nom}" if emoji not in nom else nom
            alertes.append(a_copy)
        if not last_tx.empty:
            last_amount = float(last_tx.iloc[0]["amount"])
            if last_amount > 200:
                cat = last_tx.iloc[0]["category"]
                alertes.insert(0, {
                    "categorie": f"{CAT_EMOJI.get(cat, '📊')} {CAT_SIMPLE.get(cat, cat)}",
                    "message": f"Grosse dépense récente : {last_amount:.0f} TND",
                    "severite": "moyenne",
                    "montant": round(last_amount, 1),
                    "habituel": 0,
                    "fresh": True,
                })
        result["alertes"] = alertes[:5]

        # ── 9. Résumé ──────────────────────────────────────────────
        eco_total = sum(c.get("economie_tnd", 0) for c in conseils)
        result["resume"] = {
            "nb_conseils": len(conseils),
            "nb_challenges": 0,
            "nb_alertes": len(alertes),
            "economie_potentielle_tnd": round(eco_total, 1),
            "points": 0,
        }

    except Exception as e:
        log.error(f"❌ Erreur reco live : {e}")
        result.setdefault("conseils", [])
        result.setdefault("challenges", [])
        result.setdefault("alertes", [])
        result.setdefault("budget_live", [])
        result.setdefault("budget_optim", [])
        result.setdefault("forfait_telecom", [])
        result.setdefault("budget_targets", [])
        result["gamification"] = get_niveau(0)
        result["resume_mois"] = {"message": "Données en cours de chargement"}
        result["resume"] = {"nb_conseils": 0, "nb_challenges": 0, "nb_alertes": 0}
        result["fallback"] = True
        result["error"] = str(e)

    return result


# ─────────────────────────────────────────────────────────────────────
# Méta + Stats Module 6
# ─────────────────────────────────────────────────────────────────────
def get_meta(model_dir: str) -> Dict[str, Any]:
    """Métadonnées du module 6 (utilisé par /recommendations/meta)."""
    import json
    meta = {}
    meta_path = Path(model_dir) / "reco_meta.json"
    if meta_path.exists():
        try:
            with open(meta_path) as f:
                meta = json.load(f)
        except Exception:
            pass
    meta["classification_method"] = "GBM + 43 features + 6 profils (binôme)"
    meta["module6_available"] = cls.is_loaded()
    meta["nb_clients_cache"] = len(_features_cache)
    meta["nb_profiles"] = 6
    meta["profiles"] = {str(k): v for k, v in cls.PROFILE_NAMES.items()}
    return meta


def get_stats() -> Dict[str, Any]:
    """Statistiques agrégées (utilisé par /recommendations/stats)."""
    from collections import Counter

    if not _features_cache:
        return {"error": "Cache vide — exécuter /retrain"}

    profile_counts = Counter()
    for cid, feats in _features_cache.items():
        pid = feats.get("cluster_id", -1) if isinstance(feats, dict) else -1
        profile_counts[pid] += 1

    return {
        "total_clients_profiles": len(_features_cache),
        "total_alerts": sum(len(v) for v in _alerts_cache.values()),
        "total_budget_optim": len(_budget_optim_cache),
        "distribution_profiles": {
            str(k): {
                "nb_clients": v,
                "nom": cls.PROFILE_NAMES.get(k, f"Profil {k}"),
            }
            for k, v in profile_counts.most_common()
        },
        "classification_method": "GBM + 43 features (binôme)",
    }


def get_all_alerts(page: int = 0, size: int = 20,
                   severity: Optional[str] = None) -> Dict[str, Any]:
    """Liste paginée de toutes les alertes (admin dashboard)."""
    if not _alerts_cache:
        return {"data": [], "total": 0}

    all_alerts_list = []
    for cid, alerts in _alerts_cache.items():
        for a in alerts:
            a_copy = dict(a)
            a_copy["client_id"] = cid
            if severity is None or a_copy.get("severite") == severity:
                all_alerts_list.append(a_copy)

    total = len(all_alerts_list)
    start = page * size
    end = start + size
    return {
        "data": all_alerts_list[start:end],
        "total": total,
        "page": page,
    }
