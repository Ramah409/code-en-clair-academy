# Mise en ligne de Code en Clair Academy

## Architecture (offres gratuites)

| Partie | Hébergeur | Rôle |
|---|---|---|
| Frontend Angular | Netlify | Sert l'application et relaie `/api/*` vers Render (même adresse pour le navigateur) |
| API Spring Boot | Render (Free) | Image Docker `deploy/api.Dockerfile`, profil Spring `prod` |
| Base PostgreSQL | Neon (Free) | Schéma créé par Flyway au démarrage de l'API |

Fichiers : `netlify.toml`, `render.yaml`, `deploy/api.Dockerfile`, `backend/src/main/resources/application-prod.yml`.

## Variables d'environnement de l'API (Render)

Toutes sont des secrets saisis dans Render, jamais dans le dépôt.

| Variable | Contenu |
|---|---|
| `DB_URL` | `jdbc:postgresql://<hôte Neon direct, sans -pooler>/cda_academy?sslmode=require` |
| `DB_USERNAME`, `DB_PASSWORD` | Rôle propriétaire de la base Neon |
| `JWT_SECRET` | Au moins 64 caractères aléatoires |
| `LAB_DB_PASSWORD` | Mot de passe robuste du rôle `cda_lab` (laboratoire SQL) |
| `ADMIN_PASSWORD` | Mot de passe du compte administrateur `formatrice@cda-academy.local` |
| `CORS_ALLOWED_ORIGINS` | Adresse du site Netlify, par exemple `https://code-en-clair.netlify.app` |

## Comptes de démonstration

Les comptes créés par la migration V2 ont des mots de passe publiés dans le dépôt. En production,
`DemoAccountsGuard` les remplace au démarrage tant qu'ils n'ont pas été changés : l'administratrice
reçoit `ADMIN_PASSWORD`, l'apprenante de démonstration un mot de passe aléatoire.

## Limites de l'offre gratuite

- Render met l'API en veille après 15 minutes sans visite ; le réveil prend 2 à 3 minutes. L'application
  affiche un écran d'attente et rétablit la session toute seule.
- Neon met la base en pause après 5 minutes sans activité ; le réveil prend quelques secondes.

## Mettre à jour

- **API et contenus** : pousser sur `main`, puis déclencher un déploiement Render (tableau de bord ou crochet de déploiement).
- **Frontend** : `cd frontend && npx ng build`, puis `npx netlify-cli deploy --prod --dir dist/frontend/browser` depuis la racine du dépôt (le fichier `netlify.toml` y est lu).
