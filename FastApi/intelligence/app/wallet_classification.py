"""
================================================================================
  CLASSIFICATION COMPORTEMENTALE CLIENTS — WALLET MOBILE FINTECH TUNISIE
  ══════════════════════════════════════════════════════════════════════════════
  MODÈLE :
    - UMAP + K-Means (k=6 profils comportementaux)
    - GBM (GradientBoostingClassifier) superviseur de confiance
    - Churn calibré : sigmoid(scale=5.0, center=0.22)
    - LTV économique : marge configurable (pess=1%, base=2%, opt=4%)
    - Hold-out anti-leakage : 10% réservé avant clustering
    - Détection profils fragiles : seuil silhouette < 0.30
    - PSI automatique mensuel (APScheduler)

  UTILISATION :
    python wallet_classification.py              # Pipeline complet
    python wallet_classification.py --retrain    # Forcer ré-entraînement
    python wallet_classification.py --api        # Lancer l'API FastAPI
    python wallet_classification.py --kpi        # KPI métiers uniquement
    python wallet_classification.py --drift      # Rapport drift PSI
    python wallet_classification.py --validate   # Validation churn calibration

  VARIABLES D'ENVIRONNEMENT REQUISES (.env ou export) :
    DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
================================================================================
"""

# ==============================================================================
# IMPORTS ET CONFIGURATION
# ==============================================================================
import os
import sys
import io
import json
import warnings
import logging
import time
import threading
import argparse
from datetime import datetime
from pathlib import Path

import numpy as np
import pandas as pd
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.gridspec as gridspec
import seaborn as sns
from scipy.special import expit
from scipy.stats import ks_2samp

from sklearn.preprocessing import RobustScaler
from sklearn.cluster import KMeans
from sklearn.decomposition import PCA
from sklearn.metrics import (
    silhouette_score, davies_bouldin_score,
    silhouette_samples, calinski_harabasz_score,
    classification_report, accuracy_score, f1_score
)
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.utils import resample
import joblib

# Forcer UTF-8 pour la sortie standard (Windows)
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

try:
    import umap
    UMAP_AVAILABLE = True
except ImportError:
    UMAP_AVAILABLE = False
    print("⚠ UMAP non disponible — pip install umap-learn")

try:
    import psycopg2
    import psycopg2.extras
    from psycopg2.extras import execute_values
    from psycopg2 import pool as pg_pool
    PSYCOPG2_AVAILABLE = True
except ImportError:
    PSYCOPG2_AVAILABLE = False
    print("✗ psycopg2 requis — pip install psycopg2-binary")
    sys.exit(1)

try:
    from apscheduler.schedulers.background import BackgroundScheduler
    APSCHEDULER_AVAILABLE = True
except ImportError:
    APSCHEDULER_AVAILABLE = False
    print("⚠ APScheduler non disponible — pip install apscheduler")

try:
    from dotenv import load_dotenv
    env_path = Path(__file__).parent / ".env"
    if env_path.exists():
        load_dotenv(dotenv_path=env_path)
        print(f"✓ .env chargé depuis {env_path}")
    else:
        print(f"⚠ Fichier .env introuvable dans {env_path}")
except ImportError:
    print("⚠ python-dotenv non installé")

warnings.filterwarnings('ignore')
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%H:%M:%S'
)
logger = logging.getLogger(__name__)

# ── Palette et style ──────────────────────────────────────────────────────────
PALETTE = ['#1E88E5', '#43A047', '#FB8C00', '#E53935', '#8E24AA', '#00ACC1']

plt.rcParams.update({
    'figure.facecolor': 'white',
    'axes.facecolor':   '#F8F9FA',
    'axes.grid':        True,
    'grid.alpha':       0.4,
    'font.size':        10,
    'axes.titlesize':   12,
    'axes.titleweight': 'bold',
})


# ==============================================================================
# CONFIGURATION CENTRALE
# ==============================================================================
def _require_env(key: str) -> str:
    val = os.environ.get(key)
    if val is None:
        raise EnvironmentError(
            f"Variable d'environnement '{key}' manquante. "
            f"Définissez-la dans .env ou via export {key}=..."
        )
    return val


CFG = {
    "out_dir":      "outputs_v9",
    "fig_dir":      "outputs_v9/figures",
    "model_dir":    "outputs_v9/models",
    
    # Connexion via DB_URL (cohérent avec main.py)
    "db_url":       os.environ.get("DB_URL", "postgresql://postgres:mariem@localhost:5432/client_bd"),

    # Modèle
    "k_optimal":            6,
    "k_min":                4,
    "k_max":                9,
    "random_state":         42,
    "min_txn":              2,
    "bootstrap_n":          15,
    "bootstrap_frac":       0.80,
    "confidence_threshold": 0.60,

    # Hold-out anti-leakage
    "holdout_frac":         0.10,

    # Stabilité profils
    "sil_min_profile":      0.30,
    "sil_fusion_threshold": 0.28,

    # UMAP
    "umap_n_components": 15,
    "umap_n_neighbors":  30,
    "umap_min_dist":     0.1,
    "umap_metric":       "euclidean",

    # Dates
    "date_ref":      "2026-04-07",
    "recent_months":  3,
    "medium_months":  6,

    # PSI
    "psi_threshold": 0.20,
    "psi_warning":   0.10,
    "psi_n_bins":    20,

    # Churn calibré
    "churn_w_recence":         0.40,
    "churn_w_regularite":      0.30,
    "churn_w_momentum":        0.20,
    "churn_w_reversal":        0.10,
    "churn_seuil_recence_max": 90,
    "churn_sigmoid_scale":     5.0,
    "churn_sigmoid_center":    0.22,
    "churn_high_risk_thresh":  0.50,
    "churn_critical_thresh":   0.70,

    # LTV
    "ltv_discount_rate_annual": 0.10,
    "ltv_marge_min":            0.010,
    "ltv_marge_base":           0.020,
    "ltv_marge_max":            0.040,
    "ltv_horizon_6m":           6,
    "ltv_horizon_12m":          12,

    # Profils
    "profile_names": {
        0:  "Micro-Utilisateur Passif",
        1:  "Utilisateur Essentiel Stable",
        2:  "Payeur Factures Premium",
        3:  "Client Grande Dépense",
        4:  "Client en Accélération Récente",
        5:  "Client en Croissance Digitale",
        -1: "Profil Mixte (Incertain)",
    },
    "profile_icons": {
        0: "👤", 1: "💳", 2: "📃", 3: "💰", 4: "🚀", 5: "📈", -1: "❓"
    },
    "profile_colors": {
        0: "#1E88E5", 1: "#43A047", 2: "#FB8C00",
        3: "#E53935", 4: "#8E24AA", 5: "#00ACC1", -1: "#9E9E9E"
    },
}

for d in [CFG["out_dir"], CFG["fig_dir"], CFG["model_dir"]]:
    os.makedirs(d, exist_ok=True)

CATEGORY_LABELS = {
    'ratio_factures':    'Factures & Services',
    'ratio_recharges':   'Recharge Téléphonique',
    'ratio_shopping':    'Shopping & Paiements',
    'ratio_restaurants': 'Restaurants & Livraison',
    'ratio_transferts':  'Transferts',
    'ratio_education':   'Education & Institutions',
    'ratio_voyages':     'Voyages & Réservations',
}


# ==============================================================================
# UTILITAIRES
# ==============================================================================
def _banner(title: str) -> None:
    logger.info("\n" + "═" * 70)
    logger.info(f"  {title}")
    logger.info("═" * 70)


def _model_path(name: str) -> str:
    return f"{CFG['model_dir']}/{name}.pkl"


def _profile_description(cluster_id: int) -> str:
    descriptions = {
        0:  ("Usage très limité, transactions rares et irrégulières. Montants faibles. "
             "Profil à risque de churn élevé ou client dormant."),
        1:  ("Usage quotidien simple et stable. Paiements essentiels réguliers. "
             "Client fiable avec comportement prévisible."),
        2:  ("Spécialisé dans le paiement de factures utilities et services. "
             "Fort potentiel de fidélisation et de domiciliation."),
        3:  ("Transactions moins fréquentes mais montants élevés. Client premium "
             "à fort potentiel de revenus."),
        4:  ("Momentum récent très élevé (>2.5×). En forte accélération sur "
             "les 3 derniers mois. Fort potentiel d'adoption avancée."),
        5:  ("Activité en forte croissance. Explore activement de nouvelles "
             "catégories. Phase d'adoption avancée."),
        -1: ("Client à la frontière de plusieurs profils. Comportement ambigu."),
    }
    return descriptions.get(cluster_id, "Description non disponible.")


def _activity_level(freq_mean: float) -> str:
    if   freq_mean < 1.5: return "Très faible"
    elif freq_mean < 3.0: return "Faible"
    elif freq_mean < 5.0: return "Modérée"
    elif freq_mean < 7.0: return "Élevée"
    else:                 return "Très élevée"


# ==============================================================================
# CONNEXION POSTGRESQL — Pool thread-safe
# ==============================================================================
_pool_instance = None
_pool_lock     = threading.Lock()


def get_pool():
    global _pool_instance
    if _pool_instance is None:
        with _pool_lock:
            if _pool_instance is None:
                _pool_instance = pg_pool.ThreadedConnectionPool(
                    minconn=2, maxconn=10,
                    dsn=CFG["db_url"], connect_timeout=10
                )
                logger.info("  ✓ Pool PostgreSQL initialisé (2–10 connexions)")
    return _pool_instance


def get_connection():
    try:
        return get_pool().getconn()
    except Exception:
        return psycopg2.connect(CFG["db_url"])


def release_connection(conn):
    try:
        get_pool().putconn(conn)
    except Exception:
        try:
            conn.close()
        except Exception:
            pass


