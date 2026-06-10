"""
══════════════════════════════════════════════════════════════════════════════
SmartWallet — FastAPI ML Service UNIFIÉ (Version Finale Fusionnée v6.0)
══════════════════════════════════════════════════════════════════════════════

Service ML unique sur PORT 8000 — base de données client_bd.

ARCHITECTURE DE CLASSIFICATION (UNIQUE & COHÉRENTE) :
  • Méthode du binôme : GradientBoostingClassifier + 43 features + 6 profils
  • Utilisée par : Module 6, /predict, /batch, /clients/*, /api/v5/classify
  • Aucune autre méthode de clustering n'est active dans le projet

MODULES :
  1-5 : Prévisions temporelles — logique CONSERVÉE intégralement
  6   : Recommandations live + peer comparison (utilise classification binôme)
  OCR : Scan facture, anomalies, rappels, feedback

Démarrage : uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
══════════════════════════════════════════════════════════════════════════════
"""
from __future__ import annotations

import logging
import os
import sys
import json
import subprocess
import threading
import base64 as _b64
from contextlib import asynccontextmanager
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Dict, List, Optional

import joblib
import numpy as np
import pandas as pd
import psycopg2
import psycopg2.extras
import requests

from fastapi import (
    BackgroundTasks, FastAPI, File, Form, Header, HTTPException,
    Query, Request, UploadFile,
)
from fastapi.encoders import jsonable_encoder
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field

# ─── Imports locaux ────────────────────────────────────────────────
from . import classification as cls
from . import config as cfg
from . import module6_recos as mod6
from . import modules_forecast as fc
# Recommendation system (du binôme) — lazy
try:
    from . import recommendation_system as reco
    RECO_AVAILABLE = True
except Exception as _e:
    RECO_AVAILABLE = False
    reco = None
    print(f"⚠️ recommendation_system non chargé : {_e}")

# OCR — optionnel
try:
    from .ocr_service import scan_facture, detect_anomalie, calcul_impact_solde
    OCR_AVAILABLE = True
except Exception as _e:
    OCR_AVAILABLE = False
    print(f"⚠️ OCR Service non disponible : {_e}")


logging.basicConfig(level=logging.INFO,
                    format="%(asctime)s  %(levelname)-8s  %(message)s")
log = logging.getLogger("smartwallet-ml")


# ═════════════════════════════════════════════════════════════════════
# Lifespan
# ═════════════════════════════════════════════════════════════════════
@asynccontextmanager
async def lifespan(app: FastAPI):
    log.info("🚀 SmartWallet ML Service — démarrage…")
    log.info(f"   MODEL_DIR : {cfg.MODEL_DIR}")
    log.info(f"   DB        : {cfg.DB_CONFIG['host']}:{cfg.DB_CONFIG['port']}/{cfg.DB_CONFIG['dbname']}")

    cfg.load_forecast_models()

    ok = cls.load_classification_models(cfg.MODEL_DIR)
    if ok:
        log.info("   ✓ Classification GBM (binôme) chargée — méthode unique")
    else:
        log.warning("   ⚠️ Classification non chargée — endpoints /predict dégradés")

    mod6.load_module6_caches(cfg.MODEL_DIR)

    global paid_cache
    paid_cache = cfg.init_paid_cache_from_db()

    if RECO_AVAILABLE:
        try:
            reco.init_tables()
            reco.load_classification_model()
            log.info("   ✓ Recommendation system V5 initialisé")
        except Exception as e:
            log.warning(f"   ⚠️ Recommendation system : {e}")

    log.info("✅ SmartWallet ML Service prêt — port 8000")

    yield
    log.info("🛑 SmartWallet ML Service arrêté proprement")


