# SmartWallet IZI Pay — Refonte Recommandations Marketing

## 🎯 Objectif de cette livraison

Refondre les recommandations en **2 systèmes séparés et clairement définis** :

1. **Recommandations personnalisées (module 6)** — peer-comparison existant,
   **conservé tel quel** (les conseils financiers individuels du module 6).

2. **Recommandations marketing (refonte complète)** — système refondé :
   - L'admin génère des offres puis approuve des recommandations marketing par profil
   - Envoi par **FCM topic** (`profile_{clusterId}`) → 1 publication = N destinataires (FCM free tier)
   - L'utilisateur **approuve/rejette** depuis le mobile
   - **Approbation = application RÉELLE en base** (exonération de frais effective sur les transactions, etc.)
   - Le feedback alimente l'**auto-apprentissage** : taux d'acceptation observé → re-pondération des offres

3. **Suppression totale** de l'ancien système gamification/récompenses (points, niveaux, badges, défis monétisés).

---

## 🏗️ Architecture du flux complet

```
┌─────────────┐      ┌──────────────────┐      ┌──────────────┐      ┌──────────────┐
│  Admin      │─────▶│  Spring Boot     │─────▶│  FCM topic   │─────▶│  Flutter     │
│  Angular    │      │  /send-to-profile│      │  profile_3   │      │  N devices   │
└─────────────┘      └──────────────────┘      └──────────────┘      └──────┬───────┘
                                                                            │
                                                                  Approve / Reject
                                                                            │
                                                                            ▼
                                                          ┌──────────────────────────┐
                                                          │ OfferApplicationService  │
                                                          │  .apply()                │
                                                          │  → active_offer_         │
                                                          │     application          │
                                                          │     (DB row ACTIVE)      │
                                                          └────────────┬─────────────┘
                                                                       │
                                                                       │ consulté à chaque tx
                                                                       ▼
                                                          ┌──────────────────────────┐
                                                          │ TransactionController    │
                                                          │  .hasFeeWaiver()         │
                                                          │  → exonération RÉELLE    │
                                                          └──────────────────────────┘

           (toutes les heures)
                  │
                  ▼
┌──────────────────────────────┐   ┌─────────────────────────┐
│ MarketingFeedbackScheduler   │──▶│ FastAPI                 │
│  → POST /marketing-feedback  │   │  /marketing-feedback/   │
│    /batch                    │   │     batch + retrain     │
└──────────────────────────────┘   │  → re-pondère           │
                                   │     generated_offers.   │
                                   │     boost               │
                                   └─────────────────────────┘
```

---

## 📦 Contenu de cette livraison

### `intelligence/` — FastAPI (3 fichiers patchés)

| Fichier | Changement |
|---|---|
| `app/main.py` | Retrait du hook `_notify_challenge_completed`. Ajout des endpoints `/marketing-feedback/batch`, `/marketing-feedback/stats`, `/marketing-feedback/retrain`. Buffer en mémoire + log append-only des feedbacks. |
| `app/module6_recos.py` | Neutralisation du bloc gamification dans le payload (`challenges = []`, `gamification = {}`). Le moteur peer-comparison reste intact. |
| `app/recommendation_system.py` | Nouvelle fonction `apply_feedback_reweighting()` : re-pondère `generated_offers.boost` selon les taux d'acceptation observés (lissage exponentiel 70/30, seuil min 3 signaux). |

### `clientdashboard/` — Spring Boot (14 fichiers)

#### ★ NEUFS
- `recommendation/entities/ActiveOfferApplication.java` — entité qui matérialise l'effet réel d'une offre approuvée
- `recommendation/repositories/ActiveOfferApplicationRepository.java`
- `recommendation/services/OfferApplicationService.java` — `apply(clientReco)` + `hasFeeWaiver()` + sweeper EXPIRED
- `recommendation/dtos/SendOfferToProfileRequestDTO.java`
- `recommendation/dtos/SendOfferToProfileResultDTO.java`
- `config/MarketingFeedbackScheduler.java` — push horaire des interactions vers FastAPI

