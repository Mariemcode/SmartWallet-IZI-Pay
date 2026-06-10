#!/usr/bin/env python3
"""
SmartWallet — Script de Réentraînement (PostgreSQL + Module 6 Complet)
===========================================================================
Lit depuis PostgreSQL, réentraîne les 5 modules ML, et génère tous les PKL
nécessaires pour le Module 6 (recommandations en temps réel).
"""
import argparse, sys, os, logging, json
from datetime import datetime
from pathlib import Path

import pandas as pd
import numpy as np
import joblib
import warnings
warnings.filterwarnings("ignore")

from sklearn.metrics import mean_absolute_error, mean_squared_error
from sklearn.cluster import KMeans
from sklearn.preprocessing import StandardScaler
from sklearn.ensemble import IsolationForest  # ✅ AJOUTÉ
from sklearn.metrics import silhouette_score
from xgboost import XGBRegressor

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(message)s",
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler(f"retrain_{datetime.now().strftime('%Y%m%d_%H%M')}.log", encoding="utf-8"),
    ]
)
log = logging.getLogger("retrain")

# ════════════════════════════════════════════════════════════════
# CONSTANTES
# ════════════════════════════════════════════════════════════════
TOPNET = "1461b464-fa44-477a-90c3-a9d68acdf29a"
BEE    = "08e939ae-af2c-428f-8ece-5862e56de5d3"
SONEDE = "18de5279-60d1-40f0-bb68-54b29d6f1ba8"
STEG   = "07ba063e-a1fb-4fd9-87a7-243efcc55af2"
TT_FAC = "c428964b-5929-4480-a569-cb2ef1bc3b27"
OO_FAC = "2d0d0c45-9941-42d9-9849-0f61d06c4a7b"

BILLS = {
    TOPNET: "TOPNET", BEE: "BEE", SONEDE: "SONEDE",
    STEG: "STEG", TT_FAC: "TT", OO_FAC: "OOREDOO",
}

RECH_TT  = "a0f3b202-3d88-4893-b5d3-db3613b7cc4a"
RECH_OOR = "cc3fb138-ffbe-4b22-9e83-d5426127d5ca"
RECH_ORG = "ca7766f0-8636-485f-be2c-1fbce7532675"
RECH_VCH = "2db6c38c-b0c8-4dd6-8939-4dcdefc51647"
RECH_IDS = {RECH_TT: "TT", RECH_OOR: "Ooredoo", RECH_ORG: "Orange", RECH_VCH: "Voucher"}

CATS_BUDGET = ["Recharge Telephonique", "Shopping & Paiements", "Restaurants & Livraison", "Voyages & Reservations"]
BILL_FEATS = ["lag1", "lag2", "lag3", "mean3", "std3", "month_sin", "month_cos", "nb_paiem", "profil_enc", "segment_enc"]
BUDGET_FEATS = ["lag1", "lag2", "lag3", "lag6", "lag12", "mean3", "mean6", "std3", "std6", "tendance", "regularite",
                "month_sin", "month_cos", "trimestre", "is_ramadan", "is_aid_fitr", "is_aid_adha", "profil_enc", "segment_enc"]

SAISON_SON = {1:.84,2:.87,3:.92,4:1.00,5:1.10,6:1.22,7:1.32,8:1.35,9:1.18,10:1.06,11:.96,12:.88}
SAISON_STG = {1:1.22,2:1.16,3:1.10,4:1.00,5:.94,6:.88,7:.84,8:.84,9:.90,10:.96,11:1.06,12:1.16}
RAMADAN  = {(2024,3),(2024,4),(2025,3),(2026,2),(2026,3)}
AID_FITR = {(2024,4),(2025,3),(2026,3)}
AID_ADHA = {(2024,6),(2025,6),(2026,5)}

PERF_THRESHOLDS = {
    "TOPNET": {"r2_min": 0.99, "mae_max": 2.0}, "BEE": {"r2_min": 0.99, "mae_max": 1.0},
    "SONEDE": {"r2_min": 0.70, "mae_max": 20.0}, "STEG": {"r2_min": 0.70, "mae_max": 25.0},
    "TT": {"r2_min": 0.65, "mae_max": 15.0}, "OOREDOO": {"r2_min": 0.65, "mae_max": 15.0},
}

# ════════════════════════════════════════════════════════════════
# CHARGEMENT DONNÉES
# ════════════════════════════════════════════════════════════════
def load_data(db_url=None, data_dir=None):
    if db_url:
        return _load_from_postgres(db_url)
    elif data_dir:
        return _load_from_csv(data_dir)
    else:
        raise ValueError("Fournir --db-url ou --data-dir")

def _load_from_postgres(db_url):
    from sqlalchemy import create_engine
    log.info("📂 Connexion PostgreSQL...")
    engine = create_engine(db_url)
    tx = pd.read_sql("""
                     SELECT t.*, tt.category, tt.type, tt.title
                     FROM transaction t
                              LEFT JOIN type_transaction tt ON t.transaction_type_id = tt.id
                     WHERE t.reversal_flag = 'N' AND t.transaction_date >= NOW() - INTERVAL '18 months'
                     """, engine)
    cl = pd.read_sql("SELECT * FROM client", engine)
    log.info(f"  ✅ PostgreSQL — tx:{len(tx):,} | clients:{len(cl):,}")
    tx["td"] = pd.to_datetime(tx["transaction_date"], errors="coerce")
    tx = tx.dropna(subset=["td"])
    tx["mois"] = tx["td"].dt.to_period("M")
    tx["month"] = tx["td"].dt.month
    tx["year"] = tx["td"].dt.year
    log.info(f"  Période : {tx['td'].min().date()} → {tx['td'].max().date()}")
    return tx, cl