# ==============================================================================
# INITIALISATION DES TABLES
# ==============================================================================
def init_tables():
    _banner("INITIALISATION DES TABLES")
    sql = """
          CREATE TABLE IF NOT EXISTS client_profiles_v9 (
                                                         id                   SERIAL PRIMARY KEY,
                                                         client_id            UUID NOT NULL UNIQUE,
                                                         cluster_id           INTEGER NOT NULL CHECK (cluster_id BETWEEN -1 AND 5),
              profile_name         VARCHAR(100) NOT NULL,
              profile_final        VARCHAR(100),
              is_mixte             BOOLEAN DEFAULT FALSE,
              confidence_score     DOUBLE PRECISION,
              gbm_confidence       DOUBLE PRECISION,
              assigned_at          TIMESTAMP DEFAULT NOW(),
              total_transactions   INTEGER,
              freq_mensuelle       DOUBLE PRECISION,
              montant_moyen        DOUBLE PRECISION,
              montant_median       DOUBLE PRECISION,
              montant_total        DOUBLE PRECISION,
              regularite           DOUBLE PRECISION,
              rfm_score            DOUBLE PRECISION,
              loyalty_score        DOUBLE PRECISION,
              momentum_court       DOUBLE PRECISION,
              momentum_long        DOUBLE PRECISION,
              recence_jours        INTEGER,
              churn_score_30j      DOUBLE PRECISION,
              churn_score_90j      DOUBLE PRECISION,
              churn_segment        VARCHAR(20),
              ltv_6m               DOUBLE PRECISION,
              ltv_12m              DOUBLE PRECISION,
              ltv_12m_optimiste    DOUBLE PRECISION,
              ltv_12m_pessimiste   DOUBLE PRECISION,
              hazard_rate          DOUBLE PRECISION,
              in_holdout           BOOLEAN DEFAULT FALSE,
              updated_at           TIMESTAMP DEFAULT NOW()
              );
          CREATE INDEX IF NOT EXISTS idx_cp_cluster ON client_profiles_v9(cluster_id);
          CREATE INDEX IF NOT EXISTS idx_cp_churn   ON client_profiles_v9(churn_score_30j DESC);
          CREATE INDEX IF NOT EXISTS idx_cp_segment ON client_profiles_v9(churn_segment);

          CREATE TABLE IF NOT EXISTS model_runs_v9 (
                                                    id                    SERIAL PRIMARY KEY,
                                                    run_at                TIMESTAMP DEFAULT NOW(),
              n_clients             INTEGER,
              n_holdout             INTEGER,
              n_profiles            INTEGER,
              k_used                INTEGER,
              silhouette_score      DOUBLE PRECISION,
              davies_bouldin        DOUBLE PRECISION,
              calinski_harabasz     DOUBLE PRECISION,
              bootstrap_stability   DOUBLE PRECISION,
              bootstrap_std         DOUBLE PRECISION,
              n_mixte               INTEGER,
              pct_mixte             DOUBLE PRECISION,
              psi_max               DOUBLE PRECISION,
              psi_status            VARCHAR(30),
              gbm_accuracy          DOUBLE PRECISION,
              gbm_cv_f1             DOUBLE PRECISION,
              gbm_test_f1           DOUBLE PRECISION,
              gbm_holdout_f1        DOUBLE PRECISION,
              fragile_profiles      JSONB,
              churn_pct_high_risk   DOUBLE PRECISION,
              churn_pct_critical    DOUBLE PRECISION,
              retrain_trigger       VARCHAR(50) DEFAULT 'manual',
              notes                 TEXT
              );

          CREATE TABLE IF NOT EXISTS profile_stats (
                                                       id                           SERIAL PRIMARY KEY,
                                                       cluster_id                   INTEGER NOT NULL UNIQUE,
                                                       profile_name                 VARCHAR(100),
              is_fragile                   BOOLEAN DEFAULT FALSE,
              n_clients                    INTEGER,
              pct_clients                  DOUBLE PRECISION,
              sil_mean                     DOUBLE PRECISION,
              sil_min                      DOUBLE PRECISION,
              dominant_category            VARCHAR(100),
              dominant_category_ratio      DOUBLE PRECISION,
              dominant_category_vs_global  DOUBLE PRECISION,
              secondary_category           VARCHAR(100),
              activity_level               VARCHAR(30),
              freq_mensuelle_mean          DOUBLE PRECISION,
              montant_moyen_mean           DOUBLE PRECISION,
              montant_total_mean           DOUBLE PRECISION,
              regularite_mean              DOUBLE PRECISION,
              rfm_score_mean               DOUBLE PRECISION,
              loyalty_score_mean           DOUBLE PRECISION,
              momentum_court_mean          DOUBLE PRECISION,
              momentum_long_mean           DOUBLE PRECISION,
              recence_jours_mean           DOUBLE PRECISION,
              description                  TEXT,
              computed_at                  TIMESTAMP DEFAULT NOW()
              );

          CREATE TABLE IF NOT EXISTS kpi_business (
                                                      id                        SERIAL PRIMARY KEY,
                                                      cluster_id                INTEGER NOT NULL UNIQUE,
                                                      profile_name              VARCHAR(100),
              computed_at               TIMESTAMP DEFAULT NOW(),
              churn_score_30j           DOUBLE PRECISION,
              churn_score_90j           DOUBLE PRECISION,
              churn_pct_high_risk       DOUBLE PRECISION,
              churn_pct_critical        DOUBLE PRECISION,
              taux_retention            DOUBLE PRECISION,
              hazard_rate               DOUBLE PRECISION,
              ltv_6m_base               DOUBLE PRECISION,
              ltv_12m_base              DOUBLE PRECISION,
              ltv_12m_optimiste         DOUBLE PRECISION,
              ltv_12m_pessimiste        DOUBLE PRECISION,
              arpu_mensuel              DOUBLE PRECISION,
              marge_base                DOUBLE PRECISION,
              marge_optimiste           DOUBLE PRECISION,
              marge_pessimiste          DOUBLE PRECISION,
              taux_activation           DOUBLE PRECISION,
              nb_categories_actives     DOUBLE PRECISION,
              taux_cross_sell           DOUBLE PRECISION,
              taux_reversal             DOUBLE PRECISION,
              score_risque              DOUBLE PRECISION,
              growth_rate_3m            DOUBLE PRECISION,
              growth_rate_6m            DOUBLE PRECISION
              );

          CREATE TABLE IF NOT EXISTS centroids_registry (
                                                            id              SERIAL PRIMARY KEY,
                                                            cluster_id      INTEGER NOT NULL UNIQUE,
                                                            profile_name    VARCHAR(100),
              centroid_vector DOUBLE PRECISION[],
              n_features      INTEGER,
              computed_at     TIMESTAMP DEFAULT NOW()
              );

          CREATE TABLE IF NOT EXISTS feature_distributions (
                                                               id              SERIAL PRIMARY KEY,
                                                               feature_name    VARCHAR(100) NOT NULL UNIQUE,
              bin_edges       DOUBLE PRECISION[],
              bin_counts      DOUBLE PRECISION[],
              n_samples       INTEGER,
              computed_at     TIMESTAMP DEFAULT NOW()
              );

          CREATE TABLE IF NOT EXISTS monitoring_alerts (
                                                           id              SERIAL PRIMARY KEY,
                                                           alert_type      VARCHAR(50) NOT NULL,
              feature_name    VARCHAR(100),
              psi_value       DOUBLE PRECISION,
              ks_pvalue       DOUBLE PRECISION,
              severity        VARCHAR(20) NOT NULL,
              message         TEXT,
              triggered_at    TIMESTAMP DEFAULT NOW(),
              resolved        BOOLEAN DEFAULT FALSE
              );
          CREATE INDEX IF NOT EXISTS idx_alerts_type ON monitoring_alerts(alert_type);
          CREATE INDEX IF NOT EXISTS idx_alerts_date ON monitoring_alerts(triggered_at DESC); \
          """
    conn = get_connection()
    try:
        with conn.cursor() as cur:
            cur.execute(sql)
        conn.commit()
        logger.info("  ✓ Tables initialisées / vérifiées")
    except Exception as e:
        conn.rollback()
        logger.error(f"  ✗ Erreur init tables : {e}")
        raise
    finally:
        release_connection(conn)


# ==============================================================================
# PHASE 1 — BUSINESS UNDERSTANDING
# ==============================================================================
def phase1_business_understanding():
    _banner("PHASE 1 — BUSINESS UNDERSTANDING")
    logger.info("  Objectif      : Classification comportementale wallet mobile")
    logger.info("  Profils cible : 6 profils + détection fragiles + zone mixte")
    logger.info("  UMAP          : " + ("✓ Disponible" if UMAP_AVAILABLE else "⚠ Fallback PCA"))
    logger.info("\n  CARACTÉRISTIQUES DU MODÈLE :")
    logger.info("    ✓ Churn calibré (scale=5, center=0.22) — discrimination réelle")
    logger.info("    ✓ Détection/fusion profils fragiles (seuil sil < 0.30)")
    logger.info("    ✓ Intégration FastAPI via main.py (port 8000)")
    logger.info("    ✓ Monitoring PSI auto (APScheduler)")
    logger.info("    ✓ Hold-out anti-leakage 10% avant clustering")
    logger.info("    ✓ LTV marge 2.0% base + analyse sensibilité")
    logger.info("\n  OBJECTIFS MÉTIER :")
    logger.info("    → Identifier les clients à risque churn avec discrimination réelle")
    logger.info("    → LTV actionnables pour priorisation commerciale")
    logger.info("    → API production-ready (FastAPI) avec monitoring")


# ==============================================================================
# PHASE 2 — DATA UNDERSTANDING
# ==============================================================================
def phase2_data_understanding():
    _banner("PHASE 2 — DATA UNDERSTANDING (PostgreSQL)")
    logger.info("  Chargement des données depuis PostgreSQL...")

    conn = get_connection()
    try:
        clients   = pd.read_sql("SELECT * FROM client", conn)
        providers = pd.read_sql("SELECT * FROM provider", conn)
        tx_types  = pd.read_sql("SELECT * FROM type_transaction", conn)
        txn = pd.read_sql("""
                          SELECT t.id AS txn_id, t.client_id, t.transaction_date,
                                 t.amount, t.reversal_flag, t.transaction_type_id, t.provider_id,
                                 tt.category, tt.sub_category, tt.type AS txn_type,
                                 p.provider_name, p.provider_code
                          FROM transaction t
                                   LEFT JOIN type_transaction tt ON t.transaction_type_id = tt.id
                                   LEFT JOIN provider p          ON t.provider_id = p.id
                          WHERE t.transaction_date >= '2023-01-01'
                          ORDER BY t.transaction_date
                          """, conn)
    finally:
        release_connection(conn)

    txn['transaction_date'] = pd.to_datetime(txn['transaction_date'], errors='coerce')
    txn = txn.dropna(subset=['transaction_date'])
    _enrich_dates(txn)

    date_ref   = pd.Timestamp(CFG["date_ref"])
    date_start = txn['transaction_date'].min()
    n_months   = max(1, (date_ref - date_start).days / 30.44)

    logger.info(f"  ✓ Clients      : {len(clients):,}")
    logger.info(f"  ✓ Transactions : {len(txn):,}")
    logger.info(f"  ✓ Période      : {date_start.date()} → {date_ref.date()}")
    logger.info(f"  ✓ Durée        : {n_months:.1f} mois")
    logger.info(f"  ✓ Montant méd. : {txn['amount'].median():.1f} TND")
    logger.info(f"  ✓ Taux reversal global : {(txn['reversal_flag']=='Y').mean()*100:.2f}%")

    _plot_phase2(txn)
    return txn, clients, tx_types, providers, date_ref, date_start, n_months


def _enrich_dates(df):
    df['year']         = df['transaction_date'].dt.year
    df['month']        = df['transaction_date'].dt.month
    df['day_of_week']  = df['transaction_date'].dt.dayofweek
    df['hour']         = df['transaction_date'].dt.hour
    df['year_month']   = df['transaction_date'].dt.to_period('M')
    df['year_quarter'] = df['transaction_date'].dt.to_period('Q')
    df['date_only']    = df['transaction_date'].dt.date


def _plot_phase2(df):
    fig = plt.figure(figsize=(20, 12))
    fig.suptitle('Phase 2 — Exploration des Données Wallet Mobile (CRISP-DM)',
                 fontsize=14, fontweight='bold', y=0.99)
    gs = gridspec.GridSpec(2, 3, figure=fig, hspace=0.45, wspace=0.35)

    ax1 = fig.add_subplot(gs[0, 0])
    ax1.hist(np.log1p(df['amount']), bins=60, color=PALETTE[0], edgecolor='white', alpha=0.85)
    for q, c in [(0.25, 'orange'), (0.50, 'red'), (0.75, 'green')]:
        v = df['amount'].quantile(q)
        ax1.axvline(np.log1p(v), color=c, ls='--', lw=1.5, label=f'Q{int(q*100)}: {v:.0f} TND')
    ax1.set_title('Distribution Montants (log)')
    ax1.set_xlabel('log(1 + montant TND)')
    ax1.legend(fontsize=7)

    ax2 = fig.add_subplot(gs[0, 1])
    quarterly = df.groupby('year_quarter').size().reset_index(name='count')
    quarterly['label'] = quarterly['year_quarter'].astype(str)
    c_map = {'2023': '#90CAF9', '2024': '#66BB6A', '2025': '#FFA726', '2026': '#EF5350'}
    ax2.bar(quarterly['label'], quarterly['count'],
            color=[c_map.get(l[:4], '#ccc') for l in quarterly['label']],
            alpha=0.9, edgecolor='white')
    ax2.set_title('Volume Transactions par Trimestre')
    ax2.tick_params(axis='x', rotation=45, labelsize=7)

    ax3 = fig.add_subplot(gs[0, 2])
    cat = df['category'].value_counts().head(9)
    bars = ax3.barh(cat.index[::-1], cat.values[::-1], color=PALETTE * 2, alpha=0.85)
    for bar, val in zip(bars, cat.values[::-1]):
        ax3.text(bar.get_width() + 200, bar.get_y() + bar.get_height() / 2,
                 f'{val/1000:.0f}k', va='center', fontsize=8)
    ax3.set_title('Top Catégories de Transactions')

    ax4 = fig.add_subplot(gs[1, 0])
    hr = df['hour'].value_counts().sort_index()
    ax4.bar(hr.index, hr.values, color=PALETTE[3], alpha=0.8)
    ax4.axvspan(6, 12, alpha=0.08, color='gold', label='Matin (6h-12h)')
    ax4.axvspan(19, 23, alpha=0.1, color='navy', label='Soir (19h-23h)')
    ax4.set_title('Usage par Heure de la Journée')
    ax4.legend(fontsize=8)

    ax5 = fig.add_subplot(gs[1, 1])
    tpc = df.groupby('client_id').size()
    ax5.hist(np.log1p(tpc), bins=50, color=PALETTE[2], edgecolor='white', alpha=0.85)
    ax5.axvline(np.log1p(tpc.median()), color='red', ls='--',
                label=f'Médiane: {tpc.median():.0f} txn')
    ax5.set_title('Transactions / Client (log)')
    ax5.legend(fontsize=8)

    ax6 = fig.add_subplot(gs[1, 2])
    monthly = df.groupby('year_month').agg(clients=('client_id', 'nunique')).reset_index()
    monthly['ym'] = monthly['year_month'].astype(str)
    ax6.fill_between(monthly['ym'], monthly['clients'], alpha=0.3, color=PALETTE[5])
    ax6.plot(monthly['ym'], monthly['clients'], color=PALETTE[5], lw=2, marker='o', ms=2)
    ax6.set_title('Clients Actifs Mensuels (MAU)')
    ax6.tick_params(axis='x', rotation=45, labelsize=6)

    fig.tight_layout()
    path = f"{CFG['fig_dir']}/phase2_exploration.png"
    fig.savefig(path, dpi=150, bbox_inches='tight')
    plt.close(fig)
    logger.info(f"  ✓ {path}")


