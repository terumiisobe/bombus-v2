# Webhook linked colmeia CRUD

Linked customers can list, create, record field-visit status updates, soft-exit (`perdida` / `vendida`), and — only for registry mistakes — hard-delete their own colmeias through chat tools. Prefer soft disposition over hard delete. Access is membership-scoped (`meliponario_membro`); `owner_id` alone grants nothing.

## Sub-features

- `list-compact` returns code + species common name + status (no internal id). If more than 10 hives, returns per-species counts instead. Default list excludes `perdida` and `vendida` (same config list as count — do not invent a second exclude policy).
- `create-hive` inserts a colmeia on the lowest-id meliponário the user is a member of; `code`/`startDate` only when the user provides them (else null); default status `desenvolvendo`.
- `visit-status` (`update_colmeia`) identifies by code and appends historico (acompanhamento/visita); optional `note` only when the user supplied one.
- `soft-exit` marks `perdida` or `vendida` via `update_colmeia` when the hive left the yard for real; row and history stay; code stays on the row but soft-uniqueness frees it for a new create (#12).
- `hard-delete` removes the row after `confirmed=true` — **only** for erro de cadastro (never existed / wrong entry). Prefer soft-exit for real-world exits.
- `membership` never mutates hives outside yards the user belongs to.

## How to get to it (user POV)

- Linked customer asks to list/create/update status, mark lost/sold, or (rarely) erase a mistaken registry entry in natural pt-BR.
- Locally: seed linked user + meliponário + membership, drive webhook with an OpenAI stub that emits the CRUD tools, and assert DB side effects.

## Driving it with curl

Preconditions:

- Doctor PASS; Flyway tables exist.
- Seed linked user, a meliponário, and a `meliponario_membro` row for that user and yard. `owner_id` alone grants nothing. For update/delete, seed at least one colmeia in a yard the user is a member of. Create lands in the lowest-id yard that user belongs to.
- Prefer an `OPENAI_BASE_URL` stub that returns `tool_calls` for `list_colmeias` / `create_colmeia` / `update_colmeia` / `delete_colmeia` then a final reply. Without a stub or real OpenAI, prove mutations with DB scripts mirroring the use cases and still capture SQL before/after; do not treat agent fallback as CRUD proof.

- **List.** `post-webhook.sh --from '+15550005678' --body 'liste minhas colmeias' --out .cursor/skills/verify-bombus/artifacts/webhook-linked-colmeia-crud/list-response.xml --headers .cursor/skills/verify-bombus/artifacts/webhook-linked-colmeia-crud/list-headers.txt`. Observable: HTTP 200; reply reflects tool list payload (or DB-side list proof if stubbed offline); default list omits `perdida`/`vendida`.
- **Soft exit then reuse code.** Body like `a colmeia 1 foi vendida` (or `perdi a 1`). Capture `psql` under `artifacts/webhook-linked-colmeia-crud/` showing: active row → current status `vendida`/`perdida` (row still present) → new `create_colmeia` can reuse code N.
- **Hard delete only for cadastro mistake.** Body that states erro de cadastro + explicit confirm. Capture SQL: insert with code N → row gone after `delete_colmeia` → new insert reusing code N. Do **not** use hard delete as the primary proof for “saiu do plantel”.
- **Proof.** Save webhook responses when available, SQL transcripts, and note whether OpenAI was stubbed.

## Gotchas

- Soft disposition is the default product path: prefer `perdida`/`vendida` over `delete_colmeia`.
- Hard delete removes the `colmeia` row (cascades history/location); reserve it for erro de cadastro.
- Delete tool requires `confirmed=true`; the system prompt tells the model to warn that the action is final before calling it.
- Default count/list exclude list is `colmeia.default-excluded-statuses: [perdida, vendida]` — follow that; do not fork a second policy in verify scripts.
- Codes remain on soft-exited rows; reuse is via soft uniqueness, not nullifying the code.
- Membership (`meliponario_membro`) is the access model — do not invent a second one; `owner_id` alone grants nothing.
- Fake OpenAI key against the real API is fallback, not CRUD proof.
