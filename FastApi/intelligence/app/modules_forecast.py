"""
══════════════════════════════════════════════════════════════════════
SmartWallet — Modules 1-5 : Prévision Temporelle (INCHANGÉS)
══════════════════════════════════════════════════════════════════════
Ces modules implémentent la logique de prévision existante :
  • Module 1 : Factures récurrentes (XGBoost + saisonnalité)
  • Module 2 : Prochaine recharge téléphonique (règle métier)
  • Module 3 : Budget mensuel par catégorie (XGBoost)
  • Module 4 : Habitudes individuelles (analyse comportementale)
  • Module 5 : Alerte solde / budget
══════════════════════════════════════════════════════════════════════
"""
from __future__ import annotations

import logging
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional

import numpy as np

log = logging.getLogger("smartwallet.forecast")

# ─────────────────────────────────────────────────────────────────────
# Constantes prévision (inchangées)
# ─────────────────────────────────────────────────────────────────────
BUDGET_FEATS = [
    "lag1", "lag2", "lag3", "lag6", "lag12", "mean3", "mean6", "std3", "std6",
    "tendance", "regularite", "month_sin", "month_cos", "trimestre",
    "is_ramadan", "is_aid_fitr", "is_aid_adha", "profil_enc", "segment_enc",
]
BILL_FEATS = [
    "lag1", "lag2", "lag3", "mean3", "std3", "month_sin", "month_cos",
    "nb_paiem", "profil_enc", "segment_enc",
]
CATS_BUDGET = [
    "Recharge Telephonique", "Shopping & Paiements",
    "Restaurants & Livraison", "Voyages & Reservations",
]
RAMADAN = {(2024, 3), (2024, 4), (2025, 3), (2026, 2), (2026, 3)}
AID_FITR = {(2024, 4), (2025, 3), (2026, 3)}
AID_ADHA = {(2024, 6), (2025, 6), (2026, 5)}
SAISON_SON = {1: .84, 2: .87, 3: .92, 4: 1.00, 5: 1.10, 6: 1.22,
              7: 1.32, 8: 1.35, 9: 1.18, 10: 1.06, 11: .96, 12: .88}
SAISON_STG = {1: 1.22, 2: 1.16, 3: 1.10, 4: 1.00, 5: .94, 6: .88,
              7: .84, 8: .84, 9: .90, 10: .96, 11: 1.06, 12: 1.16}


# ─────────────────────────────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────────────────────────────
def current_or_next_month():
    now = datetime.now()
    y, m = now.year, now.month
    if now.day >= 25:
        m += 1
        if m > 12:
            m = 1
            y += 1
    return y, m


def legacy_segment(client_id: str, gold_ids, moyen_ids) -> str:
    """Segment historique Gold/Moyen/Faible pour les features prévision."""
    if gold_ids and client_id in gold_ids:
        return "Gold"
    if moyen_ids and client_id in moyen_ids:
        return "Moyen"
    return "Faible"


def statut_facture(jours: int, label: str = "", client_id: str = "",
                   paid_cache: Optional[Dict[str, List[str]]] = None) -> str:
    if paid_cache and client_id and label and label in paid_cache.get(client_id, []):
        return "payée"
    if jours < -90:
        return "expirée"
    if jours < -7:
        return "en_retard"
    if jours < 0:
        return "due_maintenant"
    if jours <= 7:
        return "urgente"
    if jours <= 30:
        return "prochaine"
    return "planifiée"


def serie_client(client_id: str, cat: str, monthly) -> np.ndarray:
    if monthly is None:
        return np.array([])
    mask = (monthly["client_id"] == client_id) & (monthly["category"] == cat)
    sub = monthly[mask].sort_values("mois_dt")
    return sub["amount"].values if len(sub) > 0 else np.array([])