# ==============================================================================
# PHASE 3 — DATA PREPARATION
# ==============================================================================
def phase3_data_preparation(df, clients, date_ref, date_start, n_months):
    _banner("PHASE 3 — DATA PREPARATION (43+ features + hold-out anti-leakage)")

    df = df.dropna(subset=['transaction_date', 'client_id', 'amount'])
    df = df[df['amount'] > 0]
    logger.info(f"  Après nettoyage : {len(df):,} transactions")

    rc   = date_ref - pd.DateOffset(months=CFG["recent_months"])
    rm   = date_ref - pd.DateOffset(months=CFG["medium_months"])
    df_3m = df[df['transaction_date'] >= rc]
    df_6m = df[(df['transaction_date'] >= rm) & (df['transaction_date'] < rc)]

    categories_cles = [
        'Factures & Services', 'Recharge Telephonique',
        'Shopping & Paiements', 'Restaurants & Livraison',
        'Transferts Envoyes', 'Transferts Recus',
        'Depot & Retrait', 'Voyages & Reservations',
        'Education & Institutions',
    ]
    rename_cat = {
        'Factures & Services':      'nb_factures',
        'Recharge Telephonique':    'nb_recharges',
        'Shopping & Paiements':     'nb_shopping',
        'Restaurants & Livraison':  'nb_restaurants',
        'Transferts Envoyes':       'nb_transferts_envoyes',
        'Transferts Recus':         'nb_transferts_recus',
        'Depot & Retrait':          'nb_depot_retrait_raw',
        'Voyages & Reservations':   'nb_voyages',
        'Education & Institutions': 'nb_education',
    }

    f_act = df.groupby('client_id').agg(
        total_transactions=('client_id', 'count'),
        total_valid_txn=('reversal_flag', lambda x: (x == 'N').sum()),
        total_reversals=('reversal_flag', lambda x: (x == 'Y').sum()),
        nb_active_months=('year_month', 'nunique'),
        date_first_txn=('transaction_date', 'min'),
        date_last_txn=('transaction_date', 'max'),
    ).reset_index()
    f_act['anciennete_jours'] = (f_act['date_last_txn'] - f_act['date_first_txn']).dt.days
    f_act['maturite_jours']   = (date_ref - f_act['date_first_txn']).dt.days
    f_act['freq_mensuelle']   = f_act['total_transactions'] / n_months
    f_act['taux_reversal']    = f_act['total_reversals'] / f_act['total_transactions'].replace(0, 1)
    f_act['regularite']       = (f_act['nb_active_months'] / n_months).clip(0, 1)

    f_fin = df.groupby('client_id')['amount'].agg(
        montant_total='sum', montant_moyen='mean',
        montant_median='median', montant_max='max', montant_std='std',
    ).reset_index()
    f_fin['montant_std'] = f_fin['montant_std'].fillna(0)
    f_fin['cv_montants'] = (f_fin['montant_std'] / f_fin['montant_moyen'].replace(0, 1)).clip(0, 10)

    f_div = df.groupby('client_id').agg(
        nb_categories_distinctes=('category', 'nunique'),
        nb_providers_distincts=('provider_name', 'nunique'),
    ).reset_index()

    def shannon_entropy(series):
        counts = series.value_counts(normalize=True)
        return float(-(counts * np.log2(counts + 1e-10)).sum())

    f_entropy = df.groupby('client_id')['category'].apply(shannon_entropy).reset_index()
    f_entropy.columns = ['client_id', 'entropy_categories']

    cat_dum = pd.get_dummies(df['category']).reindex(columns=categories_cles, fill_value=0)
    cat_dum['client_id'] = df['client_id'].values
    f_cat = cat_dum.groupby('client_id')[categories_cles].sum().reset_index().rename(columns=rename_cat)

    base = f_act[['client_id', 'total_transactions']].merge(f_cat, on='client_id', how='left').fillna(0)
    eps  = 0.001
    f_ratios = pd.DataFrame({'client_id': base['client_id']})
    for col_raw, col_ratio in [
        ('nb_factures', 'ratio_factures'), ('nb_recharges', 'ratio_recharges'),
        ('nb_shopping', 'ratio_shopping'), ('nb_restaurants', 'ratio_restaurants'),
        ('nb_transferts_envoyes', 'ratio_transferts'), ('nb_voyages', 'ratio_voyages'),
        ('nb_education', 'ratio_education'),
    ]:
        if col_raw in base.columns:
            f_ratios[col_ratio] = (base[col_raw] / (base['total_transactions'] + eps)).clip(0, 1)
    if 'nb_depot_retrait_raw' in base.columns:
        f_ratios['log_depot_retrait'] = np.log1p(base['nb_depot_retrait_raw'])

    f_recence = df.groupby('client_id')['transaction_date'].max().reset_index()
    f_recence.columns = ['client_id', 'last_txn_date']
    f_recence['recence_jours'] = (date_ref - f_recence['last_txn_date']).dt.days

    f_tempo  = _build_temporal_features(df, df_3m, df_6m, date_ref, n_months)
    f_scores = _build_composite_scores(f_act, f_fin, f_recence, n_months)

    features = f_act[['client_id', 'total_transactions', 'total_valid_txn',
                      'nb_active_months', 'anciennete_jours', 'maturite_jours',
                      'freq_mensuelle', 'taux_reversal', 'regularite']]
    for f in [f_fin, f_div, f_entropy, f_cat, f_ratios,
              f_recence[['client_id', 'recence_jours']], f_tempo, f_scores]:
        features = features.merge(f, on='client_id', how='left')

    features = features.fillna(0)
    features = features[features['total_transactions'] >= CFG["min_txn"]]
    logger.info(f"  ✓ Clients actifs   : {len(features):,}")

    np.random.seed(CFG["random_state"])
    holdout_mask = np.random.rand(len(features)) < CFG["holdout_frac"]
    features['in_holdout']    = holdout_mask
    features_train   = features[~holdout_mask].copy()
    features_holdout = features[holdout_mask].copy()
    logger.info(f"  ✓ Clustering set   : {len(features_train):,} clients")
    logger.info(f"  ✓ Hold-out set     : {len(features_holdout):,} clients ({CFG['holdout_frac']*100:.0f}%)")

    exclude = ['client_id', 'date_first_txn', 'date_last_txn', 'last_txn_date',
               'total_reversals', 'in_holdout']
    feature_cols = [c for c in features.columns
                    if c not in exclude and pd.api.types.is_numeric_dtype(features[c])]
    logger.info(f"  ✓ Features         : {len(feature_cols)} variables")

    scaler           = RobustScaler()
    X_scaled         = scaler.fit_transform(features_train[feature_cols].fillna(0))
    X_holdout_scaled = scaler.transform(features_holdout[feature_cols].fillna(0))

    joblib.dump(scaler,       _model_path('scaler'))
    joblib.dump(feature_cols, _model_path('features'))

    _plot_phase3(features_train, X_scaled, feature_cols)
    return (features_train, features_holdout, X_scaled, X_holdout_scaled,
            feature_cols, scaler, features)


def _build_temporal_features(df, df_3m, df_6m, date_ref, n_months):
    clients = df['client_id'].unique()
    cnt_3m  = df_3m.groupby('client_id').size().to_dict()
    amt_3m  = df_3m.groupby('client_id')['amount'].sum().to_dict()
    cnt_6m  = df_6m.groupby('client_id').size().to_dict()
    cnt_all = df.groupby('client_id').size().to_dict()
    amt_all = df.groupby('client_id')['amount'].sum().to_dict()

    quarterly = df.groupby(['client_id', 'year_quarter']).size().reset_index(name='q_cnt')
    q_var  = quarterly.groupby('client_id')['q_cnt'].std().fillna(0)
    q_mean = quarterly.groupby('client_id')['q_cnt'].mean().replace(0, 1)
    seasonality = (q_var / q_mean).fillna(0).to_dict()

    monthly  = df.groupby(['client_id', 'year_month']).size().reset_index(name='m_cnt')
    m_std    = monthly.groupby('client_id')['m_cnt'].std().fillna(0)
    m_mean   = monthly.groupby('client_id')['m_cnt'].mean().replace(0, 1)
    stability = (1 - (m_std / m_mean).clip(0, 2) / 2).fillna(0).to_dict()

    df_h = df.copy()
    df_h['is_day'] = df_h['hour'].between(7, 20).astype(int)
    day_ratio = df_h.groupby('client_id')['is_day'].mean().fillna(0.5).to_dict()

    records = []
    for cid in clients:
        n_tot  = cnt_all.get(cid, 1)
        n_3m   = cnt_3m.get(cid, 0)
        n_6m   = cnt_6m.get(cid, 0)
        a_3m   = amt_3m.get(cid, 0)
        a_all  = amt_all.get(cid, 1)

        avg_monthly    = n_tot / max(n_months, 1)
        avg_3m_monthly = n_3m / max(CFG["recent_months"], 1)
        avg_6m_monthly = n_6m / max(CFG["medium_months"] - CFG["recent_months"], 1)

        momentum_court   = avg_3m_monthly / max(avg_monthly, 0.001)
        momentum_long    = (avg_3m_monthly + avg_6m_monthly) / max(avg_monthly * 2, 0.001)
        momentum_montant = a_3m / max(a_all / n_months * CFG["recent_months"], 0.001)

        records.append({
            'client_id':           cid,
            'momentum_court':      min(momentum_court, 5.0),
            'momentum_long':       min(momentum_long, 5.0),
            'momentum_montant':    min(momentum_montant, 5.0),
            'ratio_jour':          day_ratio.get(cid, 0.5),
            'score_saisonnalite':  min(seasonality.get(cid, 0), 2.0),
            'stabilite_mensuelle': stability.get(cid, 0.5),
        })
    return pd.DataFrame(records)


def _build_composite_scores(f_act, f_fin, f_recence, n_months):
    merged = f_act[['client_id', 'total_transactions', 'nb_active_months', 'freq_mensuelle']].merge(
        f_fin[['client_id', 'montant_total', 'montant_moyen']], on='client_id'
    ).merge(f_recence[['client_id', 'recence_jours']], on='client_id')
    max_r   = merged['recence_jours'].max() + 1
    r_score = 1 - merged['recence_jours'] / max_r
    f_score = (merged['freq_mensuelle'] / merged['freq_mensuelle'].quantile(0.95)).clip(0, 1)
    m_score = (np.log1p(merged['montant_total']) /
               np.log1p(merged['montant_total'].quantile(0.95))).clip(0, 1)
    merged['rfm_score']    = (r_score + f_score + m_score) / 3
    merged['loyalty_score'] = (merged['nb_active_months'] / n_months).clip(0, 1)
    return merged[['client_id', 'rfm_score', 'loyalty_score']]


