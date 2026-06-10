"""
API FastAPI — Wallet Classification FinTech (PRODUCTION-READY)
==============================================================
Classification comportementale clients wallet mobile — FinTech Tunisie.

Endpoints :
  GET  /health                  — Santé + drift PSI
  GET  /profiles/summary        — Résumé profils
  GET  /profiles/{id}           — Détail profil + KPI
  POST /predict                 — Classifier un client
  POST /batch                   — Batch clients
  POST /admin/retrain           — Ré-entraîner
  GET  /admin/retrain/status    — Statut retrain
  POST /admin/retrain/kill      — Arrêter retrain forcé
  GET  /admin/retrain/log       — Log retrain
  GET  /kpi/summary             — KPI métiers
  GET  /drift/status            — Statut drift
  GET  /clients/churn-at-risk   — Clients à risque
  GET  /clients/{client_id}     — Profil client
  GET  /monitoring/alerts       — Alertes PSI actives

Démarrage :
  pip install fastapi uvicorn[standard] psycopg2-binary joblib scipy numpy
  uvicorn outputs.api:app --host 0.0.0.0 --port 5000 --reload
"""
from __future__ import annotations

import json
import logging
import os
import signal
import subprocess
import threading
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Any, Dict, List, Optional

import joblib
import numpy as np
import psycopg2
import psycopg2.extras
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from psycopg2 import pool as pg_pool

# ── CORRECTION : Pydantic V2 — ConfigDict ────────────────────
from pydantic import BaseModel, ConfigDict, Field
from scipy.special import expit

load_dotenv()
logger = logging.getLogger("uvicorn.error")

# ─────────────────────────────────────────────────────────────
# Configuration
# ─────────────────────────────────────────────────────────────
DB_CONFIG: Dict[str, Any] = {
    "host":     os.environ.get("DB_HOST",   "localhost"),
    "port":     int(os.environ.get("DB_PORT", "5432")),
    "dbname":   os.environ.get("DB_NAME",   "client_bd"),
    "user":     os.environ.get("DB_USER",   "postgres"),
    "password": os.environ["DB_PASSWORD"],
}

MODEL_DIR    = "outputs/models"
THRESHOLD    = 0.6
FEATURE_COLS: List[str] = ["total_transactions", "total_valid_txn", "nb_active_months", "anciennete_jours", "maturite_jours", "freq_mensuelle", "taux_reversal", "regularite", "montant_total", "montant_moyen", "montant_median", "montant_max", "montant_std", "cv_montants", "nb_categories_distinctes", "nb_providers_distincts", "entropy_categories", "nb_factures", "nb_recharges", "nb_shopping", "nb_restaurants", "nb_transferts_envoyes", "nb_transferts_recus", "nb_depot_retrait_raw", "nb_voyages", "nb_education", "ratio_factures", "ratio_recharges", "ratio_shopping", "ratio_restaurants", "ratio_transferts", "ratio_voyages", "ratio_education", "log_depot_retrait", "recence_jours", "momentum_court", "momentum_long", "momentum_montant", "ratio_jour", "score_saisonnalite", "stabilite_mensuelle", "rfm_score", "loyalty_score"]

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

PROFILE_NAMES: Dict[int, str] = {
    0:  "Micro-Utilisateur Passif",
    1:  "Utilisateur Essentiel Stable",
    2:  "Payeur Factures Premium",
    3:  "Client Grande Dépense",
    4:  "Client en Accélération Récente",
    5:  "Client en Croissance Digitale",
    -1: "Profil Mixte (Incertain)",
}

