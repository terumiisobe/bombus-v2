# Webhook linked count

A correctly signed message from a seeded, active WhatsApp user that asks how many hives they have receives a pt-BR reply that restates the computed colmeia count (optionally filtered by species or status).

## Sub-features

- `count-plain` answers “how many hives?” with a total (default status exclusion applies in the domain).
- `count-phrase` returns TwiML whose `<Message>` includes the trusted number (template or model phrasing).
- `count-needs-intent` requires OpenAI intent parse to return COUNT (or an `OPENAI_BASE_URL` stub that does).

## How to get to it (user POV)

- Linked customer sends WhatsApp text such as `quantas colmeias eu tenho?`, `quantas jataí?`, or `quantas colmeias estáveis?`.
- Locally: seed user + optional colmeias, then `post-webhook.sh`.

## Driving it with curl

Preconditions:

- Doctor PASS; Flyway tables exist.
- Working OpenAI credentials **or** `OPENAI_BASE_URL` pointing at a stub that returns JSON intent `{"intent":"COUNT","speciesId":null,"statusId":null}` for chat completions (see app `OpenAiConversationAdapter`). With only a fake key and the real OpenAI host, parse fails → help reply — that is **not** a count proof.
- Seed user and at least one countable colmeia owned by that user (species/status via reference data). Example sketch (IDs depend on migrations):

```bash
# After boot: create verify user + whatsapp + meliponario + colmeia rows owned by that user.
# Use reference especie/status ids from the DB; exclude or include status per domain rules.
```

- Env from `meta.env` exported; if using a stub, relaunch with `OPENAI_BASE_URL` set before proving.

- **Ask for a count.** Run `.cursor/skills/verify-bombus/scripts/post-webhook.sh --from '+15550005678' --body 'quantas colmeias eu tenho?' --out .cursor/skills/verify-bombus/artifacts/webhook-linked-count/response.xml --headers .cursor/skills/verify-bombus/artifacts/webhook-linked-count/headers.txt`. Observable: HTTP `200`; TwiML `<Message>` contains the numeric total (e.g. `Você tem N colmeias` template form when phrasing falls back).
- **Cross-check DB.** `SELECT count(*) …` for that owner’s colmeias under the same filters the product uses; the number in the reply must match.
- **Proof.** Save XML, headers, and the SQL count used for cross-check under `artifacts/webhook-linked-count/`.

## Gotchas

- Fake `OPENAI_API_KEY` against the real API yields help, not count — do not mark count verified from a help reply.
- Default excluded status (`perdida`) affects plain totals; assert against the product rule, not raw table counts.
- README “interim linked greeting” is obsolete; count/help are the live linked behaviors.
- If OpenAI or a stub is unavailable, report the feature as blocked with the unmet precondition rather than skipping to help.
