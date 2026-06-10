# 📦 SmartWallet — Dossier `models/` (29 PKL + 4 JSON)

Tous les fichiers nécessaires au service IA unifié (port 8000, base `client_bd`).

## 🎯 Classification (méthode binôme — UNIQUE)

| Fichier | Taille | Description |
|---|---|---|
| `classifier.pkl` | 3.2 M | **GradientBoostingClassifier** — modèle principal de segmentation (6 profils) |
| `scaler.pkl` | 2.2 K | **RobustScaler** sur les 43 features |
| `features.pkl` | 774 B | Liste ordonnée des 43 noms de features |
| `kmeans.pkl` | 20 K | Centroides KMeans (référence — non utilisé en prédiction) |
| `umap.pkl` | 7.0 M | Réducteur UMAP (utilisé au retrain seulement) |

Profils GBM (cluster_id) :
- 0 : Micro-Utilisateur Passif
- 1 : Utilisateur Essentiel Stable
- 2 : Payeur Factures Premium
- 3 : Client Grande Dépense
- 4 : Client en Accélération Récente
- 5 : Client en Croissance Digitale
- -1 : Profil Mixte (confidence < 0.6)

## 📈 Modules 1-5 (Prévision temporelle)

| Fichier | Taille | Module | Description |
|---|---|---|---|
| `models_bill.pkl` | 3.6 M | M1 | XGBoost par fournisseur (STEG/SONEDE/TOPNET/BEE/TT/OOREDOO) |
| `results_bill.pkl` | 6.4 K | M1 | Métriques (R², MAE, RMSE) par fournisseur |
| `factures_ref.pkl` | 1.8 M | M1 | Référence factures par client (15 123 entrées) |
| `recharges_ref.pkl` | 824 K | M2 | Référence recharges par client (5 206 clients) |
| `models_budget.pkl` | 4.0 M | M3 | XGBoost par catégorie de dépense (4 catégories) |
| `ic_budget.pkl` | 870 K | M3 | Intervalles de confiance budget |
| `monthly.pkl` | 20 M | M3 | Séries mensuelles par client/catégorie |
| `habitudes_cli.pkl` | 1.1 M | M4 | Habitudes par client (5 315 clients) |
| `profil_map.pkl` | 213 K | M1+M3 | Encoding profil (feature `profil_enc`) |
| `segment_map.pkl` | 213 K | M1+M3 | Encoding segment (feature `segment_enc`) |
| `gold_ids.pkl` | 101 K | M3 | Liste clients Gold (2 634) |
| `moyen_ids.pkl` | 65 K | M3 | Liste clients Moyen (1 692) |

> ⚠️ Les segments Gold/Moyen/Faible sont conservés pour les **features** des modèles
> prévision (`segment_enc`). Pour la **segmentation client** (profil GBM), utiliser
> uniquement `app/classification.py`.

## 🎯 Module 6 (Recommandations) — caches générés par retrain.py

| Fichier | Taille | Description |
|---|---|---|
| `reco_features.pkl` | 670 K | Features par client + `cluster_id` GBM (5 315 clients) |
| `reco_profile_medians.pkl` ★ | 1.5 K | Médianes des ratios par profil GBM (6 profils) |
| `reco_profile_p25.pkl` ★ | 1.3 K | P25 des ratios par profil GBM |
| `reco_cluster_medians.pkl` | 2.3 K | Alias DataFrame (rétro-compat `recommendation_system`) |
| `reco_cluster_p25.pkl` | 2.1 K | Alias DataFrame |
| `reco_cluster_names.pkl` | 205 B | Mapping cluster_id → nom de profil |
| `reco_scaler.pkl` | 2.2 K | RobustScaler 43 features (= scaler.pkl) |
| `reco_budget_optim.pkl` | 214 K | Suggestions forfait télécom (1 743 clients) |
| `reco_budget_targets.pkl` | 1.4 M | Cibles budget par catégorie/profil |
| `reco_alerts.pkl` | 340 K | Alertes Z-score par client (1 992 clients) |
| `reco_recommendations.pkl` | 1.4 M | Recommandations pré-calculées par client |
| `reco_iso_params.pkl` | 5 B | Paramètres Isolation Forest (vide actuellement) |

★ = **Nouveaux noms** utilisés par `module6_recos.py` (peer comparison sur profil GBM)

## 📄 Métadonnées JSON

| Fichier | Description |
|---|---|
| `reco_meta.json` | Méta-info Module 6 (silhouette, méthode utilisée) |
| `retrain_history.json` | Historique des 50 derniers retrains |
| `retrain_meta.json` | Dernier retrain (timestamp, liste des PKL, etc.) |
| `ocr_patterns_log.json` | Patterns OCR appris (feedback loop) |

## 🔄 Régénération

Pour régénérer tous ces PKL depuis la base PostgreSQL :

```bash
python retrain.py --db-url postgresql://postgres:PASS@localhost:5432/client_bd \
                  --model-dir ./models

# OU via API
curl -X POST http://localhost:8000/retrain \
  -H "X-Retrain-Secret: smartwallet-retrain-2026"
```

Pour régénérer uniquement la classification GBM (modèles binôme) :

```bash
python -m app.wallet_classification --retrain
# OU
curl -X POST http://localhost:8000/admin/retrain \
  -H "Content-Type: application/json" \
  -d '{"admin_user":"admin"}'
```

## ✅ Compatibilité

- Tous les `.pkl` sont sérialisés avec `joblib` (Python 3.10+)
- Versions ML utilisées : scikit-learn 1.5.x, xgboost 2.1.x, numpy 1.26.x
- Pour charger les modèles XGBoost, installer : `pip install xgboost==2.1.1`
