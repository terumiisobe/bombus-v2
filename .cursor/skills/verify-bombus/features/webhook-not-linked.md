# Webhook not linked

A correctly signed WhatsApp message from a phone that is not linked to an active customer receives a fixed pt-BR support reply and does not create a chat session.

## Sub-features

- `not-linked-reply` returns TwiML containing the not-linked support sentence.
- `not-linked-no-session` leaves `sessao_chat` unchanged for that unknown number.

## How to get to it (user POV)

- Twilio delivers `POST /v1/whatsapp/webhook` with `From=whatsapp:+<unlinked E.164>` and any `Body`, with a valid `X-Twilio-Signature`.
- Locally, the same path is exercised with `post-webhook.sh` using a phone that has no `usuario_whatsapp` row (or `active = false`).

## Driving it with curl

Preconditions:

- Doctor PASS for the current run.
- `BASE_URL`, `TWILIO_AUTH_TOKEN`, and `TWILIO_PUBLIC_BASE_URL` exported from `runs/<RUN_ID>/meta.env`.
- Choose a phone that is **not** seeded, e.g. `+15550009999`.

- **Send unsigned-looking message as signed traffic.** Run `.cursor/skills/verify-bombus/scripts/post-webhook.sh --from '+15550009999' --body 'oi' --out .cursor/skills/verify-bombus/artifacts/webhook-not-linked/response.xml --headers .cursor/skills/verify-bombus/artifacts/webhook-not-linked/headers.txt`. Observable: HTTP `200` on stderr line `HTTP 200`; body is TwiML with `<Message>` containing `Não encontrei uma conta vinculada a este número. Por favor, entre em contato com o suporte.`
- **Confirm no session for unknown user.** Optional: `docker exec bombus-postgres psql -U bombus_usr -d bombus -c "SELECT count(*) FROM sessao_chat;"` (or equivalent `psql`) before/after — count should not increase for a never-linked phone.
- **Proof.** Keep `response.xml`, `headers.txt`, and note the exact `From` / `Body` in `artifacts/webhook-not-linked/request.txt`.

## Gotchas

- Signature URL must match `TWILIO_PUBLIC_BASE_URL` + `/v1/whatsapp/webhook` exactly or you will get 403 instead of the not-linked reply.
- Lookup is exact E.164 with leading `+` after stripping `whatsapp:`.
- This path does not call OpenAI; a disposable `OPENAI_API_KEY` is enough to boot the app.
- README historical “interim linked greeting” text is outdated — not-linked copy above matches the running service.
