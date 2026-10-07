# Webhook linked help

A correctly signed message from a seeded, active WhatsApp user that the agent cannot process (or that asks what the bot can do) receives a pt-BR reply. When OpenAI fails, the app returns the configured agent fallback, which still steers the user toward counting questions.

## Sub-features

- `linked-seed` requires an active `usuario_whatsapp` row for the sender.
- `linked-fallback-or-help` returns TwiML with either a model help reply or `chatbot.agent.fallback-reply`.
- `linked-session` persists a `sessao_chat` row for that WhatsApp user after the turn.

## How to get to it (user POV)

- Linked customer sends WhatsApp text such as `ajuda` / `o que você faz?`, or any message while the model is unreachable.
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

- **Ask for help / trigger fallback.** Run `.cursor/skills/verify-bombus/scripts/post-webhook.sh --from '+15550001234' --body 'ajuda' --out .cursor/skills/verify-bombus/artifacts/webhook-linked-help/response.xml --headers .cursor/skills/verify-bombus/artifacts/webhook-linked-help/headers.txt`. Observable: HTTP `200`; TwiML `<Message>` is non-empty pt-BR. With a disposable OpenAI key against the real API, expect the fallback containing `listar, adicionar, excluir` (from `ChatbotAgentProperties.fallbackReply`).
- **Confirm session.** Query `sessao_chat` joined to `usuario_whatsapp` for `+15550001234` — expect a row with recent `last_message_at`.
- **Proof.** Save XML, headers, SQL result snippet under `artifacts/webhook-linked-help/`.

## Gotchas

- Seed **after** first boot; seeding before Flyway yields `relation does not exist`.
- Inactive (`active = false`) numbers behave as not-linked.
- Linked turns use a bounded OpenAI tool-calling loop (`count_colmeias` / `list_vocabulary`); there is no fixed HELP intent string anymore — assert on fallback or live model text, not the old hard-coded help sentence.
- Do not confuse this TwiML with the not-linked support sentence.
- Cleanup of seed rows is optional for disposable verify DBs; never delete `artifacts/`.
