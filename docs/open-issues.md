# Open Issues

## epic-4 — Colmeia Count (review 2026-07-03)

- [ ] 🟢 `StatusColmeiaLookupAdapter.findIdByName` has no direct integration test — only exercised indirectly via `perdidaId` in `ColmeiaCountAdapterIntegrationTest`; the unknown-name → `null` path is only faked in the unit test (`src/main/kotlin/com/bombus/colmeia/adapter/outbound/persistence/StatusColmeiaLookupAdapter.kt`).
- [ ] 🟢 No test for a breakdown query that also carries an explicit `statusId` (the `includeStatusId` branch in `CountColmeiasService.breakdown`) (`src/main/kotlin/com/bombus/colmeia/application/CountColmeiasService.kt`).

## epic-6 — Chat session decisions (for D6, not yet implemented)

Decisions pinned down ahead of implementing the `sessao_chat` persistence (`ChatSessionPort` + adapter). These belong to D6/Epic 6, not D5.

- **Session TTL: 15 minutes, sliding.** On each turn set `expires_at = last_message_at + 15m` (refreshed every message). On read, treat a row with `expires_at <= now()` as a fresh conversation (empty context).
- **Context window: last 5 messages, bounded.** A "turn" is one `{role, text}` message; trim `conversation_context` to the most recent 5 before persisting.
- **Both values must be configurable**, not hardcoded — follow the existing `@ConfigurationProperties` pattern (cf. `ColmeiaCountProperties`), e.g. `chatbot.session.ttl: 15m` (a `Duration`) and `chatbot.session.max-context-messages: 5`, with those as defaults.