PROFILES_STATS: Dict[str, Any] = {
  "0": {
    "cluster_id": 0,
    "profile_name": "Micro-Utilisateur Passif",
    "description": "Usage très limité, transactions rares et irrégulières. Montants faibles. Profil à risque de churn élevé ou client dormant.",
    "n_clients": 2100,
    "pct_clients": 39.51,
    "sil_mean": 0.2192,
    "sil_min": -0.0766,
    "is_fragile": true,
    "dominant_category": "Factures & Services",
    "dominant_category_ratio": 0.2974,
    "dominant_category_vs_global": 1.061,
    "secondary_category": "Recharge Téléphonique",
    "activity_level": "Faible"
  },
  "1": {
    "cluster_id": 1,
    "profile_name": "Utilisateur Essentiel Stable",
    "description": "Usage quotidien simple et stable. Paiements essentiels réguliers. Client fiable avec comportement prévisible.",
    "n_clients": 1434,
    "pct_clients": 26.98,
    "sil_mean": 0.2798,
    "sil_min": 0.0147,
    "is_fragile": true,
    "dominant_category": "Factures & Services",
    "dominant_category_ratio": 0.2362,
    "dominant_category_vs_global": 0.843,
    "secondary_category": "Recharge Téléphonique",
    "activity_level": "Modérée"
  },
  "2": {
    "cluster_id": 2,
    "profile_name": "Payeur Factures Premium",
    "description": "Spécialisé dans le paiement de factures utilities et services. Fort potentiel de fidélisation et de domiciliation.",
    "n_clients": 615,
    "pct_clients": 11.57,
    "sil_mean": 0.4099,
    "sil_min": -0.0334,
    "is_fragile": false,
    "dominant_category": "Factures & Services",
    "dominant_category_ratio": 0.3459,
    "dominant_category_vs_global": 1.235,
    "secondary_category": "Recharge Téléphonique",
    "activity_level": "Modérée"
  },
  "3": {
    "cluster_id": 3,
    "profile_name": "Client Grande Dépense",
    "description": "Transactions moins fréquentes mais montants élevés. Client premium à fort potentiel de revenus.",
    "n_clients": 598,
    "pct_clients": 11.25,
    "sil_mean": 0.2225,
    "sil_min": -0.047,
    "is_fragile": true,
    "dominant_category": "Factures & Services",
    "dominant_category_ratio": 0.3104,
    "dominant_category_vs_global": 1.108,
    "secondary_category": "Shopping & Paiements",
    "activity_level": "Modérée"
  },
  "4": {
    "cluster_id": 4,
    "profile_name": "Client en Accélération Récente",
    "description": "Momentum récent très élevé (>2.5×). En forte accélération sur les 3 derniers mois. Fort potentiel d'adoption avancée.",
    "n_clients": 375,
    "pct_clients": 7.06,
    "sil_mean": 0.2979,
    "sil_min": -0.0859,
    "is_fragile": true,
    "dominant_category": "Factures & Services",
    "dominant_category_ratio": 0.2242,
    "dominant_category_vs_global": 0.8,
    "secondary_category": "Recharge Téléphonique",
    "activity_level": "Faible"
  },
  "5": {
    "cluster_id": 5,
    "profile_name": "Client en Croissance Digitale",
    "description": "Activité en forte croissance. Explore activement de nouvelles catégories. Phase d'adoption avancée.",
    "n_clients": 193,
    "pct_clients": 3.63,
    "sil_mean": 0.2057,
    "sil_min": -0.0762,
    "is_fragile": true,
    "dominant_category": "Factures & Services",
    "dominant_category_ratio": 0.2256,
    "dominant_category_vs_global": 0.805,
    "secondary_category": "Recharge Téléphonique",
    "activity_level": "Élevée"
  }
}

# ─────────────────────────────────────────────────────────────
# Pool PostgreSQL
# ─────────────────────────────────────────────────────────────
_pool: Optional[pg_pool.ThreadedConnectionPool] = None
_pool_lock = threading.Lock()


def get_db_pool() -> pg_pool.ThreadedConnectionPool:
    global _pool
    if _pool is None:
        with _pool_lock:
            if _pool is None:
                _pool = pg_pool.ThreadedConnectionPool(2, 10, **DB_CONFIG)
    return _pool


def get_conn():
    return get_db_pool().getconn()


def release_conn(conn):
    try:
        get_db_pool().putconn(conn)
    except Exception:
        try:
            conn.close()
        except Exception:
            pass


# ─────────────────────────────────────────────────────────────
# Chargement des modèles ML
# ─────────────────────────────────────────────────────────────
clf    = None
scaler = None


def _reload_models():
    global clf, scaler
    try:
        clf    = joblib.load(f"{MODEL_DIR}/classifier.pkl")
        scaler = joblib.load(f"{MODEL_DIR}/scaler.pkl")
        logger.info("[OK] Modèles chargés.")
    except Exception as e:
        logger.warning(f"[WARN] Modèles non chargés : {e}")
        clf = scaler = None