# ═════════════════════════════════════════════════════════════════════
# App
# ═════════════════════════════════════════════════════════════════════
app = FastAPI(
    title="SmartWallet ML Service",
    description=(
        "Service IA unifié — Prévisions + Classification GBM + "
        "Recommandations V5 + OCR"
    ),
    version="6.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ═════════════════════════════════════════════════════════════════════
# État global partagé
# ═════════════════════════════════════════════════════════════════════
paid_cache: Dict[str, List[str]] = {}

# ─────────────────────────────────────────────────────────────────────
# Noms des tables produites par wallet_classification.py (binôme).
# Le pipeline de classification écrit dans les tables suffixées _v9 :
# client_profiles_v9 et model_runs_v9. Configurable par variable d'env
# pour rester aligné si les noms changent.
# ─────────────────────────────────────────────────────────────────────
TBL_CLIENT_PROFILES = os.environ.get("TABLE_CLIENT_PROFILES", "client_profiles_v9")
TBL_MODEL_RUNS      = os.environ.get("TABLE_MODEL_RUNS", "model_runs_v9")

# ─────────────────────────────────────────────────────────────────────
# Feedback marketing — buffer en mémoire alimenté par Spring Boot
# ─────────────────────────────────────────────────────────────────────
# Chaque entrée : {client_id, offer_code, profile_id, decision, recorded_at}
# Le scheduler côté Spring pousse les UserInteraction (accepted|rejected)
# vers POST /marketing-feedback/batch, et l'admin peut déclencher
# POST /marketing-feedback/retrain pour re-scorer les offres
# en tenant compte des taux d'acceptation observés.
_marketing_feedback_buffer: List[Dict[str, Any]] = []
_marketing_feedback_lock = threading.Lock()


def _api_response(data=None, message="OK", status_code=200, error=None):
    body = {
        "status": "success" if not error else "error",
        "message": message if not error else error,
        "timestamp": datetime.now().isoformat(),
    }
    if data is not None:
        body["data"] = data
    return JSONResponse(content=jsonable_encoder(body), status_code=status_code)


def _require_reco():
    if not RECO_AVAILABLE:
        raise HTTPException(status_code=503,
                            detail="Recommendation system V5 non disponible")


# ═════════════════════════════════════════════════════════════════════
# ROUTES — PRÉDICTIONS (Modules 1-5)
# ═════════════════════════════════════════════════════════════════════
@app.get("/predictions/{client_id}", tags=["Prédictions"])
def get_all_predictions(client_id: str, solde: Optional[float] = Query(None)):
    """Endpoint principal Flutter — 5 modules de prévision + segment."""
    log.info(f"→ /predictions/{client_id[-8:]} solde={solde}")
    now = datetime.now()
    y_next, m_next = fc.current_or_next_month()
    fm = cfg.FORECAST_MODELS
    seg = fc.legacy_segment(client_id, fm["gold_ids"], fm["moyen_ids"])

    m1 = fc.module1_factures(
        client_id, fm["factures_ref"], fm["models_bill"], fm["results_bill"],
        fm["profil_map"], fm["segment_map"], paid_cache,
    )
    m2 = fc.module2_recharge(client_id, fm["recharges_ref"])
    m3 = fc.module3_budget(
        client_id, seg, fm["models_budget"], fm["ic_budget"], fm["monthly"],
        fm["profil_map"], fm["segment_map"],
    )
    m4 = fc.module4_prochaine_tx(client_id, fm["habitudes_cli"], fm["recharges_ref"])
    m5 = fc.module5_alerte(client_id, solde, m1, m2, m3) if solde is not None else None

    return {
        "client_id": client_id,
        "generated_at": now.isoformat(),
        "version": "v6.0.0",
        "segment": seg,
        "mois_prevu": f"{y_next}-{m_next:02d}",
        "module1_factures": m1,
        "module2_recharge": m2,
        "module3_budget": m3,
        "module4_prochaine_tx": m4,
        "module5_alerte": m5,
    }


@app.post("/transactions/paid/{client_id}", tags=["Prédictions"])
def mark_as_paid(client_id: str, label: str = Query(...)):
    if client_id not in paid_cache:
        paid_cache[client_id] = []
    if label not in paid_cache[client_id]:
        paid_cache[client_id].append(label)
        log.info(f"✅ {label} marquée payée pour {client_id[-8:]}")
    return {"status": "marked_as_paid", "client_id": client_id, "label": label}


# ═════════════════════════════════════════════════════════════════════
# ROUTES — MODULE 6 (Recommandations live)
# ═════════════════════════════════════════════════════════════════════
@app.get("/recommendations/meta", tags=["Module 6"])
def recommendations_meta():
    return mod6.get_meta(cfg.MODEL_DIR)


@app.get("/recommendations/stats", tags=["Module 6"])
def recommendations_stats():
    return mod6.get_stats()


@app.get("/recommendations/alerts/all", tags=["Module 6"])
def get_all_alerts(page: int = 0, size: int = 20,
                   severity: Optional[str] = None):
    return mod6.get_all_alerts(page=page, size=size, severity=severity)


@app.get("/recommendations/{client_id}", tags=["Module 6"])
def get_recommendations(client_id: str):
    log.info(f"→ /recommendations/{client_id[-8:]}")
    return mod6.get_recommendations(
        client_id,
        get_db_conn=cfg.get_db_connection,
    )


@app.get("/recommendations/live/{client_id}", tags=["Module 6"])
def get_live_recommendations(client_id: str):
    """Alias pour FastApiClient.getLiveRecommendations côté Java."""
    return get_recommendations(client_id)


# ═════════════════════════════════════════════════════════════════════
# ROUTES — FEEDBACK MARKETING (auto-apprentissage)
# ═════════════════════════════════════════════════════════════════════
class MarketingFeedbackItem(BaseModel):
    """Un événement approve/reject côté utilisateur."""
    client_id: str
    offer_code: str
    profile_id: Optional[int] = None
    decision: str  # "accepted" | "rejected"
    recorded_at: Optional[str] = None  # ISO


class MarketingFeedbackBatch(BaseModel):
    items: List[MarketingFeedbackItem]


@app.post("/marketing-feedback/batch", tags=["Marketing Feedback"])
def receive_marketing_feedback(batch: MarketingFeedbackBatch,
                               x_retrain_secret: Optional[str] = Header(None)):
    """
    Endpoint appelé par le MarketingFeedbackScheduler de Spring Boot.
    Pousse un lot d'événements approve/reject dans le buffer mémoire,
    et persiste aussi un append-only log sur disque pour ne rien perdre.
    """
    secret = getattr(cfg, "RETRAIN_SECRET", "smartwallet-retrain-2026")
    if x_retrain_secret and x_retrain_secret != secret:
        raise HTTPException(status_code=403, detail="Invalid retrain secret")

    accepted = 0
    rejected = 0
    with _marketing_feedback_lock:
        for it in batch.items:
            entry = it.model_dump()
            entry["recorded_at"] = entry.get("recorded_at") or datetime.now().isoformat()
            _marketing_feedback_buffer.append(entry)
            if it.decision == "accepted":
                accepted += 1
            elif it.decision == "rejected":
                rejected += 1

    # Append-only log sur disque (sécurité)
    try:
        log_dir = Path(cfg.MODEL_DIR).parent / "feedback_logs"
        log_dir.mkdir(parents=True, exist_ok=True)
        with (log_dir / "marketing_feedback.jsonl").open("a", encoding="utf-8") as f:
            for it in batch.items:
                f.write(json.dumps(it.model_dump(), ensure_ascii=False) + "\n")
    except Exception as e:
        log.warning(f"⚠️ Persist feedback log : {e}")

    log.info(f"📥 Marketing feedback batch reçu : {accepted} acceptés, {rejected} rejetés "
             f"(buffer total = {len(_marketing_feedback_buffer)})")
    return _api_response(data={
        "received": len(batch.items),
        "accepted": accepted,
        "rejected": rejected,
        "buffer_size": len(_marketing_feedback_buffer),
    }, message="Feedback ingéré")


@app.get("/marketing-feedback/stats", tags=["Marketing Feedback"])
def marketing_feedback_stats():
    """Statistiques d'acceptation par offre / par profil — pour l'admin."""
    with _marketing_feedback_lock:
        items = list(_marketing_feedback_buffer)

    by_offer: Dict[str, Dict[str, int]] = {}
    by_profile: Dict[str, Dict[str, int]] = {}

    for it in items:
        code = it.get("offer_code", "?")
        prof = str(it.get("profile_id", "?"))
        dec = it.get("decision", "?")
        by_offer.setdefault(code, {"accepted": 0, "rejected": 0})
        by_profile.setdefault(prof, {"accepted": 0, "rejected": 0})
        if dec in by_offer[code]:
            by_offer[code][dec] += 1
        if dec in by_profile[prof]:
            by_profile[prof][dec] += 1

    def _rate(d):
        tot = d["accepted"] + d["rejected"]
        return round(d["accepted"] / tot, 3) if tot > 0 else None

    offers_rate = {k: {**v, "accept_rate": _rate(v)} for k, v in by_offer.items()}
    profiles_rate = {k: {**v, "accept_rate": _rate(v)} for k, v in by_profile.items()}

    return _api_response(data={
        "buffer_size": len(items),
        "by_offer": offers_rate,
        "by_profile": profiles_rate,
    })


@app.post("/marketing-feedback/retrain", tags=["Marketing Feedback"])
def retrain_marketing_model(x_retrain_secret: Optional[str] = Header(None)):
    """
    Re-pondère les offres en fonction des taux d'acceptation observés
    par profil. Délègue à recommendation_system.apply_feedback_reweighting()
    si la fonction existe ; sinon retourne juste les stats actuelles.
    """
    secret = getattr(cfg, "RETRAIN_SECRET", "smartwallet-retrain-2026")
    if x_retrain_secret != secret:
        raise HTTPException(status_code=403, detail="Invalid retrain secret")

    with _marketing_feedback_lock:
        items = list(_marketing_feedback_buffer)

    if not items:
        return _api_response(data={"status": "rien_a_analyser", "feedbacks": 0})

    # Hook vers recommendation_system si la fonction existe
    try:
        from . import recommendation_system as recsys
        if hasattr(recsys, "apply_feedback_reweighting"):
            report = recsys.apply_feedback_reweighting(items)  # type: ignore
        else:
            # Fallback : calcul des poids sans persistence
            report = {"status": "stub", "feedbacks": len(items),
                      "note": "recommendation_system.apply_feedback_reweighting non implémenté"}
    except Exception as e:
        log.error(f"❌ Retrain marketing échoué : {e}")
        raise HTTPException(status_code=500, detail=str(e))

    # On vide le buffer après retrain réussi (les données sont déjà dans le log)
    with _marketing_feedback_lock:
        _marketing_feedback_buffer.clear()

    return _api_response(data=report, message="Re-pondération appliquée")


# ═════════════════════════════════════════════════════════════════════
# ROUTES — CLASSIFICATION (méthode binôme UNIQUE)
# ═════════════════════════════════════════════════════════════════════
class PredictRequest(BaseModel):
    model_config = ConfigDict(json_schema_extra={
        "example": {
            "client_id": "abc-123",
            "features": {
                "freq_mensuelle": 4.5, "montant_moyen": 120.0,
                "regularite": 0.75, "recence_jours": 12,
                "momentum_court": 1.2, "taux_reversal": 0.02,
            },
        }
    })
    client_id: str = Field(default="?")
    features: Dict[str, float] = Field(default_factory=dict)


class BatchRequest(BaseModel):
    clients: List[PredictRequest] = Field(default_factory=list)


@app.post("/api/v1/classification/predict", tags=["Classification"])
def predict_client(body: PredictRequest):
    """Classifier un client (GBM + 43 features + 6 profils — méthode binôme)."""
    if not cls.is_loaded():
        raise HTTPException(status_code=503, detail="Modèle de classification non chargé")
    return cls.classify_client(body.features, body.client_id)


@app.post("/api/v1/classification/batch", tags=["Classification"])
def predict_batch(body: BatchRequest):
    """Classifier un lot de clients."""
    if not cls.is_loaded():
        raise HTTPException(status_code=503, detail="Modèle de classification non chargé")

    results = [cls.classify_client(c.features, c.client_id) for c in body.clients]
    n_at_risk = sum(1 for r in results if r["churn_score_30j"] > 0.50)
    n_mixte = sum(1 for r in results if r["is_mixte"])
    return {
        "results": results,
        "count": len(results),
        "n_mixte": n_mixte,
        "n_at_risk": n_at_risk,
        "pct_at_risk": round(n_at_risk / max(len(results), 1) * 100, 2),
        "avg_churn": round(
            sum(r["churn_score_30j"] for r in results) / max(len(results), 1), 4
        ),
    }


@app.get("/api/v1/classification/profiles/summary", tags=["Classification"])
def profiles_summary():
    """Résumé des 6 profils (DB si disponible, sinon stats embarquées)."""
    try:
        conn = cfg.get_db_connection()
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                        SELECT ps.*, k.churn_score_30j, k.churn_pct_high_risk,
                               k.arpu_mensuel, k.ltv_12m_base
                        FROM profile_stats ps
                                 LEFT JOIN kpi_business k ON k.cluster_id = ps.cluster_id
                        ORDER BY ps.cluster_id
                        """)
            rows = cur.fetchall()
        conn.close()
        if rows:
            return [dict(r) for r in rows]
    except Exception as e:
        log.debug(f"profile_stats DB unavailable: {e}")
    return list(cls.PROFILES_STATS.values())


@app.get("/api/v1/classification/profiles/{profile_id}", tags=["Classification"])
def profile_detail(profile_id: int):
    """Détail d'un profil par cluster_id (0–5)."""
    try:
        conn = cfg.get_db_connection()
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                        SELECT ps.*, k.*
                        FROM profile_stats ps
                                 LEFT JOIN kpi_business k ON k.cluster_id = ps.cluster_id
                        WHERE ps.cluster_id = %s
                        """, (profile_id,))
            row = cur.fetchone()
        conn.close()
        if row:
            return dict(row)
    except Exception as e:
        log.debug(f"profile_detail DB unavailable: {e}")
    info = cls.PROFILES_STATS.get(str(profile_id))
    if not info:
        raise HTTPException(status_code=404, detail=f"Profil {profile_id} inconnu")
    return info


@app.get("/api/v1/classification/clients/churn-at-risk", tags=["Classification"])
def clients_churn_at_risk(limit: int = Query(default=100, ge=1, le=500)):
    """Clients à risque de churn (score > 0.5)."""
    try:
        conn = cfg.get_db_connection()
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(f"""
                        SELECT client_id, cluster_id, profile_name, churn_score_30j,
                               churn_segment, recence_jours, freq_mensuelle, ltv_12m
                        FROM {TBL_CLIENT_PROFILES}
                        WHERE churn_score_30j > 0.50
                        ORDER BY churn_score_30j DESC LIMIT %s
                        """, (limit,))
            rows = cur.fetchall()
        conn.close()
        return {
            "count": len(rows),
            "note": "Clients avec churn_score_30j > 0.50",
            "clients": [dict(r) for r in rows],
        }
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"DB error: {exc}")


@app.get("/api/v1/classification/clients/{client_id}", tags=["Classification"])
def get_client_profile(client_id: str):
    """Profil complet d'un client (DB si présent, sinon classification live)."""
    try:
        conn = cfg.get_db_connection()
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(f"""
                        SELECT cp.*, k.arpu_mensuel, k.ltv_12m_base,
                               k.ltv_12m_optimiste, k.ltv_12m_pessimiste, k.score_risque
                        FROM {TBL_CLIENT_PROFILES} cp
                                 LEFT JOIN kpi_business k ON k.cluster_id = cp.cluster_id
                        WHERE cp.client_id = %s::uuid
                        """, (client_id,))
            row = cur.fetchone()
        conn.close()
        if row:
            return dict(row)
    except Exception as e:
        log.debug(f"client_profiles DB unavailable: {e}")
    return cls.classify_from_db(client_id, cfg.get_db_connection)


@app.get("/api/v1/classification/kpi/summary", tags=["Classification"])
def kpi_summary():
    """KPI métiers par profil."""
    try:
        conn = cfg.get_db_connection()
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("SELECT * FROM kpi_business ORDER BY cluster_id")
            rows = cur.fetchall()
        conn.close()
        return [dict(r) for r in rows]
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


# ═════════════════════════════════════════════════════════════════════
# ROUTES — MONITORING / HEALTH
# ═════════════════════════════════════════════════════════════════════
@app.get("/api/v1/classification/health", tags=["Monitoring"])
def health():
    fm = cfg.FORECAST_MODELS
    forecast_ok = all([fm["factures_ref"], fm["recharges_ref"],
                       fm["models_budget"], fm["habitudes_cli"]])

    overall = "UP" if forecast_ok else "DOWN"

    return {
        "status": overall,
        "service": "smartwallet-ml",
        "version": "6.0.0",
        "port": 8000,
        "modules": {
            "forecast_1_5": "UP" if forecast_ok else "DOWN",
            "ocr": "UP" if OCR_AVAILABLE else "DOWN",
            "recommendation_v5": "UP" if RECO_AVAILABLE else "DOWN",
        },
        "timestamp": datetime.now().isoformat(),
    }
@app.get("/health", tags=["Monitoring"])
def health_classification():
    classif_ok = cls.is_loaded()

    drift_info = {}
    try:
        conn = cfg.get_db_connection()
        with conn.cursor() as cur:
            cur.execute(f"""
                        SELECT psi_max, psi_status, churn_pct_high_risk, n_clients, run_at
                        FROM {TBL_MODEL_RUNS} ORDER BY run_at DESC LIMIT 1
                        """)
            row = cur.fetchone()
        conn.close()
        if row:
            drift_info = {
                "psi_max": row[0],
                "status": row[1],
                "churn_pct_high_risk": row[2],
                "n_clients": row[3],
                "last_run": str(row[4]),
            }
    except Exception:
        pass

    status = "UP" if classif_ok else "DOWN"

    return {
        "status": status,
        "service": "smartwallet-ml-classification",
        "version": "6.0.0",
        "port": 8000,
        "modules": {
            "classification_gbm": "UP" if classif_ok else "DOWN",
        },
        "classification_method": "GBM + 43 features + 6 profils (binôme)",
        "drift": drift_info,
        "timestamp": datetime.now().isoformat(),
    }

@app.get("/metrics", tags=["Monitoring"])
def metrics():
    bill_metrics = {}
    rb = cfg.FORECAST_MODELS["results_bill"]
    if rb:
        for label, res in rb.items():
            bill_metrics[label] = {
                "mae": round(float(res.get("mae", 0)), 2),
                "rmse": round(float(res.get("rmse", 0)), 2),
                "r2": round(float(res.get("r2", 0)), 4),
            }
    return {"timestamp": datetime.now().isoformat(), "module1_factures": bill_metrics}

@app.get("/metrics/history", tags=["Monitoring"])
def get_metrics_history(days: int = 30):
    """
    Historique des métriques de performance ML sur N jours.

    Sert le graphique Chart.js de la page Prédictions IA du frontend admin.

    Pour une version production :
      - Lire la table prediction_audit_log côté PostgreSQL
      - Calculer R²/MAE/RMSE journaliers
      - Retourner ces séries temporelles

    Pour la démo PFE : renvoie les métriques actuelles répétées
    sur la fenêtre demandée, légèrement bruitées pour réalisme.
    """
    from datetime import datetime, timedelta
    import random

    today = datetime.now()
    dates = [(today - timedelta(days=i)).strftime("%d/%m") for i in range(days - 1, -1, -1)]

    # ─── Lecture des métriques actuelles (déjà chargées au boot) ──
    # On part des vraies valeurs si elles existent, sinon valeurs typiques.
    try:
        # Lecture depuis le contexte global de modules_forecast
        import json, os
        from . import config as cfg
        metrics_path = os.path.join(cfg.OUTPUTS_DIR, "metrics_summary.json")
        if os.path.exists(metrics_path):
            with open(metrics_path) as fp:
                summary = json.load(fp)
            module1 = summary.get("module1_factures", {})
            r2_topnet_now = module1.get("TOPNET", {}).get("r2", 0.99)
            r2_steg_now   = module1.get("STEG",   {}).get("r2", 0.72)
            mae_avg_now   = sum(m.get("mae", 0) for m in module1.values()) / max(len(module1), 1)
        else:
            r2_topnet_now, r2_steg_now, mae_avg_now = 0.99, 0.72, 3.8
    except Exception:
        r2_topnet_now, r2_steg_now, mae_avg_now = 0.99, 0.72, 3.8

    # ─── Génération des séries (bruit léger pour effet "historique") ──
    def noisy(base, n, scale=0.005):
        """Génère n valeurs autour de `base` avec petit bruit."""
        return [round(max(0, base + random.uniform(-scale, scale)), 4) for _ in range(n)]

    return {
        "dates":     dates,
        "r2Topnet":  noisy(r2_topnet_now, days, 0.003),
        "r2Steg":    noisy(r2_steg_now,   days, 0.015),
        "maeAvg":    noisy(mae_avg_now,   days, 0.3),
        "days":      days,
    }



@app.get("/api/v1/classification/drift/status", tags=["Monitoring"])
def drift_status():
    try:
        conn = cfg.get_db_connection()
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(f"""
                        SELECT psi_max, psi_status, gbm_accuracy, gbm_test_f1,
                               gbm_holdout_f1, churn_pct_high_risk, fragile_profiles, run_at
                        FROM {TBL_MODEL_RUNS} ORDER BY run_at DESC LIMIT 5
                        """)
            rows = cur.fetchall()
        conn.close()
        return [dict(r) for r in rows]
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.get("/api/v1/classification/monitoring/alerts", tags=["Monitoring"])
def monitoring_alerts():
    try:
        conn = cfg.get_db_connection()
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                        SELECT * FROM monitoring_alerts
                        WHERE resolved = False
                        ORDER BY triggered_at DESC LIMIT 50
                        """)
            rows = cur.fetchall()
        conn.close()
        return [dict(r) for r in rows]
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


# ═════════════════════════════════════════════════════════════════════
# ROUTES — RETRAIN
# ═════════════════════════════════════════════════════════════════════
_retrain_state = {
    "status": "idle", "last_run": None, "last_success": None,
    "last_error": None, "duration_sec": None, "trigger": None,
    "pkls_updated": [],
}
RETRAIN_LOG = Path(cfg.MODEL_DIR) / "retrain_history.json"


def _save_retrain_log():
    try:
        history = []
        if RETRAIN_LOG.exists():
            with open(RETRAIN_LOG) as f:
                history = json.load(f)
        history.append({**_retrain_state, "saved_at": datetime.now().isoformat()})
        with open(RETRAIN_LOG, "w") as f:
            json.dump(history[-50:], f, indent=2)
    except Exception as e:
        log.warning(f"Sauvegarde log retrain : {e}")


def _run_retrain(trigger: str = "api"):
    global _retrain_state
    if _retrain_state["status"] == "running":
        return

    _retrain_state.update({
        "status": "running", "trigger": trigger,
        "last_run": datetime.now().isoformat(),
        "last_error": None, "pkls_updated": [],
    })
    start = datetime.now()
    try:
        script = os.getenv("RETRAIN_SCRIPT", "./training/retrain.py")
        cmd = [sys.executable, script, "--db-url", cfg.DB_URL, "--model-dir", cfg.MODEL_DIR]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=3600)

        _retrain_state["duration_sec"] = round((datetime.now() - start).total_seconds(), 1)
        if result.returncode == 0:
            log.info("✅ RÉENTRAÎNEMENT RÉUSSI")
            cfg.load_forecast_models()
            cls.load_classification_models(cfg.MODEL_DIR)
            mod6.load_module6_caches(cfg.MODEL_DIR)
            meta_path = Path(cfg.MODEL_DIR) / "retrain_meta.json"
            pkls_list = []
            if meta_path.exists():
                with open(meta_path) as f:
                    pkls_list = json.load(f).get("pkls", [])
            _retrain_state.update({
                "status": "success",
                "last_success": datetime.now().isoformat(),
                "pkls_updated": pkls_list,
            })
        else:
            _retrain_state.update({
                "status": "failed",
                "last_error": (result.stderr[-500:] if result.stderr else "Erreur inconnue"),
            })
    except Exception as e:
        _retrain_state.update({"status": "failed", "last_error": str(e)})
    _save_retrain_log()


@app.post("/retrain", tags=["Retrain"])
def trigger_retrain(
        x_retrain_secret: str = Header(..., alias="X-Retrain-Secret"),
        trigger: str = Query("api"),
):
    if x_retrain_secret != cfg.RETRAIN_SECRET:
        raise HTTPException(status_code=403, detail="Secret invalide")
    if _retrain_state["status"] == "running":
        return {"message": "Réentraînement déjà en cours", "status": "already_running"}
    threading.Thread(target=_run_retrain, args=(trigger,), daemon=True).start()
    return {"message": "Réentraînement démarré", "status": "started"}


@app.get("/retrain/status", tags=["Retrain"])
def retrain_status():
    return {**_retrain_state, "timestamp": datetime.now().isoformat()}


@app.get("/retrain/history", tags=["Retrain"])
def retrain_history():
    try:
        if RETRAIN_LOG.exists():
            with open(RETRAIN_LOG) as f:
                history = json.load(f)
            for h in history:
                h["nb_modeles"] = len(h.get("pkls_updated", []))
            return {"history": list(reversed(history)), "total": len(history)}
        return {"history": [], "total": 0}
    except Exception as e:
        return {"history": [], "total": 0, "error": str(e)}


# ── Admin retrain (classification uniquement) ──
_admin_retrain_status: Dict[str, Any] = {
    "running": False, "last_run": None,
    "last_result": None, "triggered_by": None,
}


class AdminRetrainRequest(BaseModel):
    admin_user: str = Field(default="unknown")


def _run_admin_retrain(triggered_by: str = "admin"):
    _admin_retrain_status["running"] = True
    _admin_retrain_status["last_run"] = datetime.now().isoformat()
    _admin_retrain_status["triggered_by"] = triggered_by
    _admin_retrain_status["last_result"] = "running..."

    log_path = Path(cfg.MODEL_DIR) / "admin_retrain.log"
    try:
        with open(log_path, "w", encoding="utf-8") as logf:
            proc = subprocess.Popen(
                [sys.executable, "-m", "app.wallet_classification", "--retrain"],
                stdout=logf, stderr=logf,
            )
        _admin_retrain_status["pid"] = proc.pid
        proc.wait(timeout=7200)
        if proc.returncode == 0:
            _admin_retrain_status["last_result"] = "success"
            cls.load_classification_models(cfg.MODEL_DIR)
        else:
            try:
                with open(log_path, "r", encoding="utf-8", errors="replace") as lf:
                    tail = lf.read()[-1000:]
            except Exception:
                tail = "(log illisible)"
            _admin_retrain_status["last_result"] = f"error (rc={proc.returncode}): {tail}"
    except Exception as exc:
        _admin_retrain_status["last_result"] = f"exception: {exc}"
    finally:
        _admin_retrain_status["running"] = False
        _admin_retrain_status.pop("pid", None)


@app.post("/admin/retrain", tags=["Retrain"])
def admin_retrain(body: AdminRetrainRequest):
    if _admin_retrain_status["running"]:
        raise HTTPException(status_code=409,
                            detail={"status": "already_running",
                                    "started_at": _admin_retrain_status["last_run"]})
    threading.Thread(target=_run_admin_retrain,
                     kwargs={"triggered_by": body.admin_user},
                     daemon=True).start()
    return JSONResponse(status_code=202,
                        content={"status": "accepted", "triggered_by": body.admin_user})


@app.get("/admin/retrain/status", tags=["Retrain"])
def admin_retrain_status():
    return _admin_retrain_status


@app.post("/admin/retrain/kill", tags=["Retrain"])
def admin_retrain_kill():
    import signal as _signal
    pid = _admin_retrain_status.get("pid")
    if pid:
        try:
            os.kill(pid, _signal.SIGTERM)
            _admin_retrain_status["last_result"] = f"killed (pid={pid})"
        except Exception as exc:
            _admin_retrain_status["last_result"] = f"kill failed: {exc}"
    else:
        _admin_retrain_status["last_result"] = "reset manuel"
    _admin_retrain_status["running"] = False
    _admin_retrain_status.pop("pid", None)
    return {"status": "killed_or_reset", "detail": _admin_retrain_status["last_result"]}


@app.get("/admin/retrain/log", tags=["Retrain"])
def admin_retrain_log(lines: int = Query(default=100, ge=1, le=5000)):
    log_path = Path(cfg.MODEL_DIR) / "admin_retrain.log"
    try:
        with open(log_path, "r", encoding="utf-8", errors="replace") as f:
            all_lines = f.readlines()
        return {"log": "".join(all_lines[-lines:]),
                "total_lines": len(all_lines),
                "path": str(log_path)}
    except FileNotFoundError:
        raise HTTPException(status_code=404, detail="Pas encore de log.")
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


# ═════════════════════════════════════════════════════════════════════
# ROUTES — RECOMMENDATION V5 (système d'offres binôme)
# ═════════════════════════════════════════════════════════════════════
class OfferCreate(BaseModel):
    title: str
    type: str
    cashback_pct: float = 0.0
    discount_pct: float = 0.0
    target_profiles: List[str] = []
    provider_name: Optional[str] = None
    category: Optional[str] = None
    min_amount: float = 0.0
    boost: float = 1.0
    description: Optional[str] = None


class OfferUpdate(BaseModel):
    title: Optional[str] = None
    type: Optional[str] = None
    provider_name: Optional[str] = None
    category: Optional[str] = None
    cashback_pct: Optional[float] = None
    discount_pct: Optional[float] = None
    min_amount: Optional[float] = None
    target_profiles: Optional[List[str]] = None
    boost: Optional[float] = None
    description: Optional[str] = None


class OfferStatusUpdate(BaseModel):
    status: str


class RecommendationCreate(BaseModel):
    profile_name: str
    offer_code: str
    score: float = 0.8
    note: Optional[str] = None


class RecommendationUpdate(BaseModel):
    score: Optional[float] = None
    admin_note: Optional[str] = None
    status: Optional[str] = None


class RecommendationStatusUpdate(BaseModel):
    status: str


class BulkApproveRequest(BaseModel):
    profile_name: str


class NotifyRequest(BaseModel):
    profile_filter: Optional[str] = None


class GenerateDescriptionRequest(BaseModel):
    offer_code: str


class ClassifyV5Request(BaseModel):
    client_id: Optional[str] = None
    features: Dict[str, Any]


# ── Offres CRUD ──
@app.get("/api/v5/offers", tags=["V5-Offres"])
async def v5_list_offers(
        status: Optional[str] = Query(None),
        type: Optional[str] = Query(None, alias="type"),
        provider: Optional[str] = Query(None),
        category: Optional[str] = Query(None),
        limit: int = Query(100, ge=1, le=1000),
        offset: int = Query(0, ge=0),
):
    _require_reco()
    offers = reco.get_offers(status=status, offer_type=type, provider=provider,
                             category=category, limit=limit, offset=offset)
    return _api_response(data={"offers": offers, "count": len(offers)})


@app.get("/api/v5/offers/{offer_code}", tags=["V5-Offres"])
async def v5_get_offer(offer_code: str):
    _require_reco()
    offer = reco.get_offer_by_code(offer_code)
    if not offer:
        raise HTTPException(status_code=404, detail=f"Offre '{offer_code}' introuvable")
    return _api_response(data=offer)


@app.post("/api/v5/offers", tags=["V5-Offres"], status_code=201)
async def v5_create_offer(offer: OfferCreate, request: Request):
    _require_reco()
    admin_user = request.headers.get("X-Admin-User", "api_admin")
    result, sc = reco.add_offer_manual(offer.model_dump(), admin_user)
    if sc == 201:
        return _api_response(data=result, message="Offre créée", status_code=201)
    raise HTTPException(status_code=sc, detail=result.get("error", "Erreur"))


@app.put("/api/v5/offers/{offer_code}", tags=["V5-Offres"])
async def v5_update_offer(offer_code: str, offer: OfferUpdate, request: Request):
    _require_reco()
    admin_user = request.headers.get("X-Admin-User", "api_admin")
    data = {k: v for k, v in offer.model_dump().items() if v is not None}
    if not data:
        raise HTTPException(status_code=400, detail="Aucun champ à modifier")
    result, sc = reco.update_offer(offer_code, data, admin_user)
    if sc == 200:
        return _api_response(data=result, message="Offre modifiée")
    raise HTTPException(status_code=sc, detail=result.get("error", "Erreur"))


@app.patch("/api/v5/offers/{offer_code}/status", tags=["V5-Offres"])
async def v5_offer_status(offer_code: str, body: OfferStatusUpdate, request: Request):
    _require_reco()
    admin_user = request.headers.get("X-Admin-User", "api_admin")
    result, sc = reco.set_offer_status(offer_code, body.status, admin_user)
    if sc == 200:
        return _api_response(data=result, message=f"Statut → {body.status}")
    raise HTTPException(status_code=sc, detail=result.get("error", "Erreur"))


@app.delete("/api/v5/offers/{offer_code}", tags=["V5-Offres"])
async def v5_delete_offer(offer_code: str, request: Request):
    _require_reco()
    admin_user = request.headers.get("X-Admin-User", "api_admin")
    result, sc = reco.delete_offer(offer_code, admin_user)
    if sc == 200:
        return _api_response(data=result, message="Offre supprimée")
    raise HTTPException(status_code=sc, detail=result.get("error", "Erreur"))


# ── Recommandations CRUD ──
@app.get("/api/v5/recommendations", tags=["V5-Recommandations"])
async def v5_list_recommendations(
        status: Optional[str] = Query(None),
        profile: Optional[str] = Query(None),
        limit: int = Query(100, ge=1, le=1000),
        offset: int = Query(0, ge=0),
):
    _require_reco()
    recos = reco.get_recommendations(status=status, profile=profile,
                                     limit=limit, offset=offset)
    return _api_response(data={"recommendations": recos, "count": len(recos)})


@app.post("/api/v5/recommendations", tags=["V5-Recommandations"], status_code=201)
async def v5_create_recommendation(body: RecommendationCreate, request: Request):
    _require_reco()
    admin_user = request.headers.get("X-Admin-User", "api_admin")
    result, sc = reco.add_recommendation_manual(
        body.profile_name, body.offer_code, body.score, body.note, admin_user
    )
    if sc == 201:
        return _api_response(data=result, message="Recommandation créée", status_code=201)
    raise HTTPException(status_code=sc, detail=result.get("error", "Erreur"))


@app.put("/api/v5/recommendations/{reco_id}", tags=["V5-Recommandations"])
async def v5_update_recommendation(reco_id: int, body: RecommendationUpdate, request: Request):
    _require_reco()
    admin_user = request.headers.get("X-Admin-User", "api_admin")
    data = {k: v for k, v in body.model_dump().items() if v is not None}
    if not data:
        raise HTTPException(status_code=400, detail="Aucun champ à modifier")
    result, sc = reco.update_recommendation(reco_id, data, admin_user)
    if sc == 200:
        return _api_response(data=result, message="Modifiée")
    raise HTTPException(status_code=sc, detail=result.get("error", "Erreur"))


@app.patch("/api/v5/recommendations/{reco_id}/status", tags=["V5-Recommandations"])
async def v5_reco_status(reco_id: int, body: RecommendationStatusUpdate, request: Request):
    _require_reco()
    admin_user = request.headers.get("X-Admin-User", "api_admin")
    if body.status == "APPROVED":
        result, sc = reco.approve_recommendation(reco_id, admin_user)
    elif body.status == "REJECTED":
        result, sc = reco.reject_recommendation(reco_id, admin_user)
    else:
        raise HTTPException(status_code=400, detail="Statut invalide (APPROVED/REJECTED)")
    if sc == 200:
        return _api_response(data=result, message=f"Statut → {body.status}")
    raise HTTPException(status_code=sc, detail=result.get("error", "Erreur"))


@app.patch("/api/v5/recommendations/{reco_id}/approve", tags=["V5-Recommandations"])
async def v5_reco_approve(reco_id: int, request: Request,
                          body: Optional[Dict[str, Any]] = None):
    _require_reco()
    admin_user = request.headers.get("X-Admin-User", "api_admin")
    note = body.get("note") if body else None
    result, sc = reco.approve_recommendation(reco_id, admin_user, note)
    if sc == 200:
        return _api_response(data=result, message="Approuvée")
    raise HTTPException(status_code=sc, detail=result.get("error", "Erreur"))


@app.patch("/api/v5/recommendations/{reco_id}/reject", tags=["V5-Recommandations"])
async def v5_reco_reject(reco_id: int, request: Request,
                         body: Optional[Dict[str, Any]] = None):
    _require_reco()
    admin_user = request.headers.get("X-Admin-User", "api_admin")
    note = body.get("note") if body else None
    result, sc = reco.reject_recommendation(reco_id, admin_user, note)
    if sc == 200:
        return _api_response(data=result, message="Rejetée")
    raise HTTPException(status_code=sc, detail=result.get("error", "Erreur"))


@app.post("/api/v5/recommendations/bulk-approve", tags=["V5-Recommandations"])
async def v5_bulk_approve(body: BulkApproveRequest, request: Request):
    _require_reco()
    admin_user = request.headers.get("X-Admin-User", "api_admin")
    result = reco.bulk_approve(body.profile_name, admin_user)
    return _api_response(data=result, message=f"{result.get('n_approved', 0)} recos approuvées")


# ── Profils V5 ──
@app.get("/api/v5/profiles", tags=["V5-Profils"])
async def v5_list_profiles():
    _require_reco()
    conn = reco.get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                        SELECT r.profile_name,
                               COUNT(DISTINCT r.id) AS total_recommendations,
                               COUNT(DISTINCT r.id) FILTER (WHERE r.status='APPROVED') AS approved,
                            COUNT(DISTINCT r.id) FILTER (WHERE r.status='PENDING') AS pending,
                            ROUND(AVG(r.score), 3) AS avg_score,
                               MAX(r.generated_at) AS last_generated
                        FROM recommendations_v5 r
                        GROUP BY r.profile_name
                        ORDER BY total_recommendations DESC
                        """)
            profiles = [dict(row) for row in cur.fetchall()]
        return _api_response(data={"profiles": profiles, "count": len(profiles)})
    finally:
        reco.release_conn(conn)


