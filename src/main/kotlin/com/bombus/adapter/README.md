# Adapters (outermost)

Translate between the outside world and the application ports. All framework,
ORM, and SDK code lives here. Each adapter maps to/from domain types at the
boundary — the core never sees DTOs, requests, or rows.

Depends on: application ports + domain.

- `inbound/web/`        — REST controllers (thin: HTTP ↔ inbound ports, no business logic)
- `inbound/messaging/`  — message/event consumers
- `outbound/persistence/` — repository implementations (JPA entities live here, separate
                            from domain models)
- `outbound/external/`  — third-party API clients
