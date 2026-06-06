# bombus-v2

Bee hive management app.

## Local development

### Prerequisites

- Java 21, Maven
- Docker (for Postgres)
- [ngrok](https://ngrok.com/) (for exposing the local webhook to Twilio)
- A Twilio account with the WhatsApp Sandbox enabled

Copy `.env.example` to `.env` (or create `.env` from the template in the repo docs) and set at least:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — local Postgres connection
- `FLYWAY_ENABLED=true` — Flyway creates the schema on app startup (required for a fresh database)
- `TWILIO_AUTH_TOKEN` — Twilio account auth token (used for webhook signature validation)
- `TWILIO_PUBLIC_BASE_URL` — full ngrok `https://…ngrok-free.app` URL (update whenever ngrok restarts)

### Start Postgres

```bash
docker compose -f docker/docker-compose.yml up -d
```

### Manual test: WhatsApp webhook (customer identity)

The webhook resolves the sender's phone number to a linked customer and replies with an interim pt-BR message (linked vs not linked). End-to-end testing uses the Twilio WhatsApp Sandbox and ngrok.

**Order matters:** Flyway runs when the Spring Boot app starts. A fresh Postgres container has no tables until then — do not seed data before the app has booted at least once.

#### 1. Start the app (creates the schema)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Confirm Flyway applied migrations in the logs, e.g.:

```
Migrating schema "public" to version "1 - baseline"
Migrating schema "public" to version "2 - reference data"
```

Webhook path: `POST /v1/whatsapp/webhook`  
Full public URL: `{TWILIO_PUBLIC_BASE_URL}/v1/whatsapp/webhook`

#### 2. Verify tables exist

```bash
docker exec bombus-postgres psql -U bombus_usr -d bombus -c "\dt"
```

You should see `usuario`, `usuario_whatsapp`, `colmeia`, and the other baseline tables.

#### 3. Seed a known customer

The lookup matches `usuario_whatsapp.phone_number` exactly in E.164 with a leading `+`, and requires `active = true`. Use the WhatsApp number you will message from (after joining the sandbox):

```bash
docker exec -it bombus-postgres psql -U bombus_usr -d bombus -c \
"INSERT INTO usuario (id, email, password_hash) VALUES (1, 'you@example.com', 'x');
 INSERT INTO usuario_whatsapp (usuario_id, phone_number, display_name, active)
 VALUES (1, '+55XXXXXXXXXXX', 'Seu Nome', true);"
```

Replace `+55XXXXXXXXXXX` with your number. Omit this step (or use a different number) to test the "not linked" reply.

#### 4. Expose the app with ngrok

```bash
ngrok http 8080
```

Set `TWILIO_PUBLIC_BASE_URL` in `.env` to the full `https://….ngrok-free.app` URL (no trailing slash). Restart the app if you change it — the value must match exactly what Twilio calls, or signature validation returns 403.

#### 5. Configure the Twilio Sandbox

In Twilio Console → Messaging → Try it out → WhatsApp Sandbox → Sandbox settings:

- **When a message comes in:** `https://<your-ngrok-subdomain>.ngrok-free.app/v1/whatsapp/webhook`
- **Method:** `POST`

#### 6. Send a message

From WhatsApp, send `join <your-sandbox-code>` to the sandbox number (e.g. `+14155238886`), then send any message.

**Expected replies**

| Scenario | Reply |
|----------|--------|
| Seeded, active number | `Olá, Seu Nome! Identifiquei sua conta. Em breve poderei contar suas colmeias.` |
| Unknown or inactive number | `Não encontrei uma conta vinculada a este número. Por favor, entre em contato com o suporte.` |

#### Troubleshooting

| Symptom | Likely cause |
|---------|----------------|
| `relation "usuario" does not exist` when seeding | Schema not created yet — start the app first (step 1), then seed (step 3). |
| HTTP 403 from webhook | `TWILIO_PUBLIC_BASE_URL` or `TWILIO_AUTH_TOKEN` does not match Twilio — check ngrok URL (full host, `https`, no trailing slash) and auth token. Inspect requests at `http://127.0.0.1:4040`. |
| Always "not linked" | `phone_number` in DB does not exactly match Twilio's `From` after stripping `whatsapp:` — must be E.164 with `+`, and `active = true`. |
| App won't start | Empty DB with `FLYWAY_ENABLED=false` and `ddl-auto=validate` — enable Flyway or apply migrations another way. |

Quick sanity check without WhatsApp: `curl http://localhost:8080/v1/whatsapp/webhook` should return 403 (missing signature), confirming the endpoint is up.

**Note:** `docker compose down -v` wipes the database; boot the app again and re-seed before retesting.