def _plot_phase3(features, X_scaled, feature_cols):
    tempo_feats = ['momentum_court', 'momentum_long', 'momentum_montant',
                   'ratio_jour', 'score_saisonnalite', 'stabilite_mensuelle',
                   'rfm_score', 'loyalty_score', 'entropy_categories']
    labels = ['Momentum Court (3m)', 'Momentum Long (6m)', 'Momentum Montant',
              'Ratio Jour/Nuit', 'Score Saisonnalité', 'Stabilité Mensuelle',
              'Score RFM', 'Score Fidélité', 'Entropie Catégories']

    fig, axes = plt.subplots(3, 3, figsize=(18, 12))
    fig.suptitle('Phase 3 — Distribution des Features Clés (CRISP-DM)', fontsize=14, fontweight='bold')
    for i, (feat, label) in enumerate(zip(tempo_feats, labels)):
        ax = axes[i // 3][i % 3]
        if feat in features.columns:
            data = features[feat].dropna()
            ax.hist(data, bins=40, color=PALETTE[i % 6], edgecolor='white', alpha=0.85)
            med = data.median()
            ax.axvline(med, color='red', ls='--', lw=1.5, label=f'Med: {med:.3f}')
            ax.set_title(label, fontsize=10)
            ax.legend(fontsize=8)
    fig.tight_layout()
    fig.savefig(f"{CFG['fig_dir']}/phase3_features.png", dpi=150, bbox_inches='tight')
    plt.close(fig)
    logger.info("  ✓ Figures Phase 3")


# ==============================================================================
# PHASE 4 — MODELING
# ==============================================================================
def phase4_modeling(features_train, X_scaled, feature_cols):
    _banner("PHASE 4 — MODELING (UMAP + K-Means + Détection Profils Fragiles)")

    if UMAP_AVAILABLE:
        logger.info(f"  UMAP : {CFG['umap_n_components']} composantes...")
        umap_model = umap.UMAP(
            n_components=CFG["umap_n_components"], n_neighbors=CFG["umap_n_neighbors"],
            min_dist=CFG["umap_min_dist"], metric=CFG["umap_metric"],
            random_state=CFG["random_state"], n_jobs=-1
        )
        X_reduced = umap_model.fit_transform(X_scaled)
        joblib.dump(umap_model, _model_path('umap'))
        umap_2d = umap.UMAP(n_components=2, n_neighbors=CFG["umap_n_neighbors"],
                            min_dist=0.15, random_state=CFG["random_state"], n_jobs=-1)
        X_2d = umap_2d.fit_transform(X_scaled)
        logger.info(f"  ✓ UMAP : {X_reduced.shape}")
    else:
        logger.info("  ⚠ UMAP non disponible — fallback PCA 95% variance")
        pca = PCA(n_components=min(50, X_scaled.shape[1]), random_state=CFG["random_state"])
        X_pca = pca.fit_transform(X_scaled)
        cumvar = pca.explained_variance_ratio_.cumsum()
        n95 = np.argmax(cumvar >= 0.95) + 1
        X_reduced = X_pca[:, :n95]
        pca_2d = PCA(n_components=2, random_state=CFG["random_state"])
        X_2d = pca_2d.fit_transform(X_scaled)
        umap_model = None

    logger.info("  Recherche K optimal (k=4 à 9)...")
    k_range, inertias, silhouettes, db_scores, ch_scores = [], [], [], [], []
    for k in range(CFG["k_min"], CFG["k_max"] + 1):
        km   = KMeans(n_clusters=k, random_state=CFG["random_state"], n_init=20, max_iter=500)
        labs = km.fit_predict(X_reduced)
        inertias.append(km.inertia_)
        ss = silhouette_score(X_reduced, labs, sample_size=min(3000, len(features_train)))
        db = davies_bouldin_score(X_reduced, labs)
        ch = calinski_harabasz_score(X_reduced, labs)
        silhouettes.append(ss); db_scores.append(db); ch_scores.append(ch); k_range.append(k)
        logger.info(f"    k={k} → Sil={ss:.4f}  DB={db:.4f}  CH={ch:.0f}")

    k = CFG["k_optimal"]
    logger.info(f"\n  K-Means final k={k}...")
    km_final   = KMeans(n_clusters=k, random_state=CFG["random_state"], n_init=30, max_iter=1000)
    raw_labels = km_final.fit_predict(X_reduced)
    joblib.dump(km_final, _model_path('kmeans'))

    sil_km = silhouette_score(X_reduced, raw_labels, sample_size=min(5000, len(features_train)))
    logger.info(f"  ✓ Silhouette K-Means : {sil_km:.4f}")

    logger.info(f"  Bootstrap ({CFG['bootstrap_n']} iter)...")
    stab_scores = []
    for i in range(CFG["bootstrap_n"]):
        idx = resample(np.arange(len(X_reduced)), replace=False,
                       n_samples=int(len(X_reduced) * CFG["bootstrap_frac"]),
                       random_state=CFG["random_state"] + i)
        km_bs   = KMeans(n_clusters=k, random_state=CFG["random_state"], n_init=10)
        labs_bs = km_bs.fit_predict(X_reduced[idx])
        stab_scores.append(silhouette_score(X_reduced[idx], labs_bs))
    stab_mean = np.mean(stab_scores)
    stab_std  = np.std(stab_scores)
    logger.info(f"  ✓ Bootstrap : {stab_mean:.4f} ± {stab_std:.4f}")

    cluster_labels, centroid_mapping = _assign_cluster_labels(km_final, raw_labels, X_reduced)
    sil_samples = silhouette_samples(X_reduced, cluster_labels)

    fragile_profiles = _detect_fragile_profiles(cluster_labels, sil_samples)
    if fragile_profiles:
        logger.warning(f"  ⚠ Profils fragiles détectés : {fragile_profiles}")
        for fp in fragile_profiles:
            sil_fp = float(sil_samples[cluster_labels == fp].mean())
            n_fp   = int((cluster_labels == fp).sum())
            logger.warning(f"    → P{fp} : sil={sil_fp:.4f}, n={n_fp}")
    else:
        logger.info("  ✓ Tous les profils sont stables (sil > 0.30)")

    _plot_phase4(X_2d, X_reduced, cluster_labels,
                 k_range, inertias, silhouettes, db_scores, ch_scores,
                 stab_scores, sil_samples, fragile_profiles)

    return (km_final, cluster_labels, X_reduced, X_2d,
            sil_samples, stab_mean, stab_std, silhouettes, inertias, db_scores,
            umap_model, centroid_mapping, fragile_profiles)


def _detect_fragile_profiles(cluster_labels, sil_samples):
    k      = CFG["k_optimal"]
    fragile = []
    for c in range(k):
        mask = cluster_labels == c
        if not mask.any():
            continue
        sil_c = float(sil_samples[mask].mean())
        if sil_c < CFG["sil_min_profile"]:
            fragile.append(c)
    return fragile


def _assign_cluster_labels(km_final, raw_labels, X_reduced):
    k      = CFG["k_optimal"]
    counts = np.bincount(raw_labels, minlength=k)
    remap  = {int(old): int(new) for new, old in enumerate(np.argsort(counts)[::-1])}
    cluster_labels_final = np.array([remap[c] for c in raw_labels])
    centroid_mapping     = {int(v): int(v) for v in range(k)}
    return cluster_labels_final, centroid_mapping


def _save_centroids_to_db(km_final, cluster_labels, feature_cols):
    pnames = CFG["profile_names"]
    k      = CFG["k_optimal"]
    rows   = []
    for c in range(k):
        centroid = km_final.cluster_centers_[c].tolist()
        rows.append((c, pnames.get(c, f"P{c}"), centroid, len(centroid)))
    conn = get_connection()
    try:
        with conn.cursor() as cur:
            execute_values(cur, """
                                INSERT INTO centroids_registry
                                    (cluster_id, profile_name, centroid_vector, n_features)
                                VALUES %s
                                    ON CONFLICT (cluster_id) DO UPDATE SET
                                    centroid_vector=EXCLUDED.centroid_vector, computed_at=NOW()
                                """, rows)
        conn.commit()
    except Exception as e:
        conn.rollback(); logger.error(f"  ✗ Erreur save centroïdes : {e}")
    finally:
        release_connection(conn)


def _plot_phase4(X_2d, X_reduced, cluster_labels, k_range, inertias,
                 silhouettes, db_scores, ch_scores, stab_scores, sil_samples,
                 fragile_profiles):
    pnames = CFG["profile_names"]
    k      = CFG["k_optimal"]

    fig, axes = plt.subplots(1, 4, figsize=(22, 5))
    fig.suptitle('Phase 4 — Sélection K optimal (espace UMAP)', fontsize=14, fontweight='bold')
    axes[0].plot(k_range, inertias, 'b-o', lw=2, ms=7)
    axes[0].axvline(k, color='red', ls='--', label=f'k={k} choisi')
    axes[0].set_title('Méthode Coude (Elbow)'); axes[0].legend()
    axes[1].plot(k_range, silhouettes, 'g-s', lw=2, ms=7)
    axes[1].axvline(k, color='red', ls='--', label=f'k={k}')
    axes[1].set_title('Silhouette Score'); axes[1].legend()
    axes[2].plot(k_range, db_scores, 'r-^', lw=2, ms=7)
    axes[2].axvline(k, color='blue', ls='--', label=f'k={k}')
    axes[2].set_title('Davies-Bouldin (↓=meilleur)'); axes[2].legend()
    axes[3].plot(k_range, ch_scores, 'm-D', lw=2, ms=7)
    axes[3].axvline(k, color='blue', ls='--', label=f'k={k}')
    axes[3].set_title('Calinski-Harabasz (↑=meilleur)'); axes[3].legend()
    fig.tight_layout()
    fig.savefig(f"{CFG['fig_dir']}/phase4_elbow.png", dpi=150, bbox_inches='tight')
    plt.close(fig)

    fig2, ax = plt.subplots(figsize=(12, 4))
    ax.plot(range(1, len(stab_scores) + 1), stab_scores, 'b-o', lw=2, ms=6)
    mean_s = np.mean(stab_scores); std_s = np.std(stab_scores)
    ax.axhline(mean_s, color='red', ls='--', label=f'Moy: {mean_s:.4f} ± {std_s:.4f}')
    ax.fill_between(range(1, len(stab_scores) + 1),
                    mean_s - std_s, mean_s + std_s, alpha=0.2, color='red')
    ax.set_title(f'Stabilité Bootstrap ({len(stab_scores)} itérations)', fontweight='bold')
    ax.legend()
    fig2.tight_layout()
    fig2.savefig(f"{CFG['fig_dir']}/phase4_bootstrap.png", dpi=150, bbox_inches='tight')
    plt.close(fig2)

    fig3, ax = plt.subplots(figsize=(12, 9))
    for c in range(k):
        mask = cluster_labels == c
        label_str = f'P{c}: {pnames.get(c,"?")}'
        if c in fragile_profiles:
            label_str += ' ⚠ FRAGILE'
        ax.scatter(X_2d[mask, 0], X_2d[mask, 1], c=PALETTE[c], s=8, alpha=0.6, label=label_str)
    ax.set_title('Phase 4 — Visualisation UMAP 2D (6 profils, ⚠=fragile)', fontweight='bold')
    ax.legend(loc='upper right', fontsize=9, markerscale=3)
    ax.set_xlabel('UMAP-1'); ax.set_ylabel('UMAP-2')
    fig3.tight_layout()
    fig3.savefig(f"{CFG['fig_dir']}/phase4_umap_clusters.png", dpi=150, bbox_inches='tight')
    plt.close(fig3)

    fig4, ax = plt.subplots(figsize=(10, 7))
    y_lower = 0
    for c in range(k):
        c_sil  = np.sort(sil_samples[cluster_labels == c])
        size_c = len(c_sil)
        y_upper = y_lower + size_c
        ax.fill_betweenx(np.arange(y_lower, y_upper), 0, c_sil, alpha=0.75,
                         color=PALETTE[c], label=f'P{c} ({size_c})')
        if c in fragile_profiles:
            ax.fill_betweenx(np.arange(y_lower, y_upper), 0, c_sil, alpha=0.25,
                             color='red', hatch='////')
        y_lower = y_upper + 10
    ax.axvline(sil_samples.mean(), color='red', ls='--', label=f'Moy: {sil_samples.mean():.3f}')
    ax.axvline(CFG["sil_min_profile"], color='orange', ls=':', lw=2,
               label=f'Seuil fragile ({CFG["sil_min_profile"]})')
    ax.set_title('Phase 4 — Silhouette Plot (profils fragiles en rouge hachuré)', fontweight='bold')
    ax.set_xlabel('Valeur silhouette'); ax.legend(fontsize=8)
    fig4.tight_layout()
    fig4.savefig(f"{CFG['fig_dir']}/phase4_silhouette.png", dpi=150, bbox_inches='tight')
    plt.close(fig4)
    logger.info("  ✓ 4 figures Phase 4")


# ==============================================================================
# PHASE 5 — ÉVALUATION
# ==============================================================================
def phase5_evaluation(features_train, X_scaled, X_reduced, cluster_labels,
                      km_final, feature_cols, sil_samples, stab_mean, stab_std,
                      fragile_profiles):
    _banner("PHASE 5 — ÉVALUATION (Silhouette + DB + CH + Rapport Fragiles)")
    pnames = CFG["profile_names"]
    k      = CFG["k_optimal"]

    sil_global = float(silhouette_score(X_reduced, cluster_labels, sample_size=min(5000, len(features_train))))
    db_global  = float(davies_bouldin_score(X_reduced, cluster_labels))
    ch_global  = float(calinski_harabasz_score(X_reduced, cluster_labels))
    logger.info(f"  ► Silhouette          : {sil_global:.4f}")
    logger.info(f"  ► Davies-Bouldin      : {db_global:.4f}")
    logger.info(f"  ► Calinski-Harabasz   : {ch_global:.0f}")
    logger.info(f"  ► Bootstrap Stabilité : {stab_mean:.4f} ± {stab_std:.4f}")

    features_eval = features_train.copy()
    features_eval['cluster_id']    = cluster_labels
    features_eval['sil_score']     = sil_samples
    features_eval['profile_label'] = [pnames.get(c, f'P{c}') for c in cluster_labels]

    per_cluster = {}
    for c in range(k):
        mask = cluster_labels == c; n_c = int(mask.sum()); pct = n_c / len(features_eval) * 100
        sm   = float(sil_samples[mask].mean()); smin = float(sil_samples[mask].min())
        is_fragile = c in fragile_profiles
        per_cluster[c] = {
            'n': n_c, 'pct': round(pct, 2),
            'sil_mean': round(sm, 4), 'sil_min': round(smin, 4),
            'is_fragile': is_fragile
        }
        fragile_str = " ⚠ FRAGILE" if is_fragile else ""
        logger.info(f"  P{c} {pnames.get(c,'?'):<35} {n_c:>5} ({pct:.1f}%) | sil={sm:.4f}{fragile_str}")

    sil_min_g, sil_max_g = sil_samples.min(), sil_samples.max()
    conf_norm  = (sil_samples - sil_min_g) / (sil_max_g - sil_min_g + 1e-9)
    mixte_mask = conf_norm < CFG["confidence_threshold"]
    n_mixte    = int(mixte_mask.sum())

    features_eval['confidence_score'] = np.round(conf_norm, 4)
    features_eval['is_mixte']         = mixte_mask
    features_eval['profile_final']    = features_eval.apply(
        lambda r: 'MIXTE' if r['is_mixte'] else r['profile_label'], axis=1
    )

    _plot_phase5(features_eval, sil_samples, cluster_labels,
                 sil_global, db_global, ch_global, stab_mean, stab_std,
                 mixte_mask, per_cluster, fragile_profiles)
    return per_cluster, sil_global, db_global, ch_global, features_eval


def _plot_phase5(features_eval, sil_samples, cluster_labels, sil_global, db_global,
                 ch_global, stab_mean, stab_std, mixte_mask, per_cluster, fragile_profiles):
    pnames = CFG["profile_names"]; k = CFG["k_optimal"]

    fig, axes = plt.subplots(1, 2, figsize=(16, 6))
    fig.suptitle('Phase 5 — Évaluation : 6 Profils + Rapport Fragiles', fontsize=14, fontweight='bold')
    counts = [per_cluster[c]['n'] for c in range(k)]
    labels = [f'P{c}\n{pnames.get(c,"?")[:12]}' for c in range(k)]
    bar_colors = ['#E53935' if c in fragile_profiles else PALETTE[c] for c in range(k)]
    bars   = axes[0].bar(labels, counts, color=bar_colors, alpha=0.85, edgecolor='white')
    for bar, c in zip(bars, range(k)):
        cnt = per_cluster[c]['n']
        axes[0].text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 5,
                     f'{cnt:,}\n({cnt/len(features_eval)*100:.1f}%)', ha='center', va='bottom', fontsize=7)
    axes[0].set_title('Répartition (rouge = profil fragile)'); axes[0].set_ylabel('Nb Clients')

    axes[1].pie([int((~mixte_mask).sum()), int(mixte_mask.sum())],
                labels=[f'Profil sûr\n{int((~mixte_mask).sum()):,}',
                        f'MIXTE\n{int(mixte_mask.sum()):,}'],
                colors=['#43A047', '#FFA726'], autopct='%1.1f%%', startangle=90)
    axes[1].set_title("Zone d'Incertitude")
    fig.tight_layout()
    fig.savefig(f"{CFG['fig_dir']}/phase5_evaluation.png", dpi=150, bbox_inches='tight')
    plt.close(fig)

    hmap_cols = [c for c in ['total_transactions', 'freq_mensuelle', 'montant_moyen', 'montant_total',
                             'nb_categories_distinctes', 'regularite', 'rfm_score', 'loyalty_score',
                             'momentum_court', 'momentum_long', 'ratio_jour', 'stabilite_mensuelle',
                             'ratio_factures', 'ratio_restaurants', 'ratio_shopping',
                             'log_depot_retrait', 'entropy_categories'] if c in features_eval.columns]
    hmap_data = features_eval.groupby('cluster_id')[hmap_cols].mean()
    hmap_norm = (hmap_data - hmap_data.mean()) / (hmap_data.std() + 1e-9)
    fig2, ax  = plt.subplots(figsize=(22, 6))
    sns.heatmap(hmap_norm, ax=ax, cmap='RdYlGn', annot=True, fmt='.2f',
                linewidths=0.5, linecolor='white',
                xticklabels=[c[:16] for c in hmap_cols],
                yticklabels=[f'P{i}: {pnames.get(i,"?")[:22]}' for i in hmap_data.index])
    ax.set_title('Phase 5 — Heatmap Features par Profil', fontsize=13, fontweight='bold')
    plt.xticks(rotation=35, ha='right', fontsize=8)
    fig2.tight_layout()
    fig2.savefig(f"{CFG['fig_dir']}/phase5_heatmap.png", dpi=150, bbox_inches='tight')
    plt.close(fig2)

    radar_feats = [c for c in ['freq_mensuelle', 'montant_moyen', 'nb_categories_distinctes',
                               'regularite', 'rfm_score', 'loyalty_score',
                               'momentum_court', 'entropy_categories'] if c in features_eval.columns]
    radar_labs  = ['Fréquence', 'Montant', 'Diversité', 'Régularité',
                   'RFM', 'Fidélité', 'Momentum', 'Entropie'][:len(radar_feats)]
    rdata = features_eval.groupby('cluster_id')[radar_feats].mean()
    rnorm = (rdata - rdata.min()) / (rdata.max() - rdata.min() + 1e-9)
    N     = len(radar_feats)
    theta = np.linspace(0, 2 * np.pi, N, endpoint=False).tolist(); theta += theta[:1]
    fig3, axes = plt.subplots(2, 3, figsize=(18, 10), subplot_kw=dict(polar=True))
    fig3.suptitle('Phase 5 — Radar Chart des 6 Profils Clients', fontsize=14, fontweight='bold')
    for c in range(k):
        ax = axes.flatten()[c]
        if c not in rnorm.index: ax.axis('off'); continue
        vals = rnorm.loc[c].values.tolist(); vals += vals[:1]
        col  = PALETTE[c]
        ax.plot(theta, vals, 'o-', lw=2, color=col)
        ax.fill(theta, vals, alpha=0.28, color=col)
        ax.set_xticks(theta[:-1]); ax.set_xticklabels(radar_labs, size=8); ax.set_ylim(0, 1)
        n_c = (features_eval['cluster_id'] == c).sum()
        fragile_str = " ⚠" if c in fragile_profiles else ""
        ax.set_title(f'{CFG["profile_icons"].get(c,"?")} P{c}: {pnames.get(c,"?")}{fragile_str}\n(n={n_c:,})',
                     size=9, fontweight='bold', pad=15, color=col)
    fig3.tight_layout()
    fig3.savefig(f"{CFG['fig_dir']}/phase5_radar.png", dpi=150, bbox_inches='tight')
    plt.close(fig3)
    logger.info("  ✓ 3 figures Phase 5")


