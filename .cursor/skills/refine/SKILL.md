---
description: Refine phase — turn a high-level one-line feature description into a planning document (tradeoffs, architecture, epics split into deployable deliverables) that feeds the implement agents
globs:
alwaysApply: false
---

# Skill: Refine

You receive a **high-level, often vague description** of something to build, e.g. "implement a CRUD app for managing bee hives" or "implement an AI-powered chatbot". Your job is **not** to write code. Your job is to produce a **planning document** that another agent (running the Implement skill) can pick up and execute deliverable by deliverable.

You own: scope, assumptions, tradeoffs, architecture shape, and the breakdown into epics and deployable deliverables.
You do NOT own: implementation details, concrete code, test code, file-level cohesion — those belong to the implement agents.

Reference docs: `architecture-rules.md`, `kotlin-springboot-best-practices.md`, `api-best-practices.md`, `testing-best-practices.md`.

## Ask first — do NOT assume

**This is the moment to ask every question.** Do not invent requirements, pick a "reasonable default," or guess at intent. Anything that would otherwise become an assumption must instead become a question to the human. Only after the questions are answered do you proceed to the rest of the process. If new unknowns surface mid-planning, stop and ask rather than assume.

Gather answers across two sections — **Backend** and **Frontend** — covering at least:

### Backend questions
- [ ] **Scope & capabilities:** what must the system do; what is explicitly out of scope for now?
- [ ] **Users & access:** who uses it; authentication and authorization needs; roles/permissions?
- [ ] **Data:** core entities and relationships; expected volume and growth; retention/compliance constraints?
- [ ] **Scale & performance:** expected request/throughput; latency targets; peak vs. average?
- [ ] **Integrations:** external systems, third-party APIs, events the system must produce or consume?
- [ ] **AI/LLM (if applicable):** hosted vs. self-hosted model; privacy of data sent to the model; cost ceiling; acceptable latency; quality bar?
- [ ] **Persistence & infra:** any mandated datastore, cloud, or deployment target; existing services to reuse?
- [ ] **Non-functionals:** availability, consistency needs (strong vs. eventual), audit, observability requirements?

### Frontend questions
- [ ] **Surface & platform:** web, mobile, both; responsive/desktop-first; any existing app to extend?
- [ ] **Users & flows:** key screens and primary user journeys; number/type of distinct UI roles?
- [ ] **Stack & constraints:** mandated framework/design system; existing component library; accessibility/i18n requirements?
- [ ] **Interaction needs:** real-time updates, offline support, file uploads, notifications?
- [ ] **Auth UX:** login method (SSO, email/password, OAuth); session vs. token expectations?
- [ ] **Look & scope:** is a polished UI required now, or a minimal functional one; branding constraints?

If the human cannot answer something, record it as an explicit **open question** in the output rather than silently assuming a value.

## Process

(Begin only after the questions above are answered.)

- [ ] **Restate the request** in your own words, reflecting the answers received. Do not introduce intent the human didn't confirm.
- [ ] **Record the answers** as the confirmed requirements (this replaces guessing). Anything still unknown stays in the open-questions list.
- [ ] **Derive capabilities.** Turn the confirmed requirements into a concrete list of capabilities/use cases the system must support.
- [ ] **Identify features / bounded contexts** (feature-first per `architecture-rules.md`) and how they relate.
- [ ] **Work the tradeoffs** (see calculation guidance below) and record the decisions with reasoning, not just conclusions.
- [ ] **Describe the target architecture** and any improvements over a naive approach, mapped onto the hexagonal layering and the Kotlin/Spring conventions.
- [ ] **Break the work into epics, then into deployable deliverables.** Each deliverable must be independently shippable and small enough to hand to one implement agent.
- [ ] **Sequence the deliverables** with dependencies and a suggested order (walking skeleton first).
- [ ] **Flag risks and open questions** that need a human decision before or during implementation.

## Tradeoff calculations

For each significant decision, don't just assert — show the reasoning so a reviewer can disagree with the inputs:

- [ ] Name the decision and 2–3 viable options.
- [ ] For each option, note the axes that matter for *this* system: development cost/speed, operational complexity, scalability ceiling, latency, $ cost, team familiarity, lock-in, failure modes.
- [ ] Where numbers help, estimate them using the **confirmed** figures from the requirements (e.g. "~50 hives × N readings/day → rows/year; comfortably a single Postgres table, no sharding needed" or "stated ≤ X req/s → a single instance suffices, no queue yet"). If a needed number wasn't provided, ask — don't assume it.
- [ ] Recommend one option and state the condition that would flip the decision ("revisit if write volume exceeds …").

Examples of decisions to weigh depending on the request: datastore choice (relational vs document), sync vs async/eventing, monolith vs split services, build-vs-buy for the AI layer (hosted LLM API vs self-host), caching, read model separation, auth approach.

## AI-specific (only when the request involves AI/LLM features)

- [ ] Decide model sourcing: hosted API vs self-hosted, with cost/latency/privacy tradeoffs.
- [ ] Define how the AI capability hides behind an **outbound port** so the core stays provider-agnostic and testable (fake the port in unit tests).
- [ ] Note context/state strategy (stateless calls + history passing, retrieval, etc.), prompt/version management, rate limits, fallback/timeout behavior, and cost controls.
- [ ] Call out evaluation: how AI output quality is measured, since it can't be unit-tested like deterministic logic.

## Required output document

Produce a `.txt` document with these sections:

1. **Summary** — restated request reflecting the confirmed answers (no invented intent).
2. **Confirmed requirements** — the answers received, split into **Backend** and **Frontend** subsections. No assumptions; anything unanswered goes to open questions, not here.
3. **Capabilities / use cases** — the concrete list.
4. **Architecture overview** — features/bounded contexts, how they map to hexagonal layers, and improvements over the naive approach. A simple text diagram is fine.
5. **Key decisions & tradeoffs** — one block per decision with options, the calculation/estimate, the recommendation, and the flip condition.
6. **Risks & open questions** — what needs a human call.
7. **Delivery plan** — epics, each broken into **deployable deliverables**. For every deliverable include:
   - Goal (one sentence)
   - Scope: features/ports/adapters/endpoints touched
   - Dependencies (which deliverables must land first)
   - Acceptance criteria (observable, testable outcomes)
   - Notes/risks for the implement agent
   Keep each deliverable independently shippable; order them with a walking skeleton first.

Write for a downstream agent: precise, unambiguous, no code. Each deliverable should be actionable on its own without re-reading the whole document.

## Done when

An implement agent can take any single deliverable from the Delivery plan and build it — knowing the feature, ports, contracts, acceptance criteria, and dependencies — without needing decisions that this document should have made.