# Health

Health exposes a lightweight liveness JSON document so hosts and operators can see that the HTTP process is up without touching the database or OpenAI.

## Sub-features

- `health-up` returns HTTP 200 with body status `UP`.
- `health-no-auth` is reachable without Twilio signatures or credentials.

## How to get to it (user POV)

- Send `GET /health` to the running bombus base URL (e.g. `http://127.0.0.1:18080/health`).

## Driving it with curl

Preconditions:

- Bombus is healthy at `$BASE_URL` from the current verification run.
- `.cursor/skills/verify-bombus/scripts/doctor.sh` reports PASS.

- **Liveness.** Request health. Run `curl -fsS -D .cursor/skills/verify-bombus/artifacts/health/headers.txt -o .cursor/skills/verify-bombus/artifacts/health/response.json "$BASE_URL/health"`. HTTP status is `200` and the JSON body is `{"status":"UP"}` (key order may vary).
- **Proof.** Save the command used and a short log note that the app PID from `runs/<RUN_ID>/app.pid` served the response. Artifacts under `artifacts/health/` must show status UP.

## Gotchas

- `/health` does not prove Postgres or OpenAI connectivity — only process liveness.
- Do not use springdoc (`/swagger-ui.html`) as a substitute for this check when proving health.
- Always hit `$BASE_URL` from the run meta file, not a guessed `localhost:8080`.