# ─────────────────────────────────────────────────────────────────────
# Module 1 — Factures
# ─────────────────────────────────────────────────────────────────────
def module1_factures(
    client_id: str,
    factures_ref: Optional[Dict],
    models_bill: Optional[Dict],
    results_bill: Optional[Dict],
    profil_map: Optional[Dict],
    segment_map: Optional[Dict],
    paid_cache: Dict[str, List[str]],
) -> Dict[str, Any]:
    now = datetime.now()
    y_cible, m_cible = current_or_next_month()
    result = []
    paid_factures = paid_cache.get(client_id, [])

    if not factures_ref:
        return {"client_id": client_id, "nb_factures": 0, "factures": [], "total_a_venir_TND": 0.0}

    for (cid, tid), ref in factures_ref.items():
        if cid != client_id:
            continue
        label = ref["label"]
        if label in paid_factures:
            continue

        intervalle = int(ref["intervalle_median"])
        derniere_date = ref["derniere_date"]
        date_theorique = derniere_date + timedelta(days=intervalle)
        jours_depuis_theorique = (now - date_theorique).days

        if jours_depuis_theorique > intervalle * 2:
            date_prevue = now + timedelta(days=intervalle)
            jours_restants = intervalle
            statut = "planifiée"
        else:
            date_prevue = date_theorique
            jours_restants = int((date_prevue - now).days)
            statut = statut_facture(jours_restants, label=label,
                                    client_id=client_id, paid_cache=paid_cache)

        if statut in ("payée", "expirée"):
            continue

        r2 = float((results_bill or {}).get(label, {}).get("r2", -1))
        if models_bill and label in models_bill and r2 >= 0.5:
            m = int(date_prevue.month)
            pe = profil_map.get(client_id, 1) if profil_map else 1
            se = segment_map.get(client_id, 1) if segment_map else 1
            med = (results_bill or {}).get(label, {}).get("medians", {})
            feat_dict = {
                "lag1": ref.get("derniere_amount", ref["montant_moyen"]),
                "lag2": ref["montant_moyen"],
                "lag3": ref["montant_moyen"],
                "mean3": ref["montant_moyen"],
                "std3": ref["montant_cv"] * ref["montant_moyen"],
                "month_sin": np.sin(2 * np.pi * m / 12),
                "month_cos": np.cos(2 * np.pi * m / 12),
                "nb_paiem": ref.get("n_paiements", 5),
                "profil_enc": pe,
                "segment_enc": se,
            }
            feat = np.array([[feat_dict.get(f, float(med.get(f, 0))) for f in BILL_FEATS]])
            mt = float(max(0, models_bill[label].predict(feat)[0]))
            mt = max(mt, ref["montant_moyen"] * 0.5)
        else:
            mt = ref["montant_moyen"]
            lm = int(ref["derniere_date"].month)
            if label == "SONEDE":
                mt = mt / SAISON_SON.get(lm, 1.0) * SAISON_SON.get(m_cible, 1.0)
            elif label == "STEG":
                mt = mt / SAISON_STG.get(lm, 1.0) * SAISON_STG.get(m_cible, 1.0)

        confiance = "Haute" if r2 >= 0.95 else ("Moyenne" if r2 >= 0.60 else "Faible")
        mae = (results_bill or {}).get(label, {}).get("mae", mt * 0.15)
        result.append({
            "fournisseur": ref.get("title", label),
            "label": label,
            "montant_prevu": round(mt, 2),
            "ic_bas": round(max(0.0, mt - mae), 2),
            "ic_haut": round(mt + mae, 2),
            "date_prevue": str(date_prevue.date()),
            "jours_restants": jours_restants,
            "statut": statut,
            "confiance": confiance,
            "r2_modele": round(r2, 4),
            "intervalle_median": intervalle,
            "couche": 1,
        })

    result.sort(key=lambda x: x["jours_restants"])
    total = round(sum(f["montant_prevu"] for f in result if f["jours_restants"] >= 0), 2)
    return {
        "client_id": client_id,
        "nb_factures": len(result),
        "factures": result,
        "total_a_venir_TND": total,
    }