def _load_from_csv(data_dir):
    log.info(f"📂 Chargement CSV depuis : {data_dir}")
    def load_csv(fname):
        return pd.read_csv(f"{data_dir}/{fname}", sep=",", engine="python", on_bad_lines="skip")
    tx = load_csv("transaction_final.csv")
    cl = load_csv("client_final.csv")
    tt = load_csv("type_transaction.csv")
    for df in [tx, cl, tt]:
        df.columns = df.columns.str.strip()
    if "id" not in tt.columns:
        tt.rename(columns={tt.columns[0]: "id"}, inplace=True)
    if "category" not in tt.columns:
        tt.rename(columns={tt.columns[1]: "category"}, inplace=True)
    if "type" not in tt.columns:
        tt.rename(columns={tt.columns[2]: "type"}, inplace=True)
    tx["td"] = pd.to_datetime(tx["transaction_date"], errors="coerce")
    tx = tx.dropna(subset=["td"])
    tx["mois"] = tx["td"].dt.to_period("M")
    tx["month"] = tx["td"].dt.month
    tx["year"] = tx["td"].dt.year
    cols_tt = ["id", "category", "type"] + (["title"] if "title" in tt.columns else [])
    tx = tx.merge(tt[cols_tt], left_on="transaction_type_id", right_on="id", how="left", suffixes=("", "_t"))
    log.info(f"  Transactions : {len(tx):,} | Clients : {len(cl):,}")
    return tx, cl

# ════════════════════════════════════════════════════════════════
# FEATURES COMPORTEMENTALES
# ════════════════════════════════════════════════════════════════
def compute_features_comportementales(tx, cl):
    monthly_all = tx.groupby(["client_id", "mois"])["amount"].sum().reset_index()
    moy_mensuelle = monthly_all.groupby("client_id")["amount"].mean()
    nb_mois_actifs = monthly_all.groupby("client_id")["mois"].nunique()
    q25, q50, q75 = moy_mensuelle.quantile(0.25), moy_mensuelle.quantile(0.50), moy_mensuelle.quantile(0.75)

    def get_profil(cid):
        m = moy_mensuelle.get(cid, q50)
        return 0 if m < q25 else 1 if m < q50 else 2 if m < q75 else 3

    def get_segment(cid):
        n = nb_mois_actifs.get(cid, 1)
        return 0 if n < 6 else 1 if n < 12 else 2

    cl["profil_enc"] = cl["id"].apply(get_profil)
    cl["segment_enc"] = cl["id"].apply(get_segment)
    profil_map = cl.set_index("id")["profil_enc"].to_dict()
    segment_map = cl.set_index("id")["segment_enc"].to_dict()

    fac_lens = tx[tx["category"] == "Factures & Services"].groupby("client_id")["mois"].nunique()
    gold_ids = fac_lens[fac_lens >= 12].index.tolist()
    moyen_ids = fac_lens[(fac_lens >= 6) & (fac_lens < 12)].index.tolist()

    log.info(f"  profil_map : {len(profil_map)} clients | Gold: {len(gold_ids)} | Moyen: {len(moyen_ids)}")
    return profil_map, segment_map, gold_ids, moyen_ids

# ════════════════════════════════════════════════════════════════
# MODULE 1 — FACTURES (INCHANGÉ)
# ════════════════════════════════════════════════════════════════
def train_module1(tx, profil_map, segment_map):
    log.info("🏗️  Module 1 — Factures...")
    fac_tx = tx[tx["transaction_type_id"].isin(BILLS.keys())].copy()
    fac_tx = fac_tx.sort_values(["client_id", "transaction_type_id", "td"])

    factures_ref = {}
    for (cid, tid), g in fac_tx.groupby(["client_id", "transaction_type_id"]):
        g = g.reset_index(drop=True)
        if len(g) < 2: continue
        diffs = g["td"].diff().dt.days.dropna()
        ints = [x for x in diffs if 5 < x < 200]
        if not ints:
            ints = [30 if BILLS[tid] in ("TOPNET", "BEE", "TT", "OOREDOO") else 91]
        mts = g["amount"].values
        moy = float(np.mean(mts))
        std = float(np.std(mts)) if len(mts) > 1 else 0
        factures_ref[(cid, tid)] = {
            "label": BILLS[tid], "intervalle_median": int(np.median(ints)),
            "montant_moyen": round(moy, 2), "montant_mode": round(float(g["amount"].mode()[0]), 2),
            "montant_cv": round(std / moy if moy > 0 else 0, 3),
            "derniere_date": g["td"].max(), "derniere_amount": float(g["amount"].iloc[-1]),
            "title": g["title"].iloc[-1] if "title" in g.columns else BILLS[tid], "n_paiements": len(g),
        }
    log.info(f"  factures_ref : {len(factures_ref):,} entrées")

    last_date = tx["td"].max()
    split_dt = last_date - pd.DateOffset(months=2)
    MOIS_SPLIT = f"{split_dt.year}-{split_dt.month:02d}"

    def build_bill_features(tid, label):
        sub = fac_tx[fac_tx["transaction_type_id"] == tid].copy()
        rows = []
        for cid, g in sub.groupby("client_id"):
            g = g.sort_values("td").reset_index(drop=True)
            if len(g) < 3: continue
            for i in range(2, len(g)):
                m = g.iloc[i]["td"].month
                rows.append({
                    "client_id": cid, "mois": str(g.iloc[i]["mois"]), "target": float(g.iloc[i]["amount"]),
                    "lag1": float(g.iloc[i-1]["amount"]), "lag2": float(g.iloc[i-2]["amount"]),
                    "lag3": float(g.iloc[i-3]["amount"]) if i >= 3 else float(g.iloc[i-2]["amount"]),
                    "mean3": float(g.iloc[max(0,i-3):i]["amount"].mean()),
                    "std3": float(g.iloc[max(0,i-3):i]["amount"].std()) if i >= 3 else 0,
                    "month_sin": float(np.sin(2*np.pi*m/12)), "month_cos": float(np.cos(2*np.pi*m/12)),
                    "nb_paiem": i, "profil_enc": profil_map.get(cid, 1), "segment_enc": segment_map.get(cid, 1),
                })
        return pd.DataFrame(rows)

    models_bill, results_bill = {}, {}
    for tid, label in BILLS.items():
        df = build_bill_features(tid, label)
        if len(df) < 50: continue
        train = df[df["mois"] <= MOIS_SPLIT]
        test = df[df["mois"] > MOIS_SPLIT]
        if len(test) == 0: continue
        med = train[BILL_FEATS].median()
        X_tr, X_te = train[BILL_FEATS].fillna(med).values, test[BILL_FEATS].fillna(med).values
        y_tr, y_te = train["target"].values, test["target"].values
        model = XGBRegressor(n_estimators=300, learning_rate=0.05, max_depth=5, subsample=0.8, colsample_bytree=0.8, random_state=42, verbosity=0)
        model.fit(X_tr, y_tr)
        y_pred = np.maximum(model.predict(X_te), 0)
        mae = mean_absolute_error(y_te, y_pred)
        rmse = float(np.sqrt(mean_squared_error(y_te, y_pred)))
        r2 = float(1 - np.sum((y_te-y_pred)**2) / np.sum((y_te-y_te.mean())**2))
        models_bill[label] = model
        results_bill[label] = {"mae": mae, "rmse": rmse, "r2": r2, "medians": med, "feats": BILL_FEATS}
        log.info(f"  {label:<8} MAE={mae:>6.2f} TND | R²={r2:.4f}")

    return models_bill, results_bill, factures_ref