# ==============================================================================
# PHASE 5b — PSI DRIFT RÉEL + MONITORING AUTO
# ==============================================================================
def compute_psi_real(reference_counts, current_data, bin_edges):
    curr_counts, _ = np.histogram(current_data, bins=bin_edges)
    curr_pct = curr_counts / (curr_counts.sum() + 1e-10)
    ref_pct  = np.array(reference_counts)
    curr_pct = np.maximum(curr_pct, 1e-6)
    ref_pct  = np.maximum(ref_pct,  1e-6)
    return float(np.sum((curr_pct - ref_pct) * np.log(curr_pct / ref_pct)))


def save_distributions_to_db(features, feature_cols):
    key_features = [f for f in ['freq_mensuelle', 'montant_moyen', 'montant_total',
                                'regularite', 'rfm_score', 'momentum_court',
                                'entropy_categories', 'recence_jours', 'loyalty_score']
                    if f in features.columns]
    n_bins = CFG["psi_n_bins"]
    rows   = []
    for feat in key_features:
        data   = features[feat].dropna().values
        counts, edges = np.histogram(data, bins=n_bins)
        pct    = (counts / (counts.sum() + 1e-10)).tolist()
        rows.append((feat, edges.tolist(), pct, len(data)))

    if not rows:
        return
    conn = get_connection()
    try:
        with conn.cursor() as cur:
            execute_values(cur, """
                                INSERT INTO feature_distributions
                                    (feature_name, bin_edges, bin_counts, n_samples)
                                VALUES %s
                                    ON CONFLICT (feature_name) DO UPDATE SET
                                    bin_edges=EXCLUDED.bin_edges, bin_counts=EXCLUDED.bin_counts,
                                                                      computed_at=NOW()
                                """, rows)
        conn.commit()
        logger.info(f"  ✓ {len(rows)} distributions sauvegardées pour PSI")
    except Exception as e:
        conn.rollback(); logger.warning(f"  ⚠ Erreur save distributions : {e}")
    finally:
        release_connection(conn)


def compute_drift_report(features, feature_cols):
    _banner("PHASE 5b — DÉTECTION DATA DRIFT (PSI Réel + KS + Alertes)")

    try:
        conn = get_connection()
        with conn.cursor() as cur:
            cur.execute("SELECT fd.feature_name, fd.bin_edges, fd.bin_counts FROM feature_distributions fd")
            rows = cur.fetchall()
        release_connection(conn)
    except Exception:
        logger.info("  ℹ Aucune référence historique PSI — premier run.")
        return 0.0, {}, {}

    if not rows:
        return 0.0, {}, {}

    psi_results = {}
    ks_results  = {}
    alert_rows  = []

    for feat_name, bin_edges, bin_counts in rows:
        if feat_name not in features.columns:
            continue
        current_data = features[feat_name].dropna().values
        if len(current_data) < 10:
            continue

        bin_edges_arr = np.array(bin_edges)
        psi = compute_psi_real(bin_counts, current_data, bin_edges_arr)
        psi_results[feat_name] = round(psi, 4)

        ref_samples = np.repeat(
            (bin_edges_arr[:-1] + bin_edges_arr[1:]) / 2,
            np.maximum(np.array(bin_counts) * 1000, 1).astype(int)
        )
        ks_stat, ks_p = ks_2samp(ref_samples[:5000], current_data[:5000])
        ks_results[feat_name] = {'statistic': round(ks_stat, 4), 'p_value': round(ks_p, 4)}

        if psi >= CFG["psi_threshold"]:
            alert_rows.append((
                'PSI_DRIFT', feat_name, round(psi, 4), round(ks_p, 4),
                'CRITICAL', f'PSI={psi:.4f} ≥ {CFG["psi_threshold"]} — ré-entraînement recommandé',
            ))
        elif psi >= CFG["psi_warning"]:
            alert_rows.append((
                'PSI_WARNING', feat_name, round(psi, 4), round(ks_p, 4),
                'WARNING', f'PSI={psi:.4f} ≥ {CFG["psi_warning"]} — surveillance accrue',
            ))

    if psi_results:
        psi_max = max(psi_results.values())
        logger.info(f"  PSI max (RÉEL) : {psi_max:.4f}")
        for feat, psi in psi_results.items():
            status = "✓ Stable" if psi < 0.1 else ("⚠ Légère" if psi < 0.2 else "✗ DÉRIVE!")
            ks_p   = ks_results.get(feat, {}).get('p_value', 1.0)
            logger.info(f"    {feat:<35} PSI={psi:.4f} {status} | KS p={ks_p:.4f}")

        if alert_rows:
            _save_monitoring_alerts(alert_rows)

        _plot_psi(psi_results, ks_results)
    else:
        psi_max = 0.0

    return psi_max, psi_results, ks_results


def _save_monitoring_alerts(alert_rows):
    conn = get_connection()
    try:
        with conn.cursor() as cur:
            execute_values(cur, """
                                INSERT INTO monitoring_alerts
                                    (alert_type, feature_name, psi_value, ks_pvalue, severity, message)
                                VALUES %s
                                """, alert_rows)
        conn.commit()
        logger.warning(f"  ⚠ {len(alert_rows)} alertes enregistrées dans monitoring_alerts")
    except Exception as e:
        conn.rollback(); logger.error(f"  ✗ Erreur save alertes : {e}")
    finally:
        release_connection(conn)


def _plot_psi(psi_results, ks_results):
    if not psi_results:
        return
    fig, axes = plt.subplots(1, 2, figsize=(16, 5))
    fig.suptitle('Phase 5b — Data Drift : PSI Réel + KS Test', fontsize=14, fontweight='bold')

    feats  = list(psi_results.keys())
    values = list(psi_results.values())
    colors = ['#E53935' if v >= 0.2 else ('#FFA726' if v >= 0.1 else '#43A047') for v in values]
    bars   = axes[0].bar(feats, values, color=colors, alpha=0.85, edgecolor='white')
    axes[0].axhline(0.1, color='orange', ls='--', lw=1.5, label='Seuil léger (0.1)')
    axes[0].axhline(0.2, color='red', ls='--', lw=2, label='Seuil dérive (0.2)')
    for bar, val in zip(bars, values):
        axes[0].text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.002,
                     f'{val:.4f}', ha='center', va='bottom', fontsize=9)
    axes[0].set_title('PSI Réel par Feature')
    axes[0].set_ylabel('PSI'); axes[0].legend()
    axes[0].tick_params(axis='x', rotation=35)

    if ks_results:
        ks_feats  = [f for f in feats if f in ks_results]
        ks_stats  = [ks_results[f]['statistic'] for f in ks_feats]
        ks_colors = ['#E53935' if ks_results[f]['p_value'] < 0.05 else '#43A047' for f in ks_feats]
        axes[1].bar(ks_feats, ks_stats, color=ks_colors, alpha=0.85, edgecolor='white')
        axes[1].set_title('KS Test par Feature (rouge = p<0.05)')
        axes[1].set_ylabel('KS Statistic')
        axes[1].tick_params(axis='x', rotation=35)

    fig.tight_layout()
    fig.savefig(f"{CFG['fig_dir']}/phase5b_psi_drift.png", dpi=150, bbox_inches='tight')
    plt.close(fig)
    logger.info("  ✓ Figure PSI drift sauvegardée")


# ==============================================================================
# MONITORING AUTOMATIQUE PSI — APScheduler
# ==============================================================================
def start_psi_scheduler(features_all, feature_cols):
    if not APSCHEDULER_AVAILABLE:
        logger.warning("  ⚠ APScheduler non disponible — monitoring manuel uniquement")
        return None

    scheduler = BackgroundScheduler()

    def monthly_psi_check():
        logger.info("  [SCHEDULER] Calcul PSI automatique mensuel...")
        try:
            psi_max, psi_results, _ = compute_drift_report(features_all, feature_cols)
            if psi_max >= CFG["psi_threshold"]:
                logger.warning(f"  [SCHEDULER] ⚠ PSI max={psi_max:.4f} — ré-entraînement recommandé!")
        except Exception as e:
            logger.error(f"  [SCHEDULER] Erreur PSI mensuel : {e}")

    scheduler.add_job(
        monthly_psi_check,
        trigger='cron', day=1, hour=0, minute=0,
        id='monthly_psi_check',
        replace_existing=True
    )
    scheduler.start()
    logger.info("  ✓ Scheduler PSI mensuel démarré (1er du mois à 00:00)")
    return scheduler


