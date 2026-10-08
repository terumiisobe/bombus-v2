# Webhook linked count

A correctly signed message from a seeded, active WhatsApp user that asks how many hives they have receives a pt-BR reply whose numbers come from the `count_colmeias` tool (colmeia use cases), not from the model inventing totals.

## Sub-features

- `count-plain` answers “how many hives?” with a total (default status exclusion applies in the domain).
- `count-tool` requires the agent to call `count_colmeias` (and optionally `list_vocabulary`) then phrase a final reply.
- `count-needs-openai` needs working OpenAI (or an `OPENAI_BASE_URL` stub that returns tool_calls / final content). A fake key against the real API yields fallback — that is **not** a count proof.

## How to get to it (user POV)

- Linked customer sends WhatsApp text such as `quantas colmeias eu tenho?`, `quantas jataí?`, or `quantas colmeias estáveis?`.
- Locally: seed user + optional colmeias, then `post-webhook.sh`.

## Driving it with curl

Preconditions:

- Doctor PASS; Flyway tables exist.
- Working OpenAI credentials **or** `OPENAI_BASE_URL` pointing at a stub that speaks the chat-completions tool-calling protocol used by `OpenAiConversationAdapter`. With only a fake key and the real OpenAI host, completion fails → `chatbot.agent.fallback-reply` — do not treat that as count verification.
- Seed user and at least one countable colmeia owned by that user (species/status via reference data). Example sketch (IDs depend on migrations):

```bash
# After boot: create verify user + whatsapp + meliponario + colmeia rows.
# Access is a row in meliponario_membro. owner_id alone grants nothing.
# INSERT INTO meliponario_membro (usuario_id, meliponario_id) VALUES (<user_id>, <meliponario_id>);
# Use reference especie/status ids from the DB; exclude or include status per domain rules.
```

- Env from `meta.env` exported; if using a stub, relaunch with `OPENAI_BASE_URL` set before proving.

- **Ask for a count.** Run `.cursor/skills/verify-bombus/scripts/post-webhook.sh --from '+15550005678' --body 'quantas colmeias eu tenho?' --out .cursor/skills/verify-bombus/artifacts/webhook-linked-count/response.xml --headers .cursor/skills/verify-bombus/artifacts/webhook-linked-count/headers.txt`. Observable: HTTP `200`; TwiML `<Message>` contains the numeric total that matches the tool/DB count (not the fallback apology).
- **Cross-check DB.** Count colmeias in meliponários where that user has a `meliponario_membro` row, under the same filters the product uses. The number in the reply must match. `owner_id` alone does not include a yard.
- **Proof.** Save XML, headers, and the SQL count used for cross-check under `artifacts/webhook-linked-count/`.

## Gotchas

- Fake `OPENAI_API_KEY` against the real API yields fallback help-ish text, not a trusted count — do not mark count verified from fallback.
- Default excluded statuses (`perdida`, `vendida` from `colmeia.default-excluded-statuses`) affect plain totals; assert against that product list — do not invent a second exclude policy. Soft disposition uses the same statuses.
- Species replies may include the scientific name and an inference disclosure when the model matched vocabulary loosely; those are model-phrased from tool JSON (`speciesScientificName`, labels), not extra DB fields.
- Unknown `speciesId`/`statusId` return tool error JSON instead of a silent zero — a “0 colmeias” reply with a made-up species name is a model failure, not an empty SQL count.
- README “interim linked greeting” is obsolete; linked behavior is the tool-calling agent.
- If OpenAI or a stub is unavailable, report the feature as blocked with the unmet precondition rather than skipping to fallback.