# ─────────────────────────────────────────────────────────────
# Lifespan
# ─────────────────────────────────────────────────────────────
@asynccontextmanager
async def lifespan(app: FastAPI):
    _reload_models()
    logger.info("[STARTUP] API démarrée — modèles chargés.")
    yield
    if _pool:
        _pool.closeall()
    logger.info("[SHUTDOWN] Pool PostgreSQL fermé.")


# ─────────────────────────────────────────────────────────────
# Application FastAPI
# ─────────────────────────────────────────────────────────────
app = FastAPI(
    title="Wallet Classification API",
    description="API de classification comportementale clients wallet mobile — FinTech Tunisie.",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ─────────────────────────────────────────────────────────────
# Fonctions churn + LTV
# ─────────────────────────────────────────────────────────────
def _compute_churn(feats: Dict[str, float]) -> float:
    s_rec = min(feats.get("recence_jours", 45) / CHURN_REC_MAX, 1.0)
    s_reg = 1.0 - min(feats.get("regularite", 0.5), 1.0)
    s_mom = 1.0 - min(feats.get("momentum_court", 1.0), 2.0) / 2.0
    s_rev = min(feats.get("taux_reversal", 0.0), 0.3) / 0.3
    raw   = (CHURN_W_REC * s_rec + CHURN_W_REG * s_reg +
             CHURN_W_MOM * s_mom + CHURN_W_REV * s_rev)
    return round(float(expit(CHURN_SCALE * (raw - CHURN_CENTER))), 4)


def _churn_segment(score: float) -> str:
    if score >= CHURN_CRIT_THRESH:  return "CRITIQUE"
    if score >= CHURN_HIGH_THRESH:  return "A_RISQUE"
    if score >= 0.30:               return "SURVEILLANCE"
    return "SAIN"


def _compute_ltv(arpu: float, churn: float,
                 horizon: int = 12, scenario: str = "base") -> float:
    r_m   = (1 + LTV_RATE) ** (1 / 12) - 1
    marge = (LTV_MARGE_MAX  if scenario == "optimiste"  else
             LTV_MARGE_MIN  if scenario == "pessimiste" else
             LTV_MARGE_BASE)
    m     = arpu * marge
    p_ret = max(0.01, 1.0 - churn)
    ltv   = 0.0; p = 1.0
    for t in range(1, horizon + 1):
        p   *= p_ret
        ltv += m * p / ((1 + r_m) ** t)
    return round(max(0.0, ltv), 2)


def _vec(features_dict: Dict[str, float]) -> np.ndarray:
    return np.array(
        [float(features_dict.get(c, 0)) for c in FEATURE_COLS]
    ).reshape(1, -1)


# ─────────────────────────────────────────────────────────────
# Schémas Pydantic — V2 : ConfigDict (plus de class Config)
# ─────────────────────────────────────────────────────────────
class PredictRequest(BaseModel):
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "client_id": "abc-123",
                "features": {
                    "freq_mensuelle":  4.5,
                    "montant_moyen":   120.0,
                    "regularite":      0.75,
                    "recence_jours":   12,
                    "momentum_court":  1.2,
                    "taux_reversal":   0.02,
                },
            }
        }
    )
    client_id: str              = Field(default="?", description="Identifiant client")
    features:  Dict[str, float] = Field(default={}, description="Features comportementales du client")


class BatchRequest(BaseModel):
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "clients": [
                    {
                        "client_id": "abc-123",
                        "features": {"freq_mensuelle": 4.5, "montant_moyen": 120.0},
                    }
                ]
            }
        }
    )
    clients: List[PredictRequest] = Field(default=[], description="Liste de clients à classifier")


class AdminRetrainRequest(BaseModel):
    admin_user: str = Field(default="unknown", description="Identifiant de l'admin")


# ─────────────────────────────────────────────────────────────
# Retrain asynchrone
# ─────────────────────────────────────────────────────────────
retrain_status: Dict[str, Any] = {
    "running":      False,
    "last_run":     None,
    "last_result":  None,
    "triggered_by": None,
}


