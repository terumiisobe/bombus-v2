---
description: Review phase — verify a change against architecture, Kotlin/Spring, API, and testing guidelines before merge
globs:
alwaysApply: false
---

# Skill: Review

Use this when reviewing a change (your own or someone else's) before merge. Run the verification checklist internally, then produce the **review summary** below. The checklist is your private rubric — do not echo it back item by item. The output is a prioritized list of findings grouped by severity, plus a short scope/coverage/tradeoff verdict.

Inputs: the change/diff, and the **deliverable** from the Refine planning doc it claims to implement (goal, scope, acceptance criteria).

Reference docs: `architecture-rules.md`, `kotlin-springboot-best-practices.md`, `api-best-practices.md`, `testing-best-practices.md`.

## Required output

Keep the whole output tight. Lead with the verdict, then findings by severity, then the three short verdicts. Only include a finding if it's real — never pad a section to fill it. If a severity level has no findings, write "None."

### Verdict
One line: `Ready to merge` / `Merge after fixing blockers` / `Needs rework`. Follow with a one-sentence reason.

### 🔴 Blockers (must fix before merge)
Each item: one line stating the problem, then `→` the file/location and the fix. Blockers are: in-scope acceptance criteria that are missing/incomplete/stubbed, unsigned-off deviations from the Refine plan, inward-dependency violations, business logic leaking into controllers/repositories, exposed entities/domain models over the wire, missing tests for new behavior or bug fixes, security gaps (auth at wrong boundary, unparameterized queries, secrets in code), and missing migrations for schema changes.

### 🟡 Should fix (non-blocking, fix soon)
Each item: one line + location. Examples: thin-controller violations that still work, missing pagination, inconsistent error envelope, `!!` usage, field injection, weak test coverage on edge/failure paths, out-of-scope code that should move to a separate deliverable.

### 🟢 Minor (nits, optional)
Naming, dead code, commented-out blocks, leftover debug logging, stale docs/README. Group these into a single line each where possible.

### Scope
Two to four lines max: what the deliverable asked for that's delivered, what's missing, and anything present that's out of scope. Map missing items to the Blockers above rather than repeating them.

### Test coverage
Two to three lines: which acceptance criteria have tests, coverage by layer (unit / slice / Testcontainers), and the most important untested path. Point to Blockers/Should-fix rather than re-listing.

### Tradeoffs
List only real tradeoffs the change made (shortcuts, deferred work, deviations) with the reason and when to revisit. Flag any Refine-plan contradiction needing sign-off. If none, write "No notable tradeoffs."

## Verification checklist (internal rubric — do not echo)

Walk every box; surface only what fails or is noteworthy into the severity sections above.

### Architecture (hexagonal)
- Dependencies point inward: domain depends on nothing; application on domain + ports; adapters on ports.
- No infrastructure imports (Spring/JPA/HTTP) in `domain/` or `application/`.
- Code placed under the correct feature; no cross-feature imports of another feature's domain/adapters.
- New external dependency expressed as an outbound port, implemented in an adapter; ports small and intention-revealing.
- Business rules in the domain/use case, not controllers or repositories. Boundary mapping (DTO/entity ↔ domain) present.

### Kotlin & Spring
- Constructor injection only; no field `@Autowired`. Dependencies are `val`.
- No `!!`; nullability explicit; immutability preferred (`val`, read-only collections).
- JPA entities separate from domain models; entities not `data class`; equality on a business key.
- `@Transactional` only at the use-case boundary; queries `readOnly`; no proxy self-invocation.
- Config bound via `@ConfigurationProperties`; secrets externalized; migrations present for schema changes.

### API
- Controllers thin; no business logic or persistence in the web layer.
- Request/response DTOs used; no entities/domain models exposed over the wire.
- Correct verbs/status codes; collections paginated; API versioned and backward-compatible.
- Consistent error envelope (RFC 7807); no internals leaked; OpenAPI docs updated.
- Idempotency handled for retryable unsafe operations.

### Validation, errors, security
- Input validated at the edge; domain invariants enforced in the model.
- Errors mapped centrally via `@RestControllerAdvice`; no stack traces returned.
- Security: deny-by-default, authorization at the use-case boundary, input parameterized (no string-built queries), least privilege.

### Testing
- New behavior has tests; bug fixes include a test that fails without the fix.
- Domain/use-case logic covered by Spring-free unit tests; adapters covered by slice/Testcontainers tests.
- Tests deterministic (no real network, controlled time/randomness, no ordering dependence); each manages its own state.
- Test names describe scenario + outcome; failure/edge cases covered, not only the happy path.
- No flaky or disabled tests left without a tracked reason.

### General
- Change scoped to the refined plan; no unrelated drive-by changes.
- Names clear; no dead code, commented-out blocks, or leftover debug logging.
- Docs/README updated if behavior or contract changed.

## Done when

The review summary is produced (verdict, findings by severity, scope/coverage/tradeoffs) and every checklist item has been walked. Any Blocker — including an in-scope missing item or an unsigned-off Refine deviation — must be resolved or explicitly agreed before merge.