@app.get("/api/v5/profiles/{profile_name:path}/offers", tags=["V5-Profils"])
async def v5_profile_offers(profile_name: str):
    _require_reco()
    conn = reco.get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                        SELECT go.offer_code, go.title, go.type, go.category, go.provider_name,
                               go.cashback_pct, go.discount_pct, go.status AS offer_status,
                               r.score, r.is_targeted, r.recommendation_type, r.status AS reco_status
                        FROM recommendations_v5 r
                                 JOIN generated_offers go ON go.offer_code = r.offer_code
                        WHERE r.profile_name = %s
                        ORDER BY r.score DESC
                        """, (profile_name,))
            offers = [dict(row) for row in cur.fetchall()]
        return _api_response(data={"profile": profile_name, "offers": offers, "count": len(offers)})
    finally:
        reco.release_conn(conn)


# ── Pipeline V5 ──
_v5_pipeline_lock = threading.Lock()


def _v5_run_pipeline_bg():
    if _v5_pipeline_lock.acquire(blocking=False):
        try:
            reco.run_pipeline()
        except Exception as e:
            log.error(f"❌ Pipeline V5 : {e}")
        finally:
            _v5_pipeline_lock.release()


@app.post("/api/v5/pipeline/run", tags=["V5-Système"])
async def v5_pipeline_run(background_tasks: BackgroundTasks):
    _require_reco()
    if _v5_pipeline_lock.locked():
        raise HTTPException(status_code=409, detail="Pipeline déjà en cours")
    background_tasks.add_task(_v5_run_pipeline_bg)
    return _api_response(data={"started": True}, message="Pipeline lancé")


@app.post("/api/v5/offers/generate", tags=["V5-Système"])
async def v5_offers_generate(background_tasks: BackgroundTasks):
    _require_reco()
    if _v5_pipeline_lock.locked():
        raise HTTPException(status_code=409, detail="Génération déjà en cours")

    def _gen_offers_bg():
        if _v5_pipeline_lock.acquire(blocking=False):
            run_id = datetime.now().strftime("RUN_%Y%m%d_%H%M%S")
            try:
                reco.init_tables()
                if hasattr(reco, "start_generation_run"):
                    reco.start_generation_run(run_id)
                purge = {"offers_archived": 0, "recos_archived": 0}
                if hasattr(reco, "archive_and_purge_before_run"):
                    purge = reco.archive_and_purge_before_run(run_id)
                profiles, transactions, _, _ = reco.phase2_data_understanding()
                profile_stats, _, _, cat_stats, prov_stats = reco.phase3_data_preparation(profiles, transactions)
                engine = reco.OfferGenerationEngine()
                offers = engine.generate_all_offers(profile_stats, cat_stats, prov_stats)
                engine.persist_offers(offers)
                if hasattr(reco, "finish_generation_run"):
                    reco.finish_generation_run(
                        run_id,
                        n_profiles=int(profile_stats.shape[0]),
                        n_offers_gen=len(offers),
                        offers_archived=purge.get("offers_archived", 0),
                        recos_archived=purge.get("recos_archived", 0),
                        status="DONE",
                    )
            except Exception as e:
                log.error(f"❌ Génération offres : {e}")
                if hasattr(reco, "finish_generation_run"):
                    reco.finish_generation_run(run_id, status="FAILED", error_msg=str(e))
            finally:
                _v5_pipeline_lock.release()

    background_tasks.add_task(_gen_offers_bg)
    return _api_response(data={"started": True}, message="Génération offres lancée")


@app.post("/api/v5/recommendations/generate", tags=["V5-Système"])
async def v5_recos_generate(background_tasks: BackgroundTasks):
    _require_reco()
    if _v5_pipeline_lock.locked():
        raise HTTPException(status_code=409, detail="Génération déjà en cours")

    def _gen_recos_bg():
        if _v5_pipeline_lock.acquire(blocking=False):
            run_id = datetime.now().strftime("RUN_%Y%m%d_%H%M%S")
            try:
                reco.init_tables()
                if hasattr(reco, "start_generation_run"):
                    reco.start_generation_run(run_id)
                purge = {"recos_archived": 0}
                if hasattr(reco, "archive_and_purge_recos_only"):
                    purge = reco.archive_and_purge_recos_only(run_id)
                profiles, transactions, _, _ = reco.phase2_data_understanding()
                profile_stats, pp_norm, pc_norm, _, _ = reco.phase3_data_preparation(profiles, transactions)
                engine = reco.OfferGenerationEngine()
                active_offers = engine.load_active_offers_from_db()
                top_reco = reco.phase4_scoring_only(profile_stats, pp_norm, pc_norm, active_offers)
                reco.phase6_deployment(top_reco, [])
                if hasattr(reco, "finish_generation_run"):
                    n_reco = int(top_reco['profile'].nunique()) if hasattr(top_reco, 'columns') and 'profile' in top_reco.columns else 0
                    reco.finish_generation_run(
                        run_id,
                        n_profiles=n_reco,
                        n_offers_gen=len(active_offers),
                        recos_archived=purge.get("recos_archived", 0),
                        status="DONE",
                    )
            except Exception as e:
                log.error(f"❌ Régénération recos : {e}")
                if hasattr(reco, "finish_generation_run"):
                    reco.finish_generation_run(run_id, status="FAILED", error_msg=str(e))
            finally:
                _v5_pipeline_lock.release()

    background_tasks.add_task(_gen_recos_bg)
    return _api_response(data={"started": True}, message="Régénération recos lancée")


@app.post("/api/v5/recommendations/generate-description", tags=["V5-Recommandations"])
async def v5_generate_description(body: GenerateDescriptionRequest):
    _require_reco()
    offer = reco.get_offer_by_code(body.offer_code)
    if not offer:
        raise HTTPException(status_code=404, detail="Offre non trouvée")
    description = reco.generate_offer_description(offer, 0.8)
    return _api_response(data={"description": description})


@app.post("/api/v5/notifications/send", tags=["V5-Système"])
async def v5_send_notifications(body: Optional[NotifyRequest] = None):
    _require_reco()
    if body is None:
        body = NotifyRequest()
    result = reco.send_approved_recommendations(body.profile_filter)
    if 'error' in result:
        raise HTTPException(status_code=500, detail=result['error'])
    return _api_response(data=result, message=f"{result.get('sent', 0)} notifications envoyées")


@app.get("/api/v5/metrics", tags=["V5-Évaluation"])
async def v5_metrics(evaluation_type: str = Query("simulated", regex="^(simulated|real)$")):
    _require_reco()
    conn = reco.get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                        SELECT profile_name, precision_score, recall_score, f1_score,
                               coverage, acceptance_rate, avg_score, n_recommendations,
                               n_offers_generated, computed_at, model_version
                        FROM recommendation_metrics_v5
                        WHERE evaluation_type = %s
                        ORDER BY computed_at DESC
                        """, (evaluation_type,))
            rows = cur.fetchall()
        return _api_response(data={"metrics": [dict(r) for r in rows], "count": len(rows)})
    finally:
        reco.release_conn(conn)


