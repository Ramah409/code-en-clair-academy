#!/usr/bin/env bash
# Vérification rapide de l'API (parcours d'authentification et droits d'accès).
# Usage : ./scripts/verifier-api.sh [URL_API]   (défaut : http://localhost:8090)
set -uo pipefail

API="${1:-http://localhost:8090}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
OK=0
KO=0

check() { # libellé, attendu, obtenu
  if [[ "$2" == "$3" ]]; then echo "  ✔ $1 ($3)"; OK=$((OK + 1));
  else echo "  ✘ $1 : attendu $2, obtenu $3"; KO=$((KO + 1)); fi
}
post() { # url, json, [fichier cookies à lire], [fichier cookies à écrire]
  local args=(-s -o "$TMP/body" -w '%{http_code}' -H 'Content-Type: application/json' -X POST)
  [[ -n "${3:-}" ]] && args+=(-b "$3")
  [[ -n "${4:-}" ]] && args+=(-c "$4")
  curl "${args[@]}" -d "$2" "$API$1"
}
get() { curl -s -o "$TMP/body" -w '%{http_code}' -H "Authorization: Bearer $2" "$API$1"; }
token() { sed -E 's/.*"accessToken":"([^"]+)".*/\1/' "$TMP/body"; }

echo "Vérification de $API"
check "santé de l'API" 200 "$(curl -s -o /dev/null -w '%{http_code}' "$API/actuator/health")"
check "profil sans jeton refusé" 401 "$(curl -s -o /dev/null -w '%{http_code}' "$API/api/me")"
check "JSON obligatoire" 415 "$(curl -s -o /dev/null -w '%{http_code}' -X POST -d 'x=1' "$API/api/auth/login")"

check "connexion apprenante démo" 200 "$(post /api/auth/login '{"email":"apprenante@cda-academy.local","password":"Apprenante-2026!"}' '' "$TMP/c1")"
USER_TOKEN="$(token)"
check "profil avec jeton" 200 "$(get /api/me "$USER_TOKEN")"
check "administration refusée à USER" 403 "$(get /api/admin/users "$USER_TOKEN")"
check "mauvais mot de passe" 401 "$(post /api/auth/login '{"email":"apprenante@cda-academy.local","password":"mauvais"}')"
check "inscription invalide" 400 "$(post /api/auth/register '{"email":"x","displayName":"A","password":"court","confirmPassword":"autre","acceptTerms":false}')"

EMAIL="verif.$RANDOM$RANDOM@exemple.fr"
# (JSON construit à part : bash 3.2 de macOS gère mal les guillemets imbriqués dans $(...))
register_json() { printf '{"email":"%s","displayName":"Vérification","password":"MotDePasse123","confirmPassword":"MotDePasse123","acceptTerms":true}' "$1"; }
BODY="$(register_json "$EMAIL")"
check "inscription valide" 201 "$(post /api/auth/register "$BODY")"
BODY="$(register_json "$(echo "$EMAIL" | tr a-z A-Z)")"
check "inscription en doublon (casse différente)" 409 "$(post /api/auth/register "$BODY")"

check "renouvellement de session" 200 "$(post /api/auth/refresh '' "$TMP/c1" "$TMP/c2")"
check "réutilisation d'un ancien jeton détectée" 401 "$(post /api/auth/refresh '' "$TMP/c1")"
check "toutes les sessions révoquées après détection" 401 "$(post /api/auth/refresh '' "$TMP/c2")"

check "connexion formatrice démo" 200 "$(post /api/auth/login '{"email":"formatrice@cda-academy.local","password":"Formatrice-2026!"}')"
ADMIN_TOKEN="$(token)"
check "recherche de comptes par l'admin" 200 "$(get "/api/admin/users?search=verif" "$ADMIN_TOKEN")"
ID="$(sed -E 's/.*"content":\[\{"id":([0-9]+).*/\1/' "$TMP/body")"
check "suppression d'un compte par l'admin" 204 "$(curl -s -o /dev/null -w '%{http_code}' -X DELETE -H "Authorization: Bearer $ADMIN_TOKEN" "$API/api/admin/users/$ID")"
ADMIN_ID="$(get /api/me "$ADMIN_TOKEN" >/dev/null; sed -E 's/.*"id":([0-9]+).*/\1/' "$TMP/body")"
check "l'admin ne peut pas se supprimer elle-même" 422 "$(curl -s -o /dev/null -w '%{http_code}' -X DELETE -H "Authorization: Bearer $ADMIN_TOKEN" "$API/api/admin/users/$ADMIN_ID")"

BRUTE="brute.$RANDOM@exemple.fr"
BODY="$(printf '{"email":"%s","password":"faux"}' "$BRUTE")"
for _ in 1 2 3 4 5; do post /api/auth/login "$BODY" >/dev/null; done
check "blocage après 5 échecs de connexion" 429 "$(post /api/auth/login "$BODY")"
check "documentation OpenAPI" 200 "$(curl -s -o /dev/null -w '%{http_code}' "$API/v3/api-docs")"

echo
echo "Résultat : $OK réussi(s), $KO échec(s)"
[[ $KO -eq 0 ]]
