#!/usr/bin/env python3
"""
Système de recommandation V5.3 — Wallet Mobile Tunisie
Méthodologie CRISP-DM complet (6 phases)
Génération automatique des offres depuis données réelles

"""

import os
import sys
import json
import glob
import warnings
import logging
import time
import threading
import argparse
import hashlib
from datetime import datetime, timedelta
from pathlib import Path
from typing import Optional, List, Dict, Any

import numpy as np
import pandas as pd
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import seaborn as sns

try:
    import psycopg2
    import psycopg2.extras
    from psycopg2.extras import execute_values
    from psycopg2 import pool as pg_pool
except ImportError:
    print("✗ psycopg2 requis — pip install psycopg2-binary")
    sys.exit(1)

try:
    from apscheduler.schedulers.background import BackgroundScheduler
    APSCHEDULER_AVAILABLE = True
except ImportError:
    APSCHEDULER_AVAILABLE = False
    print("⚠ APScheduler non disponible — pip install apscheduler")

try:
    import joblib
    JOBLIB_AVAILABLE = True
except ImportError:
    JOBLIB_AVAILABLE = False
    print("⚠ joblib non disponible — pip install joblib")

try:
    from dotenv import load_dotenv
    _env = Path(__file__).parent / ".env"
    if _env.exists():
        load_dotenv(dotenv_path=_env)
        print(f"✓ .env chargé depuis {_env}")
    else:
        load_dotenv()
except ImportError:
    pass

warnings.filterwarnings('ignore')
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%H:%M:%S'
)
logger = logging.getLogger(__name__)

# ==============================================================================
# CONFIGURATION
# ==============================================================================
OUT_DIR = os.environ.get("RECO_OUT_DIR",
                         os.path.join(os.path.dirname(os.path.abspath(__file__)), "outputs_reco_v5"))

MODEL_DIR = os.environ.get("MODEL_DIR", "")
if not MODEL_DIR:
    _candidates = [
        os.path.join(os.path.dirname(os.path.abspath(__file__)), "outputs", "models"),
        os.path.join(os.path.dirname(os.path.abspath(__file__)), "classification", "outputs", "models"),
        os.path.join(os.path.dirname(os.path.abspath(__file__)), "outputs_v9", "models"),
    ]
    MODEL_DIR = next((p for p in _candidates if os.path.isdir(p)), _candidates[0])
    logger.warning(f"⚠ MODEL_DIR non définie — fallback : {MODEL_DIR}")

TABLE_CLIENT_PROFILES = os.environ.get("TABLE_CLIENT_PROFILES", "client_profiles_v9")
TABLE_MODEL_RUNS      = os.environ.get("TABLE_MODEL_RUNS", "model_runs_v9")

SCHEDULER_INTERVAL_HOURS = int(os.environ.get("SCHEDULER_INTERVAL_HOURS", "24"))
os.makedirs(OUT_DIR, exist_ok=True)
os.makedirs(os.path.join(OUT_DIR, "figures"), exist_ok=True)

DB_CONFIG = {
    "host":     os.environ.get("DB_HOST",     "localhost"),
    "port":     int(os.environ.get("DB_PORT", "5432")),
    "dbname":   os.environ.get("DB_NAME",     "client_bd"),
    "user":     os.environ.get("DB_USER",     "postgres"),
    "password": os.environ.get("DB_PASSWORD", "mariem"),
}
NOTIFY_WEBHOOK = os.environ.get("NOTIFY_WEBHOOK", "")

if not DB_CONFIG["password"]:
    logger.warning("⚠ DB_PASSWORD non défini — connexion PostgreSQL va échouer")

# ==============================================================================
# CONSTANTES PROFILS
# ==============================================================================
PROFILE_NAMES = {
    0:  "Micro-Utilisateur Passif",
    1:  "Utilisateur Essentiel Stable",
    2:  "Payeur Factures Premium",
    3:  "Client Grande Dépense",
    4:  "Client en Accélération Récente",
    5:  "Client en Croissance Digitale",
    -1: "Profil Mixte (Incertain)",
}