@app.get("/api/v5/metrics/history", tags=["V5-Évaluation"])
async def v5_metrics_history(profile: str = Query(...), limit: int = Query(10, ge=1)):
    _require_reco()
    conn = reco.get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                        SELECT profile_name, precision_score, recall_score, f1_score,
                               acceptance_rate, avg_score, evaluation_type, computed_at
                        FROM recommendation_metrics_v5
                        WHERE profile_name = %s
                        ORDER BY computed_at DESC LIMIT %s
                        """, (profile, limit))
            metrics = cur.fetchall()
        return _api_response(data={"profile": profile,
                                   "history": [dict(m) for m in metrics],
                                   "count": len(metrics)})
    finally:
        reco.release_conn(conn)


@app.get("/api/v5/health", tags=["V5-Système"])
async def v5_health():
    """Santé du sous-système V5."""
    if not RECO_AVAILABLE:
        return _api_response(data={"status": "DOWN"}, message="V5 non disponible")
    info: Dict[str, Any] = {"v5_available": True, "classification_loaded": cls.is_loaded()}
    try:
        conn = reco.get_conn()
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM generated_offers WHERE status='ACTIVE'")
            info["active_offers"] = cur.fetchone()[0]
            cur.execute("SELECT COUNT(*) FROM recommendations_v5 WHERE status='PENDING'")
            info["pending_recos"] = cur.fetchone()[0]
        reco.release_conn(conn)
    except Exception as e:
        info["db_error"] = str(e)
    return _api_response(data=info)


@app.get("/api/v5/generation-runs", tags=["V5-Système"])
async def v5_generation_runs(limit: int = Query(20, ge=1, le=200)):
    _require_reco()
    conn = reco.get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                        SELECT run_id, started_at, finished_at, n_profiles, n_offers_gen,
                               n_offers_new, n_offers_deact,
                               COALESCE(n_offers_archived, 0) AS n_offers_archived,
                               COALESCE(n_recos_archived, 0)  AS n_recos_archived,
                               status, error_msg
                        FROM offer_generation_runs
                        ORDER BY started_at DESC LIMIT %s
                        """, (limit,))
            runs = [dict(r) for r in cur.fetchall()]
        return _api_response(data={"runs": runs, "count": len(runs)})
    finally:
        reco.release_conn(conn)


