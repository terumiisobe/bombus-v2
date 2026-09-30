#!/usr/bin/env bash
# POST form-urlencoded body to /v1/whatsapp/webhook with a valid X-Twilio-Signature.
#
# Required env:
#   BASE_URL          e.g. http://127.0.0.1:18080
#   TWILIO_AUTH_TOKEN disposable token matching the running app
#   TWILIO_PUBLIC_BASE_URL  must match the app's configured public base URL
#
# Required args:
#   --from E.164 phone (with or without whatsapp: prefix)
#   --body message text
#
# Optional:
#   --out PATH   write response body here (also printed to stdout)
#   --headers PATH  write response headers here
set -euo pipefail

SKILL_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SIGN_PY="${SKILL_ROOT}/scripts/sign-twilio.py"

FROM=""
BODY=""
OUT=""
HEADERS_OUT=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --from) FROM="${2:?}"; shift 2 ;;
    --body) BODY="${2:?}"; shift 2 ;;
    --out) OUT="${2:?}"; shift 2 ;;
    --headers) HEADERS_OUT="${2:?}"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

: "${BASE_URL:?BASE_URL is required}"
: "${TWILIO_AUTH_TOKEN:?TWILIO_AUTH_TOKEN is required}"
: "${TWILIO_PUBLIC_BASE_URL:?TWILIO_PUBLIC_BASE_URL is required}"
: "${FROM:?--from is required}"
: "${BODY:?--body is required}"

if [[ "$FROM" != whatsapp:* ]]; then
  FROM="whatsapp:${FROM}"
fi

PATH_SUFFIX="/v1/whatsapp/webhook"
SIGNED_URL="${TWILIO_PUBLIC_BASE_URL%/}${PATH_SUFFIX}"
TARGET_URL="${BASE_URL%/}${PATH_SUFFIX}"

SIGNATURE="$(
  python3 "$SIGN_PY" \
    --auth-token "$TWILIO_AUTH_TOKEN" \
    --url "$SIGNED_URL" \
    --param "From=${FROM}" \
    --param "Body=${BODY}"
)"

TMP_BODY="$(mktemp)"
TMP_HDRS="$(mktemp)"
trap 'rm -f "$TMP_BODY" "$TMP_HDRS"' EXIT

HTTP_CODE="$(
  curl -sS -o "$TMP_BODY" -D "$TMP_HDRS" -w '%{http_code}' \
    -X POST "$TARGET_URL" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -H "X-Twilio-Signature: ${SIGNATURE}" \
    --data-urlencode "From=${FROM}" \
    --data-urlencode "Body=${BODY}"
)"

echo "HTTP ${HTTP_CODE}" >&2
if [[ -n "$HEADERS_OUT" ]]; then
  mkdir -p "$(dirname "$HEADERS_OUT")"
  cp "$TMP_HDRS" "$HEADERS_OUT"
fi
if [[ -n "$OUT" ]]; then
  mkdir -p "$(dirname "$OUT")"
  cp "$TMP_BODY" "$OUT"
fi
cat "$TMP_BODY"
echo
exit 0