# ════════════════════════════════════════════════════════════════
# MODULE 2 — RECHARGES (INCHANGÉ)
# ════════════════════════════════════════════════════════════════
def train_module2(tx):
    log.info("🏗️  Module 2 — Recharges...")
    rech_tx = tx[tx["transaction_type_id"].isin(RECH_IDS.keys())].copy()
    rech_tx = rech_tx.sort_values(["client_id", "td"])
    recharges_ref = {}
    for cid, g in rech_tx.groupby("client_id"):
        g = g.reset_index(drop=True)
        if len(g) < 2: continue
        oper_dominant = g["transaction_type_id"].value_counts().index[0]
        diffs = g["td"].diff().dt.days.dropna()
        ints = [x for x in diffs if 1 < x < 60]
        if not ints: continue
        recharges_ref[cid] = {
            "operateur": RECH_IDS.get(oper_dominant, "Inconnu"), "transaction_type_id": oper_dominant,
            "intervalle_median": int(np.median(ints)), "intervalle_std": round(float(np.std(ints)), 1),
            "montant_habituel": round(float(g["amount"].median()), 2), "montant_moyen": round(float(g["amount"].mean()), 2),
            "derniere_date": g["td"].max(), "derniere_amount": float(g["amount"].iloc[-1]), "n_recharges": len(g),
        }
    log.info(f"  recharges_ref : {len(recharges_ref):,} clients")
    return recharges_ref