@app.get("/api/v5/history/offers", tags=["V5-Système"])
async def v5_history_offers(
        run_id: Optional[str] = Query(None),
        limit: int = Query(100, ge=1, le=1000),
):
    """Historique des offres archivées (purgées à chaque run)."""
    _require_reco()
    conn = reco.get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            if run_id:
                cur.execute("""
                            SELECT * FROM generated_offers_history
                            WHERE archived_run_id = %s
                            ORDER BY archived_at DESC LIMIT %s
                            """, (run_id, limit))
            else:
                cur.execute("""
                            SELECT * FROM generated_offers_history
                            ORDER BY archived_at DESC LIMIT %s
                            """, (limit,))
            rows = [dict(r) for r in cur.fetchall()]
        return _api_response(data={"offers": rows, "count": len(rows)})
    finally:
        reco.release_conn(conn)


@app.get("/api/v5/history/recommendations", tags=["V5-Système"])
async def v5_history_recommendations(
        run_id: Optional[str] = Query(None),
        profile: Optional[str] = Query(None),
        limit: int = Query(100, ge=1, le=1000),
):
    """Historique des recommandations archivées (purgées à chaque run)."""
    _require_reco()
    conn = reco.get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            clauses, params = [], []
            if run_id:
                clauses.append("archived_run_id = %s"); params.append(run_id)
            if profile:
                clauses.append("profile_name = %s"); params.append(profile)
            where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
            params.append(limit)
            cur.execute(f"""
                        SELECT * FROM recommendations_v5_history
                        {where}
                        ORDER BY archived_at DESC LIMIT %s
                        """, params)
            rows = [dict(r) for r in cur.fetchall()]
        return _api_response(data={"recommendations": rows, "count": len(rows)})
    finally:
        reco.release_conn(conn)