def _run_retraining(triggered_by: str = "admin") -> None:
    retrain_status["running"]      = True
    retrain_status["last_run"]     = datetime.now().isoformat()
    retrain_status["triggered_by"] = triggered_by
    retrain_status["last_result"]  = "running..."
    script_dir = os.path.dirname(os.path.abspath(__file__))
    log_path   = os.path.join(script_dir, "retrain_output.log")
    try:
        with open(log_path, "w", encoding="utf-8") as log_file:
            proc = subprocess.Popen(
                ["python", os.path.join(script_dir, "..", "wallet_classification.py"), "--retrain"],
                stdout=log_file, stderr=log_file, cwd=script_dir,
            )
        retrain_status["pid"] = proc.pid
        proc.wait(timeout=7200)
        if proc.returncode == 0:
            retrain_status["last_result"] = "success"
            _reload_models()
        else:
            try:
                with open(log_path, "r", encoding="utf-8", errors="replace") as lf:
                    tail = lf.read()[-1000:]
            except Exception:
                tail = "(log illisible)"
            retrain_status["last_result"] = f"error (rc={proc.returncode}): {tail}"
    except subprocess.TimeoutExpired:
        proc.kill()
        retrain_status["last_result"] = "timeout (>7200s) — process tué"
    except Exception as exc:
        retrain_status["last_result"] = f"exception: {exc}"
    finally:
        retrain_status["running"] = False
        retrain_status.pop("pid", None)


# ─────────────────────────────────────────────────────────────
# Endpoints — Monitoring
# ─────────────────────────────────────────────────────────────
@app.get("/health", tags=["Monitoring"], summary="Santé de l'API + drift PSI")
def health():
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT psi_max, psi_status, churn_pct_high_risk, n_clients, run_at
                FROM model_runs ORDER BY run_at DESC LIMIT 1
            """)
            row = cur.fetchone()
        drift_info = (
            {"psi_max": row[0], "status": row[1], "churn_pct_high_risk": row[2],
              "n_clients": row[3], "last_run": str(row[4])}
            if row else {}
        )
    except Exception:
        drift_info = {}
    finally:
        release_conn(conn)
    return {
        "status":    "ok",
        "model":     "Wallet Classification — FinTech Tunisie",
        "loaded":    clf is not None,
        "drift":     drift_info,
        "timestamp": datetime.now().isoformat(),
    }


@app.get("/monitoring/alerts", tags=["Monitoring"], summary="Alertes PSI actives non résolues")
def monitoring_alerts():
    conn = get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                SELECT * FROM monitoring_alerts
                WHERE resolved = FALSE
                ORDER BY triggered_at DESC LIMIT 50
            """)
            rows = cur.fetchall()
        return [dict(r) for r in rows]
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))
    finally:
        release_conn(conn)


@app.get("/drift/status", tags=["Monitoring"], summary="Statut du drift PSI (5 derniers runs)")
def drift_status():
    conn = get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                SELECT psi_max, psi_status, gbm_accuracy, gbm_test_f1,
                       gbm_holdout_f1, churn_pct_high_risk, fragile_profiles, run_at
                FROM model_runs ORDER BY run_at DESC LIMIT 5
            """)
            rows = cur.fetchall()
        return [dict(r) for r in rows]
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))
    finally:
        release_conn(conn)


# ─────────────────────────────────────────────────────────────
# Endpoints — Profils
# ─────────────────────────────────────────────────────────────
@app.get("/profiles/summary", tags=["Profils"], summary="Résumé de tous les profils")
def profiles_summary():
    conn = get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                SELECT ps.*, k.churn_score_30j, k.churn_pct_high_risk,
                       k.arpu_mensuel, k.ltv_12m_base,
                       k.ltv_12m_optimiste, k.ltv_12m_pessimiste,
                       k.taux_activation, k.score_risque,
                       k.growth_rate_3m, k.hazard_rate, ps.is_fragile
                FROM profile_stats ps
                LEFT JOIN kpi_business k ON k.cluster_id = ps.cluster_id
                ORDER BY ps.cluster_id
            """)
            rows = cur.fetchall()
        return [dict(r) for r in rows]
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))
    finally:
        release_conn(conn)


