"""
══════════════════════════════════════════════════════════════════════
SmartWallet — Classification Unifiée 
══════════════════════════════════════════════════════════════════════
MÉTHODE OFFICIELLE ET UNIQUE pour TOUT le projet IA :
  • GradientBoostingClassifier (GBM) supervisé
  • 43 features comportementales standardisées
  • 6 profils clients fixes (0–5) + 1 "Mixte" (-1)
  • RobustScaler

Cette méthode est la SEULE source de vérité pour la segmentation client
dans le projet. Aucun autre KMeans/clustering ne doit être utilisé.

Utilisée par :
  - Module 6 (recommandations, peer comparison, alertes)
  - Endpoints /predict, /batch, /clients/{id}, /profiles/*
  - Pipeline de génération des offres V5
══════════════════════════════════════════════════════════════════════
"""
from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Any, Dict, List, Optional

import joblib
import numpy as np
import pandas as pd
from scipy.special import expit

log = logging.getLogger("smartwallet.classification")

# ─────────────────────────────────────────────────────────────────────
# CONSTANTES — Le contrat de classification du binôme
# ─────────────────────────────────────────────────────────────────────
PROFILE_NAMES: Dict[int, str] = {
    0:  "Micro-Utilisateur Passif",
    1:  "Utilisateur Essentiel Stable",
    2:  "Payeur Factures Premium",
    3:  "Client Grande Dépense",
    4:  "Client en Accélération Récente",
    5:  "Client en Croissance Digitale",
    -1: "Profil Mixte (Incertain)",
}

# Les 43 features dans le bon ordre — figées par le scaler du binôme
FEATURE_COLS: List[str] = [
    "total_transactions", "total_valid_txn", "nb_active_months",
    "anciennete_jours", "maturite_jours", "freq_mensuelle",
    "taux_reversal", "regularite", "montant_total", "montant_moyen",
    "montant_median", "montant_max", "montant_std", "cv_montants",
    "nb_categories_distinctes", "nb_providers_distincts", "entropy_categories",
    "nb_factures", "nb_recharges", "nb_shopping", "nb_restaurants",
    "nb_transferts_envoyes", "nb_transferts_recus", "nb_depot_retrait_raw",
    "nb_voyages", "nb_education",
    "ratio_factures", "ratio_recharges", "ratio_shopping", "ratio_restaurants",
    "ratio_transferts", "ratio_voyages", "ratio_education",
    "log_depot_retrait", "recence_jours", "momentum_court", "momentum_long",
    "momentum_montant", "ratio_jour", "score_saisonnalite", "stabilite_mensuelle",
    "rfm_score", "loyalty_score",
]

# Stats descriptives des 6 profils (pour /profiles/summary fallback)
PROFILES_STATS: Dict[str, Dict[str, Any]] = {
    "0": {"cluster_id": 0, "profile_name": "Micro-Utilisateur Passif",
          "description": "Usage très limité, transactions rares. Profil à risque de churn ou dormant.",
          "activity_level": "Faible"},
    "1": {"cluster_id": 1, "profile_name": "Utilisateur Essentiel Stable",
          "description": "Usage quotidien simple et stable. Client fiable.",
          "activity_level": "Modérée"},
    "2": {"cluster_id": 2, "profile_name": "Payeur Factures Premium",
          "description": "Spécialisé paiement factures utilities. Fort potentiel fidélisation.",
          "activity_level": "Modérée"},
    "3": {"cluster_id": 3, "profile_name": "Client Grande Dépense",
          "description": "Transactions peu fréquentes mais montants élevés. Premium.",
          "activity_level": "Modérée"},
    "4": {"cluster_id": 4, "profile_name": "Client en Accélération Récente",
          "description": "Momentum récent >2.5×. Forte accélération 3 derniers mois.",
          "activity_level": "Élevée"},
    "5": {"cluster_id": 5, "profile_name": "Client en Croissance Digitale",
          "description": "Activité en forte croissance, explore de nouvelles catégories.",
          "activity_level": "Élevée"},
}

