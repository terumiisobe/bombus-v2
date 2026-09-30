# Webhook linked help

A correctly signed message from a seeded, active WhatsApp user that asks for help (or is not understood as a count) receives the fixed pt-BR help reply describing how to ask for hive counts.

## Sub-features

- `linked-seed` requires an active `usuario_whatsapp` row for the sender.
- `linked-help-reply` returns TwiML with the help sentence.
- `linked-help-session` persists a `sessao_chat` row for that WhatsApp user after the turn.

## How to get to it (user POV)

- Linked customer sends WhatsApp text such as `ajuda`, `o que você faz?`, or anything the model classifies as HELP/UNKNOWN.
- Locally: seed the phone, then `post-webhook.sh` with that `From`.

## Driving it with curl

Preconditions:

- Doctor PASS.
- App has already booted once (Flyway tables exist).
- Seed disposable user (adjust IDs if collisions):

```bash
docker exec bombus-postgres psql -U bombus_usr -d bombus -c \
"INSERT INTO usuario (email, password_hash) VALUES ('verify-help@example.com', 'x')
 ON CONFLICT (email) DO NOTHING;
 INSERT INTO usuario_whatsapp (usuario_id, phone_number, display_name, active)
 SELECT u.id, '+15550001234', 'Verify Help', true FROM usuario u WHERE u.email = 'verify-help@example.com'
 ON CONFLICT (phone_number) DO UPDATE SET active = true;"
```

(If Docker is unavailable, run the same SQL via `psql` against `DB_URL`.)

- Env from `meta.env` exported (`BASE_URL`, `TWILIO_*`).

- **Ask for help.** Run `.cursor/skills/verify-bombus/scripts/post-webhook.sh --from '+15550001234' --body 'ajuda' --out .cursor/skills/verify-bombus/artifacts/webhook-linked-help/response.xml --headers .cursor/skills/verify-bombus/artifacts/webhook-linked-help/headers.txt`. Observable: HTTP `200`; TwiML `<Message>` contains `Posso contar suas colmeias` (full help string from the service).
- **With disposable OpenAI key.** Intent parse fails closed to UNKNOWN → same help reply. That is valid proof of the help path when OpenAI is unreachable.
- **Confirm session.** Query `sessao_chat` joined to `usuario_whatsapp` for `+15550001234` — expect a row with recent `last_message_at`.
- **Proof.** Save XML, headers, SQL result snippet under `artifacts/webhook-linked-help/`.

## Gotchas

- Seed **after** first boot; seeding before Flyway yields `relation does not exist`.
- Inactive (`active = false`) numbers behave as not-linked.
- Do not confuse help TwiML with the not-linked support sentence.
- Cleanup of seed rows is optional for disposable verify DBs; never delete `artifacts/`.