@app.get("/profiles/{profile_id}", tags=["Profils"], summary="Détail d'un profil")
def profile_detail(profile_id: int):
    conn = get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                SELECT ps.*, k.*
                FROM profile_stats ps
                LEFT JOIN kpi_business k ON k.cluster_id = ps.cluster_id
                WHERE ps.cluster_id = %s
            """, (profile_id,))
            row = cur.fetchone()
        if not row:
            raise HTTPException(status_code=404, detail=f"Profil {profile_id} inconnu")
        return dict(row)
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))
    finally:
        release_conn(conn)


# ─────────────────────────────────────────────────────────────
# Endpoints — Classification
# ─────────────────────────────────────────────────────────────
@app.post("/predict", tags=["Classification"], summary="Classifier un client")
def predict(body: PredictRequest, request: Request):
    if clf is None or scaler is None:
        raise HTTPException(status_code=503, detail="Modèle non chargé.")

    X     = _vec(body.features)
    Xs    = scaler.transform(X)
    c     = int(clf.predict(Xs)[0])
    probs = clf.predict_proba(Xs)[0]
    conf  = float(probs[list(clf.classes_).index(c)])

    churn   = _compute_churn(body.features)
    segment = _churn_segment(churn)
    freq    = float(body.features.get("freq_mensuelle", 0))
    amt     = float(body.features.get("montant_median",
              body.features.get("montant_moyen", 0)))
    arpu    = freq * amt

    return {
        "client_id":          body.client_id,
        "cluster_id":         c,
        "profile_name":       PROFILE_NAMES.get(c, "?"),
        "profile_final":      "MIXTE" if conf < THRESHOLD else PROFILE_NAMES.get(c, "?"),
        "confidence":         round(conf, 4),
        "is_mixte":           conf < THRESHOLD,
        "churn_score_30j":    churn,
        "churn_segment":      segment,
        "ltv_12m_base":       _compute_ltv(arpu, churn, 12, "base"),
        "ltv_12m_optimiste":  _compute_ltv(arpu, churn, 12, "optimiste"),
        "ltv_12m_pessimiste": _compute_ltv(arpu, churn, 12, "pessimiste"),
        "hazard_rate":        round(-np.log(max(0.001, 1 - churn)), 4),
        "arpu_mensuel":       round(arpu, 2),
        "all_probabilities": {
            PROFILE_NAMES.get(int(i), f"P{i}"): round(float(p), 4)
            for i, p in zip(clf.classes_, probs)
        },
        "predicted_at": datetime.now().isoformat(),
    }


@app.post("/batch", tags=["Classification"], summary="Classifier un lot de clients")
def batch_predict(body: BatchRequest):
    if clf is None or scaler is None:
        raise HTTPException(status_code=503, detail="Modèle non chargé.")

    results = []
    for client in body.clients:
        X     = _vec(client.features)
        Xs    = scaler.transform(X)
        c     = int(clf.predict(Xs)[0])
        prob  = float(clf.predict_proba(Xs)[0][list(clf.classes_).index(c)])
        churn = _compute_churn(client.features)
        freq  = float(client.features.get("freq_mensuelle", 0))
        amt   = float(client.features.get("montant_median",
                client.features.get("montant_moyen", 0)))
        results.append({
            "client_id":         client.client_id,
            "cluster_id":        c,
            "profile_name":      PROFILE_NAMES.get(c, "?"),
            "confidence":        round(prob, 4),
            "is_mixte":          prob < THRESHOLD,
            "churn_score_30j":   churn,
            "churn_segment":     _churn_segment(churn),
            "ltv_12m_base":      _compute_ltv(freq * amt, churn, 12, "base"),
            "ltv_12m_optimiste": _compute_ltv(freq * amt, churn, 12, "optimiste"),
        })

    n_risque = sum(1 for r in results if r["churn_score_30j"] > 0.50)
    return {
        "results":     results,
        "count":       len(results),
        "n_mixte":     sum(1 for r in results if r["is_mixte"]),
        "n_at_risk":   n_risque,
        "pct_at_risk": round(n_risque / max(len(results), 1) * 100, 2),
        "avg_churn":   round(
            sum(r["churn_score_30j"] for r in results) / max(len(results), 1), 4
        ),
    }


# ─────────────────────────────────────────────────────────────
# Endpoints — Admin
# ─────────────────────────────────────────────────────────────
@app.post("/admin/retrain", tags=["Admin"], summary="Lancer un ré-entraînement")
def admin_retrain(body: AdminRetrainRequest):
    if retrain_status["running"]:
        raise HTTPException(
            status_code=409,
            detail={"status": "already_running", "started_at": retrain_status["last_run"]},
        )
    t = threading.Thread(
        target=_run_retraining,
        kwargs={"triggered_by": body.admin_user},
        daemon=True,
    )
    t.start()
    return JSONResponse(
        status_code=202,
        content={"status": "accepted", "triggered_by": body.admin_user},
    )


@app.get("/admin/retrain/status", tags=["Admin"], summary="Statut du ré-entraînement")
def admin_retrain_status():
    return retrain_status


@app.post("/admin/retrain/kill", tags=["Admin"], summary="Forcer l'arrêt du ré-entraînement")
def admin_retrain_kill():
    pid = retrain_status.get("pid")
    if pid:
        try:
            os.kill(pid, signal.SIGTERM)
            retrain_status["last_result"] = f"killed (pid={pid})"
        except Exception as exc:
            retrain_status["last_result"] = f"kill failed: {exc}"
    else:
        retrain_status["last_result"] = "reset manuel (pas de pid connu)"
    retrain_status["running"] = False
    retrain_status.pop("pid", None)
    return {"status": "killed_or_reset", "detail": retrain_status["last_result"]}


@app.get("/admin/retrain/log", tags=["Admin"], summary="Log du dernier ré-entraînement")
def admin_retrain_log(lines: int = Query(default=100, ge=1, le=5000)):
    script_dir = os.path.dirname(os.path.abspath(__file__))
    log_path   = os.path.join(script_dir, "retrain_output.log")
    try:
        with open(log_path, "r", encoding="utf-8", errors="replace") as f:
            all_lines = f.readlines()
        tail = all_lines[-lines:]
        return {"log": "".join(tail), "total_lines": len(all_lines), "path": log_path}
    except FileNotFoundError:
        raise HTTPException(status_code=404, detail="Pas encore de log.")
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


# ─────────────────────────────────────────────────────────────
# Endpoints — KPI
# ─────────────────────────────────────────────────────────────
@app.get("/kpi/summary", tags=["KPI"], summary="KPI métiers par profil")
def kpi_summary():
    conn = get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("SELECT * FROM kpi_business ORDER BY cluster_id")
            rows = cur.fetchall()
        return [dict(r) for r in rows]
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))
    finally:
        release_conn(conn)


# ─────────────────────────────────────────────────────────────
# Endpoints — Clients
# ─────────────────────────────────────────────────────────────
@app.get("/clients/churn-at-risk", tags=["Clients"],
         summary="Clients à risque de churn (score > 0.5)")
def churn_at_risk(limit: int = Query(default=100, ge=1, le=500)):
    conn = get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                SELECT client_id, cluster_id, profile_name, churn_score_30j,
                       churn_segment, recence_jours, freq_mensuelle, ltv_12m
                FROM client_profiles
                WHERE churn_score_30j > 0.50
                ORDER BY churn_score_30j DESC
                LIMIT %s
            """, (limit,))
            rows = cur.fetchall()
        return {
            "count":   len(rows),
            "note":    "Clients avec churn_score_30j > 0.50 — à traiter en priorité",
            "clients": [dict(r) for r in rows],
        }
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))
    finally:
        release_conn(conn)


