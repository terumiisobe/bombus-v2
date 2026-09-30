# Webhook linked colmeia CRUD

Linked customers can list, create, update, and soft-delete (mark `perdida`) their own colmeias through chat tools. Soft-delete never removes rows; it frees the hive `code` for reuse among non-perdida hives.

## Sub-features

- `list-compact` returns a short list (code, species, status, id) suitable for WhatsApp.
- `create-hive` inserts a colmeia owned via the customer’s meliponário with generated or free code.
- `update-hive` changes species and/or status (status appends history).
- `soft-delete-frees-code` appends `perdida` and allows the same code to be reused on create.
- `ownership` never mutates another customer’s hives.

## How to get to it (user POV)

- Linked customer asks to list/create/update/remove a hive in natural pt-BR.
- Locally: seed linked user + meliponário, drive webhook with an OpenAI stub that emits the CRUD tools, and assert DB side effects.

## Driving it with curl

Preconditions:

- Doctor PASS; Flyway tables exist.
- Seed linked user, meliponário, and (for update/delete) at least one owned colmeia.
- Prefer an `OPENAI_BASE_URL` stub that returns `tool_calls` for `list_colmeias` / `create_colmeia` / `update_colmeia` / `soft_delete_colmeia` then a final reply. Without a stub or real OpenAI, prove mutations with DB scripts mirroring the use cases and still capture SQL before/after; do not treat agent fallback as CRUD proof.

- **List.** `post-webhook.sh --from '+15550005678' --body 'liste minhas colmeias' --out .cursor/skills/verify-bombus/artifacts/webhook-linked-colmeia-crud/list-response.xml --headers .cursor/skills/verify-bombus/artifacts/webhook-linked-colmeia-crud/list-headers.txt`. Observable: HTTP 200; reply reflects tool list payload (or DB-side list proof if stubbed offline).
- **Create then soft-delete then reuse code.** Capture `psql` before/after under `artifacts/webhook-linked-colmeia-crud/` showing: insert with code N → status history `perdida` → new insert reusing code N while the old row remains.
- **Proof.** Save webhook responses when available, SQL transcripts, and note whether OpenAI was stubbed.

## Gotchas

- Soft-delete must leave the `colmeia` row; only latest status becomes `perdida`.
- Plain `count_colmeias` still excludes `perdida` — after soft-delete the count drops but the row stays.
- Codes are unique among non-perdida hives per meliponário at the application layer (no DB unique constraint yet).
- Fake OpenAI key against the real API is fallback, not CRUD proof.