# ─────────────────────────────────────────────────────────────────────
# Module 2 — Recharge
# ─────────────────────────────────────────────────────────────────────
def module2_recharge(client_id: str, recharges_ref: Optional[Dict]) -> Dict[str, Any]:
    if not recharges_ref or client_id not in recharges_ref:
        return {"client_id": client_id, "erreur": "Pas assez d'historique de recharge"}

    now = datetime.now()
    ref = recharges_ref[client_id]
    intervalle = int(ref["intervalle_median"])
    date_theorique = ref["derniere_date"] + timedelta(days=intervalle)
    jours_depuis_theorique = (now - date_theorique).days

    if jours_depuis_theorique > intervalle * 2:
        date_prevue = now + timedelta(days=intervalle)
        jours_restants = intervalle
    else:
        date_prevue = date_theorique
        jours_restants = int((date_prevue - now).days)

    if jours_restants < 0:
        statut = "en retard"
    elif jours_restants <= 3:
        statut = "imminent"
    elif jours_restants <= 7:
        statut = "prochaine"
    else:
        statut = "planifiée"

    cv_int = ref.get("intervalle_std", 5) / max(intervalle, 1)
    fiabilite = "Haute" if cv_int < 0.3 else ("Moyenne" if cv_int < 0.6 else "Faible")
    return {
        "client_id": client_id,
        "operateur": ref["operateur"],
        "montant_habituel": ref["montant_habituel"],
        "intervalle_median": intervalle,
        "date_prevue": str(date_prevue.date()),
        "jours_restants": jours_restants,
        "statut": statut,
        "fiabilite": fiabilite,
        "n_historique": ref.get("n_recharges", 0),
    }


