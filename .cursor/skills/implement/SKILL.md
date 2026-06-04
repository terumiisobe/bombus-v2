---
description: Implement phase (backend) — take ONE deployable deliverable from a Refine planning doc and build it in Kotlin/Spring following hexagonal layering
globs:
alwaysApply: false
---

# Skill: Implement (Backend)

You take **one deployable deliverable** from the Delivery plan of a Refine planning document and build it end to end in Kotlin + Spring Boot. You worry about implementation details, tests, and code cohesion — the planning, scope, and tradeoffs were already decided in Refine.

Scope: **backend only.** Frontend deliverables are handled by a separate skill. If a deliverable mixes both, implement the backend slice (API, contracts, persistence) and leave the frontend to its own deliverable.

Reference docs: `architecture-rules.md`, `kotlin-springboot-best-practices.md`, `api-best-practices.md`, `testing-best-practices.md`.

## Before writing code

- [ ] **Read the deliverable** from the planning doc: goal, scope, dependencies, acceptance criteria, notes/risks.
- [ ] **Confirm dependencies have landed.** If a prerequisite deliverable isn't done, stop and surface it — don't stub around it silently.
- [ ] **Re-read the confirmed requirements** (backend section) relevant to this deliverable so behavior matches what was agreed.
- [ ] **Do NOT introduce new product decisions.** If the deliverable is missing a detail you'd have to assume, raise it back to Refine rather than inventing it. Implementation choices (naming, structure, libraries within the agreed stack) are yours; product/architecture choices are not.
- [ ] **Identify the target feature** and confirm code lands under the correct bounded context (feature-first per `architecture-rules.md`).

## Build inside-out

Work from the core outward so dependencies always point inward.

### Domain (innermost)
- [ ] Implement entities / value objects under the feature's `domain/`. Enforce invariants in constructors/factory methods that reject invalid state.
- [ ] Keep the domain pure: no Spring, JPA, HTTP, or framework annotations; no `!!`; prefer `val` and immutable collections.
- [ ] Use `data class` for value objects; sealed classes/interfaces for closed state hierarchies; put behavior on the types (no anemic models).

### Application (use cases + ports)
- [ ] Define the inbound port interface(s) for the use case(s) in `application/port/in`, named for intent (e.g. `PlaceOrderUseCase`).
- [ ] Define outbound port interface(s) in `application/port/out`, expressed in domain terms, owned by the core.
- [ ] Implement the use case in `application/usecase`, depending only on domain + outbound ports (constructor injection of `val` dependencies).
- [ ] Put `@Transactional` on the use-case boundary; mark query-only use cases `readOnly = true`; avoid proxy self-invocation.
- [ ] Throw application/domain exceptions (`NotFoundException`, `ConflictException`, …) on error paths; don't leak infrastructure exceptions.

### Driven adapters (persistence / external)
- [ ] Implement outbound ports in `adapter/out`. Keep JPA entities separate from domain models; map explicitly at the boundary.
- [ ] Use regular classes (not `data class`) for JPA entities; base `equals`/`hashCode` on a business key.
- [ ] Add a Flyway/Liquibase migration for schema changes; don't rely on `ddl-auto` beyond `validate`. Avoid N+1 (fetch joins / entity graphs).
- [ ] Wrap third-party clients (incl. any AI/LLM provider) behind the outbound port; set timeouts and fallback behavior.

### Driving adapters (web / messaging)
- [ ] Implement a thin controller in `adapter/in/web` mapping HTTP ↔ use case calls — no business logic.
- [ ] Use dedicated request/response DTOs; never expose entities or domain models. Validate request DTOs with `@Valid` + Bean Validation.
- [ ] Follow `api-best-practices.md`: correct verbs/status codes, versioned path, pagination on collections, RFC 7807 error envelope, OpenAPI annotations — matching the contract the deliverable specifies.
- [ ] Map domain/application exceptions to HTTP via `@RestControllerAdvice`; never leak stack traces or internals.

### Wiring & config
- [ ] Bind ports to adapters in the `config/` composition root. Bind configuration via `@ConfigurationProperties` data classes (no scattered `@Value`).
- [ ] Keep secrets externalized; ensure `kotlin-spring` / `kotlin-jpa` compiler plugins are enabled.
- [ ] Configure security for new endpoints: deny-by-default `SecurityFilterChain`, authorization at the use-case boundary.

## Tests (write alongside, per `testing-best-practices.md`)

- [ ] Unit-test domain and use cases with no Spring context; fake outbound ports; cover the deliverable's edge and failure cases, not just the happy path.
- [ ] Slice-test controllers (`@WebMvcTest`) and persistence (`@DataJpaTest`); integration-test against Testcontainers, not H2.
- [ ] Test transaction boundaries and lazy loading where relevant.
- [ ] Make every test deterministic and self-contained (own setup/teardown, controlled time/randomness).
- [ ] Ensure each **acceptance criterion** in the deliverable has a corresponding test that proves it.

## Cohesion & hygiene

- [ ] Keep the change scoped to this deliverable — no unrelated drive-by edits.
- [ ] Names clear and consistent with the feature; no dead code, commented-out blocks, or leftover debug logging.
- [ ] Update OpenAPI docs / README if the contract or behavior changed.

## Done when

The deliverable's acceptance criteria are all met and covered by tests, the build and tests pass, dependencies point inward, no infrastructure concern leaked into domain/application, and the change is limited to this deliverable's stated scope.