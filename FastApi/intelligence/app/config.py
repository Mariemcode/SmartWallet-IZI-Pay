"""
══════════════════════════════════════════════════════════════════════
SmartWallet — Configuration & Chargement modèles
══════════════════════════════════════════════════════════════════════
Charge :
  • Variables d'environnement (.env)
  • Modèles de prévision (Module 1-5) depuis MODEL_DIR
  • Modèles de classification depuis MODEL_DIR
  • Caches Module 6 (recommandations pré-calculées)

Variables d'environnement :
  DB_HOST=localhost  DB_PORT=5432
  DB_NAME=client_bd  DB_USER=postgres  DB_PASSWORD=...
  MODEL_DIR=./models
  RETRAIN_SECRET=smartwallet-retrain-2026
  SPRING_BOOT_URL=http://localhost:8090
══════════════════════════════════════════════════════════════════════
"""
from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Any, Dict, List, Optional

import joblib
import psycopg2

log = logging.getLogger("smartwallet.config")

# ─────────────────────────────────────────────────────────────────────
# Configuration globale
# ─────────────────────────────────────────────────────────────────────
from dotenv import load_dotenv
load_dotenv()

MODEL_DIR = os.getenv("MODEL_DIR", "./models")

DB_CONFIG = {
    "host":     os.getenv("DB_HOST", "localhost"),
    "port":     int(os.getenv("DB_PORT", "5432")),
    "dbname":   os.getenv("DB_NAME", "client_bd"),
    "user":     os.getenv("DB_USER", "postgres"),
    "password": os.getenv("DB_PASSWORD", "password"),
}

# URL legacy (utilisée par retrain.py)
DB_URL = os.getenv(
    "DB_URL",
    f"postgresql://{DB_CONFIG['user']}:{DB_CONFIG['password']}@"
    f"{DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['dbname']}",
)

SPRING_BOOT_URL = os.getenv("SPRING_BOOT_URL", "http://localhost:8090")
RETRAIN_SECRET = os.getenv("RETRAIN_SECRET", "smartwallet-retrain-2026")

# ─────────────────────────────────────────────────────────────────────
# Modèles de prévision (Modules 1-5) — variables globales partagées
# ─────────────────────────────────────────────────────────────────────
FORECAST_MODELS: Dict[str, Any] = {
    "models_bill": None,
    "results_bill": None,
    "factures_ref": None,
    "recharges_ref": None,
    "models_budget": None,
    "ic_budget": None,
    "monthly": None,
    "habitudes_cli": None,
    "profil_map": None,
    "segment_map": None,
    "gold_ids": None,
    "moyen_ids": None,
}

# ─────────────────────────────────────────────────────────────────────
# Connexion DB
# ─────────────────────────────────────────────────────────────────────
def get_db_connection():
    """Retourne une connexion psycopg2 — à fermer par l'appelant."""
    return psycopg2.connect(**DB_CONFIG)


# ─────────────────────────────────────────────────────────────────────
# Chargement
# ─────────────────────────────────────────────────────────────────────
def load_forecast_models() -> None:
    """
    Charge les modèles de prévision (Modules 1-5) dans FORECAST_MODELS.
    Tolérant aux fichiers absents.
    """
    model_dir = Path(MODEL_DIR)
    log.info(f"⏳ Chargement modèles prévision depuis : {model_dir}")

    files = {
        "models_bill":   "models_bill.pkl",
        "results_bill":  "results_bill.pkl",
        "factures_ref":  "factures_ref.pkl",
        "recharges_ref": "recharges_ref.pkl",
        "models_budget": "models_budget.pkl",
        "ic_budget":     "ic_budget.pkl",
        "monthly":       "monthly.pkl",
        "habitudes_cli": "habitudes_cli.pkl",
        "profil_map":    "profil_map.pkl",
        "segment_map":   "segment_map.pkl",
        "gold_ids":      "gold_ids.pkl",
        "moyen_ids":     "moyen_ids.pkl",
    }

    loaded = 0
    for key, filename in files.items():
        path = model_dir / filename
        if path.exists():
            try:
                FORECAST_MODELS[key] = joblib.load(path)
                loaded += 1
            except Exception as e:
                log.warning(f"  ⚠️ Erreur chargement {filename} : {e}")

    log.info(f"  ✓ Modèles prévision : {loaded}/{len(files)} chargés")

    # Récap
    if FORECAST_MODELS["models_bill"]:
        log.info(f"    bill: {len(FORECAST_MODELS['models_bill'])} fournisseurs")
    if FORECAST_MODELS["models_budget"]:
        log.info(f"    budget: {len(FORECAST_MODELS['models_budget'])} catégories")


def init_paid_cache_from_db() -> Dict[str, List[str]]:
    """
    Reconstruit paid_cache (factures + recharges payées ce mois)
    depuis la table transaction.
    """
    paid_cache: Dict[str, List[str]] = {}
    ALL_IDS = {
        "1461b464-fa44-477a-90c3-a9d68acdf29a": "TOPNET",
        "08e939ae-af2c-428f-8ece-5862e56de5d3": "BEE",
        "18de5279-60d1-40f0-bb68-54b29d6f1ba8": "SONEDE",
        "07ba063e-a1fb-4fd9-87a7-243efcc55af2": "STEG",
        "c428964b-5929-4480-a569-cb2ef1bc3b27": "TT",
        "2d0d0c45-9941-42d9-9849-0f61d06c4a7b": "OOREDOO",
        "a0f3b202-3d88-4893-b5d3-db3613b7cc4a": "RECH_TT",
        "cc3fb138-ffbe-4b22-9e83-d5426127d5ca": "RECH_OOREDOO",
    }
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        ids_sql = ",".join(f"'{k}'" for k in ALL_IDS.keys())
        cursor.execute(f"""
            SELECT DISTINCT client_id, transaction_type_id
              FROM transaction
             WHERE transaction_type_id IN ({ids_sql})
               AND reversal_flag = 'N'
               AND DATE_TRUNC('month', transaction_date) =
                   DATE_TRUNC('month', CURRENT_DATE)
        """)
        for cid, tid in cursor.fetchall():
            label = ALL_IDS.get(tid)
            if label:
                paid_cache.setdefault(cid, []).append(label)
        cursor.close()
        conn.close()
        total = sum(len(v) for v in paid_cache.values())
        log.info(f"📂 paid_cache initialisé : {total} paiements ({len(paid_cache)} clients)")
    except Exception as e:
        log.warning(f"⚠️ Erreur init paid_cache : {e}")
    return paid_cache
