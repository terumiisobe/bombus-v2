# Open Issues

## epic-5 — ConversationAiPort + OpenAI adapter (review 2026-07-03)

- [ ] 🟡 `CountVocabulary`/`SpeciesTerm`/`StatusTerm` are structurally identical to colmeia's `ColmeiaVocabulary`/`SpeciesRef`/`StatusRef` — documented as deliberate bounded-context decoupling (D6 maps between them); confirm this exact-mirror duplication is the intended tradeoff (`src/main/kotlin/com/bombus/chatbot/domain/CountVocabulary.kt`).
- [ ] 🟢 `catch (_: Exception)` in `parseIntent`/`phraseReply` is broad (swallows programming errors too); consider narrowing to `RestClientException`/IO/JSON (`src/main/kotlin/com/bombus/chatbot/adapter/outbound/external/OpenAiConversationAdapter.kt`).
- [ ] 🟢 The `openAiRestClient` bean's bearer-auth + timeout wiring is not exercised by any test (adapter test builds a bare `RestClient`) (`src/main/kotlin/com/bombus/config/OpenAiConfig.kt`).

## epic-4 — Colmeia Count (review 2026-07-03)

- [ ] 🟢 `StatusColmeiaLookupAdapter.findIdByName` has no direct integration test — only exercised indirectly via `perdidaId` in `ColmeiaCountAdapterIntegrationTest`; the unknown-name → `null` path is only faked in the unit test (`src/main/kotlin/com/bombus/colmeia/adapter/outbound/persistence/StatusColmeiaLookupAdapter.kt`).
- [ ] 🟢 No test for a breakdown query that also carries an explicit `statusId` (the `includeStatusId` branch in `CountColmeiasService.breakdown`) (`src/main/kotlin/com/bombus/colmeia/application/CountColmeiasService.kt`).

## epic-6 — Chat session decisions (implemented in D6)

Decisions pinned down for the `sessao_chat` persistence (`ChatSessionPort` + `SessaoChatAdapter`) and the `HandleIncomingWhatsAppMessage` orchestration. All implemented:

- [x] **Session TTL: 15 minutes, sliding.** On each turn `expires_at = last_message_at + ttl` (refreshed every message). On read, a row with `expires_at <= now()` is treated as a fresh conversation (empty context) — expiry policy lives in `HandleIncomingWhatsAppMessageService` with an injected `Clock`.
- [x] **Context window: last 5 messages, bounded.** A "turn" is one `{role, text}` message; `conversation_context` is trimmed to the most recent `maxContextMessages` before persisting.
- [x] **Both values configurable** via `ChatSessionProperties` (`@ConfigurationProperties(prefix = "chatbot.session")`): `chatbot.session.ttl` (a `Duration`, default 15m) and `chatbot.session.max-context-messages` (default 5).

Deferred (out of scope for D6, tracked for later):

- [ ] 🟡 **Async Twilio REST reply escape hatch (D-3).** The reply is currently returned synchronously as TwiML in the webhook response (`TwilioWhatsAppWebhookController`), which is fine while a turn (2 DB queries + 1-2 LLM calls, ~1-3s) fits inside Twilio's ~10-15s webhook timeout. If real-world LLM latency starts exceeding that budget, switch to: immediately `200` the webhook, compute, then send the message via a second outbound Twilio REST call. Would introduce a `WhatsAppSenderPort` (defined-but-unimplemented for now per the plan) + a Twilio REST adapter and Account SID/API credentials — no chatbot core changes. Only worth doing if timeouts are observed in practice.