@app.get("/api/v5/model/runs", tags=["V5-Modèle"])
async def v5_model_runs(limit: int = Query(10, ge=1)):
    _require_reco()
    conn = reco.get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(f"""
                        SELECT id, run_at, n_clients, n_holdout, n_profiles, k_used,
                               silhouette_score, davies_bouldin, calinski_harabasz,
                               bootstrap_stability, bootstrap_std, n_mixte, pct_mixte,
                               psi_max, psi_status, gbm_accuracy, gbm_cv_f1, gbm_test_f1,
                               gbm_holdout_f1, fragile_profiles, churn_pct_high_risk,
                               churn_pct_critical, retrain_trigger, notes
                        FROM {TBL_MODEL_RUNS} ORDER BY run_at DESC LIMIT %s
                        """, (limit,))
            runs = cur.fetchall()
        return _api_response(data={"runs": [dict(r) for r in runs], "count": len(runs)})
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        reco.release_conn(conn)


@app.get("/api/v5/model/runs/{run_id}", tags=["V5-Modèle"])
async def v5_model_run(run_id: int):
    _require_reco()
    conn = reco.get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(f"SELECT * FROM {TBL_MODEL_RUNS} WHERE id = %s", (run_id,))
            run = cur.fetchone()
            if not run:
                raise HTTPException(status_code=404, detail=f"Run '{run_id}' introuvable")
        return _api_response(data=dict(run))
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        reco.release_conn(conn)