# ==============================================================================
# PHASE 6 — DEPLOYMENT
# ==============================================================================
def phase6_deployment(features_eval, features_holdout, cluster_labels,
                      feature_cols, scaler, X_holdout_scaled,
                      sil_samples, sil_global, db_global, ch_global,
                      stab_mean, stab_std, per_cluster, km_final,
                      centroid_mapping, psi_max, fragile_profiles):
    _banner("PHASE 6 — DEPLOYMENT (GBM + Hold-out anti-leakage)")
    pnames = CFG["profile_names"]

    mask_sure = ~features_eval['is_mixte']
    X_cols    = [c for c in feature_cols if c in features_eval.columns]
    X_sure_sc = scaler.transform(features_eval.loc[mask_sure, X_cols].fillna(0))
    y_sure    = cluster_labels[mask_sure]

    X_train, X_test, y_train, y_test = train_test_split(
        X_sure_sc, y_sure, test_size=0.20,
        stratify=y_sure, random_state=CFG["random_state"]
    )
    logger.info(f"  GBM train={len(y_train):,} | test={len(y_test):,}")

    gbm = GradientBoostingClassifier(
        n_estimators=250, max_depth=5, learning_rate=0.08,
        random_state=CFG["random_state"], subsample=0.85, min_samples_leaf=10
    )
    gbm.fit(X_train, y_train)
    joblib.dump(gbm, _model_path('classifier'))

    y_pred_test  = gbm.predict(X_test)
    gbm_accuracy = float(accuracy_score(y_test, y_pred_test))
    gbm_test_f1  = float(f1_score(y_test, y_pred_test, average='weighted'))
    logger.info(f"  ✓ GBM Test accuracy : {gbm_accuracy:.4f}")
    logger.info(f"  ✓ GBM Test F1       : {gbm_test_f1:.4f}")
    logger.info("\n" + classification_report(y_test, y_pred_test,
                                             target_names=[pnames.get(c, f'P{c}') for c in sorted(set(y_sure))]))

    cv_scores = cross_val_score(gbm, X_sure_sc, y_sure, cv=5, scoring='f1_weighted')
    gbm_cv_f1 = float(cv_scores.mean())
    logger.info(f"  ✓ GBM CV F1 (5-fold) : {gbm_cv_f1:.4f} ± {cv_scores.std():.4f}")

    gbm_holdout_f1 = None
    if len(features_holdout) > 0:
        logger.info(f"  Hold-out : test généralisation ({len(features_holdout)} clients)...")
        y_holdout_pred = gbm.predict(X_holdout_scaled)
        probs_holdout  = gbm.predict_proba(X_holdout_scaled)

        logger.info(f"  ✓ Hold-out prédit — distribution profils :")
        ho_counts = pd.Series(y_holdout_pred).value_counts().sort_index()
        for c, cnt in ho_counts.items():
            logger.info(f"    P{c} {pnames.get(c,'?')}: {cnt} ({cnt/len(features_holdout)*100:.1f}%)")

        ho_conf = np.max(probs_holdout, axis=1).mean()
        logger.info(f"  ✓ Confiance moyenne hold-out : {ho_conf:.4f}")

        features_holdout = features_holdout.copy()
        features_holdout['cluster_id']     = y_holdout_pred
        features_holdout['gbm_confidence'] = np.max(probs_holdout, axis=1)
        features_holdout['profile_label']  = [pnames.get(c, f'P{c}') for c in y_holdout_pred]
        features_holdout['is_mixte']       = features_holdout['gbm_confidence'] < CFG["confidence_threshold"]
        gbm_holdout_f1 = round(float(ho_conf), 4)

    X_all_sc = scaler.transform(features_eval[X_cols].fillna(0))
    probs     = gbm.predict_proba(X_all_sc)
    y_pred    = gbm.predict(X_all_sc)
    features_eval = features_eval.copy()
    features_eval['gbm_confidence'] = np.array([
        float(probs[i, list(gbm.classes_).index(y_pred[i])])
        if y_pred[i] in gbm.classes_ else 0.5
        for i in range(len(y_pred))
    ])
    features_eval['is_mixte'] = features_eval['gbm_confidence'] < CFG["confidence_threshold"]

    n_mixte = int(features_eval['is_mixte'].sum())
    logger.info(f"  Zone mixte finale : {n_mixte:,} ({n_mixte/len(features_eval)*100:.1f}%)")

    importances = pd.Series(gbm.feature_importances_, index=X_cols).sort_values(ascending=False)
    logger.info("  Top 5 features :")
    for feat, imp in importances.head(5).items():
        logger.info(f"    → {feat:42s} : {imp:.4f}")

    _plot_phase6_importance(importances)

    if features_holdout is not None and len(features_holdout) > 0:
        features_all_classified = pd.concat([features_eval, features_holdout], ignore_index=True)
    else:
        features_all_classified = features_eval.copy()

    profile_stats = _compute_profile_stats(features_all_classified, per_cluster, fragile_profiles)

    _save_centroids_to_db(km_final, features_eval['cluster_id'].values, feature_cols)
    _save_model_run_to_db(sil_global, db_global, ch_global, stab_mean, stab_std,
                          n_mixte, psi_max,
                          gbm_accuracy, gbm_cv_f1, gbm_test_f1, gbm_holdout_f1,
                          features_eval, fragile_profiles, len(features_holdout))
    _save_profile_stats_to_db(profile_stats, fragile_profiles)

    # ── Note : L'API est gérée par main.py (port 8000) ──
    # API embarquée désactivée — utilisez main.py
    pass

    return features_eval, features_holdout, gbm_accuracy, gbm_cv_f1, gbm_holdout_f1


def _plot_phase6_importance(importances):
    top15 = importances.head(15)
    fig, ax = plt.subplots(figsize=(12, 7))
    ax.barh(top15.index[::-1], top15.values[::-1],
            color=[PALETTE[i % 6] for i in range(len(top15))][::-1], alpha=0.85)
    ax.axvline(top15.mean(), color='red', ls='--', label=f'Moy: {top15.mean():.4f}')
    ax.set_title('Phase 6 — Feature Importance GBM (train/test + hold-out)', fontsize=13, fontweight='bold')
    ax.legend()
    fig.tight_layout()
    fig.savefig(f"{CFG['fig_dir']}/phase6_importance.png", dpi=150, bbox_inches='tight')
    plt.close(fig)
    logger.info("  ✓ Figure importance GBM sauvegardée")


def _compute_profile_stats(features_all_classified, per_cluster, fragile_profiles):
    pnames   = CFG["profile_names"]; k = CFG["k_optimal"]
    stat_cols = [c for c in [
        'total_transactions', 'freq_mensuelle', 'montant_moyen', 'montant_total',
        'montant_median', 'montant_max', 'montant_std', 'nb_categories_distinctes',
        'regularite', 'rfm_score', 'loyalty_score', 'momentum_court', 'momentum_long',
        'recence_jours', 'ratio_factures', 'ratio_recharges', 'ratio_shopping',
        'ratio_restaurants', 'ratio_transferts', 'entropy_categories',
    ] if c in features_all_classified.columns]

    GLOBAL_RATIOS = {
        col: features_all_classified[col].mean() if col in features_all_classified.columns else 0.01
        for col in ['ratio_factures', 'ratio_recharges', 'ratio_shopping',
                    'ratio_restaurants', 'ratio_transferts', 'ratio_education', 'ratio_voyages']
    }

    total_clients = len(features_all_classified)
    stats = {}
    for c in range(k):
        mask = features_all_classified['cluster_id'] == c
        subset = features_all_classified[mask]
        n = len(subset)
        pct = n / total_clients * 100
        s = {col: {'mean': round(float(subset[col].mean()), 4),
                   'median': round(float(subset[col].median()), 4)}
             for col in stat_cols if col in subset.columns}
        cat_cols     = [col for col in GLOBAL_RATIOS if col in features_all_classified.columns]
        dominant_cat = dominant_rank2 = "N/A"
        dominant_ratio_abs = dominant_ratio_rel = None
        if cat_cols:
            cat_means    = {col: float(subset[col].mean()) for col in cat_cols}
            cat_relative = {col: cat_means[col] / max(GLOBAL_RATIOS[col], 0.001) for col in cat_cols}
            ranked_abs = sorted(cat_means.items(), key=lambda x: -x[1])
            dominant_key       = ranked_abs[0][0]
            dominant_cat       = CATEGORY_LABELS.get(dominant_key,
                                                     dominant_key.replace('ratio_', '').replace('_', ' ').title())
            dominant_ratio_abs = round(cat_means[dominant_key], 4)
            dominant_ratio_rel = round(cat_relative[dominant_key], 3)
            if len(ranked_abs) > 1:
                sec_key = ranked_abs[1][0]
                dominant_rank2 = CATEGORY_LABELS.get(sec_key,
                                                     sec_key.replace('ratio_', '').replace('_', ' ').title())
        stats[c] = {
            'cluster_id': c, 'profile_name': pnames.get(c, f'P{c}'),
            'description': _profile_description(c), 'n_clients': n, 'pct_clients': round(pct, 2),
            'sil_mean': per_cluster[c]['sil_mean'], 'sil_min': per_cluster[c]['sil_min'],
            'is_fragile': c in fragile_profiles,
            'dominant_category': dominant_cat, 'dominant_category_ratio': dominant_ratio_abs,
            'dominant_category_vs_global': dominant_ratio_rel, 'secondary_category': dominant_rank2,
            'activity_level': _activity_level(float(subset['freq_mensuelle'].mean())
                                              if 'freq_mensuelle' in subset.columns else 0),
            'statistics': s,
        }
    return stats


def _save_model_run_to_db(sil, db, ch, stab_mean, stab_std, n_mixte,
                          psi_max, gbm_accuracy, gbm_cv_f1, gbm_test_f1, gbm_holdout_f1,
                          features_eval, fragile_profiles, n_holdout):
    psi_status = ('stable' if psi_max < 0.1 else ('warning' if psi_max < 0.2 else 'drift'))
    churn_col  = 'churn_score_30j'
    pct_high   = float((features_eval[churn_col] > CFG["churn_high_risk_thresh"]).mean()) \
        if churn_col in features_eval.columns else 0.0
    pct_crit   = float((features_eval[churn_col] > CFG["churn_critical_thresh"]).mean()) \
        if churn_col in features_eval.columns else 0.0

    conn = get_connection()
    try:
        with conn.cursor() as cur:
            cur.execute("""
                        INSERT INTO model_runs_v9
                        (n_clients, n_holdout, n_profiles, k_used,
                         silhouette_score, davies_bouldin, calinski_harabasz,
                         bootstrap_stability, bootstrap_std,
                         n_mixte, pct_mixte,
                         psi_max, psi_status,
                         gbm_accuracy, gbm_cv_f1, gbm_test_f1, gbm_holdout_f1,
                         fragile_profiles,
                         churn_pct_high_risk, churn_pct_critical, retrain_trigger)
                        VALUES (%s,%s,%s,%s, %s,%s,%s, %s,%s, %s,%s, %s,%s,
                                %s,%s,%s,%s, %s, %s,%s,%s)
                        """, (len(features_eval), n_holdout, CFG["k_optimal"], CFG["k_optimal"],
                              round(float(sil), 4), round(float(db), 4), round(float(ch), 0),
                              round(float(stab_mean), 4), round(float(stab_std), 4),
                              n_mixte, round(n_mixte / len(features_eval) * 100, 2),
                              round(float(psi_max), 4), psi_status,
                              round(float(gbm_accuracy), 4), round(float(gbm_cv_f1), 4),
                              round(float(gbm_test_f1), 4),
                              round(float(gbm_holdout_f1), 4) if gbm_holdout_f1 else None,
                              json.dumps(fragile_profiles),
                              round(pct_high, 4), round(pct_crit, 4), 'manual'))
        conn.commit()
        logger.info("  ✓ Run modèle sauvegardé : model_runs_v9")
    except Exception as e:
        conn.rollback(); logger.error(f"  ✗ {e}")
    finally:
        release_connection(conn)


def _save_profile_stats_to_db(profile_stats, fragile_profiles):
    rows = []
    for c, stats in profile_stats.items():
        s = stats.get('statistics', {})
        rows.append((
            int(c), str(stats['profile_name']),
            bool(c in fragile_profiles),
            int(stats['n_clients']), float(stats['pct_clients']),
            float(stats['sil_mean']), float(stats['sil_min']),
            str(stats['dominant_category']),
            float(stats['dominant_category_ratio']) if stats['dominant_category_ratio'] is not None else None,
            float(stats['dominant_category_vs_global']) if stats['dominant_category_vs_global'] is not None else None,
            str(stats['secondary_category']), str(stats['activity_level']),
            float(s.get('freq_mensuelle', {}).get('mean', 0.0)) if s.get('freq_mensuelle') else None,
            float(s.get('montant_moyen', {}).get('mean', 0.0)) if s.get('montant_moyen') else None,
            float(s.get('montant_total', {}).get('mean', 0.0)) if s.get('montant_total') else None,
            float(s.get('regularite', {}).get('mean', 0.0)) if s.get('regularite') else None,
            float(s.get('rfm_score', {}).get('mean', 0.0)) if s.get('rfm_score') else None,
            float(s.get('loyalty_score', {}).get('mean', 0.0)) if s.get('loyalty_score') else None,
            float(s.get('momentum_court', {}).get('mean', 0.0)) if s.get('momentum_court') else None,
            float(s.get('momentum_long', {}).get('mean', 0.0)) if s.get('momentum_long') else None,
            float(s.get('recence_jours', {}).get('mean', 0.0)) if s.get('recence_jours') else None,
            str(stats['description']),
        ))
    conn = get_connection()
    try:
        with conn.cursor() as cur:
            execute_values(cur, """
                                INSERT INTO profile_stats
                                (cluster_id, profile_name, is_fragile,
                                 n_clients, pct_clients, sil_mean, sil_min,
                                 dominant_category, dominant_category_ratio,
                                 dominant_category_vs_global, secondary_category, activity_level,
                                 freq_mensuelle_mean, montant_moyen_mean, montant_total_mean,
                                 regularite_mean, rfm_score_mean, loyalty_score_mean,
                                 momentum_court_mean, momentum_long_mean, recence_jours_mean, description)
                                VALUES %s
                                    ON CONFLICT (cluster_id) DO UPDATE SET
                                    n_clients=EXCLUDED.n_clients, computed_at=NOW()
                                """, rows)
        conn.commit()
        logger.info("  ✓ Statistiques profils sauvegardées")
    except Exception as e:
        conn.rollback()
        logger.error(f"  ✗ Erreur insertion profile_stats : {e}")
    finally:
        release_connection(conn)


# ==============================================================================
# PHASE 7 — KPI MÉTIERS
# ==============================================================================
def compute_churn_score(subset):
    rec_max = CFG["churn_seuil_recence_max"]
    w_rec, w_reg, w_mom, w_rev = (
        CFG["churn_w_recence"], CFG["churn_w_regularite"],
        CFG["churn_w_momentum"], CFG["churn_w_reversal"]
    )

    s_recence = (subset['recence_jours'].clip(0, rec_max) / rec_max).fillna(0.5) \
        if 'recence_jours' in subset.columns else pd.Series(0.5, index=subset.index)
    s_regularite = (1 - subset['regularite'].clip(0, 1)).fillna(0.5) \
        if 'regularite' in subset.columns else pd.Series(0.5, index=subset.index)
    s_momentum = (1 - (subset['momentum_court'].clip(0, 2) / 2)).fillna(0.5) \
        if 'momentum_court' in subset.columns else pd.Series(0.5, index=subset.index)
    s_reversal = (subset['taux_reversal'].clip(0, 0.3) / 0.3).fillna(0.0) \
        if 'taux_reversal' in subset.columns else pd.Series(0.0, index=subset.index)

    raw_score = (
            w_rec * s_recence + w_reg * s_regularite +
            w_mom * s_momentum + w_rev * s_reversal
    ).clip(0, 1)

    scale  = CFG["churn_sigmoid_scale"]
    center = CFG["churn_sigmoid_center"]
    return pd.Series(
        expit(scale * (raw_score.values - center)),
        index=raw_score.index
    )


