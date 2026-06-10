# SmartWallet IZI Pay

> Portefeuille mobile financier enrichi par l'intelligence artificielle, développé dans le cadre d'un Projet de Fin d'Études (PFE) pour **EXCELLIA Solutions**, sur l'infrastructure de paiement **Zitouna Payment**.

SmartWallet IZI Pay permet à l'utilisateur de gérer son portefeuille (solde, paiements, factures) tout en bénéficiant de fonctions d'IA : **prévisions** de dépenses et de factures, **recommandations personnalisées**, **détection d'anomalies**, **scan de factures par OCR** et un **assistant de synthèse**.

---

## Architecture

Le système suit une architecture **multi-couches + microservices** :

- **Présentation** — application mobile (Flutter) pour le client, et tableau de bord d'administration (Angular).
- **Médiation** — une *API Gateway* (point d'entrée unique, routage, sécurité).
- **Services métier** — microservices Spring Boot (clients, transactions, notifications, offres).
- **Moteur d'IA** — service FastAPI (Python) isolé, hébergeant les modules prédictifs et l'OCR.
- **Persistance** — base PostgreSQL.
- **Transverses** — Keycloak (authentification OAuth2/OIDC), serveur de découverte (Eureka), serveur de configuration.

---

## Stack technique

| Couche | Technologie |
|---|---|
| Mobile | Flutter / Dart |
| Admin | Angular / TypeScript |
| Passerelle & microservices | Spring Boot (Spring Cloud Gateway, Eureka, Config Server) |
| Moteur d'IA | FastAPI (Python), scikit-learn, pandas |
| OCR | Tesseract (pytesseract) |
| Base de données | PostgreSQL |
| Sécurité | Keycloak (OAuth2 / OpenID Connect) |
| Méthodologie | Scrum (8 sprints) + CRISP-DM |

---


##  Modules d'intelligence artificielle

| Module | Rôle |
|---|---|
| Classification | Segmentation des clients en **6 profils** (GradientBoostingClassifier, 43 variables comportementales). |
| Module 1 | Prévision des **factures** (par fournisseur : STEG, SONEDE, TOPNET, BEE, TT, Ooredoo). |
| Module 2 | Prévision des **recharges**. |
| Module 3 | Prévision du **budget** mensuel. |
| Module 4 | Prévision de la **prochaine transaction** (habitudes). |
| Module 5 | **Alertes** de solde. |
| Module 6 | **Recommandations** par comparaison aux pairs + **détection d'anomalies** (Z-Score, Isolation Forest). |
| Module 7 | **OCR** : scan et lecture des factures. |

Le moteur expose aussi un **monitoring MLOps** : comparaison de l'erreur en production (*MAE Live*) à l'erreur d'entraînement (*MAE PKL*), détection de dégradation et réentraînement.

---

##  Prérequis

- Java 17+ et Maven
- Python 3.10+ et pip
- Node.js 18+ et Angular CLI
- Flutter SDK
- PostgreSQL 14+
- Keycloak
- Tesseract OCR (avec le pack **français** : `tesseract-ocr-fra`)

---

### 1. Base de données
Créez la base PostgreSQL `client_bd`.

### 2. Microservices Spring Boot (`backend/`)
Démarrez les services **dans cet ordre** :
```bash
# 1) Serveur de configuration   2) Serveur de découverte (Eureka)
# 3) Passerelle (gateway)        4) Service client-dashboard + autres
mvn spring-boot:run        # dans chaque module
```
La passerelle écoute sur le port **8222**.

### 3. Moteur d'IA (`intelligence/`)
```bash
cd intelligence
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### 4. Tableau de bord admin (`admin/`)
```bash
cd admin
npm install
ng serve        # http://localhost:4200
```

### 5. Application mobile (`mobile/`)
```bash
cd mobile
flutter pub get
flutter run
```