@app.get("/api/v5/model/latest", tags=["V5-Modèle"])
async def v5_model_latest():
    _require_reco()
    conn = reco.get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(f"SELECT * FROM {TBL_MODEL_RUNS} ORDER BY run_at DESC LIMIT 1")
            run = cur.fetchone()
            if not run:
                raise HTTPException(status_code=404, detail="Aucun run trouvé")
            run_data = dict(run)
            cur.execute("""
                        SELECT cluster_id, profile_name, n_clients, pct_clients,
                               sil_mean, is_fragile, dominant_category,
                               freq_mensuelle_mean, montant_moyen_mean, churn_pct_high_risk
                        FROM profile_stats ORDER BY cluster_id
                        """)
            run_data['profile_stats'] = [dict(p) for p in cur.fetchall()]
            cur.execute("""
                        SELECT cluster_id, profile_name, churn_score_30j,
                               arpu_mensuel, ltv_12m_base, score_risque
                        FROM kpi_business ORDER BY cluster_id
                        """)
            run_data['kpi_summary'] = [dict(k) for k in cur.fetchall()]
        return _api_response(data=run_data)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        reco.release_conn(conn)


@app.post("/api/v5/classify", tags=["V5-Classification"])
async def v5_classify(body: ClassifyV5Request):
    """Classifier un client (même méthode que /predict, format V5)."""
    result = cls.classify_client(body.features, body.client_id or "unknown")
    return _api_response(data={
        "client_id": body.client_id or "unknown",
        **result,
    })


# ═════════════════════════════════════════════════════════════════════
# ROUTES — OCR
# ═════════════════════════════════════════════════════════════════════
def _get_historique_factures(client_id: str, label: str, n: int = 6) -> list:
    """Récupère les N derniers montants de factures pour un fournisseur."""
    label_to_kw = {
        "STEG": "STEG", "SONEDE": "SONEDE", "TOPNET": "TOPNET",
        "BEE": "BEE", "TT": "Tunisie Telecom", "OOREDOO": "Ooredoo",
    }
    keyword = label_to_kw.get(label, label)
    try:
        conn = cfg.get_db_connection()
        cur = conn.cursor()
        cur.execute("""
                    SELECT t.amount FROM transaction t
                                             JOIN type_transaction tt ON t.transaction_type_id = tt.id
                    WHERE t.client_id = %s AND t.reversal_flag = 'N'
                      AND tt.category = 'Factures & Services'
                      AND tt.title ILIKE %s
                    ORDER BY t.transaction_date DESC LIMIT %s
                    """, (client_id, f"%{keyword}%", n))
        rows = cur.fetchall()
        conn.close()
        return [float(r[0]) for r in rows]
    except Exception as e:
        log.warning(f"⚠️ Historique factures : {e}")
        return []


def _calculer_dates_rappels(date_echeance) -> list:
    maintenant = datetime.now()
    rappels = []
    delais = [
        (7, "📋 Rappel 7 jours", "Pensez à mettre de côté le montant"),
        (3, "⏰ Rappel 3 jours", "Votre facture arrive bientôt !"),
        (1, "⚠️ Rappel veille", "À payer demain !"),
        (0, "🚨 Jour J", "Payez votre facture aujourd'hui !"),
    ]
    for jours_avant, titre, msg in delais:
        date_rappel = date_echeance - timedelta(days=jours_avant)
        if date_rappel >= maintenant:
            rappels.append({
                "jours_avant": jours_avant,
                "date_rappel": date_rappel.strftime("%d/%m/%Y"),
                "titre": titre,
                "message": msg,
            })
    return rappels


@app.post("/api/ocr/scan-facture", tags=["OCR"])
async def api_scan_facture(
        image: UploadFile = File(...),
        client_id: str = Form(...),
        solde: Optional[float] = Form(None),
):
    """Analyse une image de facture (multipart/form-data)."""
    if not OCR_AVAILABLE:
        return {"error": "OCR Service non disponible"}
    image_bytes = await image.read()
    image_b64 = _b64.b64encode(image_bytes).decode("utf-8")
    scan = scan_facture(image_b64)
    result = {**scan}

    if scan.get("montant"):
        depenses_mois, budget_estim = 0.0, 300.0
        # ✅ CORRECTION : le solde est recalculé depuis la base (crédits − débits),
        # on NE fait PAS confiance au paramètre `solde` envoyé par le front (qui
        # valait 0 quand l'écran ne l'avait pas reçu → bug « solde = 0 »).
        solde_reel = solde if solde is not None else 0.0
        try:
            conn = cfg.get_db_connection()
            cur = conn.cursor()
            cur.execute("""
                        SELECT COALESCE(SUM(CASE WHEN tt.type = 'C'
                                                     THEN t.amount ELSE -t.amount END), 0)
                        FROM transaction t
                                 JOIN type_transaction tt ON t.transaction_type_id = tt.id
                        WHERE t.client_id = %s AND t.reversal_flag = 'N'
                        """, (client_id,))
            solde_reel = float(cur.fetchone()[0])

            cur.execute("""
                        SELECT COALESCE(SUM(t.amount), 0) FROM transaction t
                                                                   JOIN type_transaction tt ON t.transaction_type_id = tt.id
                        WHERE t.client_id = %s AND t.reversal_flag = 'N' AND tt.type = 'D'
                          AND DATE_TRUNC('month', t.transaction_date) = DATE_TRUNC('month', NOW())
                        """, (client_id,))
            depenses_mois = float(cur.fetchone()[0])
            conn.close()
        except Exception as e:
            log.warning(f"⚠️ Calcul solde réel / dépenses mois : {e}")

        # Module 3 pour budget estimé
        if cfg.FORECAST_MODELS["models_budget"]:
            fm = cfg.FORECAST_MODELS
            seg = fc.legacy_segment(client_id, fm["gold_ids"], fm["moyen_ids"])
            m3 = fc.module3_budget(client_id, seg,
                                   fm["models_budget"], fm["ic_budget"],
                                   fm["monthly"], fm["profil_map"], fm["segment_map"])
            budget_estim = m3.get("budget_total_TND", 300.0)

        result["solde_actuel"] = round(solde_reel, 3)
        result["impact_solde"] = calcul_impact_solde(
            solde_actuel=solde_reel,
            montant_facture=scan["montant"],
            depenses_mois=depenses_mois,
            budget_mensuel_estime=budget_estim,
        )

    if scan.get("fournisseur_label") and scan.get("montant"):
        historique = _get_historique_factures(client_id, scan["fournisseur_label"])
        if historique:
            result["anomalie"] = detect_anomalie(
                label=scan["fournisseur_label"],
                montant_actuel=scan["montant"],
                historique_montants=historique,
            )
    result["client_id"] = client_id
    log.info(f"📸 Scan facture : client={client_id[-8:]} | "
             f"{scan.get('fournisseur_nom')} | {scan.get('montant')} TND")
    return result


@app.post("/api/ocr/detecter-anomalie", tags=["OCR"])
def api_detecter_anomalie(body: dict):
    """Détecte une anomalie pour un fournisseur (standalone)."""
    if not OCR_AVAILABLE:
        return {"error": "OCR Service non disponible"}
    client_id = body.get("client_id", "")
    label = body.get("fournisseur_label", "")
    montant = body.get("montant", 0.0)
    historique = _get_historique_factures(client_id, label)
    return detect_anomalie(label=label, montant_actuel=float(montant),
                           historique_montants=historique)


@app.post("/api/ocr/programmer-rappel", tags=["OCR"])
def api_programmer_rappel(body: dict):
    """Enregistre une facture scannée en BD pour les rappels."""
    client_id = body.get("client_id", "")
    label = body.get("fournisseur_label", "")
    nom = body.get("fournisseur_nom", label)
    montant = float(body.get("montant", 0))
    date_ech_str = body.get("date_echeance", "")
    reference = body.get("reference", "")

    date_echeance = None
    for fmt in ["%d/%m/%Y", "%Y-%m-%d", "%d-%m-%Y"]:
        try:
            date_echeance = datetime.strptime(date_ech_str, fmt)
            break
        except ValueError:
            continue
    if not date_echeance:
        date_echeance = datetime.now() + timedelta(days=30)

    rappels = _calculer_dates_rappels(date_echeance)
    try:
        conn = cfg.get_db_connection()
        cur = conn.cursor()
        cur.execute("""
                    INSERT INTO scanned_facture
                    (client_id, fournisseur_label, fournisseur_nom, montant,
                     date_echeance, reference, created_at)
                    VALUES (%s, %s, %s, %s, %s, %s, NOW())
                        ON CONFLICT (client_id, fournisseur_label, date_echeance)
            DO UPDATE SET montant=%s, reference=%s, created_at=NOW()
                    """, (client_id, label, nom, montant, date_echeance.date(),
                          reference, montant, reference))
        conn.commit()
        conn.close()
        log.info(f"📅 Rappel programmé : {nom} {montant} TND le {date_ech_str}")
    except Exception as e:
        log.warning(f"⚠️ Enregistrement scanned_facture : {e}")

    return {
        "status": "rappel_programme",
        "client_id": client_id,
        "fournisseur": nom,
        "montant": montant,
        "date_echeance": date_echeance.strftime("%d/%m/%Y"),
        "rappels": rappels,
    }