def _churn_segment(score: float) -> str:
    if score >= CFG["churn_critical_thresh"]:    return "CRITIQUE"
    elif score >= CFG["churn_high_risk_thresh"]: return "A_RISQUE"
    elif score >= 0.30:                          return "SURVEILLANCE"
    else:                                        return "SAIN"


def compute_ltv_economic(arpu_mensuel, churn_score, horizon_months, scenario='base'):
    r_annuel  = CFG["ltv_discount_rate_annual"]
    r_mensuel = (1 + r_annuel) ** (1 / 12) - 1
    if scenario == 'optimiste':    marge_nette = CFG["ltv_marge_max"]
    elif scenario == 'pessimiste': marge_nette = CFG["ltv_marge_min"]
    else:                          marge_nette = CFG["ltv_marge_base"]
    marge_mensuelle = arpu_mensuel * marge_nette
    p_retention     = max(0.01, 1.0 - float(churn_score))
    ltv = 0.0; p_survive = 1.0
    for t in range(1, horizon_months + 1):
        p_survive *= p_retention
        ltv       += marge_mensuelle * p_survive / ((1 + r_mensuel) ** t)
    return round(max(0.0, ltv), 2)


def compute_hazard_rate(churn_score: float) -> float:
    p = max(0.001, min(0.999, float(churn_score)))
    return round(-np.log(1 - p), 4)


def _save_client_profiles_to_db(features_eval, features_holdout=None):
    rows = []

    def _make_row(row, in_holdout=False):
        return (
            str(row['client_id']), int(row['cluster_id']),
            str(row.get('profile_label', '')), str(row.get('profile_final', '')),
            bool(row.get('is_mixte', False)), float(row.get('confidence_score', 0)),
            float(row.get('gbm_confidence', 0)),
            int(row.get('total_transactions', 0)), float(row.get('freq_mensuelle', 0)),
            float(row.get('montant_moyen', 0)), float(row.get('montant_median', 0)),
            float(row.get('montant_total', 0)), float(row.get('regularite', 0)),
            float(row.get('rfm_score', 0)), float(row.get('loyalty_score', 0)),
            float(row.get('momentum_court', 0)), float(row.get('momentum_long', 0)),
            int(row.get('recence_jours', 0)),
            float(row.get('churn_score_30j', 0)), float(row.get('churn_score_90j', 0)),
            str(row.get('churn_segment', 'SAIN')),
            float(row.get('ltv_6m', 0)), float(row.get('ltv_12m', 0)),
            float(row.get('ltv_12m_optimiste', 0)), float(row.get('ltv_12m_pessimiste', 0)),
            float(row.get('hazard_rate', 0)),
            bool(in_holdout),
        )

    for _, row in features_eval.iterrows():
        rows.append(_make_row(row, in_holdout=False))
    if features_holdout is not None and len(features_holdout) > 0:
        for _, row in features_holdout.iterrows():
            rows.append(_make_row(row, in_holdout=True))

    conn = get_connection()
    try:
        with conn.cursor() as cur:
            execute_values(cur, """
                                INSERT INTO client_profiles_v9 (
                                    client_id, cluster_id, profile_name, profile_final,
                                    is_mixte, confidence_score, gbm_confidence,
                                    total_transactions, freq_mensuelle, montant_moyen, montant_median,
                                    montant_total, regularite, rfm_score, loyalty_score,
                                    momentum_court, momentum_long, recence_jours,
                                    churn_score_30j, churn_score_90j, churn_segment,
                                    ltv_6m, ltv_12m, ltv_12m_optimiste, ltv_12m_pessimiste,
                                    hazard_rate, in_holdout
                                ) VALUES %s
                                    ON CONFLICT (client_id) DO UPDATE SET
                                    cluster_id=EXCLUDED.cluster_id,
                                                                   profile_name=EXCLUDED.profile_name,
                                                                   is_mixte=EXCLUDED.is_mixte,
                                                                   confidence_score=EXCLUDED.confidence_score,
                                                                   gbm_confidence=EXCLUDED.gbm_confidence,
                                                                   churn_score_30j=EXCLUDED.churn_score_30j,
                                                                   churn_segment=EXCLUDED.churn_segment,
                                                                   ltv_12m=EXCLUDED.ltv_12m,
                                                                   ltv_12m_optimiste=EXCLUDED.ltv_12m_optimiste,
                                                                   ltv_12m_pessimiste=EXCLUDED.ltv_12m_pessimiste,
                                                                   hazard_rate=EXCLUDED.hazard_rate,
                                                                   updated_at=NOW()
                                """, rows, page_size=1000)
        conn.commit()
        logger.info(f"  ✓ {len(rows):,} profils upsertés dans client_profiles_v9")
    except Exception as e:
        conn.rollback(); logger.error(f"  ✗ {e}")
    finally:
        release_connection(conn)


def phase7_kpi_business(features_eval, features_holdout=None):
    _banner("PHASE 7 — KPI MÉTIERS (Churn calibré + LTV 2.0% + 3 scénarios)")
    pnames   = CFG["profile_names"]
    k        = CFG["k_optimal"]
    kpi_rows = []

    logger.info(f"  LTV : BCT {CFG['ltv_discount_rate_annual']*100:.0f}% | "
                f"marge pess={CFG['ltv_marge_min']*100:.1f}% | "
                f"base={CFG['ltv_marge_base']*100:.1f}% | "
                f"opt={CFG['ltv_marge_max']*100:.1f}%")

    all_churn_30j = compute_churn_score(features_eval)
    features_eval = features_eval.copy()
    features_eval['churn_score_30j'] = all_churn_30j.values
    features_eval['churn_score_90j'] = (all_churn_30j * 1.15).clip(0, 1).values
    features_eval['hazard_rate']     = all_churn_30j.apply(compute_hazard_rate).values
    features_eval['churn_segment']   = all_churn_30j.apply(_churn_segment)

    if features_holdout is not None and len(features_holdout) > 0:
        ho_churn = compute_churn_score(features_holdout)
        features_holdout = features_holdout.copy()
        features_holdout['churn_score_30j'] = ho_churn.values
        features_holdout['churn_score_90j'] = (ho_churn * 1.15).clip(0, 1).values
        features_holdout['hazard_rate']     = ho_churn.apply(compute_hazard_rate).values
        features_holdout['churn_segment']   = ho_churn.apply(_churn_segment)

    churn_dist = features_eval['churn_segment'].value_counts()
    total_churn = len(features_eval)
    logger.info("  Distribution churn :")
    for seg, cnt in churn_dist.items():
        logger.info(f"    {seg:<15} : {cnt:>5} ({cnt/total_churn*100:.1f}%)")
    pct_high_risk = float((features_eval['churn_score_30j'] > CFG["churn_high_risk_thresh"]).mean())
    logger.info(f"  ✓ Clients à risque (>0.5) : {pct_high_risk*100:.1f}% (objectif 5–20%)")

    for c in range(k):
        mask   = features_eval['cluster_id'] == c
        subset = features_eval[mask]
        if len(subset) == 0:
            continue

        churn_score_30j   = float(subset['churn_score_30j'].mean())
        churn_score_90j   = float(subset['churn_score_90j'].mean())
        churn_pct_high    = float((subset['churn_score_30j'] > CFG["churn_high_risk_thresh"]).mean())
        churn_pct_critical = float((subset['churn_score_30j'] > CFG["churn_critical_thresh"]).mean())
        taux_retention    = 1.0 - churn_score_30j
        hazard            = float(subset['hazard_rate'].mean())
        freq_mean         = float(subset['freq_mensuelle'].mean()) if 'freq_mensuelle' in subset.columns else 0
        amt_mean          = float(subset['montant_median'].mean()) if 'montant_median' in subset.columns else \
            float(subset['montant_moyen'].mean()) if 'montant_moyen' in subset.columns else 0
        arpu_mensuel      = freq_mean * amt_mean

        ltv_6m_base   = compute_ltv_economic(arpu_mensuel, churn_score_30j, 6,  'base')
        ltv_12m_base  = compute_ltv_economic(arpu_mensuel, churn_score_30j, 12, 'base')
        ltv_12m_opt   = compute_ltv_economic(arpu_mensuel, churn_score_30j, 12, 'optimiste')
        ltv_12m_pess  = compute_ltv_economic(arpu_mensuel, churn_score_30j, 12, 'pessimiste')

        marge_base    = round(arpu_mensuel * CFG["ltv_marge_base"], 2)
        marge_opt     = round(arpu_mensuel * CFG["ltv_marge_max"], 2)
        marge_pess    = round(arpu_mensuel * CFG["ltv_marge_min"], 2)

        taux_activation = float(1 - churn_pct_high)
        nb_cat_actives  = float(subset['nb_categories_distinctes'].mean()) \
            if 'nb_categories_distinctes' in subset.columns else 0
        taux_cross_sell = float((subset['nb_categories_distinctes'] >= 3).mean()) \
            if 'nb_categories_distinctes' in subset.columns else 0
        taux_reversal   = float(subset['taux_reversal'].mean()) \
            if 'taux_reversal' in subset.columns else 0
        rfm_mean        = float(subset['rfm_score'].mean()) \
            if 'rfm_score' in subset.columns else 0.5
        score_risque    = float(taux_reversal * 0.30 + churn_score_30j * 0.40 + (1 - rfm_mean) * 0.30)
        growth_3m       = float(subset['momentum_court'].mean() - 1) \
            if 'momentum_court' in subset.columns else 0

        kpi_rows.append((
            c, pnames.get(c, f'P{c}'),
            round(churn_score_30j, 4),    round(churn_score_90j, 4),
            round(churn_pct_high, 4),     round(churn_pct_critical, 4),
            round(taux_retention, 4),     round(hazard, 4),
            round(ltv_6m_base, 2),        round(ltv_12m_base, 2),
            round(ltv_12m_opt, 2),        round(ltv_12m_pess, 2),
            round(arpu_mensuel, 2),       round(marge_base, 2),
            round(marge_opt, 2),          round(marge_pess, 2),
            round(taux_activation, 4),    round(nb_cat_actives, 2),
            round(taux_cross_sell, 4),    round(taux_reversal, 4),
            round(score_risque, 4),       round(growth_3m, 4),
            round(float(subset['momentum_long'].mean() - 1)
                  if 'momentum_long' in subset.columns else 0, 4),
        ))

        logger.info(f"  P{c} {pnames.get(c,'?')[:22]:<24} | "
                    f"Churn30j={churn_score_30j:.3f} ({churn_pct_high:.0%} >0.5) | "
                    f"ARPU={arpu_mensuel:.1f} TND | LTV12m={ltv_12m_base:.1f} TND")

    def _client_ltv(row, scenario):
        arpu = (row.get('freq_mensuelle', 0) *
                row.get('montant_median', row.get('montant_moyen', 0)))
        return compute_ltv_economic(arpu, row.get('churn_score_30j', 0.25), 12, scenario)

    features_eval['ltv_6m']             = features_eval.apply(lambda r: _client_ltv(r, 'base'), axis=1)
    features_eval['ltv_12m']            = features_eval.apply(lambda r: _client_ltv(r, 'base'), axis=1)
    features_eval['ltv_12m_optimiste']  = features_eval.apply(lambda r: _client_ltv(r, 'optimiste'), axis=1)
    features_eval['ltv_12m_pessimiste'] = features_eval.apply(lambda r: _client_ltv(r, 'pessimiste'), axis=1)

    if features_holdout is not None and len(features_holdout) > 0:
        features_holdout['ltv_6m']             = features_holdout.apply(lambda r: _client_ltv(r, 'base'), axis=1)
        features_holdout['ltv_12m']            = features_holdout.apply(lambda r: _client_ltv(r, 'base'), axis=1)
        features_holdout['ltv_12m_optimiste']  = features_holdout.apply(lambda r: _client_ltv(r, 'optimiste'), axis=1)
        features_holdout['ltv_12m_pessimiste'] = features_holdout.apply(lambda r: _client_ltv(r, 'pessimiste'), axis=1)

    _save_kpi_to_db(kpi_rows)
    _save_client_profiles_to_db(features_eval, features_holdout)
    _plot_phase7_kpi(kpi_rows, pnames)

    return kpi_rows, features_eval, features_holdout


def _save_kpi_to_db(kpi_rows):
    if not kpi_rows:
        return
    conn = get_connection()
    try:
        with conn.cursor() as cur:
            execute_values(cur, """
                                INSERT INTO kpi_business (
                                    cluster_id, profile_name,
                                    churn_score_30j, churn_score_90j,
                                    churn_pct_high_risk, churn_pct_critical,
                                    taux_retention, hazard_rate,
                                    ltv_6m_base, ltv_12m_base, ltv_12m_optimiste, ltv_12m_pessimiste,
                                    arpu_mensuel, marge_base, marge_optimiste, marge_pessimiste,
                                    taux_activation, nb_categories_actives, taux_cross_sell,
                                    taux_reversal, score_risque, growth_rate_3m, growth_rate_6m
                                ) VALUES %s
                                    ON CONFLICT (cluster_id) DO UPDATE SET
                                    churn_score_30j=EXCLUDED.churn_score_30j,
                                                                    churn_pct_high_risk=EXCLUDED.churn_pct_high_risk,
                                                                    ltv_12m_base=EXCLUDED.ltv_12m_base, computed_at=NOW()
                                """, kpi_rows)
        conn.commit()
        logger.info("  ✓ KPI sauvegardés dans kpi_business")
    except Exception as e:
        conn.rollback(); logger.error(f"  ✗ {e}")
    finally:
        release_connection(conn)


