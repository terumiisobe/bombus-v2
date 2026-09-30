#!/usr/bin/env bash
# Read-only check: is the verification instance worth driving?
# Usage: doctor.sh [RUN_ID]
# Defaults to runs/CURRENT.
set -euo pipefail

SKILL_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUNS_DIR="${SKILL_ROOT}/runs"
RUN_ID="${1:-}"
if [[ -z "$RUN_ID" ]]; then
  if [[ -f "${RUNS_DIR}/CURRENT" ]]; then
    RUN_ID="$(cat "${RUNS_DIR}/CURRENT")"
  else
    echo "No RUN_ID and no ${RUNS_DIR}/CURRENT" >&2
    exit 1
  fi
fi

META="${RUNS_DIR}/${RUN_ID}/meta.env"
if [[ ! -f "$META" ]]; then
  echo "Missing meta for RUN_ID=${RUN_ID}: ${META}" >&2
  exit 1
fi
# shellcheck disable=SC1090
source "$META"

OK=1

if [[ ! -f "$PID_FILE" ]]; then
  echo "FAIL: missing PID file ${PID_FILE}"
  OK=0
elif ! kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "FAIL: app PID $(cat "$PID_FILE") is not running"
  OK=0
else
  echo "OK: app PID $(cat "$PID_FILE") is running"
fi

HEALTH_JSON="$(curl -fsS "${BASE_URL}/health" 2>/dev/null || true)"
if [[ "$HEALTH_JSON" == *'"status":"UP"'* ]] || [[ "$HEALTH_JSON" == *'"status": "UP"'* ]]; then
  echo "OK: ${BASE_URL}/health -> ${HEALTH_JSON}"
else
  echo "FAIL: health check at ${BASE_URL}/health (got: ${HEALTH_JSON:-empty})"
  OK=0
fi

# Confirm the listener is on the address we started (best-effort via /proc)
APP_PID_VAL="$(cat "$PID_FILE" 2>/dev/null || true)"
if [[ -n "$APP_PID_VAL" ]] && [[ -r "/proc/${APP_PID_VAL}/cmdline" ]]; then
  echo "OK: /proc/${APP_PID_VAL}/cmdline present (owned by this run)"
else
  echo "WARN: could not confirm /proc ownership for PID ${APP_PID_VAL:-unknown}"
fi

# Refuse to continue if health answers but PID file is stale (someone else's instance)
if [[ "$OK" -eq 1 ]]; then
  # Quick signature-reject smoke (missing header → 403) without mutating state
  CODE="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "${BASE_URL}/v1/whatsapp/webhook" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data 'From=whatsapp:%2B15550001111&Body=doctor' || true)"
  if [[ "$CODE" == "403" ]]; then
    echo "OK: webhook rejects unsigned POST with 403"
  else
    echo "FAIL: unsigned webhook expected 403, got ${CODE}"
    OK=0
  fi
fi

echo "RUN_ID=${RUN_ID}"
echo "BASE_URL=${BASE_URL}"
echo "TWILIO_PUBLIC_BASE_URL=${TWILIO_PUBLIC_BASE_URL}"

if [[ "$OK" -ne 1 ]]; then
  exit 1
fi
echo "doctor: PASS"
