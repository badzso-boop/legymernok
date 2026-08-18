#!/usr/bin/env bash
# Létrehozza (vagy frissíti) a legymernok_admin alatti Gitea mission-template
# repókat a gitea-templates/ mappa tartalmából. Idempotens: ha egy repo már
# létezik, csak a tartalmát pusholja felül.
#
# Ez a script oldja fel a "Gitea template hiány" hibaosztályt (issue #34):
# a backend (application.properties: gitea.template.*) egy mission-js-template,
# mission-quiz-template és mission-verifier repót vár legymernok_admin alatt —
# ha a Gitea adatvolument valaha törölni/újraépíteni kell, ez a script
# reprodukálja őket.
#
# Használat: GITEA_ADMIN_TOKEN=... ./scripts/bootstrap-gitea-templates.sh
#   (a token/host felülírható env var-okkal, lásd lent)

set -euo pipefail

GITEA_URL="${GITEA_URL:-http://localhost:3001}"
GITEA_ADMIN_USERNAME="${GITEA_ADMIN_USERNAME:-legymernok_admin}"
GITEA_ADMIN_TOKEN="${GITEA_ADMIN_TOKEN:?GITEA_ADMIN_TOKEN env var szükséges}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATES_DIR="$SCRIPT_DIR/../gitea-templates"
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

create_or_skip_repo() {
  local repo="$1" private="$2"
  local status
  status=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: token ${GITEA_ADMIN_TOKEN}" -H "Content-Type: application/json" \
    -d "{\"name\":\"${repo}\",\"private\":${private},\"auto_init\":false}" \
    "${GITEA_URL}/api/v1/user/repos")
  if [ "$status" = "201" ]; then
    echo "  létrehozva: $repo"
  elif [ "$status" = "409" ]; then
    echo "  már létezik: $repo (tartalom felülírásra kerül)"
  else
    echo "  HIBA ($status) a(z) $repo létrehozásakor" >&2
    exit 1
  fi
}

push_repo() {
  local src="$1" repo="$2"
  local dest="$WORKDIR/$repo"
  rm -rf "$dest"
  mkdir -p "$dest"
  cp -r "$src"/. "$dest"/
  (
    cd "$dest"
    git init -q -b main
    git config user.email "legymernok_admin@ujjweb.hu"
    git config user.name "legymernok_admin"
    git add -A
    git commit -q -m "Template bootstrap ($(date -Iseconds))"
    git remote add origin "${GITEA_URL/http:\/\//http://${GITEA_ADMIN_USERNAME}:${GITEA_ADMIN_TOKEN}@}/${GITEA_ADMIN_USERNAME}/${repo}.git"
    git push -q -f -u origin main
  )
  echo "  pusholva: $repo"
}

echo "Gitea mission-template repók bootstrap-olása ($GITEA_URL) ..."

create_or_skip_repo "mission-js-template" true
push_repo "$TEMPLATES_DIR/mission-js-template" "mission-js-template"

create_or_skip_repo "mission-quiz-template" true
push_repo "$TEMPLATES_DIR/mission-quiz-template" "mission-quiz-template"

create_or_skip_repo "mission-python-template" true
push_repo "$TEMPLATES_DIR/mission-python-template" "mission-python-template"

# A mission-verifier NYILVÁNOS repo kell legyen: a ci.yml workflow-k
# `uses: http://gitea:3000/legymernok_admin/mission-verifier/actions@main`
# formában, névtelen git clone-nal töltik le — privát repónál ez
# "authorization failed: User permission denied" hibával elszáll.
create_or_skip_repo "mission-verifier" false
push_repo "$TEMPLATES_DIR/mission-verifier" "mission-verifier"
curl -s -o /dev/null -X PATCH \
  -H "Authorization: token ${GITEA_ADMIN_TOKEN}" -H "Content-Type: application/json" \
  -d '{"private":false}' \
  "${GITEA_URL}/api/v1/repos/${GITEA_ADMIN_USERNAME}/mission-verifier"

echo "Kész."