@app.get("/clients/{client_id}", tags=["Clients"], summary="Profil complet d'un client")
def client_profile(client_id: str):
    conn = get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                SELECT cp.*,
                       k.churn_pct_high_risk AS kpi_churn_pct,
                       k.arpu_mensuel, k.ltv_12m_base,
                       k.ltv_12m_optimiste, k.ltv_12m_pessimiste,
                       k.score_risque, k.hazard_rate AS kpi_hazard
                FROM client_profiles cp
                LEFT JOIN kpi_business k ON k.cluster_id = cp.cluster_id
                WHERE cp.client_id = %s::uuid
            """, (client_id,))
            row = cur.fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Client non trouvé")
        return dict(row)
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))
    finally:
        release_conn(conn)


# ─────────────────────────────────────────────────────────────
# Point d'entrée — CORRECTION : uvicorn dans __main__
# ─────────────────────────────────────────────────────────────
if __name__ == "__main__":
    try:
        import uvicorn
        print("=" * 60)
        print("  Wallet Classification API — FinTech Tunisie")
        print("  Démarrage sur http://0.0.0.0:5000")
        print("  Swagger UI : http://localhost:5000/docs")
        print("=" * 60)
        uvicorn.run(
            "api:app",
            host="0.0.0.0",
            port=5000,
            reload=False,
            log_level="info",
        )
    except ImportError:
        print("\u274c uvicorn non installé — pip install uvicorn[standard]")
        print("   Démarrage manuel : uvicorn outputs.api:app --host 0.0.0.0 --port 5000")