# ─────────────────────────────────────────────────────────────────────
# Module 3 — Budget
# ─────────────────────────────────────────────────────────────────────
def module3_budget(
    client_id: str,
    seg: str,
    models_budget: Optional[Dict],
    ic_budget: Optional[Dict],
    monthly,
    profil_map: Optional[Dict],
    segment_map: Optional[Dict],
) -> Dict[str, Any]:
    y_next, m_next = current_or_next_month()
    pe = profil_map.get(client_id, 1) if profil_map else 1
    se = segment_map.get(client_id, 1) if segment_map else 1
    par_cat, total = {}, 0.0

    for cat in CATS_BUDGET:
        if not models_budget or cat not in models_budget:
            par_cat[cat] = {"predit": 0, "ic_bas": 0, "ic_haut": 0, "confiance": "Insuffisant"}
            continue

        vals = serie_client(client_id, cat, monthly)
        n = len(vals)
        if n < 6 or seg == "Faible":
            if n >= 3:
                moy = float(np.mean(vals[-3:]))
                ecart = float(np.std(vals[-3:])) + 10
                par_cat[cat] = {
                    "predit": round(moy, 2),
                    "ic_bas": round(max(0.0, moy - ecart), 2),
                    "ic_haut": round(moy + ecart, 2),
                    "confiance": "Faible",
                }
                total += moy
            else:
                par_cat[cat] = {"predit": 0, "ic_bas": 0, "ic_haut": 0, "confiance": "Insuffisant"}
            continue

        h6, h3 = vals[-6:], vals[-3:]
        feat_dict = {
            "lag1": float(vals[-1]),
            "lag2": float(vals[-2]) if n >= 2 else 0,
            "lag3": float(vals[-3]) if n >= 3 else 0,
            "lag6": float(vals[-6]) if n >= 6 else 0,
            "lag12": float(vals[-12]) if n >= 12 else float(np.mean(h6)),
            "mean3": float(np.mean(h3)),
            "mean6": float(np.mean(h6)),
            "std3": float(np.std(h3)) if n >= 3 else 0,
            "std6": float(np.std(h6)) if n >= 6 else 0,
            "tendance": float(np.polyfit(np.arange(len(h3)), h3, 1)[0]) if np.any(h3 > 0) else 0,
            "regularite": float(np.mean(h6 > 0)),
            "month_sin": float(np.sin(2 * np.pi * m_next / 12)),
            "month_cos": float(np.cos(2 * np.pi * m_next / 12)),
            "trimestre": int((m_next - 1) // 3 + 1),
            "is_ramadan": float((y_next, m_next) in RAMADAN),
            "is_aid_fitr": float((y_next, m_next) in AID_FITR),
            "is_aid_adha": float((y_next, m_next) in AID_ADHA),
            "profil_enc": float(pe),
            "segment_enc": float(se),
        }
        feat = np.array([[feat_dict.get(f, 0.0) for f in BUDGET_FEATS]])
        predit = float(max(0.0, models_budget[cat].predict(feat)[0]))
        ic_hw = float(ic_budget.get(cat, {}).get(client_id, predit * 0.20)) if ic_budget else predit * 0.20
        par_cat[cat] = {
            "predit": round(predit, 2),
            "ic_bas": round(max(0.0, predit - ic_hw), 2),
            "ic_haut": round(predit + ic_hw, 2),
            "confiance": "Haute" if seg == "Gold" else "Moyenne",
        }
        total += predit

    return {
        "client_id": client_id,
        "segment": seg,
        "mois_prevu": f"{y_next}-{m_next:02d}",
        "budget_total_TND": round(min(total, 600.0), 2),
        "par_categorie": par_cat,
    }


# ─────────────────────────────────────────────────────────────────────
# Module 4 — Prochaine transaction
# ─────────────────────────────────────────────────────────────────────
def module4_prochaine_tx(
    client_id: str,
    habitudes_cli: Optional[Dict],
    recharges_ref: Optional[Dict],
) -> Dict[str, Any]:
    if not habitudes_cli or client_id not in habitudes_cli:
        return {"client_id": client_id, "erreur": "Historique insuffisant"}

    now = datetime.now()
    hab = habitudes_cli[client_id]
    jours_depuis = int(hab.get("jours_depuis", 0))
    delai = max(1, int(hab["int_median"]) - jours_depuis)
    total_tx = sum(hab["top3_cats"].values())
    top3 = []
    for j, (cat, count) in enumerate(hab["top3_cats"].items()):
        prob = count / total_tx if total_tx > 0 else 0
        top3.append({
            "categorie": cat,
            "probabilite": round(float(prob), 3),
            "dans_jours": delai + j,
        })

    if recharges_ref and client_id in recharges_ref:
        rech = recharges_ref[client_id]
        date_rech = rech["derniere_date"] + timedelta(days=rech["intervalle_median"])
        jr_rech = int((date_rech - now).days)
        if jr_rech <= 0:
            for t in top3:
                if "Recharge" in t["categorie"]:
                    t["probabilite"] = min(t["probabilite"] * 1.5, 0.95)
                    t["dans_jours"] = max(1, jr_rech + rech["intervalle_median"])

    cv_int = hab.get("int_std", 3) / max(hab["int_median"], 1)
    confiance = "Haute" if cv_int < 0.35 else ("Moyenne" if cv_int < 0.65 else "Faible")
    return {
        "client_id": client_id,
        "cat_dominante": hab["cat_dominante"],
        "delai_estime_jours": delai,
        "jours_depuis_tx": int(hab.get("jours_depuis", 0)),
        "confiance": confiance,
        "top3": top3,
    }


# ─────────────────────────────────────────────────────────────────────
# Module 5 — Alerte
# ─────────────────────────────────────────────────────────────────────
def module5_alerte(
    client_id: str,
    solde: float,
    m1: Dict,
    m2: Dict,
    m3: Dict,
) -> Dict[str, Any]:
    urgentes = [f for f in m1.get("factures", []) if f.get("statut") in ("urgente", "due_maintenant")]
    total_urgent = sum(f.get("montant_prevu", 0) for f in urgentes)
    rech_urgente = 0.0
    if "erreur" not in m2 and m2.get("jours_restants", 99) <= 5:
        rech_urgente = m2.get("montant_habituel", 0.0)
        total_urgent += rech_urgente
    budget_var = m3.get("budget_total_TND", 0.0)
    total_mois = round(total_urgent + budget_var, 2)
    solde_apres = round(solde - total_mois, 2)

    if solde < total_urgent:
        niveau, message = "CRITIQUE", f"Solde insuffisant ({total_urgent:.0f} TND requis)"
        reco = f"Rechargez au moins {total_urgent - solde + 20:.0f} TND"
    elif solde < total_mois or solde_apres < 30:
        niveau, message = "ATTENTION", "Solde faible en fin de mois"
        reco = f"Rechargez au moins {max(0, 50 - solde_apres):.0f} TND"
    else:
        niveau, message, reco = "OK", "Solde suffisant", ""

    return {
        "client_id": client_id,
        "solde_actuel_TND": round(solde, 2),
        "total_urgent_TND": round(total_urgent, 2),
        "budget_variable_TND": round(budget_var, 2),
        "total_prevu_TND": total_mois,
        "solde_apres_TND": solde_apres,
        "niveau_alerte": niveau,
        "nb_urgents": len(urgentes),
        "message": message,
        "recommandation": reco,
    }