# Paramètres de confiance / churn / LTV (calibrés par le binôme)
CONFIDENCE_THRESHOLD = 0.6

CHURN_SCALE       = 5.0
CHURN_CENTER      = 0.22
CHURN_REC_MAX     = 90
CHURN_W_REC       = 0.4
CHURN_W_REG       = 0.3
CHURN_W_MOM       = 0.2
CHURN_W_REV       = 0.1
CHURN_HIGH_THRESH = 0.5
CHURN_CRIT_THRESH = 0.7

LTV_RATE       = 0.1
LTV_MARGE_MIN  = 0.01
LTV_MARGE_BASE = 0.02
LTV_MARGE_MAX  = 0.04

# Mappage profil → "segment historique" pour compatibilité Modules 1-5
# (qui s'attendent à Gold/Moyen/Faible)
PROFILE_TO_LEGACY_SEGMENT: Dict[int, str] = {
    0: "Faible",
    1: "Moyen",
    2: "Gold",
    3: "Gold",
    4: "Moyen",
    5: "Moyen",
    -1: "Moyen",
}

# ─────────────────────────────────────────────────────────────────────
# Chargement des modèles
# ─────────────────────────────────────────────────────────────────────
_clf = None
_scaler = None
_kmeans = None
_feat_cols: Optional[List[str]] = None


def load_classification_models(model_dir: str) -> bool:
    """
    Charge les 4 artefacts du binôme :
      - classifier.pkl  (GradientBoostingClassifier)
      - scaler.pkl      (RobustScaler 43 features)
      - kmeans.pkl      (référence centroides — optionnel)
      - features.pkl    (liste des 43 noms de features)
    """
    global _clf, _scaler, _kmeans, _feat_cols

    model_dir_p = Path(model_dir)
    if not model_dir_p.exists():
        log.warning(f"⚠️ MODEL_DIR introuvable : {model_dir_p}")
        return False

    try:
        clf_p   = model_dir_p / "classifier.pkl"
        sc_p    = model_dir_p / "scaler.pkl"
        km_p    = model_dir_p / "kmeans.pkl"
        feat_p  = model_dir_p / "features.pkl"

        if clf_p.exists():
            _clf = joblib.load(clf_p)
            log.info(f"  ✓ GBM classifier chargé ({len(_clf.classes_)} classes)")
        if sc_p.exists():
            _scaler = joblib.load(sc_p)
            log.info(f"  ✓ Scaler chargé ({_scaler.n_features_in_} features)")
        if km_p.exists():
            _kmeans = joblib.load(km_p)
            log.info(f"  ✓ KMeans centroides chargés ({_kmeans.n_clusters} clusters)")
        if feat_p.exists():
            _feat_cols = joblib.load(feat_p)
            log.info(f"  ✓ Features chargées ({len(_feat_cols)} vars)")
        else:
            _feat_cols = FEATURE_COLS

        if _clf is None or _scaler is None:
            log.warning(f"⚠️ Modèles incomplets — classifier ou scaler manquant")
            return False

        return True
    except Exception as e:
        log.error(f"❌ Erreur chargement classification : {e}")
        return False


def is_loaded() -> bool:
    return _clf is not None and _scaler is not None


def get_feature_cols() -> List[str]:
    return _feat_cols if _feat_cols else FEATURE_COLS


