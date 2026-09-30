---
name: verify-bombus
description: "Drive bombus (Spring Boot WhatsApp/Twilio HTTP API) locally with curl + Twilio HMAC signatures — launch, doctor, prove webhook/health features, capture evidence. Use when verifying bombus behavior end-to-end without a web UI."
---

# Verify bombus

Bombus is an HTTP API (no web UI): a Spring Boot WhatsApp bot that accepts Twilio webhooks and replies with TwiML. Verification drives the real app over plain HTTP on `127.0.0.1`, signs webhook posts like Twilio, and captures status codes, bodies, and logs.

Skill root: `.cursor/skills/verify-bombus/`  
Feature map: `features/`  
Helpers: `scripts/`  
Evidence: `artifacts/` (survives cleanup; gitignored except `.gitkeep`)  
Run metadata: `runs/<RUN_ID>/` (gitignored)

## Launch

Preconditions: Java 21, Maven, Docker (preferred for Postgres) or an existing Postgres matching `DB_URL`. Copy `.env.example` → `.env.verify` (gitignored) with **disposable** values only — never commit real Twilio/OpenAI secrets.

Example `.env.verify` for a side-by-side verify port:

```bash
DB_URL=jdbc:postgresql://127.0.0.1:15432/bombus
DB_USERNAME=bombus_usr
DB_PASSWORD=bombuspass
FLYWAY_ENABLED=true
TWILIO_AUTH_TOKEN=verify-test-auth-token
TWILIO_PUBLIC_BASE_URL=http://127.0.0.1:18080
OPENAI_API_KEY=sk-verify-unused
```

`TWILIO_PUBLIC_BASE_URL` must equal the URL used when computing `X-Twilio-Signature` (for local curl, set it to `http://127.0.0.1:$SERVER_PORT` — no trailing slash). Flyway runs on app boot; do not seed tables before the first successful start.

Start (records `runs/<RUN_ID>/meta.env`, writes `runs/CURRENT`):

```bash
SERVER_PORT=18080 POSTGRES_PORT=15432 \
  .cursor/skills/verify-bombus/scripts/launch.sh
```

Ready when `GET $BASE_URL/health` returns `{"status":"UP"}` (the script waits and prints `BASE_URL`). App binds to `127.0.0.1` by default via `SERVER_ADDRESS`.

Teardown: see **Cleanup**. Refuse to drive any process this run did not start (no PID in `runs/<RUN_ID>/app.pid`).

## Doctor

```bash
.cursor/skills/verify-bombus/scripts/doctor.sh          # uses runs/CURRENT
.cursor/skills/verify-bombus/scripts/doctor.sh <RUN_ID>
```

Pass requires: recorded app PID alive, `/health` → UP, unsigned `POST /v1/whatsapp/webhook` → `403`. Run doctor first whenever anything looks off.

## Drive

Harness: plain HTTP (`curl`) plus `scripts/post-webhook.sh` (HMAC-SHA1 signature via `scripts/sign-twilio.py`, matching `TwilioWhatsAppWebhookControllerTest`).

Load run env, then drive:

```bash
set -a && source .cursor/skills/verify-bombus/runs/$(cat .cursor/skills/verify-bombus/runs/CURRENT)/meta.env && set +a

# Health
curl -fsS "$BASE_URL/health"

# Signed webhook (not-linked / help / count recipes live in features/)
.cursor/skills/verify-bombus/scripts/post-webhook.sh \
  --from '+15550009999' \
  --body 'oi' \
  --out .cursor/skills/verify-bombus/artifacts/<feature>/response.xml \
  --headers .cursor/skills/verify-bombus/artifacts/<feature>/headers.txt
```

User-facing endpoints:

| Method | Path | Notes |
|--------|------|--------|
| `GET` | `/health` | JSON `{"status":"UP"}` |
| `POST` | `/v1/whatsapp/webhook` | `application/x-www-form-urlencoded`; requires `X-Twilio-Signature`; returns TwiML `text/xml` |

Form fields Twilio uses: `From` (`whatsapp:+E164`), `Body` (message text).

Prefer the feature files under `features/` over inventing new paths. Seed linked customers only after Flyway has created tables (see feature gotchas). Optional DB inspection: `docker exec bombus-postgres psql -U bombus_usr -d bombus -c '\dt'` (or `psql` against `DB_URL` when not using Docker).

## Evidence

Store proofs under `.cursor/skills/verify-bombus/artifacts/<feature-id>/` (named in this skill; gitignored). For each proof capture:

1. The HTTP action (method, URL, relevant headers/body) — save request notes or the command used.
2. Response status + body (`headers.txt`, `response.json` / `response.xml`).
3. A log excerpt from `runs/<RUN_ID>/app.log` covering the request (Flyway lines for first boot; webhook reject warnings for signature cases).
4. Side effects when relevant (e.g. `sessao_chat` row after a linked turn via `psql`).

Standards: exercise the real webhook/health paths (not internal setters or test-only endpoints). Mock OpenAI only by pointing `OPENAI_BASE_URL` at a stub if you must; with a disposable fake key, intent parse failures fall back to the help reply (see `OpenAiConversationAdapter`). Never commit `.env.verify` or real secrets. Cleanup must not delete `artifacts/`.

## Cleanup

```bash
.cursor/skills/verify-bombus/scripts/cleanup.sh          # uses runs/CURRENT
.cursor/skills/verify-bombus/scripts/cleanup.sh <RUN_ID>
```

Stops the app PID (process group) recorded for that run. If launch started Docker Compose Postgres (`STARTED_COMPOSE=1`), runs `docker compose … down`. Does **not** delete `artifacts/`. After cleanup, confirm evidence files still exist at the paths you wrote.

## Helpers

All under `.cursor/skills/verify-bombus/scripts/` (executable):

| Script | Role |
|--------|------|
| `launch.sh` | Start Postgres (compose if available) + `mvn spring-boot:run -Dspring-boot.run.profiles=dev`; wait for health; write `runs/<RUN_ID>/` |
| `doctor.sh` | Read-only readiness (PID, health, unsigned webhook 403) |
| `cleanup.sh` | Tear down this run’s app (+ compose if we started it); keep artifacts |
| `sign-twilio.py` | `python3 sign-twilio.py --auth-token T --url URL --param From=… --param Body=…` → signature on stdout |
| `post-webhook.sh` | Signed `POST /v1/whatsapp/webhook`; needs `BASE_URL`, `TWILIO_AUTH_TOKEN`, `TWILIO_PUBLIC_BASE_URL` |

Example signature-only:

```bash
python3 .cursor/skills/verify-bombus/scripts/sign-twilio.py \
  --auth-token verify-test-auth-token \
  --url 'http://127.0.0.1:18080/v1/whatsapp/webhook' \
  --param 'From=whatsapp:+15550009999' \
  --param 'Body=oi'
```

## Isolation

- Bind `SERVER_ADDRESS=127.0.0.1` (default in `launch.sh`).
- Alternate `SERVER_PORT` / `POSTGRES_PORT` for side-by-side runs.
- Docker Compose uses fixed container name `bombus-postgres` — only one compose Postgres at a time; use alternate host ports, not a second compose project with the same name.
- **Refuse** to drive an instance whose PID is not in this run’s `app.pid`.

## Feature map

Read `features/README.md`, then drive one mapped feature per proof. Start with `health` or `webhook-signature-reject` when validating the skill itself; use `webhook-not-linked` / linked recipes for bot behavior.