def _plot_phase7_kpi(kpi_rows, pnames):
    if not kpi_rows:
        return
    k              = len(kpi_rows)
    profile_labels = [f'P{r[0]}: {r[1][:14]}' for r in kpi_rows]
    colors         = [PALETTE[r[0] % 6] for r in kpi_rows]

    fig, axes = plt.subplots(2, 3, figsize=(20, 12))
    fig.suptitle('Phase 7 — KPI : Churn Calibré + LTV 2.0% + 3 Scénarios',
                 fontsize=14, fontweight='bold')

    vals = [r[2] * 100 for r in kpi_rows]
    churn_colors = ['#E53935' if v > 50 else ('#FFA726' if v > 30 else '#43A047') for v in vals]
    axes[0, 0].bar(profile_labels, vals, color=churn_colors, alpha=0.85, edgecolor='white')
    axes[0, 0].axhline(50, color='red', ls='--', label='Seuil risque (50%)')
    axes[0, 0].set_title('Score Churn 30j (%)'); axes[0, 0].set_ylabel('%')
    axes[0, 0].legend(fontsize=8); axes[0, 0].tick_params(axis='x', rotation=30, labelsize=8)
    for i, v in enumerate(vals):
        axes[0, 0].text(i, v + 0.3, f'{v:.1f}%', ha='center', fontsize=8)

    vals_hr = [r[4] * 100 for r in kpi_rows]
    hr_colors = ['#E53935' if v > 20 else ('#FFA726' if v > 10 else '#43A047') for v in vals_hr]
    axes[0, 1].bar(profile_labels, vals_hr, color=hr_colors, alpha=0.85, edgecolor='white')
    axes[0, 1].axhline(20, color='red', ls='--', label='Seuil élevé (20%)')
    axes[0, 1].set_title('% Clients churn >0.5 par Profil')
    axes[0, 1].legend(fontsize=8); axes[0, 1].tick_params(axis='x', rotation=30, labelsize=8)

    x = np.arange(k); w = 0.25
    axes[0, 2].bar(x - w, [r[9] for r in kpi_rows], w, color='#E53935', alpha=0.8, label='Pess. 1%')
    axes[0, 2].bar(x,     [r[8] for r in kpi_rows], w, color=PALETTE[0], alpha=0.8, label='Base 2%')
    axes[0, 2].bar(x + w, [r[10] for r in kpi_rows], w, color='#43A047', alpha=0.8, label='Opt. 4%')
    axes[0, 2].set_title('LTV VAN 12 mois — 3 Scénarios (TND)')
    axes[0, 2].set_xticks(x); axes[0, 2].set_xticklabels([f'P{r[0]}' for r in kpi_rows])
    axes[0, 2].legend(fontsize=8)

    vals = [r[12] for r in kpi_rows]
    axes[1, 0].bar(profile_labels, vals, color=colors, alpha=0.85, edgecolor='white')
    axes[1, 0].set_title('ARPU Mensuel (TND)')
    axes[1, 0].tick_params(axis='x', rotation=30, labelsize=8)

    vals = [r[7] * 100 for r in kpi_rows]
    h_colors = ['#E53935' if v > 30 else ('#FFA726' if v > 15 else '#43A047') for v in vals]
    axes[1, 1].bar(profile_labels, vals, color=h_colors, alpha=0.85, edgecolor='white')
    axes[1, 1].set_title('Taux de Hazard Mensuel')
    axes[1, 1].tick_params(axis='x', rotation=30, labelsize=8)

    vals = [r[21] for r in kpi_rows]
    risk_colors = ['#E53935' if v > 0.5 else ('#FFA726' if v > 0.25 else '#43A047') for v in vals]
    axes[1, 2].bar(profile_labels, vals, color=risk_colors, alpha=0.85, edgecolor='white')
    axes[1, 2].set_title('Score de Risque Composite')
    axes[1, 2].tick_params(axis='x', rotation=30, labelsize=8)

    fig.tight_layout()
    fig.savefig(f"{CFG['fig_dir']}/phase7_kpi.png", dpi=150, bbox_inches='tight')
    plt.close(fig)
    logger.info("  ✓ Figure KPI sauvegardée")


# ==============================================================================
# VALIDATION CHURN
# ==============================================================================
def validate_churn_calibration(features_eval):
    _banner("VALIDATION CALIBRATION CHURN")
    if 'churn_score_30j' not in features_eval.columns:
        logger.warning("  ⚠ Pas de score churn disponible")
        return

    scores = features_eval['churn_score_30j']
    pct_high    = (scores > 0.50).mean() * 100
    pct_crit    = (scores > 0.70).mean() * 100
    pct_survey  = ((scores > 0.30) & (scores <= 0.50)).mean() * 100
    pct_sain    = (scores <= 0.30).mean() * 100

    logger.info(f"  Distribution scores churn :")
    logger.info(f"    SAIN            (≤0.30) : {pct_sain:.1f}%")
    logger.info(f"    SURVEILLANCE (0.30–0.50) : {pct_survey:.1f}%")
    logger.info(f"    A_RISQUE     (0.50–0.70) : {pct_high - pct_crit:.1f}%")
    logger.info(f"    CRITIQUE        (>0.70)  : {pct_crit:.1f}%")
    logger.info(f"    Total > 0.50 : {pct_high:.1f}% (objectif : 5–20%)")

    if 5 <= pct_high <= 25:
        logger.info("  ✓ Calibration correcte — discrimination utile")
    elif pct_high < 5:
        logger.warning("  ⚠ Churn trop bas — augmenter center ou réduire scale")
    else:
        logger.warning("  ⚠ Churn trop élevé — réduire center ou augmenter scale")

    pnames = CFG["profile_names"]; k = CFG["k_optimal"]
    fig, axes = plt.subplots(1, 2, figsize=(16, 5))
    fig.suptitle('Validation Calibration Churn', fontsize=14, fontweight='bold')

    data_by_cluster = [features_eval[features_eval['cluster_id'] == c]['churn_score_30j'].values
                       for c in range(k)]
    bp = axes[0].boxplot(data_by_cluster, patch_artist=True,
                         medianprops=dict(color='red', lw=2))
    for i, patch in enumerate(bp['boxes']):
        patch.set_facecolor(PALETTE[i]); patch.set_alpha(0.7)
    axes[0].axhline(0.5, color='red', ls='--', lw=1.5, label='Seuil risque (0.5)')
    axes[0].set_xticks(range(1, k + 1))
    axes[0].set_xticklabels([f'P{c}\n{pnames.get(c,"?")[:10]}' for c in range(k)], fontsize=8)
    axes[0].set_title('Distribution Churn Score par Profil'); axes[0].legend(fontsize=8)

    axes[1].hist(scores, bins=50, color=PALETTE[0], edgecolor='white', alpha=0.85)
    axes[1].axvline(0.5, color='red', ls='--', lw=2, label=f'Seuil 0.5 ({pct_high:.1f}% >)')
    axes[1].axvline(scores.mean(), color='blue', ls=':', lw=1.5,
                    label=f'Moy: {scores.mean():.3f}')
    axes[1].set_title('Distribution Globale Score Churn'); axes[1].legend(fontsize=8)

    fig.tight_layout()
    fig.savefig(f"{CFG['fig_dir']}/validation_churn.png", dpi=150, bbox_inches='tight')
    plt.close(fig)
    logger.info("  ✓ Figure validation churn sauvegardée")


# ==============================================================================
# GÉNÉRATION API FASTAPI — CORRECTION PYDANTIC V2
# ==============================================================================
def _save_fastapi(feature_cols, profile_stats):
    """
    API FastAPI générée — DÉSACTIVÉE.
    L'API principale est main.py sur le port 8000.
    """
    logger.info("  ℹ API interne désactivée — utilisez main.py sur port 8000")
    return


# ==============================================================================
# POINT D'ENTRÉE PRINCIPAL
# ==============================================================================
def main(auto_retrain=False):
    start = time.time()
    logger.info("\n" + "█" * 72)
    logger.info("  WALLET CLASSIFICATION — CRISP-DM (FinTech Tunisie)")
    logger.info("  CHURN CALIBRÉ | HOLD-OUT | FASTAPI | MONITORING AUTO")
    logger.info("█" * 72)

    init_tables()
    phase1_business_understanding()

    df, clients, tx_types, providers, date_ref, date_start, n_months = \
        phase2_data_understanding()

    (features_train, features_holdout, X_scaled, X_holdout_scaled,
     feature_cols, scaler, features_all) = phase3_data_preparation(
        df, clients, date_ref, date_start, n_months
    )

    (km_final, cluster_labels, X_reduced, X_2d,
     sil_samples, stab_mean, stab_std,
     silhouettes, inertias, db_scores, umap_model,
     centroid_mapping, fragile_profiles) = \
        phase4_modeling(features_train, X_scaled, feature_cols)

    per_cluster, sil_global, db_global, ch_global, features_eval = \
        phase5_evaluation(features_train, X_scaled, X_reduced, cluster_labels,
                          km_final, feature_cols, sil_samples, stab_mean, stab_std,
                          fragile_profiles)

    psi_max, psi_results, ks_results = compute_drift_report(features_train, feature_cols)
    save_distributions_to_db(features_train, feature_cols)

    (features_eval, features_holdout,
     gbm_accuracy, gbm_cv_f1, gbm_holdout_f1) = phase6_deployment(
        features_eval, features_holdout, cluster_labels,
        feature_cols, scaler, X_holdout_scaled,
        sil_samples, sil_global, db_global, ch_global,
        stab_mean, stab_std, per_cluster,
        km_final, centroid_mapping, psi_max, fragile_profiles
    )

    kpi_rows, features_eval, features_holdout = phase7_kpi_business(
        features_eval, features_holdout
    )

    validate_churn_calibration(features_eval)

    scheduler = start_psi_scheduler(features_all, feature_cols)

    elapsed   = int(time.time() - start)
    n_mixte   = int(features_eval['is_mixte'].sum())
    pct_high  = (features_eval['churn_score_30j'] > 0.5).mean() * 100 \
        if 'churn_score_30j' in features_eval.columns else 0

    logger.info(f"\n  ✅ Pipeline terminé en {elapsed}s")
    logger.info(f"  ✅ {len(features_eval):,} clients clustering + {len(features_holdout):,} hold-out")
    logger.info(f"  ✅ Silhouette         : {sil_global:.4f}")
    logger.info(f"  ✅ PSI max (RÉEL)     : {psi_max:.4f}")
    logger.info(f"  ✅ GBM Test F1        : {gbm_accuracy:.4f} | CV F1 : {gbm_cv_f1:.4f}")
    logger.info(f"  ✅ Hold-out confiance : {gbm_holdout_f1 or 'N/A'}")
    logger.info(f"  ✅ Zone MIXTE         : {n_mixte:,} ({n_mixte/len(features_eval)*100:.1f}%)")
    logger.info(f"  ✅ À risque >0.5      : {pct_high:.1f}% clients (objectif 5–20%)")
    logger.info(f"  ✅ Profils fragiles   : {fragile_profiles if fragile_profiles else 'Aucun'}")
    logger.info(f"  ✅ Figures dans       : {CFG['fig_dir']}/")
    logger.info("  ✅ API (port 8000)    : utilisez main.py")
    logger.info("     → uvicorn main:app --host 0.0.0.0 --port 8000 --reload")
    logger.info("     → Swagger UI : http://localhost:8000/docs")
    logger.info("█" * 72 + "\n")

    if scheduler:
        try:
            import signal as _signal
            def _shutdown(sig, frame):
                logger.info("  Arrêt du scheduler...")
                scheduler.shutdown(wait=False)
                sys.exit(0)
            _signal.signal(_signal.SIGINT, _shutdown)
            _signal.signal(_signal.SIGTERM, _shutdown)
        except Exception:
            pass

    return features_eval


# ==============================================================================
# CLI
# ==============================================================================
if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Wallet Classification — CRISP-DM FinTech Tunisie"
    )
    parser.add_argument('--retrain',  action='store_true', help='Forcer le ré-entraînement')
    # --api supprimé — utilisez main.py pour l'API sur port 8000
    parser.add_argument('--kpi',      action='store_true', help='KPI métiers uniquement')
    parser.add_argument('--drift',    action='store_true', help='Rapport drift PSI')
    parser.add_argument('--validate', action='store_true', help='Validation calibration churn')
    args = parser.parse_args()

    if args.kpi:
        init_tables()
        conn = get_connection()
        features_eval = pd.read_sql(
            "SELECT * FROM client_profiles_v9 WHERE in_holdout = FALSE", conn
        )
        release_connection(conn)
        phase7_kpi_business(features_eval)

    elif args.drift:
        init_tables()
        conn = get_connection()
        features = pd.read_sql("SELECT * FROM client_profiles_v9", conn)
        release_connection(conn)
        compute_drift_report(
            features,
            [c for c in features.columns if features[c].dtype in [np.float64, np.int64]]
        )

    elif args.validate:
        init_tables()
        conn = get_connection()
        features_eval = pd.read_sql(
            "SELECT * FROM client_profiles_v9 WHERE in_holdout = FALSE", conn
        )
        release_connection(conn)
        validate_churn_calibration(features_eval)

    else:
        result = main(auto_retrain=args.retrain)