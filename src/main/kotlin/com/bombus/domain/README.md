# Domain (innermost)

Pure business logic: entities, value objects, domain services, domain events.

- No framework, ORM, HTTP, or I/O imports. No infrastructure annotations
  (`@Service`, `@Component`, `@Transactional`, JPA, etc.).
- Knows nothing about how it is persisted or delivered.
- Rich, persistence-agnostic models; invariants enforced in constructors/factories.

Depends on: nothing.

- `model/`   — entities, value objects, domain events
- `service/` — domain services (logic spanning multiple aggregates)