#### MODIFIÉS
- `notification/FcmService.java` — ajout `sendToTopic()`, `subscribeTokensToTopic()`, `subscribeClientToProfile()`
- `recommendation/services/OfferNotificationService.java` — méthode `sendOfferToProfile()` + hook `OfferApplicationService.apply()` dans `respondToOffer`
- `recommendation/services/InteractionServiceImpl.java` — hook `OfferApplicationService.apply()` dans `recordInteraction`
- `recommendation/controller/RecommendationController.java` — endpoint `POST /api/recommendations/{id}/send-to-profile`
- `controller/NotificationController.java` — auto-subscribe au topic `profile_{clusterId}` à l'enregistrement du token FCM
- `controller/AdminController.java` — purge des endpoints rewards/challenges, remplacés par `/marketing-feedback/{push,retrain,stats}`
- `controller/TransactionController.java` — `hasActiveReductionFrais()` (qui lisait `ClientReward`) → `offerApplicationService.hasFeeWaiver()`
- `controller/RecommendationController.java` (legacy) — nettoyage du commentaire mensonger « v9 avec gamification »

### `app/` — Angular Admin (5 fichiers patchés)

- `ia-admin/ia-admin.module.ts` — `RewardsComponent` retiré
- `ia-admin/ia-admin.routing.ts` — route `rewards` retirée
- `services/ia-admin/ia-admin.service.ts` — 5 méthodes rewards retirées, 3 méthodes marketing-feedback ajoutées
- `layout/header/header.component.html` — lien "Récompenses" retiré
- `services/recommendation/recommendation/recommendation.service.ts` — `sendToProfile()` + `sendToClient()`
- `recommendations/recommendationlist/recommendationlist.component.{ts,html}` — bouton "Envoyer au profil" (visible sur recos APPROVED)

### `lib/` — Flutter (4 fichiers patchés)

- `services/notification_service.dart` — `registerTokenAndSubscribe()`, `subscribeToProfileTopic()`, `unsubscribeFromProfileTopic()`
- `services/reco_provider.dart` — getters gamification supprimés (`points`, `niveauNom`, `niveauEmoji`, `niveauProgres`, `prochainNiveau`, `defisCompletes`)
- `screens/profile_screen.dart` — section "Mes récompenses" supprimée
- `screens/recommendation_screen.dart` — onglet `_RewardsTab` supprimé (4 tabs → 3), carte niveau supprimée de l'onglet Défis, badge points supprimé des cartes défi

---

## 🚀 Déploiement

### 1. Copier les fichiers
```bash
# Copier les fichiers de outputs/ dans ton repo en préservant l'arborescence.
# (Si tu utilises rsync, attention à ne PAS supprimer ce qui existe déjà.)
rsync -av /mnt/user-data/outputs/intelligence/      ton-repo/intelligence/
rsync -av /mnt/user-data/outputs/clientdashboard/   ton-repo/services/client-dashboard/src/main/java/com/pfe/clientdashboard/
rsync -av /mnt/user-data/outputs/app/               ton-repo/frontend-admin/src/app/
rsync -av /mnt/user-data/outputs/lib/               ton-repo/mobile/lib/
```

### 2. Exécuter la purge
```bash
cp /mnt/user-data/outputs/patch.sh ton-repo/
cd ton-repo
# Ajuste les variables d'environnement si nécessaire :
SPRING_SRC="services/client-dashboard/src/main/java/com/pfe/clientdashboard" \
ANGULAR_SRC="frontend-admin/src/app" \
FLUTTER_SRC="mobile/lib" \
./patch.sh
```

### 3. Migration BDD
```bash
psql -U $DB_USER -d $DB_NAME -f /mnt/user-data/outputs/SQL_MIGRATION.sql
```
> Le `DROP TABLE reward` est commenté par sécurité — décommente quand tu es sûr.

### 4. Rebuild
```bash
# Spring
cd services/client-dashboard && mvn clean package

# Angular
cd frontend-admin && ng build

# Flutter
cd mobile && flutter pub get && flutter run
```

