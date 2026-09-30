#!/usr/bin/env bash
# Launch a disposable bombus verification instance (Postgres + Spring Boot).
# Records run metadata under .cursor/skills/verify-bombus/runs/<RUN_ID>/.
#
# Never drives or reuses an instance this script did not start.
set -euo pipefail

SKILL_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(cd "${SKILL_ROOT}/../../.." && pwd)"
RUNS_DIR="${SKILL_ROOT}/runs"
ARTIFACTS_DIR="${SKILL_ROOT}/artifacts"

RUN_ID="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
SERVER_PORT="${SERVER_PORT:-18080}"
POSTGRES_PORT="${POSTGRES_PORT:-15432}"
SERVER_ADDRESS="${SERVER_ADDRESS:-127.0.0.1}"

RUN_DIR="${RUNS_DIR}/${RUN_ID}"
mkdir -p "$RUN_DIR" "$ARTIFACTS_DIR"

ENV_FILE="${ENV_FILE:-${REPO_ROOT}/.env.verify}"
if [[ ! -f "$ENV_FILE" ]]; then
  cat >&2 <<EOF
Missing verification env file: ${ENV_FILE}
Copy .env.example to .env.verify (gitignored) and set disposable values, e.g.:
  DB_URL=jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/bombus
  DB_USERNAME=bombus_usr
  DB_PASSWORD=bombuspass
  FLYWAY_ENABLED=true
  TWILIO_AUTH_TOKEN=verify-test-auth-token
  TWILIO_PUBLIC_BASE_URL=http://127.0.0.1:${SERVER_PORT}
  OPENAI_API_KEY=sk-verify-unused
EOF
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${TWILIO_AUTH_TOKEN:?}"
: "${TWILIO_PUBLIC_BASE_URL:?}"
: "${OPENAI_API_KEY:?}"
: "${DB_URL:?}"
: "${DB_USERNAME:?}"
: "${DB_PASSWORD:?}"

BASE_URL="http://${SERVER_ADDRESS}:${SERVER_PORT}"
export BASE_URL SERVER_PORT POSTGRES_PORT SERVER_ADDRESS RUN_ID RUN_DIR
export TWILIO_AUTH_TOKEN TWILIO_PUBLIC_BASE_URL

COMPOSE_FILE="${REPO_ROOT}/docker/docker-compose.yml"
STARTED_COMPOSE=0

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  echo "Starting Postgres via docker compose (host port ${POSTGRES_PORT})..."
  POSTGRES_PORT="$POSTGRES_PORT" docker compose -f "$COMPOSE_FILE" up -d
  STARTED_COMPOSE=1
  for _ in $(seq 1 60); do
    if docker exec bombus-postgres pg_isready -U "${POSTGRES_USER:-bombus_usr}" -d "${POSTGRES_DB:-bombus}" >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done
  docker exec bombus-postgres pg_isready -U "${POSTGRES_USER:-bombus_usr}" -d "${POSTGRES_DB:-bombus}"
  export DB_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/bombus"
  export DB_USERNAME="${DB_USERNAME:-bombus_usr}"
  export DB_PASSWORD="${DB_PASSWORD:-bombuspass}"
else
  echo "Docker unavailable; using existing Postgres from DB_URL=${DB_URL}"
  if command -v psql >/dev/null 2>&1; then
    # Parse host/port from jdbc:postgresql://host:port/db when possible; else try 127.0.0.1:5432
    if [[ "$DB_URL" =~ jdbc:postgresql://([^:/]+):([0-9]+)/([^?]+) ]]; then
      PGHOST="${BASH_REMATCH[1]}"
      PGPORT="${BASH_REMATCH[2]}"
      PGDATABASE="${BASH_REMATCH[3]}"
    else
      PGHOST=127.0.0.1
      PGPORT=5432
      PGDATABASE=bombus
    fi
    PGPASSWORD="$DB_PASSWORD" psql -h "$PGHOST" -p "$PGPORT" -U "$DB_USERNAME" -d "$PGDATABASE" -c 'SELECT 1' >/dev/null \
      || { echo "Postgres not reachable at ${PGHOST}:${PGPORT}/${PGDATABASE}" >&2; exit 1; }
  fi
fi

LOG_FILE="${RUN_DIR}/app.log"
PID_FILE="${RUN_DIR}/app.pid"
META_FILE="${RUN_DIR}/meta.env"

cd "$REPO_ROOT"
# setsid so cleanup can signal the whole process group started by this run
setsid env \
  SERVER_PORT="$SERVER_PORT" \
  SERVER_ADDRESS="$SERVER_ADDRESS" \
  DB_URL="$DB_URL" \
  DB_USERNAME="$DB_USERNAME" \
  DB_PASSWORD="$DB_PASSWORD" \
  FLYWAY_ENABLED="${FLYWAY_ENABLED:-true}" \
  TWILIO_AUTH_TOKEN="$TWILIO_AUTH_TOKEN" \
  TWILIO_PUBLIC_BASE_URL="$TWILIO_PUBLIC_BASE_URL" \
  OPENAI_API_KEY="$OPENAI_API_KEY" \
  OPENAI_BASE_URL="${OPENAI_BASE_URL:-https://api.openai.com/v1}" \
  mvn -q spring-boot:run -Dspring-boot.run.profiles=dev \
  >"$LOG_FILE" 2>&1 &
APP_PID=$!
echo "$APP_PID" >"$PID_FILE"

cat >"$META_FILE" <<EOF
RUN_ID=${RUN_ID}
BASE_URL=${BASE_URL}
SERVER_ADDRESS=${SERVER_ADDRESS}
SERVER_PORT=${SERVER_PORT}
POSTGRES_PORT=${POSTGRES_PORT}
TWILIO_AUTH_TOKEN=${TWILIO_AUTH_TOKEN}
TWILIO_PUBLIC_BASE_URL=${TWILIO_PUBLIC_BASE_URL}
APP_PID=${APP_PID}
STARTED_COMPOSE=${STARTED_COMPOSE}
LOG_FILE=${LOG_FILE}
PID_FILE=${PID_FILE}
REPO_ROOT=${REPO_ROOT}
SKILL_ROOT=${SKILL_ROOT}
EOF

echo "Waiting for ${BASE_URL}/health ..."
READY=0
for _ in $(seq 1 180); do
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo "App process exited early. Last log lines:" >&2
    tail -n 120 "$LOG_FILE" >&2 || true
    exit 1
  fi
  if curl -fsS "${BASE_URL}/health" >/dev/null 2>&1; then
    READY=1
    break
  fi
  sleep 2
done

if [[ "$READY" -ne 1 ]]; then
  echo "Timed out waiting for health. Last log lines:" >&2
  tail -n 120 "$LOG_FILE" >&2 || true
  exit 1
fi

if grep -Eq 'Migrating schema|Successfully applied|Current version of schema' "$LOG_FILE" 2>/dev/null; then
  echo "Flyway activity detected in logs."
fi

echo "Launched RUN_ID=${RUN_ID}"
echo "BASE_URL=${BASE_URL}"
echo "META=${META_FILE}"
echo "LOG=${LOG_FILE}"
echo "${RUN_ID}" >"${RUNS_DIR}/CURRENT"
