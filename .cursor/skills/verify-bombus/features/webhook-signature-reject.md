# Webhook signature reject

Unsigned or falsely signed inbound WhatsApp webhook posts are rejected so only Twilio-authenticated traffic reaches the bot.

## Sub-features

- `sig-missing` rejects posts with no `X-Twilio-Signature` header.
- `sig-invalid` rejects posts with a non-matching signature value.
- `sig-problem-json` returns RFC 7807 problem details with HTTP 403.

## How to get to it (user POV)

- `POST /v1/whatsapp/webhook` as `application/x-www-form-urlencoded` without a valid Twilio signature (direct HTTP client, misconfigured ngrok URL, or wrong auth token).

## Driving it with curl

Preconditions:

- Bombus is healthy at `$BASE_URL` from the current verification run.
- Doctor PASS.
- Do **not** use `post-webhook.sh` for the reject cases (it always signs correctly).

- **Missing signature.** Post form fields without the header. Run `curl -sS -D .cursor/skills/verify-bombus/artifacts/webhook-signature-reject/missing-headers.txt -o .cursor/skills/verify-bombus/artifacts/webhook-signature-reject/missing-body.json -w '%{http_code}\n' -X POST "$BASE_URL/v1/whatsapp/webhook" -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode 'From=whatsapp:+15550001111' --data-urlencode 'Body=oi'`. Observable: HTTP `403`; body is problem+json mentioning signature verification failure / Forbidden.
- **Invalid signature.** Post with a junk header. Run `curl -sS -D .cursor/skills/verify-bombus/artifacts/webhook-signature-reject/invalid-headers.txt -o .cursor/skills/verify-bombus/artifacts/webhook-signature-reject/invalid-body.json -w '%{http_code}\n' -X POST "$BASE_URL/v1/whatsapp/webhook" -H 'Content-Type: application/x-www-form-urlencoded' -H 'X-Twilio-Signature: definitely-not-valid' --data-urlencode 'From=whatsapp:+15550001111' --data-urlencode 'Body=oi'`. Observable: HTTP `403`; problem+json; no TwiML `<Message>`.
- **Proof.** Copy a matching warn line from `runs/<RUN_ID>/app.log` (rejected webhook) into `artifacts/webhook-signature-reject/log-excerpt.txt`.

## Gotchas

- A 403 from a **wrong** `TWILIO_PUBLIC_BASE_URL` on an otherwise correctly signed request is also signature failure — when proving reject, use an obviously missing/invalid header so the cause is unambiguous.
- Spring Security permits the path; the 403 comes from Twilio signature validation, not from form login.
- Do not treat GET `/v1/whatsapp/webhook` as the primary reject proof (POST is the user path).
