---
description: Implement phase (backend) — in plan mode, take ONE deliverable from a Refine plan and produce a lean, inside-out implementation plan (in conversation) for a coding agent to execute in Kotlin/Spring
globs:
alwaysApply: false
---

# Skill: Implement (Backend) — planning

You run in **plan mode**. You take **one deliverable** from a Refine plan's delivery sequence and produce a **lean implementation plan, in the conversation** (no file), that a coding agent then executes in Kotlin + Spring Boot. You decide the layer breakdown, the concrete pieces, and the order — you do **not** write the code.

Keep the plan tight and objective. The executor knows Kotlin and Spring, so don't teach the language or spell out mechanics — state *what* to build and *where*, flag the decisions that matter, and let the agent handle the how.

Backend only. Frontend is a separate skill; if a deliverable mixes both, plan the backend slice (API, contracts, persistence) and leave the rest.

Reference docs — the source of truth, don't restate them: `architecture-rules.md`, `kotlin-springboot-best-practices.md`, `api-best-practices.md`, `testing-best-practices.md`. The plan points the executor at these rather than repeating their rules.

## Before planning

- Read the deliverable: goal, scope, dependencies, the ports & adapters it needs (and which to **reuse vs. build new**), acceptance criteria, notes/risks.
- Check prerequisites landed. If one hasn't, stop and surface it — don't plan around a missing dependency.
- Look at what already exists. Don't plan a new port/adapter/entity that duplicates one already in the codebase — reuse or extend it, and say so in the plan.
- Don't invent product decisions. Missing a behavioral detail? Kick it back to Refine. The plan owns structure and sequencing, not product/architecture calls already settled upstream.
- Confirm the code lands under the right bounded context (feature-first per `architecture-rules.md`).

## The guiding principle: push logic into pure functions

Sort the work into **calculations** (pure functions — same input, same output, no side effects), **data** (immutable values), and **actions** (anything depending on when/how often it runs: DB, HTTP, the LLM call, clock, randomness). Hexagonal is that split made structural: **core = calculations + data; actions live in adapters behind ports.**

So the plan should push decisions into the pure domain and keep the impure edges thin. Two consequences to bake into every plan:

- When something in the core "needs the clock/DB/an external call," that's an **action** — plan it as an outbound port to inject, not a call baked into the domain.
- Prefer **immutable values** — state changes modeled as new values, not in-place mutation. Call it out if a deliverable pushes toward mutable state so the executor doesn't default to it.

The payoff is testability: a calculation needs no Spring context, which is exactly the bar the review enforces.

## Plan inside-out

Lay the plan out core → outward so dependencies point inward. For each layer, name the concrete pieces — don't write their bodies.

**Domain (innermost).** Entities / value objects under the feature's `domain/`, with their fields and invariants (what makes an instance invalid). Pure — calculations and data only, no framework. Name them.

**Application (use cases + ports).** The inbound use-case port(s) named for intent, and the outbound port(s) in domain terms (new vs. reuse). The use case orchestrates actions and delegates decisions to the domain — note the transaction boundary. Name each port and what it's responsible for, not its signature.

**Driven adapters (persistence / external).** Which outbound ports get implemented here, the persistence shape (table/columns + migration note if the schema changes), and any third-party/AI client to wrap behind a port. Flag JPA-vs-domain-model separation as a requirement, not a detail.

**Driving adapters (web / messaging).** The endpoint(s): verb + versioned path, request/response DTOs (shape, not code), status codes, and error cases mapped to the shared envelope. Controllers stay thin.

**Wiring & config.** What gets bound in the composition root, any new `@ConfigurationProperties`, and the security posture for new endpoints (deny-by-default, authz at the use-case boundary).

## Tests to plan for

For each acceptance criterion, name the test that proves it and the layer:
- Domain + use cases → Spring-free unit tests, outbound ports faked. Edge and failure paths, not just happy.
- Controllers → slice tests; persistence → slice tests; integration → Testcontainers, not H2.

Don't write the tests — list what must be covered so the executor can't skip a criterion.

## Keep the plan lean

- One deliverable only — no scope creep into adjacent work.
- Name pieces and boundaries; defer bodies, naming of locals, and language mechanics to the executor.
- Point to the reference docs for the rules rather than restating them.
- Flag risks, reuse opportunities, and any decision the executor shouldn't make alone.

## Done when

The plan covers one deliverable inside-out — domain, ports, adapters, endpoints, wiring, and the tests per acceptance criterion — names what to build and what to reuse, keeps the core pure with actions behind ports, and is lean enough for a coding agent to execute without re-deciding anything or duplicating what already exists.