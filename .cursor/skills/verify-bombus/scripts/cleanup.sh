#!/usr/bin/env bash
# Tear down the verification instance this run started.
# Never kills by process name. Never deletes proof artifacts under artifacts/.
# Usage: cleanup.sh [RUN_ID]
set -euo pipefail

SKILL_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUNS_DIR="${SKILL_ROOT}/runs"
RUN_ID="${1:-}"
if [[ -z "$RUN_ID" ]]; then
  if [[ -f "${RUNS_DIR}/CURRENT" ]]; then
    RUN_ID="$(cat "${RUNS_DIR}/CURRENT")"
  else
    echo "No RUN_ID and no ${RUNS_DIR}/CURRENT — nothing to clean" >&2
    exit 0
  fi
fi

META="${RUNS_DIR}/${RUN_ID}/meta.env"
if [[ ! -f "$META" ]]; then
  echo "Missing meta for RUN_ID=${RUN_ID}" >&2
  exit 1
fi
# shellcheck disable=SC1090
source "$META"

if [[ -f "$PID_FILE" ]]; then
  APP_PID_VAL="$(cat "$PID_FILE")"
  if kill -0 "$APP_PID_VAL" 2>/dev/null; then
    echo "Stopping app PID ${APP_PID_VAL} (and children)..."
    # spring-boot:run leaves a Maven parent + forked JVM; kill the process group when possible
    if [[ -r "/proc/${APP_PID_VAL}/stat" ]]; then
      PGID="$(awk '{print $5}' "/proc/${APP_PID_VAL}/stat" 2>/dev/null || true)"
      if [[ -n "${PGID:-}" ]] && [[ "$PGID" != "0" ]]; then
        kill -TERM "-${PGID}" 2>/dev/null || kill -TERM "$APP_PID_VAL" 2>/dev/null || true
      else
        kill -TERM "$APP_PID_VAL" 2>/dev/null || true
      fi
    else
      kill -TERM "$APP_PID_VAL" 2>/dev/null || true
    fi
    for _ in $(seq 1 30); do
      kill -0 "$APP_PID_VAL" 2>/dev/null || break
      sleep 1
    done
    if kill -0 "$APP_PID_VAL" 2>/dev/null; then
      kill -KILL "$APP_PID_VAL" 2>/dev/null || true
      kill -KILL "-${PGID:-$APP_PID_VAL}" 2>/dev/null || true
    fi
  else
    echo "App PID ${APP_PID_VAL} already stopped"
  fi
  rm -f "$PID_FILE"
fi

if [[ "${STARTED_COMPOSE:-0}" == "1" ]] && command -v docker >/dev/null 2>&1; then
  echo "Stopping docker compose Postgres started by this run..."
  POSTGRES_PORT="${POSTGRES_PORT}" docker compose -f "${REPO_ROOT}/docker/docker-compose.yml" down
fi

if [[ -f "${RUNS_DIR}/CURRENT" ]] && [[ "$(cat "${RUNS_DIR}/CURRENT")" == "$RUN_ID" ]]; then
  rm -f "${RUNS_DIR}/CURRENT"
fi

echo "Cleanup done for RUN_ID=${RUN_ID}"
echo "Evidence retained under ${SKILL_ROOT}/artifacts/ (untouched)"