# ─────────────────────────────────────────────────────────────────────
# Construction des 43 features à partir de transactions PostgreSQL
# ─────────────────────────────────────────────────────────────────────
def build_features_from_transactions(
    tx: pd.DataFrame,
    date_ref: Optional[pd.Timestamp] = None,
) -> pd.DataFrame:
    """
    Reconstruit les 43 features comportementales à partir d'un DataFrame transactions.

    Colonnes attendues dans tx :
      client_id, transaction_date, amount, reversal_flag, category, provider_name (opt.)

    Sortie :
      DataFrame indexé par client_id avec les 43 colonnes FEATURE_COLS
    """
    if "td" not in tx.columns:
        tx = tx.copy()
        tx["td"] = pd.to_datetime(tx["transaction_date"])

    if date_ref is None:
        date_ref = tx["td"].max()

    n_months = max(1, (date_ref - tx["td"].min()).days // 30)
    rc = date_ref - pd.DateOffset(months=3)
    rm = date_ref - pd.DateOffset(months=6)
    df_3m = tx[tx["td"] >= rc]
    df_6m = tx[(tx["td"] >= rm) & (tx["td"] < rc)]

    categories_cles = [
        "Factures & Services", "Recharge Telephonique", "Shopping & Paiements",
        "Restaurants & Livraison", "Transferts Envoyes", "Transferts Recus",
        "Depot & Retrait", "Voyages & Reservations", "Education & Institutions",
    ]
    rename_cat = {
        "Factures & Services": "nb_factures",
        "Recharge Telephonique": "nb_recharges",
        "Shopping & Paiements": "nb_shopping",
        "Restaurants & Livraison": "nb_restaurants",
        "Transferts Envoyes": "nb_transferts_envoyes",
        "Transferts Recus": "nb_transferts_recus",
        "Depot & Retrait": "nb_depot_retrait_raw",
        "Voyages & Reservations": "nb_voyages",
        "Education & Institutions": "nb_education",
    }

    if "year_month" not in tx.columns:
        tx["year_month"] = tx["td"].dt.to_period("M").astype(str)
    if "year_quarter" not in tx.columns:
        tx["year_quarter"] = tx["td"].dt.to_period("Q").astype(str)
    if "hour" not in tx.columns:
        tx["hour"] = tx["td"].dt.hour
    if "provider_name" not in tx.columns:
        tx["provider_name"] = "default"

    # ── Activité ─────────────────────────────────────────────────────
    f_act = tx.groupby("client_id").agg(
        total_transactions=("client_id", "count"),
        total_valid_txn=("reversal_flag", lambda x: (x == "N").sum()),
        total_reversals=("reversal_flag", lambda x: (x == "Y").sum()),
        nb_active_months=("year_month", "nunique"),
        date_first_txn=("td", "min"),
        date_last_txn=("td", "max"),
    ).reset_index()
    f_act["anciennete_jours"] = (f_act["date_last_txn"] - f_act["date_first_txn"]).dt.days
    f_act["maturite_jours"] = (date_ref - f_act["date_first_txn"]).dt.days
    f_act["freq_mensuelle"] = f_act["total_transactions"] / max(n_months, 1)
    f_act["taux_reversal"] = f_act["total_reversals"] / f_act["total_transactions"].replace(0, 1)
    f_act["regularite"] = (f_act["nb_active_months"] / max(n_months, 1)).clip(0, 1)

    # ── Financier ────────────────────────────────────────────────────
    f_fin = tx.groupby("client_id")["amount"].agg(
        montant_total="sum", montant_moyen="mean", montant_median="median",
        montant_max="max", montant_std="std").reset_index()
    f_fin["montant_std"] = f_fin["montant_std"].fillna(0)
    f_fin["cv_montants"] = (f_fin["montant_std"] / f_fin["montant_moyen"].replace(0, 1)).clip(0, 10)

    # ── Diversité ────────────────────────────────────────────────────
    f_div = tx.groupby("client_id").agg(
        nb_categories_distinctes=("category", "nunique"),
        nb_providers_distincts=("provider_name", "nunique")).reset_index()

    def shannon(s):
        p = s.value_counts(normalize=True)
        return float(-(p * np.log2(p + 1e-10)).sum())

    f_ent = tx.groupby("client_id")["category"].apply(shannon).reset_index()
    f_ent.columns = ["client_id", "entropy_categories"]

    # ── Catégories (nb) + ratios ─────────────────────────────────────
    cat_dum = pd.get_dummies(tx["category"]).reindex(columns=categories_cles, fill_value=0)
    cat_dum["client_id"] = tx["client_id"].values
    f_cat = cat_dum.groupby("client_id")[categories_cles].sum().reset_index().rename(columns=rename_cat)

    base = f_act[["client_id", "total_transactions"]].merge(f_cat, on="client_id", how="left").fillna(0)
    eps = 0.001
    f_ratios = pd.DataFrame({"client_id": base["client_id"]})
    for raw, ratio in [
        ("nb_factures", "ratio_factures"),
        ("nb_recharges", "ratio_recharges"),
        ("nb_shopping", "ratio_shopping"),
        ("nb_restaurants", "ratio_restaurants"),
        ("nb_transferts_envoyes", "ratio_transferts"),
        ("nb_voyages", "ratio_voyages"),
        ("nb_education", "ratio_education"),
    ]:
        if raw in base.columns:
            f_ratios[ratio] = (base[raw] / (base["total_transactions"] + eps)).clip(0, 1)
    if "nb_depot_retrait_raw" in base.columns:
        f_ratios["log_depot_retrait"] = np.log1p(base["nb_depot_retrait_raw"])

    # ── Récence ──────────────────────────────────────────────────────
    f_rec = tx.groupby("client_id")["td"].max().reset_index()
    f_rec.columns = ["client_id", "last_txn_date"]
    f_rec["recence_jours"] = (date_ref - f_rec["last_txn_date"]).dt.days

    # ── Momentum / temporel ──────────────────────────────────────────
    cnt_3m = df_3m.groupby("client_id").size().to_dict()
    amt_3m = df_3m.groupby("client_id")["amount"].sum().to_dict()
    cnt_6m = df_6m.groupby("client_id").size().to_dict()
    cnt_all = tx.groupby("client_id").size().to_dict()
    amt_all = tx.groupby("client_id")["amount"].sum().to_dict()

    quarterly = tx.groupby(["client_id", "year_quarter"]).size().reset_index(name="q_cnt")
    q_var = quarterly.groupby("client_id")["q_cnt"].std().fillna(0)
    q_mean = quarterly.groupby("client_id")["q_cnt"].mean().replace(0, 1)
    seasonality = (q_var / q_mean).fillna(0).to_dict()

    monthly_cnt = tx.groupby(["client_id", "year_month"]).size().reset_index(name="m_cnt")
    m_std = monthly_cnt.groupby("client_id")["m_cnt"].std().fillna(0)
    m_mean = monthly_cnt.groupby("client_id")["m_cnt"].mean().replace(0, 1)
    stability = (1 - (m_std / m_mean).clip(0, 2) / 2).fillna(0).to_dict()

    df_h = tx.copy()
    df_h["is_day"] = df_h["hour"].between(7, 20).astype(int)
    day_ratio = df_h.groupby("client_id")["is_day"].mean().fillna(0.5).to_dict()

    records = []
    for cid in tx["client_id"].unique():
        n_tot = cnt_all.get(cid, 1)
        n_3 = cnt_3m.get(cid, 0)
        n_6 = cnt_6m.get(cid, 0)
        a_3 = amt_3m.get(cid, 0)
        a_all = amt_all.get(cid, 1)
        avg_m = n_tot / max(n_months, 1)
        avg_3 = n_3 / max(3, 1)
        avg_6 = n_6 / max(3, 1)
        records.append({
            "client_id": cid,
            "momentum_court": min(avg_3 / max(avg_m, 0.001), 5.0),
            "momentum_long": min((avg_3 + avg_6) / max(avg_m * 2, 0.001), 5.0),
            "momentum_montant": min(a_3 / max(a_all / n_months * 3, 0.001), 5.0),
            "ratio_jour": day_ratio.get(cid, 0.5),
            "score_saisonnalite": min(seasonality.get(cid, 0), 2.0),
            "stabilite_mensuelle": stability.get(cid, 0.5),
        })
    f_tempo = pd.DataFrame(records)

    # ── RFM + Loyalty ────────────────────────────────────────────────
    merged = f_act[["client_id", "total_transactions", "nb_active_months", "freq_mensuelle"]].merge(
        f_fin[["client_id", "montant_total", "montant_moyen"]], on="client_id"
    ).merge(
        f_rec[["client_id", "recence_jours"]], on="client_id"
    )
    max_r = merged["recence_jours"].max() + 1
    r_score = 1 - merged["recence_jours"] / max_r
    q95_freq = merged["freq_mensuelle"].quantile(0.95) or 1
    q95_mt = merged["montant_total"].quantile(0.95) or 1
    f_score = (merged["freq_mensuelle"] / q95_freq).clip(0, 1)
    m_score = (np.log1p(merged["montant_total"]) / max(np.log1p(q95_mt), 1)).clip(0, 1)
    merged["rfm_score"] = (r_score + f_score + m_score) / 3
    merged["loyalty_score"] = (merged["nb_active_months"] / max(n_months, 1)).clip(0, 1)
    f_scores = merged[["client_id", "rfm_score", "loyalty_score"]]

    # ── Fusion finale ────────────────────────────────────────────────
    features = f_act[["client_id", "total_transactions", "total_valid_txn", "nb_active_months",
                      "anciennete_jours", "maturite_jours", "freq_mensuelle",
                      "taux_reversal", "regularite"]]
    for f in [f_fin.drop(columns=["client_id"]).reset_index(drop=True) if False else f_fin,
              f_div, f_ent, f_cat, f_ratios,
              f_rec[["client_id", "recence_jours"]], f_tempo, f_scores]:
        features = features.merge(f, on="client_id", how="left")

    features = features.fillna(0)
    features = features[features["total_transactions"] >= 2]

    # Garantir que toutes les colonnes des 43 features existent
    cols = get_feature_cols()
    for c in cols:
        if c not in features.columns:
            features[c] = 0.0

    return features.set_index("client_id")


# ─────────────────────────────────────────────────────────────────────
# Vectorisation
# ─────────────────────────────────────────────────────────────────────
def _vec(features_dict: Dict[str, float]) -> np.ndarray:
    cols = get_feature_cols()
    return np.array(
        [float(features_dict.get(c, 0.0) or 0.0) for c in cols]
    ).reshape(1, -1)


# ─────────────────────────────────────────────────────────────────────
# Churn + LTV (formule binôme)
# ─────────────────────────────────────────────────────────────────────
def compute_churn(feats: Dict[str, float]) -> float:
    s_rec = min(feats.get("recence_jours", 45) / CHURN_REC_MAX, 1.0)
    s_reg = 1.0 - min(feats.get("regularite", 0.5), 1.0)
    s_mom = 1.0 - min(feats.get("momentum_court", 1.0), 2.0) / 2.0
    s_rev = min(feats.get("taux_reversal", 0.0), 0.3) / 0.3
    raw = (CHURN_W_REC * s_rec + CHURN_W_REG * s_reg
           + CHURN_W_MOM * s_mom + CHURN_W_REV * s_rev)
    return round(float(expit(CHURN_SCALE * (raw - CHURN_CENTER))), 4)


def churn_segment(score: float) -> str:
    if score >= CHURN_CRIT_THRESH:
        return "CRITIQUE"
    if score >= CHURN_HIGH_THRESH:
        return "A_RISQUE"
    if score >= 0.30:
        return "SURVEILLANCE"
    return "SAIN"


def compute_ltv(arpu: float, churn: float, horizon: int = 12,
                scenario: str = "base") -> float:
    r_m = (1 + LTV_RATE) ** (1 / 12) - 1
    if scenario == "optimiste":
        marge = LTV_MARGE_MAX
    elif scenario == "pessimiste":
        marge = LTV_MARGE_MIN
    else:
        marge = LTV_MARGE_BASE
    m = arpu * marge
    p_ret = max(0.01, 1.0 - churn)
    ltv = 0.0
    p = 1.0
    for t in range(1, horizon + 1):
        p *= p_ret
        ltv += m * p / ((1 + r_m) ** t)
    return round(max(0.0, ltv), 2)


# ─────────────────────────────────────────────────────────────────────
# API publique de classification — UTILISÉE PAR TOUS LES MODULES
# ─────────────────────────────────────────────────────────────────────
def classify_client(features_dict: Dict[str, float],
                    client_id: str = "?") -> Dict[str, Any]:
    """
    POINT D'ENTRÉE UNIQUE pour classifier un client.

    Retourne un dict avec :
      - cluster_id, profile_name, profile_final
      - confidence, is_mixte
      - churn_score_30j, churn_segment
      - ltv_12m_base, ltv_12m_optimiste, ltv_12m_pessimiste
      - hazard_rate, arpu_mensuel
      - all_probabilities
      - legacy_segment (Gold/Moyen/Faible pour compat Modules 1-5)
    """
    if not is_loaded():
        return {
            "client_id": client_id,
            "cluster_id": -1,
            "profile_name": PROFILE_NAMES[-1],
            "profile_final": PROFILE_NAMES[-1],
            "confidence": 0.0,
            "is_mixte": True,
            "churn_score_30j": 0.5,
            "churn_segment": "SURVEILLANCE",
            "ltv_12m_base": 0.0,
            "ltv_12m_optimiste": 0.0,
            "ltv_12m_pessimiste": 0.0,
            "hazard_rate": 0.0,
            "arpu_mensuel": 0.0,
            "all_probabilities": {},
            "legacy_segment": "Moyen",
            "source": "fallback_no_model",
        }

    X = _vec(features_dict)
    X = np.nan_to_num(X, nan=0.0)
    Xs = _scaler.transform(X)
    cid_pred = int(_clf.predict(Xs)[0])
    probas = _clf.predict_proba(Xs)[0]
    classes = [int(c) for c in _clf.classes_]
    conf = float(probas[classes.index(cid_pred)]) if cid_pred in classes else 0.0

    is_mixte = conf < CONFIDENCE_THRESHOLD
    effective_cid = -1 if is_mixte else cid_pred
    profile_name = PROFILE_NAMES.get(cid_pred, "?")
    profile_final = PROFILE_NAMES[-1] if is_mixte else profile_name

    churn = compute_churn(features_dict)
    seg = churn_segment(churn)
    freq = float(features_dict.get("freq_mensuelle", 0))
    amt = float(features_dict.get("montant_median",
                                  features_dict.get("montant_moyen", 0)))
    arpu = freq * amt

    return {
        "client_id": client_id,
        "cluster_id": effective_cid,
        "profile_name": profile_name,
        "profile_final": profile_final,
        "confidence": round(conf, 4),
        "is_mixte": is_mixte,
        "churn_score_30j": churn,
        "churn_segment": seg,
        "ltv_12m_base": compute_ltv(arpu, churn, 12, "base"),
        "ltv_12m_optimiste": compute_ltv(arpu, churn, 12, "optimiste"),
        "ltv_12m_pessimiste": compute_ltv(arpu, churn, 12, "pessimiste"),
        "hazard_rate": round(-np.log(max(0.001, 1 - churn)), 4),
        "arpu_mensuel": round(arpu, 2),
        "all_probabilities": {
            PROFILE_NAMES.get(int(i), f"P{i}"): round(float(p), 4)
            for i, p in zip(_clf.classes_, probas)
        },
        "legacy_segment": PROFILE_TO_LEGACY_SEGMENT.get(effective_cid, "Moyen"),
        "source": "gbm_binome_v1",
    }


def classify_from_db(client_id: str, get_db_conn) -> Dict[str, Any]:
    """
    Construit les 43 features depuis PostgreSQL pour UN client et le classifie.

    `get_db_conn` est un callable qui retourne une connexion psycopg2.
    """
    if not is_loaded():
        return classify_client({}, client_id)

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

        if tx.empty:
            log.warning(f"⚠️ Aucune transaction pour client {client_id}")
            return classify_client({}, client_id)

        feats_df = build_features_from_transactions(tx)
        if client_id not in feats_df.index:
            return classify_client({}, client_id)

        feats_dict = feats_df.loc[client_id].to_dict()
        return classify_client(feats_dict, client_id)
    except Exception as e:
        log.error(f"❌ Erreur classify_from_db({client_id}) : {e}")
        return classify_client({}, client_id)