### 5. Configuration FCM
Dans `application.yml` côté Spring, vérifier :
```yaml
firebase:
  credentials-path: /chemin/vers/serviceAccountKey.json
  project-id: smartwallet-pfe
```
Les topics `profile_0`, `profile_1`, etc. sont créés **automatiquement** à la première publication / abonnement — pas de config additionnelle.

---

## ⚠️ Points d'attention

### Compatibilité ascendante
- Le payload `module6` continue d'inclure les clés `challenges` et `gamification`, mais **vides** (`[]` et `{}`). Aucun client cassé.
- Les anciens `ClientReward` / `Reward` en BDD sont conservés tant que tu n'as pas exécuté les `DROP TABLE` du SQL. Pas d'impact runtime — les services qui les lisaient ont été supprimés.

### Idempotence
- `OfferApplicationService.apply()` est **idempotent** : un client qui clique 2× sur "Approuver" la même offre dans le même mois ne déclenche qu'**une** application (clé `clientId|offerCode|YYYY-MM`).
- Le `MarketingFeedbackScheduler` maintient un curseur en mémoire — pas de doublon mais en cas de redémarrage Spring, le curseur revient à J-7 (rattrapage non-destructif côté FastAPI qui dédup en pratique via le log append-only).

### Quotas FCM
La version gratuite supporte :
- **publication topic** illimitée (1 publi = N destinataires)
- **2000 abonnements par device** à des topics — largement au-delà de nos besoins (un device = 1 ou 2 topics : `admin` + `profile_X`)

### Module 6 (recommandations personnalisées)
**Aucun changement** sur le moteur peer-comparison. Seul le payload de retour a été nettoyé pour ne plus inclure les blocs gamification. Les conseils financiers, alertes Z-score, budget targets et challenges (sans points) restent disponibles côté mobile.

---

## 📞 Endpoints exposés (récapitulatif)

### Spring Boot (port 8090)
```
POST   /api/recommendations/{id}/send-to-client     Envoi FCM individuel (existant)
POST   /api/recommendations/{id}/send-to-profile    ★ NOUVEAU — diffusion topic FCM
POST   /api/notifications/register-token            Auto-subscribe au topic profil
POST   /api/ia/admin/marketing-feedback/push        Push manuel batch → FastAPI
POST   /api/ia/admin/marketing-feedback/retrain     Déclenche retrain FastAPI
GET    /api/ia/admin/marketing-feedback/stats       Stats acceptation par offre/profil
GET    /api/ia/recommendations/{clientId}           Module 6 (inchangé)
```

### FastAPI (port 8000)
```
POST   /marketing-feedback/batch                    Reçoit le batch depuis Spring
GET    /marketing-feedback/stats                    Stats par offre / profil
POST   /marketing-feedback/retrain                  Re-pondère generated_offers.boost
GET    /recommendations/{client_id}                 Module 6 (gamif retirée)
```

---

## 🧪 Sanity checks après déploiement

1. **Spring démarre sans conflit de mapping** (auparavant `ClientRecommendationController` + `RecommendationController` à la racine entraient en collision sur `/api/ia/recommendations/{clientId}`)
2. **Le client peut créer une transaction** → si une offre FEE_WAIVER est ACTIVE, les frais sont effectivement remboursés
3. **L'admin clique "Envoyer au profil 3"** → tous les devices abonnés à `profile_3` reçoivent la notif en 1 publi
4. **L'utilisateur clique "Approuver"** → une ligne ACTIVE apparaît dans `active_offer_application`, et la prochaine tx du même mois ne facture plus de frais
5. **Au bout d'une heure** → `MarketingFeedbackScheduler` pousse le batch vers FastAPI ; `GET /marketing-feedback/stats` renvoie les taux observés
6. **L'admin clique "Retrain"** → les boosts de `generated_offers` sont ajustés selon les taux d'acceptation

---

✅ **Refonte terminée.** Le système gamification est entièrement remplacé par un mécanisme d'incitation **mesurable et réellement appliqué en base**, avec une boucle d'auto-apprentissage fermée.
