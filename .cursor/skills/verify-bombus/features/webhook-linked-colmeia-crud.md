# Webhook linked colmeia CRUD

Linked customers can list, create, update status, and hard-delete their own colmeias through chat tools. Delete is permanent; the model must confirm with the user first.

## Sub-features

- `list-compact` returns code + species common name + status (no internal id). If more than 10 hives, returns per-species counts instead.
- `create-hive` inserts a colmeia on the customer’s single meliponário; `code`/`startDate` only when the user provides them (else null); default status `desenvolvendo`.
- `update-hive` identifies by code and changes status only (appends history).
- `hard-delete` removes the row after `confirmed=true` (frees the code).
- `ownership` never mutates another customer’s hives.

## How to get to it (user POV)

- Linked customer asks to list/create/update/remove a hive in natural pt-BR.
- Locally: seed linked user + meliponário, drive webhook with an OpenAI stub that emits the CRUD tools, and assert DB side effects.

## Driving it with curl

Preconditions:

- Doctor PASS; Flyway tables exist.
- Seed linked user, meliponário, and (for update/delete) at least one owned colmeia.
- Prefer an `OPENAI_BASE_URL` stub that returns `tool_calls` for `list_colmeias` / `create_colmeia` / `update_colmeia` / `delete_colmeia` then a final reply. Without a stub or real OpenAI, prove mutations with DB scripts mirroring the use cases and still capture SQL before/after; do not treat agent fallback as CRUD proof.

- **List.** `post-webhook.sh --from '+15550005678' --body 'liste minhas colmeias' --out .cursor/skills/verify-bombus/artifacts/webhook-linked-colmeia-crud/list-response.xml --headers .cursor/skills/verify-bombus/artifacts/webhook-linked-colmeia-crud/list-headers.txt`. Observable: HTTP 200; reply reflects tool list payload (or DB-side list proof if stubbed offline).
- **Create then hard-delete then reuse code.** Capture `psql` before/after under `artifacts/webhook-linked-colmeia-crud/` showing: insert with code N → row gone after delete → new insert reusing code N.
- **Proof.** Save webhook responses when available, SQL transcripts, and note whether OpenAI was stubbed.

## Gotchas

- Hard delete removes the `colmeia` row (cascades history/location).
- Delete tool requires `confirmed=true`; the system prompt tells the model to warn that the action is final before calling it.
- Codes are unique per meliponário at the application layer (no DB unique constraint yet).
- Fake OpenAI key against the real API is fallback, not CRUD proof.
