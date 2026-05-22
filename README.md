# CotisApp v3.0 — Gestion de cotisation GIE / Organisations

Application de gestion des cotisations, emprunts et remboursements pour les GIE (Groupements d'Intérêt Économique), avec cloisonnement strict par organisation et par rôle.

## Stack technique

| Couche | Technologies |
|--------|----------------|
| Frontend | Angular 17+ (standalone, signals, `inject()`), TailwindCSS, Angular Material 17 |
| Backend | Spring Boot 3.2, Java 17+, Spring Security, JWT |
| BDD | MySQL 8+ (H2 en tests) |
| ORM | Spring Data JPA + Hibernate |
| Migrations | Flyway |
| Export | iText PDF, Apache POI Excel (dépendances présentes) |
| Tests | JUnit 5, Mockito (backend) |

## Fonctionnalités v3

- **COTISATION_MOIS** : crédit simultané Épargne membre + Caisse organisation (+ solidarité auto optionnelle)
- **Remboursements différenciés** : Étalé → Caisse | Solidarité → fonds Solidarité | Caisse/Financement → capital + frais sur Dépense membre
- **Suivi mensuel** : génération et mise à jour automatique (`SuiviMensuel`)
- **Cloisonnement** : JWT (`organisationId`, `role`, `membreId`), filtres Spring, guards Angular

## Prérequis

- **Java 17+**
- **Maven 3.9+**
- **Node.js 18+** et npm
- **MySQL 8+** (ou Docker)

## Variables d'environnement (backend)

| Variable | Description | Défaut |
|----------|-------------|--------|
| `DB_URL` | JDBC MySQL | `jdbc:mysql://localhost:3306/cotisapp?...` |
| `DB_USER` | Utilisateur BDD | `root` |
| `DB_PASSWORD` | Mot de passe BDD | `root` |
| `JWT_SECRET` | Clé HMAC JWT (≥ 256 bits) | *(voir `application.yml`)* |
| `JWT_EXPIRATION_MS` | Durée token (ms) | `86400000` |
| `SERVER_PORT` | Port API | `8084` |

## Démarrage rapide

### 1. Base de données

```sql
CREATE DATABASE IF NOT EXISTS cotisapp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. Backend

**Développement (rechargement automatique après modification des sources) :**

```powershell
# Depuis la racine du projet
powershell -ExecutionPolicy Bypass -File scripts/dev-backend.ps1
```

Ou dans Cursor : **Terminal → Exécuter la tâche** → `Backend (rechargement auto)`.

Le profil `dev` active Spring DevTools : à chaque sauvegarde / fin de modification (recompilation), l’API redémarre en quelques secondes.

**Démarrage simple (sans watch) :**

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Au premier démarrage, Flyway applique le schéma et `DataInitializer` crée **uniquement** les comptes suivants (aucune organisation, membre ni opération démo) :

| Compte | Mot de passe | Rôle |
|--------|--------------|------|
| `superadmin@cotisapp.sn` | `Admin@2026` | SUPERADMIN |
| `admin@cotisapp.sn` | `Admin@2026` | SUPERADMIN |

Pour repartir de zéro : `mysql -u root -p < scripts/reset-database.sql` puis redémarrer le backend.

Les organisations, membres et opérations se créent ensuite via l’interface (superadmin → organisations, etc.).

### 3. Frontend

```bash
cd frontend
npm install
npm start
```

Ouvrir http://localhost:4284 — connexion admin → écran **Opération — Type Mois**.

### 4. Tests backend

```bash
cd backend
mvn test
```

Tests couverts : `OrgSecurityService`, `RembourserService` (3 types), `MoteurOperationService`.

## API principales

```
POST /api/auth/login
GET  /api/organisations
POST /api/organisations/{orgId}/operations/cotisation-mois
POST /api/organisations/{orgId}/operations/cotisation-mois/preview
GET  /api/organisations/{orgId}/suivi-mensuel?mois=2026-05
POST /api/organisations/{orgId}/emprunts/{empId}/rembourser
POST /api/organisations/{orgId}/emprunts/{empId}/rembourser/etale
POST /api/organisations/{orgId}/emprunts/{empId}/rembourser/solidarite
POST /api/organisations/{orgId}/emprunts/{empId}/rembourser/caisse
```

## Structure du projet

```
GestionCotisation/
├── backend/          # API Spring Boot
│   └── src/main/java/com/cotisapp/
│       ├── controller/
│       ├── service/      # MoteurOperation, Rembourser, SuiviMensuel...
│       ├── security/     # JWT, OrganisationContext, OrgSecurityService
│       └── domain/
├── frontend/         # Angular 17
│   └── src/app/
│       ├── core/         # auth, guards, interceptors
│       ├── layout/       # AppShell (sidebar + responsive)
│       └── features/     # cotisation-mois, login, superadmin...
└── README.md
```

## Rôles et navigation

| Rôle | Accès |
|------|--------|
| SUPERADMIN | Vue globale, toutes les orgs |
| ADMIN_GIE | Dashboard, membres, opérations (son GIE) |
| MEMBRE | Mon compte uniquement |

Guards Angular : `authGuard`, `orgGuard`, `roleGuard`.

## Écrans remboursement (v3)

| Route | Type | Comportement |
|-------|------|--------------|
| `/organisations/:orgId/remboursements/etale` | ETALE | Échéance + montant → Caisse |
| `/organisations/:orgId/remboursements/solidarite` | SOLIDARITE | Montant → fonds Solidarité |
| `/organisations/:orgId/remboursements/caisse` | CAISSE | Capital + frais (frais sur Dépense membre) |

## Suivi mensuel

- **Scheduler** : génération au démarrage + cron le 1er de chaque mois (`cotisapp.suivi-mensuel.cron`)
- **UI** : `/organisations/:orgId/suivi-mensuel` — tableau par membre, bouton « Générer le mois »
- Mis à jour automatiquement après chaque `COTISATION_MOIS`

## Prochaines étapes (Phase 4)

- Rapports PDF/Excel
- Notifications (retards emprunts, cotisations manquantes)
- Tests E2E Playwright

## Licence

Projet interne — GIE / organisations partenaires.