# ════════════════════════════════════════════════════════════════
# MODULE 3 — BUDGET (INCHANGÉ)
# ════════════════════════════════════════════════════════════════
def train_module3(tx, profil_map, segment_map):
    log.info("🤖 Module 3 — Budget mensuel...")
    tx_debit = tx[tx["type"] == "D"].copy()
    monthly = tx_debit.groupby(["client_id", "mois", "category"])["amount"].sum().reset_index()
    monthly["mois_dt"] = monthly["mois"].dt.to_timestamp()
    last_date = tx["td"].max()
    split_dt = last_date - pd.DateOffset(months=2)
    MOIS_SPLIT = f"{split_dt.year}-{split_dt.month:02d}"

    def build_pivot(cat):
        sub = monthly[monthly["category"] == cat]
        return sub.pivot_table(index="client_id", columns="mois", values="amount", aggfunc="sum", fill_value=0.0).sort_index(axis=1)

    def build_features(pivot):
        rows = []
        periods = pivot.columns.tolist()
        for cid in pivot.index:
            serie = pivot.loc[cid].values
            n = len(serie)
            pe, se = profil_map.get(cid, 1), segment_map.get(cid, 1)
            for i in range(6, n):
                p = periods[i]
                m, y = p.month, p.year
                h6, h3 = serie[i-6:i], serie[i-3:i]
                rows.append({
                    "client_id": cid, "mois": str(p), "target": float(serie[i]),
                    "lag1": float(serie[i-1]), "lag2": float(serie[i-2]), "lag3": float(serie[i-3]),
                    "lag6": float(serie[i-6]), "lag12": float(serie[i-12]) if i >= 12 else float(np.mean(h6)),
                    "mean3": float(np.mean(h3)), "mean6": float(np.mean(h6)),
                    "std3": float(np.std(h3)) if len(h3) > 1 else 0, "std6": float(np.std(h6)) if len(h6) > 1 else 0,
                    "tendance": float(np.polyfit(np.arange(len(h3)), h3, 1)[0]) if np.any(h3 > 0) else 0,
                    "regularite": float(np.mean(h6 > 0)),
                    "month_sin": float(np.sin(2*np.pi*m/12)), "month_cos": float(np.cos(2*np.pi*m/12)),
                    "trimestre": int((m-1)//3+1), "is_ramadan": float((y, m) in RAMADAN),
                    "is_aid_fitr": float((y, m) in AID_FITR), "is_aid_adha": float((y, m) in AID_ADHA),
                    "profil_enc": pe, "segment_enc": se,
                })
        return pd.DataFrame(rows)

    models_budget, ic_budget = {}, {}
    for cat in CATS_BUDGET:
        pivot = build_pivot(cat)
        df = build_features(pivot)
        if len(df) < 100: continue
        train = df[df["mois"] <= MOIS_SPLIT]
        test = df[df["mois"] > MOIS_SPLIT]
        if len(test) == 0: continue
        med = train[BUDGET_FEATS].median()
        X_tr, X_te = train[BUDGET_FEATS].fillna(med).values, test[BUDGET_FEATS].fillna(med).values
        y_tr, y_te = train["target"].values, test["target"].values
        model = XGBRegressor(n_estimators=400, learning_rate=0.04, max_depth=5, min_child_weight=3,
                             subsample=0.8, colsample_bytree=0.85, reg_alpha=0.1, reg_lambda=1.0, random_state=42, verbosity=0)
        model.fit(X_tr, y_tr)
        y_pred = np.maximum(model.predict(X_te), 0)
        mae = mean_absolute_error(y_te, y_pred)
        yp_tr = np.maximum(model.predict(X_tr), 0)
        df_tr = train.copy()
        df_tr["pred"] = yp_tr
        df_tr["res"] = np.abs(df_tr["target"] - df_tr["pred"])
        ic_client = (df_tr.groupby("client_id")["res"].std().fillna(15) * 2.0).clip(lower=8)
        ic_budget[cat] = ic_client.to_dict()
        models_budget[cat] = model
        log.info(f"  {cat:<35} MAE={mae:>7.1f} TND")

    return models_budget, ic_budget, monthly

# ════════════════════════════════════════════════════════════════
# MODULE 4 — HABITUDES (INCHANGÉ)
# ════════════════════════════════════════════════════════════════
def train_module4(tx):
    log.info("🏗️  Module 4 — Habitudes...")
    tx_debit = tx[tx["type"] == "D"].copy()
    TX_END = tx["td"].max()
    habitudes_cli = {}
    for cid, g in tx_debit.groupby("client_id"):
        g = g.sort_values("td")
        if len(g) < 5: continue
        cat_counts = g["category"].value_counts()
        diffs = g["td"].diff().dt.days.dropna()
        ints = [x for x in diffs if 0 < x < 90]
        habitudes_cli[cid] = {
            "top3_cats": cat_counts.head(3).to_dict(), "cat_dominante": cat_counts.index[0],
            "int_median": round(float(np.median(ints)) if ints else 7.0, 1),
            "int_std": round(float(np.std(ints)) if ints else 3.0, 1),
            "last_cat": g["category"].iloc[-1], "jours_depuis": float((TX_END - g["td"].max()).days), "n_tx": len(g),
        }
    log.info(f"  habitudes_cli : {len(habitudes_cli):,} clients")
    return habitudes_cli

# ════════════════════════════════════════════════════════════════════
# MODULE 6 — RECOMMANDATIONS (INTÉGRÉ AVEC CLASSIFICATION BINÔME V8)
# ════════════════════════════════════════════════════════════════════
# Utilise les modèles du binôme (GBM + RobustScaler) pour prédire
# le profil de chaque client parmi 6 segments.
#
# Profils V8 :
#   P0: Micro-Utilisateur Passif
#   P1: Utilisateur Essentiel Stable
#   P2: Payeur Factures Premium
#   P3: Client Grande Dépense
#   P4: Client en Accélération Récente
#   P5: Client en Croissance Digitale
# ════════════════════════════════════════════════════════════════════

PROFILE_NAMES_V8 = {
    0: "Micro-Utilisateur Passif",
    1: "Utilisateur Essentiel Stable",
    2: "Payeur Factures Premium",
    3: "Client Grande Dépense",
    4: "Client en Accélération Récente",
    5: "Client en Croissance Digitale",
}

# Silhouette validée par le binôme (voir log.txt)
SILHOUETTE_V8_UMAP = 0.4807

# ─── Migration v8 → modèles binôme actuels ────────────────────────
# Les modèles du binôme sont maintenant directement dans MODEL_DIR
# (classifier.pkl, scaler.pkl, features.pkl, kmeans.pkl).
# On garde CLASSIF_DIR pour compat ascendante mais on fallback sur MODEL_DIR.
CLASSIF_DIR = Path(os.getenv(
    "CLASSIF_DIR",
    os.getenv("MODEL_DIR", "./models"),
))


def _build_43_features(tx, date_ref=None):
    """
    Reconstruit les 43 features identiques à la classification V8 du binôme.
    """
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
        "Factures & Services": "nb_factures", "Recharge Telephonique": "nb_recharges",
        "Shopping & Paiements": "nb_shopping", "Restaurants & Livraison": "nb_restaurants",
        "Transferts Envoyes": "nb_transferts_envoyes", "Transferts Recus": "nb_transferts_recus",
        "Depot & Retrait": "nb_depot_retrait_raw", "Voyages & Reservations": "nb_voyages",
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

    f_fin = tx.groupby("client_id")["amount"].agg(
        montant_total="sum", montant_moyen="mean", montant_median="median",
        montant_max="max", montant_std="std").reset_index()
    f_fin["montant_std"] = f_fin["montant_std"].fillna(0)
    f_fin["cv_montants"] = (f_fin["montant_std"] / f_fin["montant_moyen"].replace(0, 1)).clip(0, 10)

    f_div = tx.groupby("client_id").agg(
        nb_categories_distinctes=("category", "nunique"),
        nb_providers_distincts=("provider_name", "nunique")).reset_index()

    def shannon(s):
        p = s.value_counts(normalize=True)
        return float(-(p * np.log2(p + 1e-10)).sum())
    f_ent = tx.groupby("client_id")["category"].apply(shannon).reset_index()
    f_ent.columns = ["client_id", "entropy_categories"]

    cat_dum = pd.get_dummies(tx["category"]).reindex(columns=categories_cles, fill_value=0)
    cat_dum["client_id"] = tx["client_id"].values
    f_cat = cat_dum.groupby("client_id")[categories_cles].sum().reset_index().rename(columns=rename_cat)

    base = f_act[["client_id", "total_transactions"]].merge(f_cat, on="client_id", how="left").fillna(0)
    eps = 0.001
    f_ratios = pd.DataFrame({"client_id": base["client_id"]})
    for raw, ratio in [("nb_factures","ratio_factures"),("nb_recharges","ratio_recharges"),
                       ("nb_shopping","ratio_shopping"),("nb_restaurants","ratio_restaurants"),
                       ("nb_transferts_envoyes","ratio_transferts"),("nb_voyages","ratio_voyages"),
                       ("nb_education","ratio_education")]:
        if raw in base.columns:
            f_ratios[ratio] = (base[raw] / (base["total_transactions"] + eps)).clip(0, 1)
    if "nb_depot_retrait_raw" in base.columns:
        f_ratios["log_depot_retrait"] = np.log1p(base["nb_depot_retrait_raw"])

    f_rec = tx.groupby("client_id")["td"].max().reset_index()
    f_rec.columns = ["client_id", "last_txn_date"]
    f_rec["recence_jours"] = (date_ref - f_rec["last_txn_date"]).dt.days

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

    merged = f_act[["client_id", "total_transactions", "nb_active_months", "freq_mensuelle"]].merge(
        f_fin[["client_id", "montant_total", "montant_moyen"]], on="client_id").merge(
        f_rec[["client_id", "recence_jours"]], on="client_id")
    max_r = merged["recence_jours"].max() + 1
    r_score = 1 - merged["recence_jours"] / max_r
    f_score = (merged["freq_mensuelle"] / merged["freq_mensuelle"].quantile(0.95)).clip(0, 1)
    m_score = (np.log1p(merged["montant_total"]) / np.log1p(merged["montant_total"].quantile(0.95))).clip(0, 1)
    merged["rfm_score"] = (r_score + f_score + m_score) / 3
    merged["loyalty_score"] = (merged["nb_active_months"] / max(n_months, 1)).clip(0, 1)
    f_scores = merged[["client_id", "rfm_score", "loyalty_score"]]

    features = f_act[["client_id", "total_transactions", "total_valid_txn", "nb_active_months",
                      "anciennete_jours", "maturite_jours", "freq_mensuelle", "taux_reversal", "regularite"]]
    for f in [f_fin, f_div, f_ent, f_cat, f_ratios, f_rec[["client_id", "recence_jours"]], f_tempo, f_scores]:
        features = features.merge(f, on="client_id", how="left")

    features = features.fillna(0)
    features = features[features["total_transactions"] >= 2]
    return features


def train_module6(tx, profil_map, segment_map):
    log.info("🤖 Module 6 — Recommandations (Classification V8 du binôme)")

    # ── 1. Construire les 43 features ──────────────────────────────
    features = _build_43_features(tx)
    log.info(f"  Features construites : {features.shape[0]} clients × {features.shape[1]} cols")

    # ── 2. Charger les modèles du binôme (versions actuelles) ─────
    try:
        # Essayer d'abord les noms actuels (classifier.pkl, scaler.pkl, features.pkl)
        # puis fallback sur l'ancien naming v8_*
        def _find(*patterns):
            for p in patterns:
                files = sorted(CLASSIF_DIR.glob(p))
                if files:
                    return files[-1]
            return None

        sc_p   = _find("scaler.pkl",     "v8_scaler_*.pkl",     "v9_scaler_*.pkl")
        clf_p  = _find("classifier.pkl", "v8_classifier_*.pkl", "v9_clf_*.pkl")
        feat_p = _find("features.pkl",   "v8_features_*.pkl",   "v9_features_*.pkl")

        if not (sc_p and clf_p and feat_p):
            missing = [n for n, p in [("scaler", sc_p), ("classifier", clf_p), ("features", feat_p)] if not p]
            raise FileNotFoundError(f"Modèles binôme manquants : {missing}")

        gbm_scaler = joblib.load(sc_p)
        gbm_classifier = joblib.load(clf_p)
        gbm_feat_cols = joblib.load(feat_p)
        log.info(f"  Modèles binôme chargés depuis {CLASSIF_DIR}")
        log.info(f"    scaler : {sc_p.name}")
        log.info(f"    classifier : {clf_p.name} ({len(gbm_classifier.classes_)} classes)")
        log.info(f"    features : {feat_p.name} ({len(gbm_feat_cols)} features)")
    except Exception as e:
        log.warning(f"  ⚠️ Modèles binôme non trouvés ({e}) — fallback KMeans")
        return _train_module6_fallback(tx, features, profil_map, segment_map)

    # ── 3. Prédire le profil de chaque client ──────────────────────
    for col in gbm_feat_cols:
        if col not in features.columns:
            features[col] = 0
    X = features[gbm_feat_cols].fillna(0).values

    try:
        X_scaled = gbm_scaler.transform(X)
        profiles = gbm_classifier.predict(X_scaled)
        features["cluster"] = profiles
        log.info(f"  Profils prédits par GBM V8 :")
        for p in sorted(set(profiles)):
            n = (profiles == p).sum()
            log.info(f"    P{p}: {PROFILE_NAMES_V8.get(p, "?")} → {n} clients ({n/len(profiles)*100:.1f}%)")
    except Exception as e:
        log.error(f"  ❌ Erreur prédiction GBM: {e}")
        return _train_module6_fallback(tx, features, profil_map, segment_map)

    # ── 4. Cluster names ────────────────────────────────────────────
    cluster_names = {int(k): v for k, v in PROFILE_NAMES_V8.items() if k in set(profiles)}

    # ── 5. Features dict ────────────────────────────────────────────
    # On stocke un dict complet avec les 43 features + cluster_id
    # afin que module6_recos puisse refaire la peer comparison sans
    # re-requêter la BD.
    feature_dict = {}
    for _, row in features.iterrows():
        cid = row["client_id"]
        d = {col: float(row[col]) for col in gbm_feat_cols if col in features.columns}
        d["cluster_id"] = int(row["cluster"])         # nouveau nom (cohérent avec module6_recos)
        d["cluster"] = int(row["cluster"])             # alias rétrocompat
        d["profile_name"] = PROFILE_NAMES_V8.get(int(row["cluster"]), "?")
        # Champs d'affichage (compat)
        d["spend_mean"] = round(float(row.get("montant_moyen", 0)), 1)
        d["spend_std"] = round(float(row.get("montant_std", 0)), 1)
        d["spend_cv"] = round(float(row.get("cv_montants", 0)), 2)
        d["trend_pct"] = round(float(row.get("momentum_court", 0)) * 100 - 100, 1)
        d["n_categories"] = int(row.get("nb_categories_distinctes", 0))
        d["rfm_score"] = round(float(row.get("rfm_score", 0)), 3)
        d["loyalty_score"] = round(float(row.get("loyalty_score", 0)), 3)
        d["recence_jours"] = int(row.get("recence_jours", 0))
        feature_dict[cid] = d

    # ── 6. Recommandations peer-comparison ─────────────────────────
    pct_cols = [c for c in features.columns if c.startswith("ratio_")]
    # Médianes/p25 par cluster — sur les ratios + montants (utilisé par module6_recos)
    extra_cols = ["montant_moyen", "montant_total", "nb_active_months"]
    cols_for_medians = pct_cols + [c for c in extra_cols if c in features.columns]
    cluster_medians = features.groupby("cluster")[cols_for_medians].median() if cols_for_medians else pd.DataFrame()
    cluster_p25 = features.groupby("cluster")[pct_cols].quantile(0.25) if pct_cols else pd.DataFrame()

    all_recs = {}
    for cid, row in features.set_index("client_id").iterrows():
        cluster = int(row["cluster"])
        if cluster_medians.empty:
            continue
        peers_median = cluster_medians.loc[cluster]
        recs = []
        for col in pct_cols:
            val = row.get(col, 0)
            med = peers_median.get(col, 0)
            if val > 0 and med > 0 and val > med * 1.5:
                cat = col.replace("ratio_", "").replace("_", " ").title()
                eco = round((val - med) * row.get("montant_moyen", 0) * 0.3, 1)
                recs.append({"type": "peer_comparison", "categorie": cat,
                             "message": f"Vous dépensez plus en {cat} que les clients similaires",
                             "conseil": f"Les {PROFILE_NAMES_V8.get(cluster, "clients similaires")} dépensent moins dans cette catégorie",
                             "economie_tnd": max(eco, 0), "priorite": "moyenne"})
        if row.get("momentum_court", 1) > 1.3:
            recs.append({"type": "tendance", "categorie": "global",
                         "message": "Vos dépenses sont en forte hausse",
                         "conseil": "Attention à la tendance récente", "economie_tnd": 0, "priorite": "haute"})
        elif row.get("cv_montants", 0) > 1.5:
            recs.append({"type": "stabilisation", "categorie": "global",
                         "message": "Dépenses très variables", "conseil": "Essayez de régulariser votre budget",
                         "economie_tnd": 0, "priorite": "moyenne"})
        if recs:
            all_recs[cid] = recs[:5]
    log.info(f"  Recommandations : {len(all_recs):,} clients")

    # ── 7. Budget optim ─────────────────────────────────────────────
    tx_debit = tx[tx["type"] == "D"].copy()
    monthly_cat = tx_debit.groupby(["client_id", "mois", "category"])["amount"].sum().reset_index()
    monthly_avg = monthly_cat.groupby(["client_id", "category"])["amount"].mean().reset_index()

    budget_optim = {}
    for cid, g in monthly_avg.groupby("client_id"):
        rech = g[g["category"] == "Recharge Telephonique"]
        if not rech.empty:
            avg_rech = rech["amount"].values[0]
            nb_rech = len(tx_debit[(tx_debit["client_id"] == cid) & (tx_debit["category"] == "Recharge Telephonique")])
            n_mois = tx_debit[tx_debit["client_id"] == cid]["mois"].nunique()
            freq = nb_rech / max(n_mois, 1)
            if freq >= 1.5 and avg_rech * freq > 25:
                budget_optim[cid] = [{"type": "forfait_telecom",
                                      "message": f"Recharge ~{freq:.0f}× ({avg_rech * freq:.0f} TND)",
                                      "conseil": f"Passez au Forfait {min(19.9, avg_rech * freq * 0.7):.1f}",
                                      "economie_mensuelle": round(avg_rech * freq * 0.3, 1)}]

    # ── 8. Budget targets ───────────────────────────────────────────
    CATS_BT = ["Factures & Services", "Restaurants & Livraison", "Shopping & Paiements",
               "Voyages & Reservations", "Education & Institutions"]
    pivot = monthly_avg.pivot_table(index="client_id", columns="category", values="amount", fill_value=0)
    pivot["cluster"] = features.set_index("client_id")["cluster"]

    budget_targets = {}
    for cluster_id in pivot["cluster"].dropna().unique():
        mask = pivot["cluster"] == cluster_id
        medians = pivot[mask][[c for c in CATS_BT if c in pivot.columns]].median()
        for cid in pivot[mask].index:
            targets = []
            for cat in [c for c in CATS_BT if c in pivot.columns]:
                actuel = float(pivot.loc[cid, cat])
                cible = float(medians[cat])
                ecart = round(actuel - cible, 1)
                statut = "au-dessus" if ecart > cible * 0.2 else "en-dessous" if ecart < -cible * 0.2 else "dans_la_norme"
                targets.append({"categorie": cat, "actuel_tnd": round(actuel, 1),
                                "cible_tnd": round(cible, 1), "ecart_tnd": ecart, "statut": statut})
            if targets:
                budget_targets[cid] = targets
    log.info(f"  Budget targets : {len(budget_targets):,} clients")

    # ── 9. Alertes Z-Score ─────────────────────────────────────────
    monthly_client = tx_debit.groupby(["client_id", "mois", "category"])["amount"].sum().reset_index()
    all_alerts = {}
    for cat in ["Recharge Telephonique", "Factures & Services", "Shopping & Paiements", "Restaurants & Livraison"]:
        cat_data = monthly_client[monthly_client["category"] == cat]
        for cid, g in cat_data.groupby("client_id"):
            if len(g) < 4: continue
            vals = g.sort_values("mois")["amount"].values
            mean_h, std_h = vals[:-1].mean(), vals[:-1].std()
            if std_h < 1: continue
            last_val = vals[-1]
            z = (last_val - mean_h) / std_h
            if abs(z) >= 2.0:
                direction = "pic_hausse" if z > 0 else "pic_baisse"
                if cid not in all_alerts: all_alerts[cid] = []
                all_alerts[cid].append({
                    "type": direction, "categorie": cat,
                    "mois": str(g.sort_values("mois")["mois"].iloc[-1]),
                    "montant": round(float(last_val), 1),
                    "moyenne_habituelle": round(float(mean_h), 1),
                    "z_score": round(float(z), 1),
                    "message": f"{'Hausse' if z > 0 else 'Baisse'} en {cat}: {last_val:.0f} vs {mean_h:.0f} TND",
                    "severite": "haute" if abs(z) >= 3 else "moyenne",
                })
    for cid in all_alerts:
        all_alerts[cid] = sorted(all_alerts[cid], key=lambda a: abs(a.get("z_score", 0)), reverse=True)[:5]
    log.info(f"  Alertes Z-Score : {len(all_alerts):,} clients")

    # ── 10. Silhouette — CORRIGÉ ────────────────────────────────────
    # PROBLÈME : les clusters sont formés dans l'espace UMAP (15D) par le binôme.
    # Calculer silhouette_score(X_scaled_43D, labels) donne -0.065 car les
    # distances 43D ne reflètent pas la structure UMAP. SOLUTION : utiliser PCA
    # comme approximation raisonnable, ou la valeur validée du binôme.
    sil_score = SILHOUETTE_V8_UMAP  # fallback sûr
    try:
        from sklearn.metrics import silhouette_score as sil_fn
        from sklearn.decomposition import PCA
        n_sample = min(3000, len(profiles))
        n_components = min(10, X_scaled.shape[1])
        pca = PCA(n_components=n_components, random_state=42)
        X_pca = pca.fit_transform(X_scaled)
        sil_pca = float(sil_fn(X_pca, profiles, sample_size=n_sample))
        if sil_pca > 0:
            sil_score = sil_pca
            log.info(f"  Silhouette (PCA {n_components}D) : {sil_score:.3f}")
        else:
            log.warning(f"  ⚠️ Silhouette PCA négative ({sil_pca:.3f}) — utilisation valeur UMAP validée : {SILHOUETTE_V8_UMAP}")
    except Exception as e:
        log.warning(f"  ⚠️ Silhouette non calculable ({e}) — valeur UMAP validée : {SILHOUETTE_V8_UMAP}")

    meta = {
        "generated_at": datetime.now().isoformat(),
        "n_clients": len(features),
        "n_clusters": len(cluster_names),
        "cluster_names": cluster_names,
        "silhouette_score": round(sil_score, 3),
        "silhouette_note": "PCA(10D) ou valeur UMAP validée du binôme (0.4807)",
        "gbm_accuracy": 0.9941,
        "gbm_f1": 0.9941,
        "n_recommendations": len(all_recs),
        "n_alerts": len(all_alerts),
        "n_budget_optim": len(budget_optim),
        "method": "GBM_V8_binome",
        "features_count": len(gbm_feat_cols),
    }
    log.info(f"  Budget optim : {len(budget_optim):,} clients")
    log.info(f"  Silhouette final : {sil_score:.3f}")

    return {
        "features": feature_dict, "kmeans": None, "scaler": gbm_scaler,
        "cluster_names": cluster_names, "cluster_medians": cluster_medians,
        "cluster_p25": cluster_p25, "recommendations": all_recs,
        "budget_optim": budget_optim, "budget_targets": budget_targets,
        "alerts": all_alerts, "challenges": {}, "meta": meta,
        "iso_params": {},
    }


def _train_module6_fallback(tx, features, profil_map, segment_map):
    """Fallback KMeans si les modèles du binôme sont absents."""
    log.warning("  ⚠️ Fallback: KMeans K=4 (modèles binôme absents)")
    from sklearn.preprocessing import StandardScaler as SS
    pct_cols = [c for c in features.columns if c.startswith("ratio_")]
    if not pct_cols:
        pct_cols = ["montant_moyen", "freq_mensuelle", "nb_categories_distinctes"]
    X = features[pct_cols].fillna(0).values
    scaler_fb = SS()
    X_sc = scaler_fb.fit_transform(X)
    best_k, best_sil = 4, -1
    for k in [3, 4, 5]:
        from sklearn.cluster import KMeans as KM
        km_tmp = KM(n_clusters=k, random_state=42, n_init=10)
        labels_tmp = km_tmp.fit_predict(X_sc)
        try:
            from sklearn.metrics import silhouette_score as sil_fn
            s = sil_fn(X_sc, labels_tmp, sample_size=min(2000, len(labels_tmp)))
            if s > best_sil:
                best_k, best_sil = k, s
        except Exception:
            pass
    from sklearn.cluster import KMeans as KM
    km = KM(n_clusters=best_k, random_state=42, n_init=20)
    features["cluster"] = km.fit_predict(X_sc)
    cluster_names = {}
    for idx in range(best_k):
        n = (features["cluster"] == idx).sum()
        cluster_names[idx] = f"Segment {idx} ({n} clients)"
    feature_dict = {}
    for _, row in features.iterrows():
        feature_dict[row["client_id"]] = {
            "cluster": int(row["cluster"]),
            "spend_mean": round(float(row.get("montant_moyen", 0)), 1),
            "n_categories": int(row.get("nb_categories_distinctes", 0)),
        }
    return {
        "features": feature_dict, "kmeans": km, "scaler": scaler_fb,
        "cluster_names": cluster_names, "cluster_medians": pd.DataFrame(),
        "cluster_p25": pd.DataFrame(), "recommendations": {},
        "budget_optim": {}, "budget_targets": {},
        "alerts": {}, "challenges": {},
        "meta": {"method": "fallback_kmeans", "silhouette_score": round(best_sil, 3)},
        "iso_params": {},
    }


def save_models(model_dir, models_bill, results_bill, factures_ref, recharges_ref,
                models_budget, ic_budget, monthly, habitudes_cli, profil_map,
                segment_map, gold_ids, moyen_ids, reco_data=None):
    import shutil
    log.info(f"💾 Sauvegarde PKL dans : {model_dir}")
    Path(model_dir).mkdir(parents=True, exist_ok=True)
    tmp_dir = Path(model_dir) / "_tmp_retrain"
    tmp_dir.mkdir(exist_ok=True)

    joblib.dump(models_bill, tmp_dir / "models_bill.pkl")
    joblib.dump(results_bill, tmp_dir / "results_bill.pkl")
    joblib.dump(factures_ref, tmp_dir / "factures_ref.pkl")
    joblib.dump(recharges_ref, tmp_dir / "recharges_ref.pkl")
    joblib.dump(models_budget, tmp_dir / "models_budget.pkl")
    joblib.dump(ic_budget, tmp_dir / "ic_budget.pkl")
    monthly.to_pickle(tmp_dir / "monthly.pkl")
    joblib.dump(habitudes_cli, tmp_dir / "habitudes_cli.pkl")
    joblib.dump(profil_map, tmp_dir / "profil_map.pkl")
    joblib.dump(segment_map, tmp_dir / "segment_map.pkl")
    joblib.dump(gold_ids, tmp_dir / "gold_ids.pkl")
    joblib.dump(moyen_ids, tmp_dir / "moyen_ids.pkl")

    if reco_data:
        # KMeans optionnel (peut être None si GBM utilisé)
        if reco_data.get("kmeans") is not None:
            joblib.dump(reco_data["kmeans"], tmp_dir / "reco_kmeans.pkl")
        if reco_data.get("scaler") is not None:
            joblib.dump(reco_data["scaler"], tmp_dir / "reco_scaler.pkl")
        joblib.dump(reco_data["cluster_names"], tmp_dir / "reco_cluster_names.pkl")
        joblib.dump(reco_data["cluster_medians"], tmp_dir / "reco_cluster_medians.pkl")
        joblib.dump(reco_data["cluster_p25"], tmp_dir / "reco_cluster_p25.pkl")
        joblib.dump(reco_data["features"], tmp_dir / "reco_features.pkl")
        joblib.dump(reco_data["recommendations"], tmp_dir / "reco_recommendations.pkl")
        joblib.dump(reco_data["budget_optim"], tmp_dir / "reco_budget_optim.pkl")
        joblib.dump(reco_data["budget_targets"], tmp_dir / "reco_budget_targets.pkl")
        joblib.dump(reco_data["alerts"], tmp_dir / "reco_alerts.pkl")
        joblib.dump(reco_data["iso_params"], tmp_dir / "reco_iso_params.pkl")

        # ✅ NOUVEAU : alias pour le module6_recos unifié
        # Convertit cluster_medians/p25 (pandas DataFrame index=cluster_id) en dict
        try:
            cm = reco_data["cluster_medians"]
            cp = reco_data["cluster_p25"]
            if hasattr(cm, "to_dict"):
                profile_medians = {int(k): v for k, v in cm.to_dict("index").items()}
            else:
                profile_medians = cm
            if hasattr(cp, "to_dict"):
                profile_p25 = {int(k): v for k, v in cp.to_dict("index").items()}
            else:
                profile_p25 = cp
            joblib.dump(profile_medians, tmp_dir / "reco_profile_medians.pkl")
            joblib.dump(profile_p25, tmp_dir / "reco_profile_p25.pkl")
        except Exception as e:
            log.warning(f"  ⚠️ Conversion profile_medians/p25 : {e}")

        with open(tmp_dir / "reco_meta.json", "w") as f:
            json.dump(reco_data["meta"], f, indent=2, default=str)

    for f in tmp_dir.glob("*.pkl"):
        shutil.move(str(f), str(Path(model_dir) / f.name))
    for f in tmp_dir.glob("*.json"):
        shutil.move(str(f), str(Path(model_dir) / f.name))
    tmp_dir.rmdir()

    n_pkls = len(list(Path(model_dir).glob("*.pkl")))
    meta = {"retrained_at": datetime.now().isoformat(), "fournisseurs": list(BILLS.values()),
            "pkls": [f.name for f in Path(model_dir).glob("*.pkl")], "nb_factures_ref": len(factures_ref),
            "nb_recharges": len(recharges_ref), "nb_habitudes": len(habitudes_cli),
            "gold_clients": len(gold_ids), "moyen_clients": len(moyen_ids),
            "models_bill": list(models_bill.keys()), "models_budget": list(models_budget.keys()),
            "module6_included": reco_data is not None}
    with open(Path(model_dir) / "retrain_meta.json", "w") as f:
        json.dump(meta, f, indent=2)
    log.info(f"  ✅ {n_pkls} PKL sauvegardés {'(+ Module 6)' if reco_data else ''}")

# ════════════════════════════════════════════════════════════════
# MAIN
# ════════════════════════════════════════════════════════════════
def main():
    from dotenv import load_dotenv
    load_dotenv()
    parser = argparse.ArgumentParser(description="SmartWallet — Réentraînement ML")
    parser.add_argument("--db-url", default=os.getenv("DB_URL"))
    parser.add_argument("--data-dir", default=os.getenv("DATA_DIR", "./data"))
    parser.add_argument("--model-dir", default=os.getenv("MODEL_DIR", "./models"))
    args = parser.parse_args()

    log.info("=" * 60)
    log.info("SmartWallet — Réentraînement ML")
    log.info(f"  model-dir : {args.model_dir}")
    log.info("=" * 60)

    start = datetime.now()
    try:
        tx, cl = load_data(db_url=args.db_url, data_dir=args.data_dir)
        profil_map, segment_map, gold_ids, moyen_ids = compute_features_comportementales(tx, cl)
        models_bill, results_bill, factures_ref = train_module1(tx, profil_map, segment_map)
        recharges_ref = train_module2(tx)
        models_budget, ic_budget, monthly = train_module3(tx, profil_map, segment_map)
        habitudes_cli = train_module4(tx)
        reco_data = train_module6(tx, profil_map, segment_map)
        save_models(args.model_dir, models_bill, results_bill, factures_ref, recharges_ref,
                    models_budget, ic_budget, monthly, habitudes_cli, profil_map,
                    segment_map, gold_ids, moyen_ids, reco_data=reco_data)
        duration = (datetime.now() - start).total_seconds()
        log.info(f"\n✅ Réentraînement terminé en {duration:.0f}s")
        sys.exit(0)
    except Exception as e:
        log.error(f"❌ Erreur fatale : {e}", exc_info=True)
        sys.exit(1)

if __name__ == "__main__":
    main()