FEATURE_COLS = [
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

PALETTE = {
    "Payeur Factures Premium":        "#2563EB",
    "Micro-Utilisateur Passif":       "#9CA3AF",
    "Client en Croissance Digitale":  "#10B981",
    "Client Grande Dépense":          "#F59E0B",
    "Utilisateur Essentiel Stable":   "#8B5CF6",
    "Client en Accélération Récente": "#EF4444",
    "Profil Mixte (Incertain)":       "#6366F1",
}

OFFER_GENERATION_CONFIG = {
    "min_txn_for_loyalty":        30,
    "min_txn_for_reactivation":    5,
    "discount_high_pct":          20.0,
    "discount_standard_pct":      10.0,
    "churn_threshold":             0.5,
    "TOP_CATEGORIES_PER_PROFILE":   2,   # utilisées pour les offres ciblées
    "TOP_PROVIDERS_PER_PROFILE":    2,
    # Plus de cashback
}

plt.rcParams.update({
    'figure.facecolor': 'white',
    'axes.facecolor':   '#F8F9FA',
    'axes.grid':         True,
    'grid.alpha':        0.4,
    'font.size':         10,
    'axes.titlesize':    12,
    'axes.titleweight': 'bold',
    'axes.spines.top':  False,
    'axes.spines.right': False,
})

# ==============================================================================
# POOL DE CONNEXIONS
# ==============================================================================
_pool = None
_pool_lock = threading.Lock()

def get_pool():
    global _pool
    if _pool is None:
        with _pool_lock:
            if _pool is None:
                try:
                    _pool = pg_pool.ThreadedConnectionPool(2, 10, **DB_CONFIG)
                    logger.info("✓ Pool PostgreSQL initialisé (2-10 connexions)")
                except Exception as e:
                    logger.error(f"✗ Connexion PostgreSQL échouée : {e}")
                    raise
    return _pool

def get_conn():
    return get_pool().getconn()

def release_conn(conn):
    try:
        get_pool().putconn(conn)
    except Exception:
        try:
            conn.close()
        except Exception:
            pass

# ==============================================================================
# CHARGEMENT DU MODÈLE DE CLASSIFICATION
# ─────────────────────────────────────────────────────────────────────
# IMPORTANT : Cette logique délègue au module `classification` unifié,
# qui est la SEULE source de vérité de classification dans le projet IA.
# ==============================================================================
_clf = None
_scaler = None
_km = None
_feat_cols = None


def load_classification_model() -> bool:
    """
    Charge la classification du binôme (GBM + 43 features + 6 profils).
    Délègue au module `classification` unifié, puis miroir des variables
    globales pour rétro-compatibilité avec le code existant de ce fichier.
    """
    global _clf, _scaler, _km, _feat_cols
    try:
        # Délégation au module classification unifié
        try:
            from app import classification as _cls_module
        except ImportError:
            import classification as _cls_module  # exécution standalone

        ok = _cls_module.load_classification_models(MODEL_DIR)
        if ok:
            _clf       = _cls_module._clf
            _scaler    = _cls_module._scaler
            _km        = _cls_module._kmeans
            _feat_cols = _cls_module.get_feature_cols()
            logger.info("  ✓ Classification déléguée au module unifié (GBM + 43 features)")
        return ok
    except Exception as e:
        logger.error(f"✗ Erreur load_classification_model : {e}")
        return False


def classify_client(features_dict: dict) -> dict:
    """
    Délègue à la classification unifiée du binôme.
    Garde la même signature qu'avant pour rétro-compat.
    """
    try:
        try:
            from app import classification as _cls_module
        except ImportError:
            import classification as _cls_module
        result = _cls_module.classify_client(features_dict)
        # Format compatible avec l'ancien code
        return {
            "cluster_id": result["cluster_id"],
            "profile_name": result["profile_final"],
            "confidence": result["confidence"],
            "source": result["source"],
            # Champs additionnels disponibles
            "churn_score_30j": result.get("churn_score_30j", 0.0),
            "churn_segment": result.get("churn_segment", "SAIN"),
            "ltv_12m_base": result.get("ltv_12m_base", 0.0),
            "arpu_mensuel": result.get("arpu_mensuel", 0.0),
            "is_mixte": result.get("is_mixte", False),
        }
    except Exception as e:
        logger.error(f"✗ Erreur classify_client : {e}")
        return {"cluster_id": -1, "profile_name": "Profil Mixte (Incertain)",
                "confidence": 0.0, "source": "fallback"}

# ==============================================================================
# DDL – TABLES
# ==============================================================================
_DDL_STATEMENTS = [
    """
    CREATE TABLE IF NOT EXISTS generated_offers (
                                                    id               SERIAL PRIMARY KEY,
                                                    offer_code       VARCHAR(40)   NOT NULL UNIQUE,
        title            VARCHAR(300)  NOT NULL,
        type             VARCHAR(50)   NOT NULL,
        provider_name    VARCHAR(100),
        category         VARCHAR(100),
        cashback_pct     NUMERIC(5,2)  DEFAULT 0,
        discount_pct     NUMERIC(5,2)  DEFAULT 0,
        min_amount       NUMERIC(10,2) DEFAULT 0,
        target_profiles  JSONB         NOT NULL DEFAULT '[]',
        boost            NUMERIC(4,2)  DEFAULT 1.0,
        description      TEXT,
        status           VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
        generation_method VARCHAR(50)  DEFAULT 'auto',
        generation_run   VARCHAR(50),
        created_at       TIMESTAMP     DEFAULT NOW(),
        updated_at       TIMESTAMP     DEFAULT NOW(),
        CONSTRAINT chk_offer_status CHECK (status IN ('ACTIVE','INACTIVE'))
        )
    """,
    "CREATE INDEX IF NOT EXISTS idx_go_status ON generated_offers(status)",
    "CREATE INDEX IF NOT EXISTS idx_go_type ON generated_offers(type)",
    "CREATE INDEX IF NOT EXISTS idx_go_provider ON generated_offers(provider_name)",
    "CREATE INDEX IF NOT EXISTS idx_go_category ON generated_offers(category)",
    """
    CREATE TABLE IF NOT EXISTS recommendations_v5 (
                                                      id                   BIGSERIAL PRIMARY KEY,
                                                      profile_name         VARCHAR(100) NOT NULL,
        cluster_id           INTEGER,
        offer_code           VARCHAR(40)  NOT NULL REFERENCES generated_offers(offer_code),
        score                NUMERIC(6,4) NOT NULL,
        score_profile        NUMERIC(6,4),
        score_provider       NUMERIC(6,4),
        score_category       NUMERIC(6,4),
        score_amount         NUMERIC(6,4),
        score_loyalty        NUMERIC(6,4),
        score_churn_boost    NUMERIC(6,4),
        is_targeted          BOOLEAN      DEFAULT FALSE,
        recommendation_type  VARCHAR(30)  DEFAULT 'auto_generated',
        status               VARCHAR(20)  DEFAULT 'PENDING',
        admin_note           TEXT,
        description          TEXT,
        generated_at         TIMESTAMP    DEFAULT NOW(),
        approved_at          TIMESTAMP,
        rejected_at          TIMESTAMP,
        notified_at          TIMESTAMP,
        model_version        VARCHAR(20)  DEFAULT 'V5.0',
        CONSTRAINT chk_rec5_status CHECK (status IN ('PENDING','APPROVED','REJECTED'))
        )
    """,
    "CREATE INDEX IF NOT EXISTS idx_rec5_profile ON recommendations_v5(profile_name)",
    "CREATE INDEX IF NOT EXISTS idx_rec5_status  ON recommendations_v5(status)",
    "CREATE INDEX IF NOT EXISTS idx_rec5_offer   ON recommendations_v5(offer_code)",
    """
    CREATE TABLE IF NOT EXISTS client_recommendations_v5 (
                                                             id                BIGSERIAL PRIMARY KEY,
                                                             client_id         UUID         NOT NULL,
                                                             recommendation_id BIGINT       REFERENCES recommendations_v5(id) ON DELETE CASCADE,
        offer_code        VARCHAR(40)  NOT NULL REFERENCES generated_offers(offer_code),
        profile_name      VARCHAR(100),
        cluster_id        INTEGER,
        personal_score    NUMERIC(6,4),
        status            VARCHAR(20)  DEFAULT 'PENDING',
        sent_at           TIMESTAMP,
        opened_at         TIMESTAMP,
        accepted_at       TIMESTAMP,
        rejected_at       TIMESTAMP,
        generated_at      TIMESTAMP    DEFAULT NOW(),
        CONSTRAINT chk_crec5_status
        CHECK (status IN ('PENDING','SENT','OPENED','ACCEPTED','REJECTED'))
        )
    """,
    "CREATE INDEX IF NOT EXISTS idx_crec5_client ON client_recommendations_v5(client_id)",
    "CREATE INDEX IF NOT EXISTS idx_crec5_offer  ON client_recommendations_v5(offer_code)",
    """
    CREATE TABLE IF NOT EXISTS recommendation_history_v5 (
                                                             id                BIGSERIAL PRIMARY KEY,
                                                             recommendation_id BIGINT,
                                                             profile_name      VARCHAR(100),
        offer_code        VARCHAR(40),
        old_status        VARCHAR(20),
        new_status        VARCHAR(20),
        changed_by        VARCHAR(100) DEFAULT 'system',
        note              TEXT,
        changed_at        TIMESTAMP    DEFAULT NOW()
        )
    """,
    "CREATE INDEX IF NOT EXISTS idx_rhist5_reco ON recommendation_history_v5(recommendation_id)",
    "CREATE INDEX IF NOT EXISTS idx_rhist5_date ON recommendation_history_v5(changed_at DESC)",
    """
    CREATE TABLE IF NOT EXISTS recommendation_metrics_v5 (
                                                             id               SERIAL PRIMARY KEY,
                                                             profile_name     VARCHAR(100),
        precision_score  NUMERIC(6,4),
        recall_score     NUMERIC(6,4),
        f1_score         NUMERIC(6,4),
        coverage         NUMERIC(6,4),
        acceptance_rate  NUMERIC(6,4),
        avg_score        NUMERIC(6,4),
        n_recommendations INTEGER,
        n_offers_generated INTEGER,
        evaluation_type  VARCHAR(20)  DEFAULT 'simulated',
        computed_at      TIMESTAMP    DEFAULT NOW(),
        model_version    VARCHAR(20)  DEFAULT 'V5.0'
        )
    """,
    """
    CREATE TABLE IF NOT EXISTS user_interactions_v5 (
                                                        id          BIGSERIAL PRIMARY KEY,
                                                        client_id   UUID        NOT NULL,
                                                        offer_code  VARCHAR(40) NOT NULL REFERENCES generated_offers(offer_code),
        action      VARCHAR(20) NOT NULL,
        recorded_at TIMESTAMP   DEFAULT NOW(),
        CONSTRAINT chk_action5 CHECK (action IN ('viewed','clicked','accepted','rejected'))
        )
    """,
    "CREATE INDEX IF NOT EXISTS idx_int5_client ON user_interactions_v5(client_id)",
    "CREATE INDEX IF NOT EXISTS idx_int5_offer  ON user_interactions_v5(offer_code)",
    """
    CREATE TABLE IF NOT EXISTS offer_generation_runs (
                                                         id             SERIAL PRIMARY KEY,
                                                         run_id         VARCHAR(50) NOT NULL UNIQUE,
        started_at     TIMESTAMP   DEFAULT NOW(),
        finished_at    TIMESTAMP,
        n_profiles     INTEGER,
        n_offers_gen   INTEGER,
        n_offers_new   INTEGER,
        n_offers_deact INTEGER,
        n_offers_archived  INTEGER DEFAULT 0,
        n_recos_archived   INTEGER DEFAULT 0,
        status         VARCHAR(20) DEFAULT 'RUNNING',
        error_msg      TEXT
        )
    """,
    # ── Colonnes ajoutées rétro-compatibles (si la table existait déjà) ──
    "ALTER TABLE offer_generation_runs ADD COLUMN IF NOT EXISTS n_offers_archived INTEGER DEFAULT 0",
    "ALTER TABLE offer_generation_runs ADD COLUMN IF NOT EXISTS n_recos_archived  INTEGER DEFAULT 0",
    # ── Historique des offres : on archive AVANT de purger à chaque run ──
    """
    CREATE TABLE IF NOT EXISTS generated_offers_history (
                                                            history_id        BIGSERIAL PRIMARY KEY,
                                                            archived_run_id   VARCHAR(50),
        archived_at       TIMESTAMP     DEFAULT NOW(),
        offer_code        VARCHAR(40)   NOT NULL,
        title             VARCHAR(300),
        type              VARCHAR(50),
        provider_name     VARCHAR(100),
        category          VARCHAR(100),
        cashback_pct      NUMERIC(5,2),
        discount_pct      NUMERIC(5,2),
        min_amount        NUMERIC(10,2),
        target_profiles   JSONB,
        boost             NUMERIC(4,2),
        description        TEXT,
        status            VARCHAR(10),
        generation_method VARCHAR(50),
        generation_run    VARCHAR(50),
        created_at        TIMESTAMP,
        updated_at        TIMESTAMP
        )
    """,
    "CREATE INDEX IF NOT EXISTS idx_goh_run  ON generated_offers_history(archived_run_id)",
    "CREATE INDEX IF NOT EXISTS idx_goh_code ON generated_offers_history(offer_code)",
    "CREATE INDEX IF NOT EXISTS idx_goh_date ON generated_offers_history(archived_at DESC)",
    # ── Historique des recommandations auto purgées à chaque run ──
    """
    CREATE TABLE IF NOT EXISTS recommendations_v5_history (
                                                              history_id        BIGSERIAL PRIMARY KEY,
                                                              archived_run_id   VARCHAR(50),
        archived_at        TIMESTAMP    DEFAULT NOW(),
        original_id        BIGINT,
        profile_name       VARCHAR(100),
        cluster_id         INTEGER,
        offer_code         VARCHAR(40),
        score              NUMERIC(6,4),
        score_profile      NUMERIC(6,4),
        score_provider     NUMERIC(6,4),
        score_category     NUMERIC(6,4),
        score_amount       NUMERIC(6,4),
        score_loyalty      NUMERIC(6,4),
        score_churn_boost  NUMERIC(6,4),
        is_targeted        BOOLEAN,
        recommendation_type VARCHAR(30),
        status             VARCHAR(20),
        admin_note         TEXT,
        description        TEXT,
        generated_at       TIMESTAMP,
        model_version      VARCHAR(20)
        )
    """,
    "CREATE INDEX IF NOT EXISTS idx_rh5_run  ON recommendations_v5_history(archived_run_id)",
    "CREATE INDEX IF NOT EXISTS idx_rh5_prof ON recommendations_v5_history(profile_name)",
    "CREATE INDEX IF NOT EXISTS idx_rh5_date ON recommendations_v5_history(archived_at DESC)",
]

def init_tables():
    logger.info("\n" + "─" * 70)
    logger.info("  INITIALISATION DES TABLES V5")
    logger.info("─" * 70)
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            for stmt in _DDL_STATEMENTS:
                s = stmt.strip()
                if s:
                    cur.execute(s)
        conn.commit()
        logger.info("  ✓ Tables V5 créées / vérifiées")
    except Exception as e:
        conn.rollback()
        logger.error(f"  ✗ Erreur init tables : {e}")
        raise
    finally:
        release_conn(conn)

# ==============================================================================
# PHASE 1 — BUSINESS UNDERSTANDING
# ==============================================================================
def phase1_business_understanding():
    logger.info("\n" + "█" * 70)
    logger.info("  PHASE 1 — BUSINESS UNDERSTANDING (CRISP-DM)")
    logger.info("█" * 70)
    logger.info("""
  CONTEXTE : Wallet Mobile — Marché FinTech Tunisien
  ─────────────────────────────────────────────────────────────────
  Population bancaire partielle, fort usage mobile, 6 profils comportementaux
  identifiés par wallet_classification.py. Ce système génère des offres
  AUTOMATIQUEMENT depuis les patterns réels — aucune offre codée en dur.

  SOURCE DES PROFILS : table 'client_profiles_v9' (wallet_classification.py)
  MODÈLES ML         : outputs/models/ (classifier.pkl, scaler.pkl)

  OBJECTIFS BUSINESS
  ────────────────────
  1. Réduire le churn       → offres anti-churn sur clients à risque (>0.5)
  2. Augmenter l'ARPU       → réductions premium sur profils actifs
  3. Réactiver dormants     → offres spéciales Micro-Utilisateur Passif
  4. Fidéliser stables      → programmes loyalty Utilisateur Essentiel
  5. Accélérer adoption     → packs digitaux Croissance Digitale
  6. Personnalisation réelle → offres générées depuis comportements réels
    """)

# ==============================================================================
# PHASE 2 — DATA UNDERSTANDING
# ==============================================================================
def phase2_data_understanding():
    logger.info("\n" + "█" * 70)
    logger.info("  PHASE 2 — DATA UNDERSTANDING (PostgreSQL)")
    logger.info("█" * 70)
    conn = get_conn()
    try:
        logger.info(f"  → Chargement {TABLE_CLIENT_PROFILES} (wallet_classification)...")

        # Log du dernier run (informatif uniquement)
        try:
            with conn.cursor() as cur:
                cur.execute(f"SELECT MAX(run_at) FROM {TABLE_MODEL_RUNS}")
                row = cur.fetchone()
                if row and row[0]:
                    logger.info(f"  ✓ Dernier run classification : {row[0]}")
        except Exception as e:
            logger.warning(f"  ⚠ Impossible de lire {TABLE_MODEL_RUNS} : {e}")
            # ⚠ IMPORTANT : si la requête échoue (table absente), la transaction
            # PostgreSQL passe en état « aborted ». On la réinitialise tout de
            # suite, sinon toutes les requêtes suivantes (chargement profils,
            # transactions…) échouent avec « current transaction is aborted ».
            try:
                conn.rollback()
            except Exception:
                pass

        # ✅ TOUJOURS CHARGER TOUS LES PROFILS — sans filtre de date
        profiles_query = f"""
            SELECT
                cp.client_id, cp.cluster_id, cp.profile_name, cp.profile_final,
                cp.is_mixte, cp.confidence_score, cp.gbm_confidence,
                cp.total_transactions, cp.freq_mensuelle, cp.montant_moyen,
                cp.montant_median, cp.montant_total, cp.regularite,
                cp.rfm_score, cp.loyalty_score, cp.momentum_court,
                cp.momentum_long, cp.recence_jours, cp.churn_score_30j,
                cp.churn_score_90j, cp.churn_segment, cp.ltv_12m, cp.in_holdout
            FROM {TABLE_CLIENT_PROFILES} cp
        """
        profiles = pd.read_sql(profiles_query, conn)
        logger.info(f"  ✓ {len(profiles):,} profils chargés depuis {TABLE_CLIENT_PROFILES}")

        if 'arpu_mensuel' not in profiles.columns:
            profiles['arpu_mensuel'] = (
                    profiles.get('freq_mensuelle', 0) *
                    profiles.get('montant_median', profiles.get('montant_moyen', 0))
            ).fillna(0)

            # ═══════════════════════════════════════════════════════
        # ✅ AJOUT : ROLLBACK avant les transactions
        # ═══════════════════════════════════════════════════════
        try:
            conn.rollback()
        except:
            pass

        # ✅ REQUÊTE DIRECTE - sans _probe_transactions_schema
        transactions = pd.DataFrame()
        try:
            txn_query = """
                        SELECT t.client_id, t.amount, t.transaction_date,
                               t.reversal_flag, p.provider_name,
                               tt.category, tt.sub_category
                        FROM transaction t
                                 LEFT JOIN provider p ON t.provider_id = p.id
                                 LEFT JOIN type_transaction tt ON t.transaction_type_id = tt.id
                        WHERE t.amount > 0
                            LIMIT 200000 \
                        """
            transactions = pd.read_sql(txn_query, conn)
            transactions['transaction_date'] = pd.to_datetime(transactions['transaction_date'], errors='coerce')
            transactions['amount'] = pd.to_numeric(transactions['amount'], errors='coerce')
            transactions.dropna(subset=['amount', 'client_id'], inplace=True)
            logger.info(f"  ✓ {len(transactions):,} transactions valides")
        except Exception as e:
            logger.warning(f"  ⚠ Transactions non chargées : {e}")

        # Provider
        try:
            providers_df = pd.read_sql("SELECT id, provider_code, provider_name FROM provider", conn)
            logger.info(f"  ✓ {len(providers_df)} providers")
        except Exception as e:
            providers_df = pd.DataFrame()
            logger.warning(f"  ⚠ Providers : {e}")

    except Exception as e:
        logger.error(f"  ✗ Erreur chargement données : {e}")
        raise
    finally:
        release_conn(conn)

    profiles['profile_final'] = profiles['profile_final'].fillna('').astype(str).str.strip()
    mask_empty = profiles['profile_final'] == ''
    if mask_empty.any():
        profiles.loc[mask_empty, 'profile_final'] = profiles.loc[mask_empty, 'profile_name']

    profiles_clean = profiles[
        (profiles['profile_final'] != '') &
        (~profiles['profile_final'].str.contains('MIXTE|Mixte|mixte|Incertain', na=False))
        ].copy()
    profiles_mixte = profiles[
        profiles['profile_final'].str.contains('MIXTE|Mixte|mixte|Incertain|^$', regex=True, na=True)
    ].copy()
    profiles_mixte['profile_final'] = 'Profil Mixte (Incertain)'

    logger.info(f"\n  Profils valides : {len(profiles_clean):,}")
    logger.info(f"  MIXTE/Incertain : {len(profiles_mixte):,}")
    profile_counts = profiles_clean['profile_final'].value_counts()
    for pn, cnt in profile_counts.items():
        logger.info(f"  {pn:<42}: {cnt:,}")

    _plot_phase2(profiles_clean, transactions, profile_counts)
    return profiles_clean, transactions, providers_df, profile_counts


def _probe_transactions_schema(conn) -> str:
    """Détecte dynamiquement le schéma de la table transactions."""
    table_name = None
    for t in ('transaction', 'transactions'):
        try:
            with conn.cursor() as cur:
                cur.execute(f"""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_name = '{t}' AND table_schema = 'public'
                """)
                cols = {row[0] for row in cur.fetchall()}
                if cols:
                    table_name = t
                    break
        except Exception:
            pass

    if table_name is None:
        raise ValueError("Table 'transaction' ou 'transactions' introuvable.")

    with conn.cursor() as cur:
        cur.execute(f"""
            SELECT column_name FROM information_schema.columns
            WHERE table_name = '{table_name}' AND table_schema = 'public'
        """)
        cols = {row[0] for row in cur.fetchall()}

    amount_col   = next((c for c in ['amount', 'montant', 'transaction_amount', 'txn_amount'] if c in cols), None)
    date_col     = next((c for c in ['transaction_date', 'date', 'txn_date', 'created_at', 'transaction_datetime'] if c in cols), None)
    reversal_col = next((c for c in ['reversal_flag', 'is_reversal', 'reversal', 'reversed'] if c in cols), None)
    provider_fk  = next((c for c in ['provider_id', 'fournisseur_id'] if c in cols), None)
    type_fk      = next((c for c in ['transaction_type_id', 'type_id', 'type_transaction_id'] if c in cols), None)
    client_fk    = next((c for c in ['client_id', 'user_id', 'customer_id'] if c in cols), None)

    if not amount_col or not client_fk:
        raise ValueError(f"Colonnes essentielles manquantes dans '{table_name}'. Trouvées : {cols}")

    sel, where, joins = [], [f"t.{amount_col} IS NOT NULL", f"t.{amount_col} > 0"], ""
    sel.append(f"t.{client_fk} AS client_id")
    sel.append(f"t.{amount_col} AS amount")
    sel.append(f"t.{date_col} AS transaction_date" if date_col else "NOW() AS transaction_date")

    if reversal_col:
        sel.append(f"t.{reversal_col} AS reversal_flag")
        where.append(f"(t.{reversal_col} = 'N' OR t.{reversal_col} IS NULL)")
    else:
        sel.append("'N' AS reversal_flag")

    if provider_fk:
        for prov_table in ('provider', 'providers'):
            try:
                with conn.cursor() as c:
                    c.execute(f"SELECT 1 FROM {prov_table} LIMIT 1")
                joins += f" LEFT JOIN {prov_table} p ON p.id = t.{provider_fk}"
                sel.append("p.provider_name AS provider_name")
                break
            except Exception:
                pass
        else:
            sel.append("NULL AS provider_name")
    else:
        sel.append("NULL AS provider_name")

    if type_fk:
        for tt_table in ('type_transactions', 'transaction_types'):
            try:
                with conn.cursor() as c:
                    c.execute(f"SELECT 1 FROM {tt_table} LIMIT 1")
                joins += f" LEFT JOIN {tt_table} tt ON tt.id = t.{type_fk}"
                sel.append("tt.category AS category")
                sel.append("tt.sub_category AS sub_category")
                break
            except Exception:
                pass
        else:
            sel.append("NULL AS category")
            sel.append("NULL AS sub_category")
    else:
        sel.append("NULL AS category")
        sel.append("NULL AS sub_category")

    return f"SELECT {', '.join(sel)} FROM {table_name} t{joins} WHERE {' AND '.join(where)}"


def _plot_phase2(profiles, transactions, profile_counts):
    fig_dir = os.path.join(OUT_DIR, "figures")
    fig, axes = plt.subplots(1, 2, figsize=(16, 6))
    fig.suptitle("Distribution des Profils Clients V5", fontsize=15, fontweight='bold')
    colors = [PALETTE.get(p, "#64748B") for p in profile_counts.index]
    wedges, _, autotexts = axes[0].pie(
        profile_counts.values, labels=None, colors=colors,
        autopct='%1.1f%%', startangle=140, pctdistance=0.82,
        wedgeprops=dict(width=0.55, edgecolor='white', linewidth=2))
    for at in autotexts:
        at.set_fontsize(9); at.set_fontweight('bold'); at.set_color('white')
    axes[0].set_title("Répartition par Profil")
    axes[0].legend(wedges, profile_counts.index, loc="center left",
                   bbox_to_anchor=(-0.3, 0.5), fontsize=8)
    bars = axes[1].barh(profile_counts.index, profile_counts.values, color=colors, edgecolor='white')
    for bar, val in zip(bars, profile_counts.values):
        axes[1].text(bar.get_width() + 10, bar.get_y() + bar.get_height()/2,
                     f'{val:,}', va='center', fontsize=9, fontweight='bold')
    axes[1].set_xlabel("Nombre de Clients"); axes[1].set_title("Volume par Profil")
    plt.tight_layout()
    plt.savefig(f"{fig_dir}/01_distribution_profils.png", dpi=150, bbox_inches='tight')
    plt.close()

    kpi_cols = ['montant_moyen', 'freq_mensuelle', 'rfm_score', 'churn_score_30j']
    kpi_labs = ['Montant Moyen (TND)', 'Fréq. Mensuelle', 'Score RFM', 'Score Churn 30j']
    fig, axes = plt.subplots(2, 2, figsize=(16, 10))
    fig.suptitle("KPI Comportementaux par Profil Client", fontsize=14, fontweight='bold')
    for ax, col, lab in zip(axes.flatten(), kpi_cols, kpi_labs):
        if col not in profiles.columns:
            ax.set_title(f"{lab} (N/A)"); continue
        grp = profiles.groupby('profile_final')[col].mean().sort_values(ascending=False)
        c = [PALETTE.get(p, '#64748B') for p in grp.index]
        ax.bar(range(len(grp)), grp.values, color=c, edgecolor='white')
        ax.set_xticks(range(len(grp)))
        ax.set_xticklabels([p.replace(' ', '\n') for p in grp.index], fontsize=7.5)
        ax.set_title(lab, fontweight='bold')
        for i, v in enumerate(grp.values):
            ax.text(i, v*1.02, f'{v:.2f}', ha='center', fontsize=8, fontweight='bold')
    plt.tight_layout()
    plt.savefig(f"{fig_dir}/02_kpi_comportementaux.png", dpi=150, bbox_inches='tight')
    plt.close()
    logger.info("  ✓ Figures Phase 2 générées")

# ==============================================================================
# PHASE 3 — DATA PREPARATION
# ==============================================================================
def phase3_data_preparation(profiles, transactions):
    logger.info("\n" + "█" * 70)
    logger.info("  PHASE 3 — DATA PREPARATION & FEATURE ENGINEERING")
    logger.info("█" * 70)
    pp_norm = pd.DataFrame()
    pc_norm = pd.DataFrame()
    profile_category_stats = {}
    profile_provider_stats = {}
    if len(transactions) > 0 and 'profile_final' in profiles.columns:
        df = transactions.merge(profiles[['client_id', 'profile_final']], on='client_id', how='inner')
        df = df[df['profile_final'].str.strip() != ''].copy()
        if len(df) > 0:
            pp = df.groupby(['profile_final', 'provider_name'])['amount'].agg(['count', 'mean']).reset_index()
            pp.columns = ['profile_final', 'provider_name', 'tx_count', 'amount_mean']
            pp_pivot = pp.pivot_table(index='profile_final', columns='provider_name', values='tx_count', fill_value=0)
            pp_norm = pp_pivot.div(pp_pivot.sum(axis=1), axis=0)
            pc = df.groupby(['profile_final', 'category'])['amount'].agg(['count', 'mean', 'sum']).reset_index()
            pc.columns = ['profile_final', 'category', 'tx_count', 'amount_mean', 'amount_sum']
            pc_pivot = pc.pivot_table(index='profile_final', columns='category', values='tx_count', fill_value=0)
            pc_norm = pc_pivot.div(pc_pivot.sum(axis=1), axis=0)
            for pname in df['profile_final'].unique():
                sub = df[df['profile_final'] == pname]
                profile_category_stats[pname] = {}
                for cat in sub['category'].dropna().unique():
                    sub_cat = sub[sub['category'] == cat]
                    profile_category_stats[pname][cat] = {
                        'count': len(sub_cat),
                        'amount_mean': float(sub_cat['amount'].mean()),
                        'amount_sum': float(sub_cat['amount'].sum()),
                        'ratio': len(sub_cat) / max(len(sub), 1),
                    }
                profile_provider_stats[pname] = {}
                for prov in sub['provider_name'].dropna().unique():
                    sub_prov = sub[sub['provider_name'] == prov]
                    profile_provider_stats[pname][prov] = {
                        'count': len(sub_prov),
                        'amount_mean': float(sub_prov['amount'].mean()),
                        'ratio': len(sub_prov) / max(len(sub), 1),
                    }
            logger.info(f"  ✓ Matrice profil×provider  : {pp_norm.shape}")
            logger.info(f"  ✓ Matrice profil×catégorie : {pc_norm.shape}")
            _plot_heatmaps(pp_norm, pc_norm)
        else:
            logger.warning("  ⚠ Join transactions×profils vide")
    else:
        logger.warning("  ⚠ Pas de transactions — matrices d'affinité vides")

    agg_cols = ['montant_moyen', 'montant_total', 'freq_mensuelle', 'rfm_score',
                'loyalty_score', 'total_transactions', 'recence_jours', 'churn_score_30j']
    agg = {'client_id': 'count'}
    for col in agg_cols:
        if col in profiles.columns:
            agg[col] = 'mean'
    profile_stats = profiles.groupby('profile_final').agg(agg).round(3)
    profile_stats.rename(columns={'client_id': 'nb_clients'}, inplace=True)
    num_cols = [c for c in agg_cols if c in profiles.columns]
    if len(num_cols) >= 3:
        fig, ax = plt.subplots(figsize=(12, 9))
        sns.heatmap(profiles[num_cols].corr(), annot=True, fmt='.2f',
                    cmap='coolwarm', linewidths=0.5, ax=ax, center=0, vmin=-1, vmax=1)
        ax.set_title("Matrice Corrélation — Features Comportementales", fontsize=13, fontweight='bold')
        plt.tight_layout()
        plt.savefig(f"{os.path.join(OUT_DIR,'figures')}/03_correlation_features.png",
                    dpi=150, bbox_inches='tight')
        plt.close()
    logger.info(f"  ✓ Stats agrégées : {len(profile_stats)} profils")
    return profile_stats, pp_norm, pc_norm, profile_category_stats, profile_provider_stats


def _plot_heatmaps(pp_norm, pc_norm):
    fig_dir = os.path.join(OUT_DIR, "figures")
    fig, axes = plt.subplots(1, 2, figsize=(20, 7))
    fig.suptitle("Matrices d'Affinité — Profil × Provider & Catégorie", fontsize=14, fontweight='bold')
    top_prov = pp_norm.sum(axis=0).nlargest(12).index
    if len(top_prov) > 0:
        sns.heatmap(pp_norm[top_prov], annot=True, fmt='.2f', cmap='Blues', linewidths=0.5, ax=axes[0])
        axes[0].set_xticklabels(axes[0].get_xticklabels(), rotation=40, ha='right', fontsize=8)
    axes[0].set_title("Profil × Provider (Top 12)")
    if not pc_norm.empty:
        sns.heatmap(pc_norm, annot=True, fmt='.2f', cmap='Greens', linewidths=0.5, ax=axes[1])
        axes[1].set_xticklabels(axes[1].get_xticklabels(), rotation=40, ha='right', fontsize=8)
    axes[1].set_title("Profil × Catégorie")
    plt.tight_layout()
    plt.savefig(f"{fig_dir}/04_heatmap_affinites.png", dpi=150, bbox_inches='tight')
    plt.close()
    logger.info("  ✓ Heatmaps affinités générées")

# ==============================================================================
# GÉNÉRATION D'OFFRES (TOUTES CIBLÉES CATÉGORIE / PROVIDER, SANS CASHBACK)
# ==============================================================================
def _make_offer_code(profile: str, offer_type: str, target: str) -> str:
    raw = f"{profile}|{offer_type}|{target}".lower().replace(' ', '_')
    h = hashlib.md5(raw.encode()).hexdigest()[:8].upper()
    return f"AUTO_{h}"

def _generate_loyalty_category_offer(profile_name: str, profile_data, category: str, cat_stats: dict) -> Optional[dict]:
    cfg = OFFER_GENERATION_CONFIG
    freq = float(getattr(profile_data, 'freq_mensuelle', 0) or 0)
    loyalty = float(getattr(profile_data, 'loyalty_score', 0) or 0)
    txn = float(getattr(profile_data, 'total_transactions', 0) or 0)
    if txn < cfg['min_txn_for_loyalty'] or freq < 3:
        return None
    discount = 10 if loyalty > 0.7 else 5
    cat_clean = str(category).replace('_', ' ').title()
    ratio = cat_stats.get('ratio', 0.0)
    return {
        "offer_code": _make_offer_code(profile_name, "loyalty_cat", category),
        "title": f"Fidélité {cat_clean} -{discount}%",
        "type": "loyalty",
        "provider_name": None,
        "category": category,
        "cashback_pct": 0.0,
        "discount_pct": float(discount),
        "min_amount": 0.0,
        "target_profiles": [profile_name],
        "boost": 1.0 + loyalty * 0.6,
        "description": f"Programme fidélité : -{discount}% sur vos achats en {cat_clean}. "
                       f"({ratio*100:.0f}% de vos transactions). Score fidélité : {loyalty:.2f}.",
        "generation_method": "auto_loyalty_category",
    }

def _generate_provider_discount_offer(profile_name: str, profile_data, provider: str, prov_stats: dict) -> Optional[dict]:
    cfg = OFFER_GENERATION_CONFIG
    ratio = prov_stats.get('ratio', 0.0)
    if ratio < 0.05:
        return None
    discount = 15 if ratio > 0.25 else 10 if ratio > 0.15 else 7
    amt_mean = prov_stats.get('amount_mean', 50.0)
    min_amt = round(max(amt_mean * 0.5, 10.0), 0)
    return {
        "offer_code": _make_offer_code(profile_name, "provider_discount", provider),
        "title": f"Réduction {provider} -{discount}%",
        "type": "provider_bonus",
        "provider_name": provider,
        "category": None,
        "cashback_pct": 0.0,
        "discount_pct": float(discount),
        "min_amount": min_amt,
        "target_profiles": [profile_name],
        "boost": 1.3 if ratio > 0.20 else 1.1,
        "description": f"{discount}% de réduction sur vos paiements chez {provider} (dès {min_amt:.0f} TND). "
                       f"Basé sur {ratio*100:.1f}% de votre activité.",
        "generation_method": "auto_provider_discount",
    }

def _generate_growth_category_offer(profile_name: str, profile_data, category: str, cat_stats: dict) -> Optional[dict]:
    momentum = float(getattr(profile_data, 'momentum_court', 0) or 0)
    if momentum < 0.2:
        return None
    discount = 10.0
    cat_clean = str(category).replace('_', ' ').title()
    return {
        "offer_code": _make_offer_code(profile_name, "growth_cat", category),
        "title": f"Croissance {cat_clean} -{discount}%",
        "type": "growth",
        "provider_name": None,
        "category": category,
        "cashback_pct": 0.0,
        "discount_pct": discount,
        "min_amount": 0.0,
        "target_profiles": [profile_name],
        "boost": 1.0 + momentum * 0.8,
        "description": f"Profitez de votre élan : -{discount}% sur vos achats en {cat_clean}. "
                       f"Momentum actuel : {momentum:.2f}.",
        "generation_method": "auto_growth_category",
    }

def _generate_reactivation_offer(profile_name: str, profile_data, target_entity: dict) -> Optional[dict]:
    cfg = OFFER_GENERATION_CONFIG
    recence = float(getattr(profile_data, 'recence_jours', 0) or 0)
    is_dormant = recence > 30
    if not is_dormant:
        return None
    discount = 20 if recence > 60 else 15
    desc_suffix = ""
    if target_entity['type'] == 'category':
        category = target_entity['name']
        cat_clean = str(category).replace('_', ' ').title()
        provider = None
        desc_suffix = f" sur vos achats en {cat_clean}"
    else:
        provider = target_entity['name']
        category = None
        desc_suffix = f" chez {provider}"
    return {
        "offer_code": _make_offer_code(profile_name, "reactivation", target_entity['name']),
        "title": f"Offre Retour {discount}% — Bienvenue à Nouveau",
        "type": "reactivation",
        "provider_name": provider,
        "category": category,
        "cashback_pct": 0.0,
        "discount_pct": float(discount),
        "min_amount": 0.0,
        "target_profiles": [profile_name],
        "boost": 2.0,
        "description": f"Offre spéciale retour : {discount}% de réduction{desc_suffix}. "
                       f"Dernière activité : {recence:.0f} jours. Valable 30 jours.",
        "generation_method": "auto_reactivation",
    }

def _generate_churn_prevention_offer(profile_name: str, profile_data, target_entity: dict) -> Optional[dict]:
    churn = float(getattr(profile_data, 'churn_score_30j', 0) or 0)
    if churn < OFFER_GENERATION_CONFIG['churn_threshold']:
        return None
    intensity = "Fort" if churn > 0.75 else "Modéré"
    discount = 15 if churn > 0.75 else 10
    desc_suffix = ""
    if target_entity['type'] == 'category':
        category = target_entity['name']
        cat_clean = str(category).replace('_', ' ').title()
        provider = None
        desc_suffix = f" sur vos achats en {cat_clean}"
    else:
        provider = target_entity['name']
        category = None
        desc_suffix = f" chez {provider}"
    return {
        "offer_code": _make_offer_code(profile_name, "anti_churn", target_entity['name']),
        "title": f"Offre Rétention {intensity} -{discount}% — Restez avec nous",
        "type": "anti_churn",
        "provider_name": provider,
        "category": category,
        "cashback_pct": 0.0,
        "discount_pct": float(discount),
        "min_amount": 0.0,
        "target_profiles": [profile_name],
        "boost": 1.5 + churn * 0.5,
        "description": f"Offre personnalisée rétention : {discount}% de réduction{desc_suffix}. "
                       f"Risque de désabonnement détecté : {churn*100:.1f}%. Offre valable 15 jours uniquement.",
        "generation_method": "auto_anti_churn",
    }


class OfferGenerationEngine:
    def __init__(self, config: dict = None):
        self.config = config or OFFER_GENERATION_CONFIG
        self.run_id = datetime.now().strftime("RUN_%Y%m%d_%H%M%S")
        self._generated_codes = set()

    def generate_all_offers(self, profile_stats: pd.DataFrame,
                            profile_category_stats: dict,
                            profile_provider_stats: dict) -> List[dict]:
        logger.info(f"\n  MOTEUR DE GÉNÉRATION — Run {self.run_id}")
        logger.info("  (offres 100% personnalisées par catégorie/provider, sans cashback)")
        logger.info("  " + "─" * 60)
        all_offers = []
        cfg = self.config
        top_cat_n = cfg.get('TOP_CATEGORIES_PER_PROFILE', 2)
        top_prov_n = cfg.get('TOP_PROVIDERS_PER_PROFILE', 2)

        for profile_name, p_data in profile_stats.iterrows():
            logger.info(f"  → Profil : {profile_name}")

            # Récupération des tops catégories / providers réels du profil
            cat_dict = profile_category_stats.get(profile_name, {})
            prov_dict = profile_provider_stats.get(profile_name, {})
            sorted_cats = sorted(cat_dict.items(), key=lambda x: x[1].get('count', 0), reverse=True)[:top_cat_n]
            sorted_provs = sorted(prov_dict.items(), key=lambda x: x[1].get('count', 0), reverse=True)[:top_prov_n]

            # Si aucune catégorie ni provider, passer ce profil
            if not sorted_cats and not sorted_provs:
                logger.warning(f"     ⚠ Aucune catégorie/provider pour {profile_name}, ignoré.")
                continue

            profile_offers = []

            # 1) Offres liées aux catégories
            for cat_name, cst in sorted_cats:
                if not cat_name:
                    continue
                off = _generate_loyalty_category_offer(profile_name, p_data, cat_name, cst)
                if off: profile_offers.append(off)
                off = _generate_growth_category_offer(profile_name, p_data, cat_name, cst)
                if off: profile_offers.append(off)

            # 2) Offres liées aux providers
            for prov_name, pst in sorted_provs:
                if not prov_name:
                    continue
                off = _generate_provider_discount_offer(profile_name, p_data, prov_name, pst)
                if off: profile_offers.append(off)

            # 3) Offres de réactivation / rétention (on choisit l'entité la plus représentative)
            top_entity = None
            if sorted_cats or sorted_provs:
                # On prend la catégorie si elle a plus de transactions que le meilleur provider
                if sorted_cats and (not sorted_provs or sorted_cats[0][1]['count'] >= sorted_provs[0][1]['count']):
                    top_entity = {'type': 'category', 'name': sorted_cats[0][0]}
                elif sorted_provs:
                    top_entity = {'type': 'provider', 'name': sorted_provs[0][0]}

            if top_entity:
                off = _generate_reactivation_offer(profile_name, p_data, top_entity)
                if off: profile_offers.append(off)
                off = _generate_churn_prevention_offer(profile_name, p_data, top_entity)
                if off: profile_offers.append(off)

            logger.info(f"     → {len(profile_offers)} offres générées")
            for off in profile_offers:
                off['generation_run'] = self.run_id
                off['status'] = 'ACTIVE'
                if off['offer_code'] not in self._generated_codes:
                    self._generated_codes.add(off['offer_code'])
                    all_offers.append(off)

        logger.info(f"\n  ✓ Total offres générées : {len(all_offers)} (uniques)")
        return all_offers

    def persist_offers(self, offers: List[dict]) -> tuple:
        if not offers:
            return 0, 0, 0
        conn = get_conn()
        n_new = n_updated = n_deactivated = 0
        try:
            with conn.cursor() as cur:
                existing = set()
                cur.execute("SELECT offer_code FROM generated_offers WHERE status='ACTIVE'")
                for row in cur.fetchall():
                    existing.add(row[0])
                current_codes = {o['offer_code'] for o in offers}
                for off in offers:
                    if off['offer_code'] in existing:
                        cur.execute("""
                                    UPDATE generated_offers SET
                                                                title=%(title)s, type=%(type)s,
                                                                provider_name=%(provider_name)s, category=%(category)s,
                                                                cashback_pct=%(cashback_pct)s, discount_pct=%(discount_pct)s,
                                                                min_amount=%(min_amount)s,
                                                                target_profiles=%(target_profiles)s::jsonb,
                                boost=%(boost)s, description=%(description)s,
                                        generation_run=%(generation_run)s,
                                        updated_at=NOW()
                                    WHERE offer_code=%(offer_code)s
                                    """, {**off, 'target_profiles': json.dumps(off['target_profiles'])})
                        n_updated += 1
                    else:
                        cur.execute("""
                                    INSERT INTO generated_offers
                                    (offer_code,title,type,provider_name,category,
                                     cashback_pct,discount_pct,min_amount,
                                     target_profiles,boost,description,status,
                                     generation_method,generation_run,created_at,updated_at)
                                    VALUES (%(offer_code)s,%(title)s,%(type)s,%(provider_name)s,
                                            %(category)s,%(cashback_pct)s,%(discount_pct)s,
                                            %(min_amount)s,%(target_profiles)s::jsonb,
                                            %(boost)s,%(description)s,'ACTIVE',
                                            %(generation_method)s,%(generation_run)s,NOW(),NOW())
                                    """, {**off, 'target_profiles': json.dumps(off['target_profiles'])})
                        n_new += 1
                obsolete = existing - current_codes
                if obsolete:
                    cur.execute("""
                                UPDATE generated_offers
                                SET status='INACTIVE', updated_at=NOW()
                                WHERE offer_code = ANY(%s)
                                  AND generation_method != 'manual'
                                """, (list(obsolete),))
                    n_deactivated = len(obsolete)
            conn.commit()
            logger.info(f"  ✓ Offres → nouvelles:{n_new} | màj:{n_updated} | désactivées:{n_deactivated}")
            return n_new, n_updated, n_deactivated
        except Exception as e:
            conn.rollback()
            logger.error(f"  ✗ Erreur persistance offres : {e}")
            raise
        finally:
            release_conn(conn)

    def load_active_offers_from_db(self) -> List[dict]:
        conn = get_conn()
        try:
            with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
                cur.execute("""
                            SELECT offer_code,title,type,provider_name,category,
                                   cashback_pct,discount_pct,min_amount,
                                   target_profiles,boost,description,status
                            FROM generated_offers
                            WHERE status='ACTIVE'
                            ORDER BY boost DESC, cashback_pct DESC
                            """)
                rows = cur.fetchall()
                offers = []
                for r in rows:
                    d = dict(r)
                    if isinstance(d['target_profiles'], str):
                        d['target_profiles'] = json.loads(d['target_profiles'])
                    offers.append(d)
                return offers
        finally:
            release_conn(conn)

# ==============================================================================
# SCORING HYBRIDE
# ==============================================================================
def _get_val(p, key: str, default: float = 0.0) -> float:
    try:
        if hasattr(p, 'get'):
            return float(p.get(key, default) or default)
        elif hasattr(p, '__getitem__'):
            v = p[key] if key in p.index else default
            return float(v if pd.notna(v) else default)
    except Exception:
        pass
    return float(default)

def score_offer(offer: dict, profile_name: str, profile_data, pp_norm: pd.DataFrame, pc_norm: pd.DataFrame) -> dict:
    EXCLUSIVE = {"reactivation", "anti_churn"}
    if (offer.get('type') in EXCLUSIVE and profile_name not in offer.get('target_profiles', [])):
        return dict(score=0.0, score_profile=0.0, score_provider=0.0,
                    score_category=0.0, score_amount=0.0, score_loyalty=0.0,
                    score_churn_boost=0.0, is_targeted=False)
    w = {"profile": 0.35, "provider": 0.20, "category": 0.25, "amount": 0.10, "loyalty": 0.10}
    s_profile = 1.0 if profile_name in offer.get('target_profiles', []) else 0.2
    prov = offer.get('provider_name')
    if prov and not pp_norm.empty and profile_name in pp_norm.index:
        s_prov = min(float(pp_norm.loc[profile_name].get(prov, 0.0)) * 5, 1.0)
    else:
        s_prov = 0.1 if prov else 0.3
    cat = offer.get('category')
    if cat and not pc_norm.empty and profile_name in pc_norm.index:
        s_cat = min(float(pc_norm.loc[profile_name].get(cat, 0.0)) * 5, 1.0)
    else:
        s_cat = 0.1 if cat else 0.3
    amt = _get_val(profile_data, 'montant_moyen', 50.0)
    min_a = float(offer.get('min_amount', 0))
    if min_a == 0:
        s_amt = 1.0
    elif amt >= min_a*2:
        s_amt = 1.0
    elif amt >= min_a:
        s_amt = 0.7
    elif amt >= min_a*0.5:
        s_amt = 0.4
    else:
        s_amt = 0.15
    rfm = _get_val(profile_data, 'rfm_score', 0.5)
    s_loy = min(rfm, 1.0)
    raw = (w["profile"]*s_profile + w["provider"]*s_prov + w["category"]*s_cat
           + w["amount"]*s_amt + w["loyalty"]*s_loy)
    boosted = raw * float(offer.get('boost', 1.0))
    churn = _get_val(profile_data, 'churn_score_30j', 0.0)
    if offer.get('type') in ('loyalty', 'reactivation', 'anti_churn') and churn > 0.5:
        churn_boost = 1.0 + (churn - 0.5) * 0.6
        s_churn = round(churn_boost - 1.0, 4)
    else:
        churn_boost = 1.0
        s_churn = 0.0
    final = round(min(boosted * churn_boost, 1.0), 4)
    return dict(score=final, score_profile=round(s_profile, 4),
                score_provider=round(s_prov, 4), score_category=round(s_cat, 4),
                score_amount=round(s_amt, 4), score_loyalty=round(s_loy, 4),
                score_churn_boost=s_churn,
                is_targeted=profile_name in offer.get('target_profiles', []))

def _cluster_id_for(profile_name: str) -> int:
    for k, v in PROFILE_NAMES.items():
        if v == profile_name:
            return k
    return -1

# ==============================================================================
# GÉNÉRATION DE DESCRIPTION
# ==============================================================================
def generate_offer_description(offer: dict, score: float) -> str:
    desc = "✨ Offre spéciale pour vous !\n"
    discount = offer.get('discount_pct', 0)
    if discount > 0:
        desc += f"🎁 Bénéficiez de {discount:.1f}% de réduction immédiate.\n"
    category = offer.get('category')
    if category and category not in ('', 'None', 'null'):
        desc += f"🛍️ Cette offre concerne la catégorie {category}.\n"
    provider = offer.get('provider_name')
    if provider and provider not in ('', 'None', 'null'):
        desc += f"🏢 Partenaire : {provider}.\n"
    min_amount = offer.get('min_amount', 0)
    if min_amount > 0:
        desc += f"💰 À partir de {min_amount:.0f} TND d'achat.\n"
    else:
        desc += "💰 Aucun montant minimum exigé.\n"
    offer_type = offer.get('type', '')
    if offer_type == 'loyalty':
        desc += "🏆 Offre de fidélité – récompense pour votre confiance.\n"
    elif offer_type == 'reactivation':
        desc += "🌟 Offre de retour – nous sommes heureux de vous revoir !\n"
    elif offer_type == 'anti_churn':
        desc += "💎 Offre spéciale rétention – nous tenons à vous fidéliser.\n"
    desc += "⏳ Offre valable 15 jours – profitez-en vite !\n"
    return desc

# ==============================================================================
# PHASE 4 — MODELING
# ==============================================================================
def phase4_modeling(profile_stats, pp_norm, pc_norm, profile_category_stats, profile_provider_stats):
    logger.info("\n" + "█" * 70)
    logger.info("  PHASE 4 — GÉNÉRATION AUTOMATIQUE DES OFFRES + SCORING HYBRIDE")
    logger.info("█" * 70)
    engine = OfferGenerationEngine()
    generated_offers = engine.generate_all_offers(profile_stats, profile_category_stats, profile_provider_stats)
    n_new, n_upd, n_deact = engine.persist_offers(generated_offers)
    active_offers = engine.load_active_offers_from_db()
    logger.info(f"  ✓ {len(active_offers)} offres actives chargées depuis DB")
    records = []
    for profile_name, p_data in profile_stats.iterrows():
        for offer in active_offers:
            if not offer.get('target_profiles'):
                continue
            sc = score_offer(offer, profile_name, p_data, pp_norm, pc_norm)
            rtype = ("profile_based" if sc['is_targeted']
                     else "content_based" if sc['score_category'] > 0.4
            else "collaborative" if sc['score_provider'] > 0.3
            else "rule_based")
            description = generate_offer_description(offer, sc['score'])
            records.append({
                "profile": profile_name,
                "cluster_id": _cluster_id_for(profile_name),
                "offer_code": offer['offer_code'],
                "offer_title": offer['title'],
                "offer_type": offer['type'],
                "provider": offer.get('provider_name') or "Tous",
                "category": offer.get('category') or "Toutes",
                "cashback_pct": float(offer.get('cashback_pct', 0)),
                "discount_pct": float(offer.get('discount_pct', 0)),
                "recommendation_type": rtype,
                "description": description,
                **sc,
            })
    reco_df = pd.DataFrame(records).sort_values(['profile', 'score'], ascending=[True, False])
    top_reco = (reco_df[reco_df['score'] >= 0.40]
                .groupby('profile').head(5).reset_index(drop=True))
    logger.info(f"\n  Total scorés     : {len(reco_df):,}")
    logger.info(f"  Profils traités  : {reco_df['profile'].nunique()}")
    logger.info(f"  Qualifiés (≥0.40): {len(top_reco)}")
    _plot_phase4(reco_df, top_reco, generated_offers)
    return reco_df, top_reco, generated_offers, engine

def phase4_scoring_only(profile_stats, pp_norm, pc_norm, active_offers):
    logger.info("  → Scoring uniquement (offres existantes)")
    records = []
    for profile_name, p_data in profile_stats.iterrows():
        for offer in active_offers:
            sc = score_offer(offer, profile_name, p_data, pp_norm, pc_norm)
            rtype = ("profile_based" if sc['is_targeted']
                     else "content_based" if sc['score_category'] > 0.4
            else "collaborative" if sc['score_provider'] > 0.3
            else "rule_based")
            description = generate_offer_description(offer, sc['score'])
            records.append({
                "profile": profile_name,
                "cluster_id": _cluster_id_for(profile_name),
                "offer_code": offer['offer_code'],
                "score": sc['score'],
                "score_profile": sc['score_profile'],
                "score_provider": sc['score_provider'],
                "score_category": sc['score_category'],
                "score_amount": sc['score_amount'],
                "score_loyalty": sc['score_loyalty'],
                "score_churn_boost": sc['score_churn_boost'],
                "is_targeted": sc['is_targeted'],
                "recommendation_type": rtype,
                "description": description,
            })
    reco_df = pd.DataFrame(records).sort_values(['profile', 'score'], ascending=[True, False])
    top_reco = (reco_df[reco_df['score'] >= 0.40]
                .groupby('profile').head(5).reset_index(drop=True))
    return top_reco

def _plot_phase4(reco_df, top_reco, generated_offers):
    fig_dir = os.path.join(OUT_DIR, "figures")
    types_count = pd.Series([o['type'] for o in generated_offers]).value_counts()
    fig, axes = plt.subplots(1, 2, figsize=(16, 6))
    fig.suptitle("Offres Générées Automatiquement — Analyse", fontsize=14, fontweight='bold')
    axes[0].bar(types_count.index, types_count.values,
                color=sns.color_palette("tab10", len(types_count)), edgecolor='white')
    axes[0].set_title("Types d'offres générées")
    axes[0].tick_params(axis='x', rotation=30)
    for i, v in enumerate(types_count.values):
        axes[0].text(i, v+0.2, str(v), ha='center', fontweight='bold')
    sm = reco_df.pivot_table(index='profile', columns='offer_code', values='score')
    if not sm.empty:
        sns.heatmap(sm.iloc[:, :20], annot=False, cmap='RdYlGn',
                    linewidths=0.3, ax=axes[1], vmin=0, vmax=1)
        axes[1].set_title("Heatmap Scores (20 premières offres)")
        axes[1].set_xlabel("Offres")
    plt.tight_layout()
    plt.savefig(f"{fig_dir}/05_generated_offers_analysis.png", dpi=150, bbox_inches='tight')
    plt.close()
    profs = list(top_reco['profile'].unique())
    nc = 3; nr = max(1, (len(profs)+nc-1)//nc)
    fig2, axes2 = plt.subplots(nr, nc, figsize=(20, 5*nr))
    fig2.suptitle("Top 5 Recommandations par Profil (Offres Auto-Générées)", fontsize=14, fontweight='bold')
    axes_flat = (axes2.flatten() if hasattr(axes2, 'flatten') else
                 [a for row in axes2 for a in (row if hasattr(row, '__iter__') else [row])])
    for i, pname in enumerate(profs):
        sub = top_reco[top_reco['profile']==pname].head(5)
        c = PALETTE.get(pname, '#64748B')
        titles = [t[:32]+'…' if len(t)>32 else t for t in sub['offer_title']]
        bars = axes_flat[i].barh(titles, sub['score'], color=c, edgecolor='white', alpha=0.88)
        for bar, s in zip(bars, sub['score']):
            axes_flat[i].text(bar.get_width()+0.01, bar.get_y()+bar.get_height()/2,
                              f'{s:.2f}', va='center', fontsize=9, fontweight='bold')
        axes_flat[i].set_xlim(0, 1.2)
        axes_flat[i].set_title(pname, fontweight='bold', color=c, fontsize=10)
    for j in range(len(profs), len(axes_flat)):
        axes_flat[j].axis('off')
    plt.tight_layout()
    plt.savefig(f"{fig_dir}/06_top_reco_par_profil.png", dpi=150, bbox_inches='tight')
    plt.close()
    logger.info("  ✓ Figures Phase 4 générées")

# ==============================================================================
# PHASE 5 — ÉVALUATION (inchangée)
# ==============================================================================
def phase5_evaluation(top_reco: pd.DataFrame) -> pd.DataFrame:
    logger.info("\n" + "█" * 70)
    logger.info("  PHASE 5 — ÉVALUATION DU SYSTÈME V5")
    logger.info("█" * 70)
    real = _load_real_interactions()
    if len(real) > 0:
        logger.info(f"  ✓ ÉVALUATION RÉELLE — {len(real):,} interactions")
        ev = _eval_real(top_reco, real)
        eval_type = 'real'
    else:
        logger.info("  ⚠ ÉVALUATION SIMULÉE (user_interactions_v5 vide)")
        ev = _eval_simulated(top_reco)
        eval_type = 'simulated'
    eval_df = pd.DataFrame(ev)
    logger.info(f"\n  {'Profil':<42} {'Prec':>6} {'Recall':>7} {'F1':>6} {'Acc%':>6}")
    logger.info("  " + "─" * 65)
    for _, r in eval_df.iterrows():
        logger.info(f"  {r['profile']:<42} {r['precision']:>6.3f} {r['recall']:>7.3f}"
                    f" {r['f1']:>6.3f} {r['acceptance_rate']*100:>5.1f}%")
    logger.info(f"\n  MOYENNE → P:{eval_df['precision'].mean():.3f}"
                f" | R:{eval_df['recall'].mean():.3f}"
                f" | F1:{eval_df['f1'].mean():.3f}")
    _save_metrics(eval_df, eval_type)
    _plot_eval(eval_df)
    eval_df.to_csv(f"{OUT_DIR}/evaluation_metrics_v5.csv", index=False)
    return eval_df

def _load_real_interactions() -> pd.DataFrame:
    conn = get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("SELECT COUNT(*) AS n FROM user_interactions_v5")
            if cur.fetchone()['n'] == 0:
                return pd.DataFrame()
        return pd.read_sql(f"""
            SELECT ui.client_id, ui.offer_code, ui.action,
                   cp.profile_final AS profile
            FROM user_interactions_v5 ui
            LEFT JOIN {TABLE_CLIENT_PROFILES} cp ON cp.client_id = ui.client_id
        """, conn)
    except Exception:
        return pd.DataFrame()
    finally:
        release_conn(conn)

def _eval_real(top_reco, interactions) -> list:
    rows = []
    for pname in top_reco['profile'].unique():
        sub = top_reco[top_reco['profile']==pname]
        intr = interactions[interactions['profile']==pname]
        accepted = set(intr[intr['action'].isin(['accepted', 'clicked'])]['offer_code'])
        sub_codes = set(sub['offer_code'])
        tp = len(sub_codes & accepted); fp = len(accepted - sub_codes); fn = len(sub_codes - accepted)
        p = tp/(tp+fp) if (tp+fp)>0 else 0.0
        r = tp/(tp+fn) if (tp+fn)>0 else 0.0
        f = 2*p*r/(p+r) if (p+r)>0 else 0.0
        rows.append({"profile": pname, "precision": round(p, 3), "recall": round(r, 3), "f1": round(f, 3),
                     "coverage": round(len(sub[sub['score']>=0.5])/max(len(sub), 1), 3),
                     "acceptance_rate": round(len(accepted)/max(len(sub), 1), 3),
                     "avg_score": round(sub['score'].mean(), 3), "n_recommendations": len(sub)})
    return rows

def _eval_simulated(top_reco) -> list:
    np.random.seed(42)
    ev = top_reco.copy()
    noise = np.random.normal(0, 0.08, len(ev))
    ev['acceptance_prob'] = (ev['score']*0.65 + 0.1 + noise).clip(0.05, 0.92)
    ev.loc[~ev['is_targeted'], 'acceptance_prob'] *= 0.5
    ev['accepted'] = ev['acceptance_prob'] > 0.45
    ev['relevant'] = ev['is_targeted']
    rows = []
    for pname in ev['profile'].unique():
        sub = ev[ev['profile']==pname]
        tp = ((sub['accepted']) & (sub['relevant'])).sum()
        fp = ((sub['accepted']) & (~sub['relevant'])).sum()
        fn = ((~sub['accepted']) & (sub['relevant'])).sum()
        p = tp/(tp+fp) if (tp+fp)>0 else 0.0
        r = tp/(tp+fn) if (tp+fn)>0 else 0.0
        f = 2*p*r/(p+r) if (p+r)>0 else 0.0
        rows.append({"profile": pname, "precision": round(p, 3), "recall": round(r, 3), "f1": round(f, 3),
                     "coverage": round(len(sub[sub['accepted']])/max(len(sub), 1), 3),
                     "acceptance_rate": round(sub['accepted'].mean(), 3),
                     "avg_score": round(sub['score'].mean(), 3), "n_recommendations": len(sub)})
    return rows

def _save_metrics(eval_df, eval_type):
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM recommendation_metrics_v5 WHERE model_version='V5.0' AND evaluation_type=%s", (eval_type,))
            execute_values(cur, """
                                INSERT INTO recommendation_metrics_v5
                                (profile_name,precision_score,recall_score,f1_score,coverage,
                                 acceptance_rate,avg_score,n_recommendations,evaluation_type,model_version)
                                VALUES %s
                                """, [(r['profile'], r['precision'], r['recall'], r['f1'], r['coverage'],
                                       r['acceptance_rate'], r['avg_score'], r['n_recommendations'], eval_type, 'V5.0')
                                      for _, r in eval_df.iterrows()])
        conn.commit()
        logger.info(f"  ✓ Métriques sauvegardées (type='{eval_type}')")
    except Exception as e:
        conn.rollback()
        logger.warning(f"  ⚠ Métriques non sauvegardées : {e}")
    finally:
        release_conn(conn)

def _plot_eval(eval_df):
    fig_dir = os.path.join(OUT_DIR, "figures")
    bar_colors = [PALETTE.get(p, '#64748B') for p in eval_df['profile']]
    fig, axes = plt.subplots(2, 2, figsize=(16, 10))
    fig.suptitle("Dashboard Évaluation V5 — Offres Auto-Générées", fontsize=14, fontweight='bold')
    x = np.arange(len(eval_df)); w = 0.35
    axes[0,0].bar(x-w/2, eval_df['precision'], w, label='Precision', color='#2563EB', alpha=0.85)
    axes[0,0].bar(x+w/2, eval_df['recall'], w, label='Recall', color='#10B981', alpha=0.85)
    axes[0,0].set_xticks(x)
    axes[0,0].set_xticklabels([p.replace(' ', '\n') for p in eval_df['profile']], fontsize=7)
    axes[0,0].set_ylim(0, 1.2); axes[0,0].legend(); axes[0,0].set_title("Precision & Recall")
    axes[0,1].bar(range(len(eval_df)), eval_df['f1'], color=bar_colors, edgecolor='white')
    axes[0,1].set_xticks(range(len(eval_df)))
    axes[0,1].set_xticklabels([p[:20] for p in eval_df['profile']], rotation=30, ha='right', fontsize=8)
    axes[0,1].set_ylim(0, 1.15); axes[0,1].set_title("F1-Score")
    for i, v in enumerate(eval_df['f1']):
        axes[0,1].text(i, v+0.02, f'{v:.2f}', ha='center', fontsize=9, fontweight='bold')
    axes[1,0].bar(range(len(eval_df)), eval_df['acceptance_rate'], color=bar_colors, edgecolor='white', alpha=0.88)
    axes[1,0].set_xticks(range(len(eval_df)))
    axes[1,0].set_xticklabels([p[:20] for p in eval_df['profile']], rotation=30, ha='right', fontsize=8)
    axes[1,0].set_ylim(0, 1.15); axes[1,0].set_title("Taux d'Acceptation")
    for i, v in enumerate(eval_df['acceptance_rate']):
        axes[1,0].text(i, v+0.02, f'{v:.0%}', ha='center', fontsize=9, fontweight='bold')
    cats = ['precision', 'recall', 'f1', 'coverage', 'acceptance_rate']
    angles = np.linspace(0, 2*np.pi, len(cats), endpoint=False).tolist() + [0]
    ax_r = fig.add_subplot(2, 2, 4, polar=True)
    for _, r in eval_df.iterrows():
        vals = [r[c] for c in cats] + [r[cats[0]]]
        col = PALETTE.get(r['profile'], '#64748B')
        ax_r.plot(angles, vals, 'o-', lw=1.8, color=col, alpha=0.9, label=r['profile'][:20])
        ax_r.fill(angles, vals, alpha=0.07, color=col)
    ax_r.set_xticks(angles[:-1]); ax_r.set_xticklabels(cats, fontsize=9)
    ax_r.set_ylim(0, 1.0); ax_r.set_title("Vue Radar", fontweight='bold', pad=20)
    ax_r.legend(loc='upper right', bbox_to_anchor=(1.5, 1.1), fontsize=7)
    plt.tight_layout()
    plt.savefig(f"{fig_dir}/07_evaluation_dashboard.png", dpi=150, bbox_inches='tight')
    plt.close()
    logger.info("  ✓ Dashboard évaluation généré")

# ==============================================================================
# ARCHIVAGE + PURGE (à chaque run, AVANT de régénérer)
# ==============================================================================
def _archive_purge_recos(cur, run_id: str) -> int:
    """
    Archive (dans recommendations_v5_history) puis SUPPRIME les recommandations
    auto-générées PENDING non notifiées. Préserve l'approuvé/rejeté/notifié.
    Opère sur un curseur fourni (pas de commit ici). Retourne le nb archivé.
    """
    cur.execute("""
                INSERT INTO recommendations_v5_history (
                    archived_run_id, original_id, profile_name, cluster_id, offer_code,
                    score, score_profile, score_provider, score_category, score_amount,
                    score_loyalty, score_churn_boost, is_targeted, recommendation_type,
                    status, admin_note, description, generated_at, model_version)
                SELECT %s, id, profile_name, cluster_id, offer_code,
                       score, score_profile, score_provider, score_category, score_amount,
                       score_loyalty, score_churn_boost, is_targeted, recommendation_type,
                       status, admin_note, description, generated_at, model_version
                FROM recommendations_v5
                WHERE status = 'PENDING'
                  AND notified_at IS NULL
                  AND recommendation_type = 'auto_generated'
                """, (run_id,))
    n = cur.rowcount
    cur.execute("""
                DELETE FROM recommendations_v5
                WHERE status = 'PENDING'
                  AND notified_at IS NULL
                  AND recommendation_type = 'auto_generated'
                """)
    return n


def _archive_purge_offers(cur, run_id: str) -> int:
    """
    Archive (dans generated_offers_history) puis SUPPRIME les offres auto
    SANS recommandation encore liée. Préserve generation_method='manual'.
    Opère sur un curseur fourni (pas de commit ici). Retourne le nb archivé.
    """
    cur.execute("""
                INSERT INTO generated_offers_history (
                    archived_run_id, offer_code, title, type, provider_name, category,
                    cashback_pct, discount_pct, min_amount, target_profiles, boost,
                    description, status, generation_method, generation_run,
                    created_at, updated_at)
                SELECT %s, offer_code, title, type, provider_name, category,
                       cashback_pct, discount_pct, min_amount, target_profiles, boost,
                       description, status, generation_method, generation_run,
                       created_at, updated_at
                FROM generated_offers go
                WHERE go.generation_method != 'manual'
                  AND NOT EXISTS (
                    SELECT 1 FROM recommendations_v5 r
                    WHERE r.offer_code = go.offer_code)
                """, (run_id,))
    n = cur.rowcount
    cur.execute("""
                DELETE FROM generated_offers go
                WHERE go.generation_method != 'manual'
                  AND NOT EXISTS (
                    SELECT 1 FROM recommendations_v5 r
                    WHERE r.offer_code = go.offer_code)
                """)
    return n


def archive_and_purge_before_run(run_id: str) -> dict:
    """
    Avant chaque nouvelle génération COMPLÈTE (pipeline ou /offers/generate) :
      1. ARCHIVE les recommandations auto PENDING dans
         recommendations_v5_history, puis les SUPPRIME (FK respectée : recos
         purgées AVANT les offres).
      2. ARCHIVE les offres auto sans reco liée dans generated_offers_history,
         puis les SUPPRIME.

    Politique de préservation (choix admin) :
      • Offres generation_method='manual'  → CONSERVÉES (jamais purgées).
      • Recommandations APPROVED/REJECTED ou déjà notifiées → CONSERVÉES,
        et les offres encore liées à ces recos ne sont pas supprimées non plus.
      • Seul l'auto-généré PENDING est archivé puis supprimé pour ne laisser
        que les nouvelles entrées du run courant.

    L'historique est conservé indéfiniment (jamais purgé).
    Retourne {'offers_archived': int, 'recos_archived': int}.
    """
    logger.info("\n" + "─" * 70)
    logger.info(f"  ARCHIVAGE + PURGE avant run {run_id}")
    logger.info("─" * 70)
    conn = get_conn()
    offers_archived = recos_archived = 0
    try:
        with conn.cursor() as cur:
            recos_archived = _archive_purge_recos(cur, run_id)
            offers_archived = _archive_purge_offers(cur, run_id)
        conn.commit()
        logger.info(f"  ✓ Archivées : {offers_archived} offres | {recos_archived} recommandations")
        logger.info(f"  ✓ Anciennes entrées auto purgées — seules les nouvelles seront affichées")
    except Exception as e:
        conn.rollback()
        logger.error(f"  ✗ Archivage/purge échoué : {e}")
        raise
    finally:
        release_conn(conn)
    return {"offers_archived": offers_archived, "recos_archived": recos_archived}


def archive_and_purge_recos_only(run_id: str) -> dict:
    """
    Variante pour le RE-SCORING seul (route /recommendations/generate) :
    on archive+purge UNIQUEMENT les recommandations auto PENDING, en gardant
    les offres existantes intactes (ce sont elles qu'on va re-scorer).
    Retourne {'recos_archived': int}.
    """
    logger.info("\n" + "─" * 70)
    logger.info(f"  ARCHIVAGE + PURGE recommandations seules — run {run_id}")
    logger.info("─" * 70)
    conn = get_conn()
    recos_archived = 0
    try:
        with conn.cursor() as cur:
            recos_archived = _archive_purge_recos(cur, run_id)
        conn.commit()
        logger.info(f"  ✓ Archivées : {recos_archived} recommandations (offres conservées)")
    except Exception as e:
        conn.rollback()
        logger.error(f"  ✗ Archivage/purge recos échoué : {e}")
        raise
    finally:
        release_conn(conn)
    return {"recos_archived": recos_archived}


# ==============================================================================
# PHASE 6 — DÉPLOIEMENT
# ==============================================================================
def phase6_deployment(top_reco: pd.DataFrame, generated_offers: list):
    logger.info("\n" + "█" * 70)
    logger.info("  PHASE 6 — DÉPLOIEMENT & PERSISTANCE POSTGRESQL V5")
    logger.info("█" * 70)
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            # NB : la purge des anciennes recommandations auto PENDING est
            # désormais effectuée (avec archivage) par
            # archive_and_purge_before_run() en amont du pipeline.
            rows = [
                (r['profile'], r.get('cluster_id', _cluster_id_for(r['profile'])),
                 r['offer_code'], r['score'],
                 r.get('score_profile'), r.get('score_provider'),
                 r.get('score_category'), r.get('score_amount'),
                 r.get('score_loyalty'), r.get('score_churn_boost'),
                 bool(r.get('is_targeted', False)), r.get('recommendation_type', 'auto_generated'),
                 'PENDING', datetime.now().isoformat(), 'V5.0', r.get('description', ''))
                for _, r in top_reco.iterrows()
            ]
            execute_values(cur, """
                                INSERT INTO recommendations_v5
                                (profile_name, cluster_id, offer_code, score,
                                 score_profile, score_provider, score_category, score_amount,
                                 score_loyalty, score_churn_boost, is_targeted, recommendation_type,
                                 status, generated_at, model_version, description)
                                VALUES %s
                                """, rows)
        conn.commit()
        logger.info(f"  ✓ {len(rows)} recommandations insérées")
    except Exception as e:
        conn.rollback()
        logger.error(f"  ✗ Erreur persistance DB : {e}")
        raise
    finally:
        release_conn(conn)
    top_reco.to_csv(f"{OUT_DIR}/recommendations_top5_v5.csv", index=False)
    logger.info(f"  ✓ CSV : {OUT_DIR}/recommendations_top5_v5.csv")
    logger.info(f"\n  RÉSUMÉ → {len(generated_offers)} offres auto | {len(rows)} recommandations | {top_reco['profile'].nunique()} profils")

# ==============================================================================
# NOTIFICATIONS
# ==============================================================================
def send_approved_recommendations(profile_filter=None):
    logger.info("\n" + "─" * 70)
    logger.info("  NOTIFICATIONS")
    logger.info("─" * 70)
    conn = get_conn()
    try:
        q = """
            SELECT r.id AS reco_id, r.profile_name, r.cluster_id, r.offer_code, r.score
            FROM recommendations_v5 r
            WHERE r.status='APPROVED' AND r.notified_at IS NULL \
            """
        p = []
        if profile_filter:
            q += " AND r.profile_name = %s"
            p.append(profile_filter)
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(q, p)
            approved = cur.fetchall()

        if not approved:
            logger.info("  ℹ Aucune recommandation approuvée en attente")
            return {"sent": 0, "profiles": []}

        total = 0
        notified = set()
        webhook_payload = None

        with conn.cursor() as cur:
            for reco in approved:
                cur.execute("""
                            SELECT o.title, r.description, r.offer_code
                            FROM recommendations_v5 r
                                     JOIN generated_offers o ON r.offer_code = o.offer_code
                            WHERE r.id = %s
                            """, (reco['reco_id'],))
                offer_title, description, offer_code = cur.fetchone()

                cur.execute(f"""
                    SELECT client_id FROM {TABLE_CLIENT_PROFILES}
                    WHERE profile_final = %s
                    LIMIT 5000
                """, (reco['profile_name'],))
                clients = cur.fetchall()
                if not clients:
                    logger.warning(f"Aucun client trouvé pour le profil {reco['profile_name']}")
                    continue

                rows = []
                for (client_id,) in clients:
                    cur.execute("""
                                SELECT 1 FROM client_recommendations_v5
                                WHERE client_id = %s AND offer_code = %s
                                """, (client_id, offer_code))
                    if not cur.fetchone():
                        rows.append((client_id, reco['reco_id'], offer_code,
                                     reco['profile_name'], reco['cluster_id'],
                                     reco['score'], 'SENT', datetime.now()))

                if rows:
                    execute_values(cur, """
                                        INSERT INTO client_recommendations_v5
                                        (client_id, recommendation_id, offer_code, profile_name, cluster_id,
                                         personal_score, status, sent_at)
                                        VALUES %s
                                        """, rows)

                cur.execute("UPDATE recommendations_v5 SET notified_at=NOW() WHERE id=%s", (reco['reco_id'],))
                total += len(rows)
                notified.add(reco['profile_name'])
                logger.info(f"  → Profil {reco['profile_name']} : {len(rows)} nouvelles notifications (sur {len(clients)} clients)")

                if webhook_payload is None and rows:
                    webhook_payload = {
                        "reco_id": reco['reco_id'],
                        "offer_code": offer_code,
                        "title": offer_title,
                        "body": description if description else "Découvrez votre nouvelle offre personnalisée !",
                        "deep_link": f"walletapp://offer/{offer_code}"
                    }

        conn.commit()

        if NOTIFY_WEBHOOK and total > 0 and webhook_payload:
            _call_webhook(total, list(notified), webhook_payload)

        logger.info(f"  ✅ {total:,} notifications | {len(notified)} profils")
        return {"sent": total, "profiles": list(notified), "sent_at": datetime.now().isoformat()}
    except Exception as e:
        conn.rollback()
        logger.error(f"  ✗ Erreur notifications : {e}")
        return {"error": str(e), "sent": 0}
    finally:
        release_conn(conn)


def _call_webhook(total: int, profiles: list, payload: dict):
    try:
        import urllib.request
        data = json.dumps({
            "event":      "recommendations_ready",
            "count":      total,
            "profiles":   profiles,
            "sent_at":    datetime.now().isoformat(),
            "reco_id":    payload.get("reco_id"),
            "offer_code": payload.get("offer_code"),
            "title":      payload.get("title"),
            "body":       payload.get("body", "Découvrez votre nouvelle offre personnalisée !"),
            "deep_link":  payload.get("deep_link"),
        }).encode()
        req = urllib.request.Request(
            NOTIFY_WEBHOOK, data=data,
            headers={"Content-Type": "application/json"}, method="POST"
        )
        urllib.request.urlopen(req, timeout=5)
        logger.info("  ✓ Webhook notifié avec payload enrichi")
    except Exception as e:
        logger.warning(f"  ⚠ Webhook échoué : {e}")

# ==============================================================================
# ADMIN CRUD (offres, recommandations)
# ==============================================================================
def get_offers(status=None, offer_type=None, provider=None, category=None, limit=100, offset=0):
    conn = get_conn()
    try:
        q = """
            SELECT go.*, array_agg(DISTINCT cr.profile_name) AS linked_profiles,
                   COUNT(DISTINCT cr.id) AS n_recommendations
            FROM generated_offers go
            LEFT JOIN recommendations_v5 cr ON cr.offer_code = go.offer_code
            WHERE 1=1 \
            """
        p = []
        if status:     q += " AND go.status=%s";        p.append(status)
        if offer_type: q += " AND go.type=%s";          p.append(offer_type)
        if provider:   q += " AND go.provider_name=%s"; p.append(provider)
        if category:   q += " AND go.category=%s";      p.append(category)
        q += " GROUP BY go.id ORDER BY go.updated_at DESC"
        q += f" LIMIT {int(limit)} OFFSET {int(offset)}"
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(q, p)
            rows = cur.fetchall()
            result = []
            for r in rows:
                d = dict(r)
                if isinstance(d.get('target_profiles'), str):
                    d['target_profiles'] = json.loads(d['target_profiles'])
                result.append(d)
            return result
    finally:
        release_conn(conn)

def get_offer_by_code(offer_code: str) -> Optional[dict]:
    conn = get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                        SELECT go.*, array_agg(DISTINCT r.profile_name) AS linked_profiles
                        FROM generated_offers go
                LEFT JOIN recommendations_v5 r ON r.offer_code = go.offer_code
                        WHERE go.offer_code = %s
                        GROUP BY go.id
                        """, (offer_code,))
            row = cur.fetchone()
            if not row:
                return None
            d = dict(row)
            if isinstance(d.get('target_profiles'), str):
                d['target_profiles'] = json.loads(d['target_profiles'])
            return d
    finally:
        release_conn(conn)

def add_offer_manual(data: dict, admin_user: str = "admin") -> tuple:
    required = ['title', 'type', 'discount_pct', 'target_profiles']
    for f in required:
        if f not in data:
            return {"error": f"Champ requis manquant : {f}"}, 400
    code = _make_offer_code("manual", data['type'], data.get('title', 'x'))
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("""
                        INSERT INTO generated_offers
                        (offer_code,title,type,provider_name,category,cashback_pct,discount_pct,
                         min_amount,target_profiles,boost,description,status,generation_method,created_at,updated_at)
                        VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s::jsonb,%s,%s,'ACTIVE','manual',NOW(),NOW())
                            RETURNING id
                        """, (code, data['title'], data['type'],
                              data.get('provider_name'), data.get('category'),
                              data.get('cashback_pct', 0), data['discount_pct'],
                              data.get('min_amount', 0),
                              json.dumps(data['target_profiles']),
                              data.get('boost', 1.0), data.get('description', ''),))
            new_id = cur.fetchone()[0]
        conn.commit()
        return {"success": True, "id": new_id, "offer_code": code, "status": "ACTIVE"}, 201
    except Exception as e:
        conn.rollback()
        return {"error": str(e)}, 500
    finally:
        release_conn(conn)

def update_offer(offer_code: str, data: dict, admin_user: str = "admin") -> tuple:
    allowed = ['title', 'type', 'provider_name', 'category', 'cashback_pct', 'discount_pct',
               'min_amount', 'target_profiles', 'boost', 'description']
    updates = {k: v for k, v in data.items() if k in allowed}
    if not updates:
        return {"error": "Aucun champ valide à mettre à jour"}, 400
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id FROM generated_offers WHERE offer_code=%s", (offer_code,))
            if not cur.fetchone():
                return {"error": f"Offre {offer_code} introuvable"}, 404
            set_parts, vals = [], []
            for k, v in updates.items():
                if k == 'target_profiles':
                    set_parts.append(f"{k}=%s::jsonb")
                    vals.append(json.dumps(v))
                else:
                    set_parts.append(f"{k}=%s")
                    vals.append(v)
            set_parts.append("updated_at=NOW()")
            vals.append(offer_code)
            cur.execute(f"UPDATE generated_offers SET {', '.join(set_parts)} WHERE offer_code=%s", vals)
        conn.commit()
        return {"success": True, "offer_code": offer_code, "updated_fields": list(updates.keys())}, 200
    except Exception as e:
        conn.rollback()
        return {"error": str(e)}, 500
    finally:
        release_conn(conn)

def set_offer_status(offer_code: str, status: str, admin_user: str = "admin") -> tuple:
    if status not in ('ACTIVE', 'INACTIVE'):
        return {"error": "Statut doit être ACTIVE ou INACTIVE"}, 400
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT status FROM generated_offers WHERE offer_code=%s", (offer_code,))
            row = cur.fetchone()
            if not row:
                return {"error": f"Offre {offer_code} introuvable"}, 404
            cur.execute("UPDATE generated_offers SET status=%s, updated_at=NOW() WHERE offer_code=%s",
                        (status, offer_code))
        conn.commit()
        return {"success": True, "offer_code": offer_code, "new_status": status}, 200
    except Exception as e:
        conn.rollback()
        return {"error": str(e)}, 500
    finally:
        release_conn(conn)

def delete_offer(offer_code: str, admin_user: str = "admin") -> tuple:
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id, title FROM generated_offers WHERE offer_code=%s", (offer_code,))
            row = cur.fetchone()
            if not row:
                return {"error": f"Offre {offer_code} introuvable"}, 404
            cur.execute("DELETE FROM recommendations_v5 WHERE offer_code=%s", (offer_code,))
            cur.execute("DELETE FROM generated_offers WHERE offer_code=%s", (offer_code,))
        conn.commit()
        return {"success": True, "offer_code": offer_code, "deleted": True}, 200
    except Exception as e:
        conn.rollback()
        return {"error": str(e)}, 500
    finally:
        release_conn(conn)

def get_recommendations(status=None, profile=None, limit=100, offset=0):
    conn = get_conn()
    try:
        q = """
            SELECT r.id, r.profile_name, r.cluster_id, r.offer_code,
                   o.title AS offer_title, o.type AS offer_type,
                   o.cashback_pct, o.discount_pct, o.description,
                   r.score, r.score_profile, r.score_provider, r.score_churn_boost,
                   r.is_targeted, r.recommendation_type, r.status,
                   r.admin_note, r.generated_at, r.approved_at, r.rejected_at
            FROM recommendations_v5 r
                     JOIN generated_offers o ON o.offer_code = r.offer_code
            WHERE 1=1 \
            """
        p = []
        if status:  q += " AND r.status=%s";       p.append(status)
        if profile: q += " AND r.profile_name=%s"; p.append(profile)
        q += f" ORDER BY r.profile_name, r.score DESC LIMIT {int(limit)} OFFSET {int(offset)}"
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(q, p)
            return [dict(r) for r in cur.fetchall()]
    finally:
        release_conn(conn)

def _change_reco_status(reco_id, from_s, to_s, admin_user, note):
    if isinstance(from_s, str): from_s = [from_s]
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT status, profile_name, offer_code FROM recommendations_v5 WHERE id=%s", (reco_id,))
            row = cur.fetchone()
            if not row:
                return {"error": f"ID {reco_id} introuvable"}, 404
            cur_st, pname, ocode = row
            if cur_st not in from_s:
                return {"error": f"Statut actuel='{cur_st}'"}, 400
            ts_col = 'approved_at' if to_s=='APPROVED' else 'rejected_at' if to_s=='REJECTED' else 'generated_at'
            cur.execute(f"UPDATE recommendations_v5 SET status=%s, {ts_col}=NOW(), admin_note=%s WHERE id=%s",
                        (to_s, note, reco_id))
            cur.execute("""
                        INSERT INTO recommendation_history_v5
                        (recommendation_id,profile_name,offer_code,old_status,new_status,changed_by,note)
                        VALUES (%s,%s,%s,%s,%s,%s,%s)
                        """, (reco_id, pname, ocode, cur_st, to_s, admin_user, note))
        conn.commit()
        return {"success": True, "id": reco_id, "new_status": to_s}, 200
    except Exception as e:
        conn.rollback()
        return {"error": str(e)}, 500
    finally:
        release_conn(conn)

def approve_recommendation(reco_id, admin_user="admin", note=None):
    return _change_reco_status(reco_id, ['PENDING', 'REJECTED'], 'APPROVED', admin_user, note)

def reject_recommendation(reco_id, admin_user="admin", note=None):
    return _change_reco_status(reco_id, ['PENDING', 'APPROVED'], 'REJECTED', admin_user, note)

def bulk_approve(profile_name, admin_user="admin"):
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id FROM recommendations_v5 WHERE profile_name=%s AND status='PENDING'", (profile_name,))
            ids = [r[0] for r in cur.fetchall()]
            if not ids:
                return {"success": True, "approved": 0}, 200
            cur.execute("UPDATE recommendations_v5 SET status='APPROVED', approved_at=NOW() WHERE profile_name=%s AND status='PENDING'", (profile_name,))
            execute_values(cur, """
                                INSERT INTO recommendation_history_v5
                                (recommendation_id,profile_name,offer_code,old_status,new_status,changed_by,note)
                                VALUES %s
                                """, [(rid, profile_name, None, 'PENDING', 'APPROVED', admin_user, 'Bulk approve') for rid in ids])
        conn.commit()
        return {"success": True, "approved": len(ids)}, 200
    except Exception as e:
        conn.rollback()
        return {"error": str(e)}, 500
    finally:
        release_conn(conn)

def add_recommendation_manual(profile_name: str, offer_code: str, score: float = 0.8,
                              admin_user: str = "admin", note: str = None) -> tuple:
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("""
                        SELECT offer_code FROM generated_offers
                        WHERE offer_code = %s AND status = 'ACTIVE'
                        """, (offer_code,))
            if not cur.fetchone():
                return {"error": f"Offre '{offer_code}' introuvable ou inactive"}, 404
            valid_profiles = list(PROFILE_NAMES.values())
            if profile_name not in valid_profiles:
                return {"error": f"Profil '{profile_name}' invalide"}, 400
            cluster_id = _cluster_id_for(profile_name) if profile_name != "Profil Mixte (Incertain)" else -1
            cur.execute("""
                        INSERT INTO recommendations_v5
                        (profile_name, cluster_id, offer_code, score,
                         score_profile, score_provider, score_category,
                         score_amount, score_loyalty, score_churn_boost,
                         is_targeted, recommendation_type, status,
                         generated_at, admin_note, model_version)
                        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                                NOW(), %s, 'V5.0')
                            RETURNING id
                        """, (
                            profile_name, cluster_id, offer_code, score,
                            1.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                            True, 'manual_override', 'PENDING', note
                        ))
            new_id = cur.fetchone()[0]
            cur.execute("""
                        INSERT INTO recommendation_history_v5
                        (recommendation_id, profile_name, offer_code, old_status, new_status, changed_by, note)
                        VALUES (%s, %s, %s, NULL, 'PENDING', %s, %s)
                        """, (new_id, profile_name, offer_code, admin_user, note or 'Ajout manuel'))
        conn.commit()
        return {"success": True, "id": new_id, "offer_code": offer_code,
                "profile": profile_name, "status": "PENDING"}, 201
    except Exception as e:
        conn.rollback()
        return {"error": str(e)}, 500
    finally:
        release_conn(conn)

def update_recommendation(reco_id: int, data: dict, admin_user: str = "admin") -> tuple:
    allowed = ['score', 'admin_note', 'status']
    updates = {k: v for k, v in data.items() if k in allowed}
    if not updates:
        return {"error": "Aucun champ modifiable fourni (score, admin_note, status)"}, 400
    if 'status' in updates and updates['status'] not in ('APPROVED', 'REJECTED'):
        return {"error": "Le statut doit être APPROVED ou REJECTED"}, 400
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT status, profile_name, offer_code FROM recommendations_v5 WHERE id=%s", (reco_id,))
            row = cur.fetchone()
            if not row:
                return {"error": f"Recommandation {reco_id} introuvable"}, 404
            old_status, pname, ocode = row
            set_clauses = []
            vals = []
            if 'score' in updates:
                set_clauses.append("score=%s")
                vals.append(updates['score'])
            if 'admin_note' in updates:
                set_clauses.append("admin_note=%s")
                vals.append(updates['admin_note'])
            if 'status' in updates and updates['status'] != old_status:
                set_clauses.append("status=%s")
                vals.append(updates['status'])
                ts_col = 'approved_at' if updates['status'] == 'APPROVED' else 'rejected_at'
                set_clauses.append(f"{ts_col}=NOW()")
            set_clauses.append("updated_at=NOW()")
            vals.append(reco_id)
            cur.execute(f"UPDATE recommendations_v5 SET {', '.join(set_clauses)} WHERE id=%s", vals)
            if 'status' in updates and updates['status'] != old_status:
                cur.execute("""
                            INSERT INTO recommendation_history_v5
                            (recommendation_id, profile_name, offer_code, old_status, new_status, changed_by, note)
                            VALUES (%s, %s, %s, %s, %s, %s, %s)
                            """, (reco_id, pname, ocode, old_status, updates['status'], admin_user, updates.get('admin_note', '')))
        conn.commit()
        return {"success": True, "id": reco_id, "updated": list(updates.keys())}, 200
    except Exception as e:
        conn.rollback()
        return {"error": str(e)}, 500
    finally:
        release_conn(conn)

# ==============================================================================
# PIPELINE PRINCIPAL
# ==============================================================================
def start_generation_run(run_id: str) -> None:
    """Insère une ligne RUNNING dans offer_generation_runs (trace du run)."""
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("""
                        INSERT INTO offer_generation_runs (run_id, status)
                        VALUES (%s, 'RUNNING')
                            ON CONFLICT (run_id) DO NOTHING
                        """, (run_id,))
        conn.commit()
        logger.info(f"  ✓ Run {run_id} enregistré (RUNNING)")
    except Exception as e:
        conn.rollback()
        logger.error(f"  ✗ Impossible d'enregistrer le run {run_id} : {e}")
    finally:
        release_conn(conn)


def finish_generation_run(run_id: str, n_profiles: int = 0, n_offers_gen: int = 0,
                          offers_archived: int = 0, recos_archived: int = 0,
                          status: str = "DONE", error_msg: str = None) -> None:
    """Clôture la ligne du run dans offer_generation_runs avec les compteurs."""
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("""
                        UPDATE offer_generation_runs
                        SET finished_at=NOW(), status=%s,
                            n_profiles=%s, n_offers_gen=%s,
                            n_offers_archived=%s, n_recos_archived=%s,
                            error_msg=%s
                        WHERE run_id=%s
                        """, (status, n_profiles, n_offers_gen,
                              offers_archived, recos_archived, error_msg, run_id))
            updated = cur.rowcount
        conn.commit()
        if updated:
            logger.info(f"  ✓ Run {run_id} clôturé ({status}) : "
                        f"{n_offers_gen} offres, {n_profiles} profils")
        else:
            logger.warning(f"  ⚠ Run {run_id} introuvable à la clôture "
                           f"(start_generation_run a-t-il été appelé ?)")
    except Exception as e:
        conn.rollback()
        logger.error(f"  ✗ Impossible de clôturer le run {run_id} : {e}")
    finally:
        release_conn(conn)


def run_pipeline():
    start = time.time()
    run_id = datetime.now().strftime("RUN_%Y%m%d_%H%M%S")
    logger.info("\n" + "█" * 70)
    logger.info("  SYSTÈME RECOMMANDATION V5.3 — OFFRES PERSONNALISÉES (CATÉGORIE/PROVIDER)")
    logger.info(f"  Run ID : {run_id}")
    logger.info("█" * 70)

    model_ok = load_classification_model()
    if not model_ok:
        logger.warning("⚠ Modèle wallet_classification non chargé — mode Profile-Based only")

    phase1_business_understanding()
    init_tables()

    # Trace du run APRÈS init_tables (la table doit exister)
    start_generation_run(run_id)

    # ── Archivage + purge des anciennes offres/recos AVANT régénération ──
    #    On historise puis on supprime l'auto-généré pour n'afficher que le neuf.
    purge_report = {"offers_archived": 0, "recos_archived": 0}
    try:
        purge_report = archive_and_purge_before_run(run_id)
    except Exception as e:
        logger.error(f"⚠ Archivage/purge non bloquant échoué : {e}")

    profiles, transactions, providers_df, profile_counts = phase2_data_understanding()
    profile_stats, pp_norm, pc_norm, cat_stats, prov_stats = phase3_data_preparation(profiles, transactions)
    reco_df, top_reco, generated_offers, engine = phase4_modeling(
        profile_stats, pp_norm, pc_norm, cat_stats, prov_stats)
    eval_df = phase5_evaluation(top_reco)
    phase6_deployment(top_reco, generated_offers)

    elapsed = int(time.time() - start)
    finish_generation_run(
        run_id,
        n_profiles=int(reco_df['profile'].nunique()),
        n_offers_gen=len(generated_offers),
        offers_archived=purge_report["offers_archived"],
        recos_archived=purge_report["recos_archived"],
        status="DONE",
    )

    logger.info(f"\n{'█'*70}")
    logger.info(f"  ✅ Pipeline V5.3 terminé en {elapsed}s")
    logger.info(f"  ✅ Modèle wallet_classification : {'Oui' if model_ok else 'Non (fallback)'}")
    logger.info(f"  ✅ Source profils   : table '{TABLE_CLIENT_PROFILES}'")
    logger.info(f"  ✅ Profils traités  : {reco_df['profile'].nunique()}")
    logger.info(f"  ✅ Offres générées  : {len(generated_offers)} (100% personnalisées)")
    logger.info(f"  ✅ Recommandations  : {len(top_reco)}")
    logger.info(f"  ✅ Archivé (run)    : {purge_report['offers_archived']} offres | {purge_report['recos_archived']} recos")
    logger.info(f"  ✅ F1 moyen         : {eval_df['f1'].mean():.3f}")
    logger.info(f"  ✅ Figures          : {OUT_DIR}/figures/")
    logger.info(f"{'█'*70}\n")
    return reco_df, top_reco, eval_df, generated_offers

# ==============================================================================
# SCHEDULER
# ==============================================================================
def start_scheduler():
    if not APSCHEDULER_AVAILABLE:
        logger.error("✗ APScheduler non disponible — pip install apscheduler")
        logger.info("  Exécution unique du pipeline au lieu du scheduler...")
        run_pipeline()
        return
    logger.info(f"\n  SCHEDULER — Intervalle : {SCHEDULER_INTERVAL_HOURS}h")
    scheduler = BackgroundScheduler()
    scheduler.add_job(
        func=run_pipeline,
        trigger='interval',
        hours=SCHEDULER_INTERVAL_HOURS,
        id='reco_pipeline',
        name='Recommandation Pipeline V5',
        next_run_time=datetime.now()
    )
    scheduler.start()
    logger.info(f"  ✓ Scheduler démarré — prochain run dans {SCHEDULER_INTERVAL_HOURS}h")
    try:
        while True:
            time.sleep(60)
            jobs = scheduler.get_jobs()
            for job in jobs:
                logger.info(f"  ⏱ Prochain run : {job.next_run_time}")
            time.sleep(3600)
    except (KeyboardInterrupt, SystemExit):
        scheduler.shutdown()
        logger.info("  ✓ Scheduler arrêté proprement")


# ==============================================================================
# FEEDBACK MARKETING — Re-pondération des offres selon l'acceptation utilisateur
# ==============================================================================
def apply_feedback_reweighting(feedback_items: list) -> dict:
    """
    Re-pondère les offres marketing en fonction des taux d'acceptation observés.

    Algorithme simple et robuste (online learning lite) :
      1. Calculer, par couple (profile_id, offer_code), le taux d'acceptation
         observé : accept_rate = accepted / (accepted + rejected).
      2. Convertir en un multiplicateur de boost lissé :
            new_boost = clip(0.5 + accept_rate, 0.5, 1.5)
         (0.5 = offre toujours rejetée → on la pénalise mais on ne la coupe
          pas totalement ; 1.5 = offre toujours acceptée → on l'amplifie.)
      3. Lissage exponentiel avec le boost actuel pour éviter les sauts brusques :
            updated = 0.7 * current_boost + 0.3 * new_boost
      4. Persister dans `generated_offers.boost`.

    Args
    ----
    feedback_items : liste de {client_id, offer_code, profile_id, decision, recorded_at}

    Returns
    -------
    dict avec le rapport de la re-pondération (nb offres mises à jour, etc.)
    """
    if not feedback_items:
        return {"status": "rien_a_analyser", "feedbacks": 0, "offers_updated": 0}

    # ── 1. Agréger les feedbacks par offre ─────────────────────────────
    stats: Dict[str, Dict[str, int]] = {}
    for it in feedback_items:
        code = it.get("offer_code")
        if not code:
            continue
        s = stats.setdefault(code, {"accepted": 0, "rejected": 0})
        dec = it.get("decision")
        if dec in s:
            s[dec] += 1

    if not stats:
        return {"status": "rien_a_analyser", "feedbacks": len(feedback_items),
                "offers_updated": 0}

    # ── 2. Calculer les nouveaux boosts ────────────────────────────────
    updates = []
    for code, s in stats.items():
        total = s["accepted"] + s["rejected"]
        if total < 3:  # bruit, on attend plus de signaux
            continue
        accept_rate = s["accepted"] / total
        target_boost = max(0.5, min(1.5, 0.5 + accept_rate))
        updates.append((code, target_boost, s["accepted"], s["rejected"]))

    if not updates:
        return {"status": "trop_peu_de_signaux", "feedbacks": len(feedback_items),
                "offers_updated": 0,
                "note": "Au moins 3 feedbacks par offre nécessaires pour mettre à jour."}

    # ── 3. Persister les nouveaux boosts en base ───────────────────────
    conn = conn = get_conn()
    updated = 0
    details = []
    try:
        with conn.cursor() as cur:
            for code, target_boost, acc, rej in updates:
                cur.execute(
                    "SELECT boost FROM generated_offers WHERE offer_code = %s",
                    (code,)
                )
                row = cur.fetchone()
                if not row:
                    continue
                current = float(row[0]) if row[0] is not None else 1.0
                # Lissage exponentiel
                new_boost = round(0.7 * current + 0.3 * target_boost, 3)
                cur.execute(
                    "UPDATE generated_offers SET boost = %s, updated_at = NOW() "
                    "WHERE offer_code = %s",
                    (new_boost, code)
                )
                updated += 1
                details.append({
                    "offer_code": code,
                    "accepted": acc, "rejected": rej,
                    "old_boost": current, "new_boost": new_boost,
                })
        conn.commit()
    except Exception as e:
        conn.rollback()
        logger.error(f"❌ Re-pondération échouée : {e}")
        raise
    finally:
        conn.close()

    logger.info(f"✅ Marketing retrain : {updated} offres re-pondérées sur {len(updates)} candidates")
    return {
        "status": "ok",
        "feedbacks": len(feedback_items),
        "offers_updated": updated,
        "details": details,
    }


# ==============================================================================
# CLI
# ==============================================================================
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Système Recommandation V5.3 — Offres Personnalisées")
    parser.add_argument('--generate',  action='store_true', help="Générer recommandations")
    parser.add_argument('--evaluate',  action='store_true', help="Évaluation uniquement")
    parser.add_argument('--notify',    action='store_true', help="Envoyer notifications")
    parser.add_argument('--init-db',   action='store_true', help="Init tables DB seulement")
    parser.add_argument('--scheduler', action='store_true', help="Démarrer le scheduler 24h")
    args = parser.parse_args()
    if args.init_db:
        init_tables()
        logger.info("✅ Tables V5 initialisées.")
    elif args.generate:
        load_classification_model(); init_tables()
        _gen_run = datetime.now().strftime("RUN_%Y%m%d_%H%M%S")
        start_generation_run(_gen_run)
        _purge = {"offers_archived": 0, "recos_archived": 0}
        try:
            _purge = archive_and_purge_before_run(_gen_run)
        except Exception as e:
            logger.error(f"⚠ Archivage/purge échoué : {e}")
        p, t, _, _ = phase2_data_understanding()
        ps, pp, pc, cs, pvs = phase3_data_preparation(p, t)
        _, tr, go, _ = phase4_modeling(ps, pp, pc, cs, pvs)
        phase6_deployment(tr, go)
        finish_generation_run(
            _gen_run,
            n_profiles=int(tr['profile'].nunique()) if hasattr(tr, 'columns') and 'profile' in tr.columns else 0,
            n_offers_gen=len(go),
            offers_archived=_purge.get("offers_archived", 0),
            recos_archived=_purge.get("recos_archived", 0),
            status="DONE",
        )
        logger.info("✅ Recommandations générées.")
    elif args.evaluate:
        load_classification_model(); init_tables()
        p, t, _, _ = phase2_data_understanding()
        ps, pp, pc, cs, pvs = phase3_data_preparation(p, t)
        _, tr, go, _ = phase4_modeling(ps, pp, pc, cs, pvs)
        ev = phase5_evaluation(tr)
        logger.info(f"\nF1 moyen : {ev['f1'].mean():.3f}")
    elif args.notify:
        init_tables()
        r = send_approved_recommendations()
        logger.info(f"\nEnvoyées : {r.get('sent', 0)}")
    elif args.scheduler:
        init_tables()
        start_scheduler()
    else:
        run_pipeline()