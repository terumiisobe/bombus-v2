# Bombus verification map

This directory is the maintained source for verifying the user-facing HTTP behavior of bombus (WhatsApp Twilio webhook + health). Read this index before driving the app, then use the matching feature file as the recipe.

## Baseline preconditions

- Launch via `.cursor/skills/verify-bombus/scripts/launch.sh` with a disposable `.env.verify`.
- Bind to `127.0.0.1` and prefer `SERVER_PORT=18080` (or another free port) so you do not collide with a developer’s `:8080`.
- Set `TWILIO_PUBLIC_BASE_URL` to `http://127.0.0.1:$SERVER_PORT` (no trailing slash) so local signed curls match the app’s validator.
- Use a disposable `TWILIO_AUTH_TOKEN` (e.g. `verify-test-auth-token`) — never a production Twilio token in verification files.
- Run `.cursor/skills/verify-bombus/scripts/doctor.sh` and require PASS before driving.
- Never drive an instance that was not started by this verification run (`runs/<RUN_ID>/app.pid`).

## Driving conventions

- Start every recipe from the baseline state unless its preconditions say otherwise.
- Drive with `curl` and `.cursor/skills/verify-bombus/scripts/post-webhook.sh`.
- Treat every command as literal. Keep paths, header names, and phone formats unchanged.
- Phone numbers in `From` are E.164 with a leading `+`, optionally prefixed with `whatsapp:` (the helper adds the prefix if missing).
- Linked-customer recipes require a DB seed **after** Flyway has created tables (app has booted once).
- Restore or leave disposable seed rows; do not remove proof artifacts during cleanup.

## Proof and skip reporting

- Capture the HTTP action and the resulting status/body, not only a final success line.
- Webhook proof includes status code, TwiML or problem+json body, and a log excerpt.
- Mutation proof (linked turns) includes a second read of session state when relevant (`sessao_chat`).
- Record the feature ID and entry point used with every artifact under `.cursor/skills/verify-bombus/artifacts/<feature-id>/`.
- Report an unreachable path with the attempted command and the unmet precondition.
- Do not report a skipped entry point as verified through a different path.

## Feature entry contract

Each feature file starts with an H1 title and one paragraph describing the user-visible behavior. It then uses exactly four H2 sections in this order.

1. `Sub-features` lists short IDs with one line for each behavior.
2. `How to get to it (user POV)` lists every user entry point.
3. `Driving it with curl` starts with `Preconditions:` and uses labeled bullets that pair each user action with an exact command and observable result.
4. `Gotchas` lists traps that can waste or invalidate a verification run.

Keep implementation details out of the map. Name only user paths, stable handles, required state, commands, and observable proof.

## Features

- [Health](./health.md) covers the liveness JSON endpoint.
- [Webhook signature reject](./webhook-signature-reject.md) covers missing/invalid `X-Twilio-Signature` → 403.
- [Webhook not linked](./webhook-not-linked.md) covers a valid signature from an unknown phone → not-linked TwiML.
- [Webhook linked help](./webhook-linked-help.md) covers a seeded active WhatsApp user receiving help or agent fallback reply.
- [Webhook linked count](./webhook-linked-count.md) covers counting colmeias for a seeded user via the tool-calling agent (needs working OpenAI or an `OPENAI_BASE_URL` stub).
- [Webhook linked colmeia CRUD](./webhook-linked-colmeia-crud.md) covers list/create/update/hard-delete (with confirmation) for a seeded linked user.
