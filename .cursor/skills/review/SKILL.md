---
description: Review phase — verify a change against architecture, Kotlin/Spring, API, and testing guidelines before merge
globs:
alwaysApply: false
---

# Skill: Review

Use this when reviewing a change (your own or someone else's) before merge. The review has two parts: (1) run the verification checklist below, and (2) produce a **review summary** that maps the change against its deliverable — what's in scope, what's out of scope, test coverage, and any tradeoffs. Any unchecked item is a blocking comment unless explicitly justified.

Inputs: the change/diff, and the **deliverable** from the Refine planning doc it claims to implement (goal, scope, acceptance criteria).

Reference docs: `architecture-rules.md`, `kotlin-springboot-best-practices.md`, `api-best-practices.md`, `testing-best-practices.md`.

## Required output: review summary

Produce this summary alongside the checklist results:

### Scope: in vs. out
- [ ] **In scope & implemented** — list what the deliverable asked for that the change actually delivers, mapping each acceptance criterion to where it's met.
- [ ] **In scope but missing/incomplete** — anything the deliverable required that is absent, partial, or stubbed. These are blocking unless agreed otherwise.
- [ ] **Out of scope but present** — code in the change that goes beyond the deliverable (scope creep, drive-by changes, speculative features). Flag for removal or a separate deliverable.
- [ ] **Out of scope and correctly excluded** — note things a reader might expect but that the deliverable explicitly deferred, so reviewers don't mistake them for gaps.

### Test coverage
- [ ] State which acceptance criteria have corresponding tests and which don't.
- [ ] Note coverage by layer: domain/use-case unit tests, adapter slice tests, Testcontainers integration tests.
- [ ] Call out untested edge/failure paths and any happy-path-only areas.

### Tradeoffs
- [ ] Record any tradeoff the implementation made (shortcuts, deferred optimizations, simplifying choices, deviations from the Refine plan), with the reasoning and the condition under which it should be revisited.
- [ ] If the change contradicts a decision made in Refine, flag it explicitly — it needs sign-off, not silent acceptance.
- [ ] If there are no tradeoffs, say so.

## Verification checklist

### Architecture (hexagonal)
- [ ] Dependencies point inward: domain depends on nothing; application on domain + ports; adapters on ports.
- [ ] No infrastructure imports (Spring/JPA/HTTP) in `domain/` or `application/`.
- [ ] Code is placed under the correct feature; no cross-feature imports of another feature's domain/adapters.
- [ ] New external dependency expressed as an outbound port, implemented in an adapter; ports are small and intention-revealing.
- [ ] Business rules live in the domain/use case, not in controllers or repositories. Boundary mapping (DTO/entity ↔ domain) is present.

### Kotlin & Spring
- [ ] Constructor injection only; no field `@Autowired`. Dependencies are `val`.
- [ ] No `!!`; nullability explicit; immutability preferred (`val`, read-only collections).
- [ ] JPA entities separate from domain models; entities are not `data class`; equality on a business key.
- [ ] `@Transactional` only at the use-case boundary; queries `readOnly`; no proxy self-invocation.
- [ ] Config bound via `@ConfigurationProperties`; secrets externalized; migrations present for schema changes.

### API
- [ ] Controllers thin; no business logic or persistence in the web layer.
- [ ] Request/response DTOs used; no entities/domain models exposed over the wire.
- [ ] Correct verbs/status codes; collections paginated; API versioned and backward-compatible.
- [ ] Consistent error envelope (RFC 7807); no internals leaked; OpenAPI docs updated.
- [ ] Idempotency handled for retryable unsafe operations.

### Validation, errors, security
- [ ] Input validated at the edge; domain invariants enforced in the model.
- [ ] Errors mapped centrally via `@RestControllerAdvice`; no stack traces returned.
- [ ] Security: deny-by-default, authorization at the use-case boundary, input parameterized (no string-built queries), least privilege.

### Testing
- [ ] New behavior has tests; bug fixes include a test that fails without the fix.
- [ ] Domain/use-case logic covered by Spring-free unit tests; adapters covered by slice/Testcontainers tests.
- [ ] Tests deterministic (no real network, controlled time/randomness, no ordering dependence); each manages its own state.
- [ ] Test names describe scenario + outcome; failure/edge cases covered, not only the happy path.
- [ ] No flaky or disabled tests left without a tracked reason.

### General
- [ ] Change is scoped to the refined plan; no unrelated drive-by changes.
- [ ] Names are clear; no dead code, commented-out blocks, or leftover debug logging.
- [ ] Docs/README updated if behavior or contract changed.

## Done when

The review summary is produced (scope in/out, test coverage, tradeoffs) and every checklist box is either checked or has an explicit, agreed justification recorded in the review thread. Any "in scope but missing" item or unsigned-off Refine deviation blocks the merge.