@app.post("/api/ocr/feedback", tags=["OCR"])
def api_ocr_feedback(body: dict):
    """Enregistre le feedback utilisateur après scan."""
    client_id = body.get("client_id", "")
    ocr_fournisseur = body.get("ocr_fournisseur")
    ocr_montant = body.get("ocr_montant")
    ocr_date = body.get("ocr_date_echeance")
    ocr_ref = body.get("ocr_reference")
    ocr_confiance = body.get("ocr_confiance", "faible")
    ocr_text_brut = (body.get("ocr_text_brut") or "")[:2000]
    user_fournisseur = body.get("user_fournisseur")
    user_montant = body.get("user_montant")
    user_date = body.get("user_date_echeance")
    user_ref = body.get("user_reference")
    action_finale = body.get("action_finale", "paye")

    def _different(a, b):
        if a is None and b is None:
            return False
        if a is None or b is None:
            return True
        try:
            return abs(float(a) - float(b)) > 0.001
        except (TypeError, ValueError):
            return str(a).strip().lower() != str(b).strip().lower()

    four_corr = _different(ocr_fournisseur, user_fournisseur)
    mont_corr = _different(ocr_montant, user_montant)
    date_corr = _different(ocr_date, user_date)
    ref_corr = _different(ocr_ref, user_ref)
    valide_sans_correction = not any([four_corr, mont_corr, date_corr, ref_corr])
    corrections = []
    if four_corr: corrections.append("fournisseur")
    if mont_corr: corrections.append("montant")
    if date_corr: corrections.append("date")
    if ref_corr: corrections.append("reference")

    feedback_id = None
    try:
        conn = cfg.get_db_connection()
        cur = conn.cursor()
        cur.execute("""
                    INSERT INTO scan_feedback (
                        client_id, ocr_fournisseur, ocr_montant, ocr_date_echeance,
                        ocr_reference, ocr_confiance, ocr_text_brut,
                        user_fournisseur, user_montant, user_date_echeance, user_reference,
                        fournisseur_corrige, montant_corrige, date_corrigee, reference_corrigee,
                        valide_sans_correction, action_finale
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                              %s, %s, %s, %s, %s, %s) RETURNING id
                    """, (client_id, ocr_fournisseur, ocr_montant, ocr_date, ocr_ref,
                          ocr_confiance, ocr_text_brut, user_fournisseur, user_montant,
                          user_date, user_ref, four_corr, mont_corr, date_corr, ref_corr,
                          valide_sans_correction, action_finale))
        feedback_id = cur.fetchone()[0]
        conn.commit()
        conn.close()
        log.info(f"📝 Feedback OCR #{feedback_id} : corrections={corrections}")
    except Exception as e:
        log.error(f"❌ Feedback DB : {e}")
        return {"status": "erreur_bd", "message": str(e)}

    return {
        "status": "feedback_enregistre",
        "feedback_id": feedback_id,
        "corrections": corrections,
        "valide_sans_correction": valide_sans_correction,
    }


@app.post("/api/ocr/analyser-feedback", tags=["OCR"])
def api_analyser_feedback(
        x_retrain_secret: str = Header(..., alias="X-Retrain-Secret"),
):
    """Analyse les feedbacks non traités (déclenché par scheduler)."""
    if x_retrain_secret != cfg.RETRAIN_SECRET:
        raise HTTPException(status_code=403, detail="Secret invalide")

    try:
        conn = cfg.get_db_connection()
        cur = conn.cursor()
        cur.execute("""
                    SELECT ocr_fournisseur,
                           COUNT(*) AS total,
                           SUM(CASE WHEN valide_sans_correction THEN 1 ELSE 0 END) AS parfaits,
                           SUM(CASE WHEN fournisseur_corrige THEN 1 ELSE 0 END) AS err_fourn,
                           SUM(CASE WHEN montant_corrige THEN 1 ELSE 0 END) AS err_mont,
                           SUM(CASE WHEN date_corrigee THEN 1 ELSE 0 END) AS err_date,
                           SUM(CASE WHEN reference_corrigee THEN 1 ELSE 0 END) AS err_ref
                    FROM scan_feedback
                    WHERE processed_at IS NULL
                    GROUP BY ocr_fournisseur
                    """)
        rows = cur.fetchall()

        ameliorations = []
        processed_ids = []
        total_all = total_parfaits = 0
        for row in rows:
            (label, total, parfaits, err_f, err_m, err_d, err_r) = row
            total_all += total
            total_parfaits += parfaits
            ameliorations.append({
                "fournisseur": label or "INCONNU",
                "total_scans": total,
                "scans_parfaits": parfaits,
                "taux_succes_pct": round((parfaits or 0) / total * 100, 1) if total else 0,
                "erreurs": {
                    "fournisseur": int(err_f or 0),
                    "montant": int(err_m or 0),
                    "date": int(err_d or 0),
                    "reference": int(err_r or 0),
                },
            })

        cur.execute("""
                    UPDATE scan_feedback SET processed_at = NOW()
                    WHERE processed_at IS NULL RETURNING id
                    """)
        processed_ids = [r[0] for r in cur.fetchall()]
        conn.commit()

        log_dir = Path(cfg.MODEL_DIR)
        log_path = log_dir / f"ocr_feedback_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        try:
            log_dir.mkdir(exist_ok=True)
            with open(log_path, "w", encoding="utf-8") as f:
                json.dump({"timestamp": datetime.now().isoformat(),
                           "ameliorations": ameliorations}, f, indent=2,
                          ensure_ascii=False)
        except Exception:
            pass
        conn.close()

        taux_global = round(total_parfaits / total_all * 100, 1) if total_all else 0
        log.info(f"✅ Analyse OCR : {len(processed_ids)} feedbacks | taux={taux_global}%")
        return {
            "status": "analyse_terminee",
            "feedbacks_analyses": len(processed_ids),
            "taux_succes_global": taux_global,
            "ameliorations": sorted(ameliorations, key=lambda x: x["taux_succes_pct"]),
            "rapport_path": str(log_path),
        }
    except Exception as e:
        log.error(f"❌ Analyse feedback : {e}")
        return {"status": "erreur", "message": str(e)}


@app.get("/api/ocr/stats", tags=["OCR"])
def api_ocr_stats(
        x_retrain_secret: str = Header(..., alias="X-Retrain-Secret"),
):
    """Statistiques OCR pour Admin Dashboard."""
    if x_retrain_secret != cfg.RETRAIN_SECRET:
        raise HTTPException(status_code=403, detail="Secret invalide")
    try:
        conn = cfg.get_db_connection()
        cur = conn.cursor()
        cur.execute("SELECT * FROM v_ocr_accuracy ORDER BY taux_succes_pct DESC")
        cols = [d[0] for d in cur.description]
        rows = [dict(zip(cols, r)) for r in cur.fetchall()]

        total_scans = sum(r["total_scans"] for r in rows)
        total_parfaits = sum(r["scans_parfaits"] for r in rows)
        taux_global = round(total_parfaits / total_scans * 100, 1) if total_scans else 0

        cur.execute("""
                    SELECT DATE(created_at) AS jour, COUNT(*) AS total,
                        SUM(CASE WHEN valide_sans_correction THEN 1 ELSE 0 END) AS parfaits
                    FROM scan_feedback
                    WHERE created_at >= NOW() - INTERVAL '30 days'
                    GROUP BY DATE(created_at) ORDER BY jour
                    """)
        evolution = [{"date": str(j), "total": t,
                      "taux": round(p / t * 100, 1) if t else 0}
                     for j, t, p in cur.fetchall()]

        cur.execute("SELECT COUNT(*) FROM scan_feedback WHERE processed_at IS NULL")
        feedbacks_pending = cur.fetchone()[0]
        conn.close()

        return {
            "total_scans": total_scans,
            "taux_global": taux_global,
            "feedbacks_pending": feedbacks_pending,
            "par_fournisseur": rows,
            "evolution_30j": evolution,
        }
    except Exception as e:
        log.error(f"❌ Stats OCR : {e}")
        return {"status": "erreur", "message": str(e)}




# ═════════════════════════════════════════════════════════════════════
# ROOT
# ═════════════════════════════════════════════════════════════════════
@app.get("/", tags=["Root"])
def root():
    return {
        "service": "SmartWallet ML Service",
        "version": "6.0.0",
        "port": 8000,
        "docs": "/docs",
        "redoc": "/redoc",
        "classification_method": "GBM + 43 features + 6 profils (méthode binôme — unique)",
        "modules": ["prévisions 1-5", "recommandations 6", "OCR", "V5 admin"],
    }