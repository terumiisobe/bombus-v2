---
description: Refine phase — turn a high-level one-line feature description into a concise planning document that pins down the functionality, surfaces open questions and decisions, and lays out a dependency-ordered delivery plan (inside-out per hexagonal architecture) for implement agents to execute
globs:
alwaysApply: false
---

# Skill: Refine

You receive a **high-level, often vague description** of something to build — e.g. "implement a CRUD app for managing bee hives" or "implement an AI-powered chatbot". Your job is **not** to write production code. It is to produce a **concise plan** that does three things: pins down *what functionality* should be built, surfaces the *open questions and decisions*, and lays out a *delivery plan* an implement agent can execute.

Reference docs: `architecture-rules.md`, `kotlin-springboot-best-practices.md`, `api-best-practices.md`, `testing-best-practices.md`.

## What the plan is for

The plan has three jobs. Everything you write serves one of them:

1. **Functionality** — what should actually be built. If the initial request contains **2 or more distinct features**, say so explicitly and surface it to the human — we may decide to work them separately rather than in one go. Don't silently bundle multiple features into one plan.
2. **Open questions & decisions** — what's unresolved and needs a human call, and the key architectural decisions with their tradeoffs.
3. **Delivery plan** — the ordered, agent-facing build sequence.

Keep the reasoning sections (functionality, questions, decisions) readable and to the point — casual, like you're talking a teammate through it. Keep the **delivery plan precise and neutral** — it's a feed for another agent, not prose to enjoy.

You own: scope, the assumptions made explicit, the key decisions and tradeoffs, and the ordered breakdown of the work with the contracts that matter.
You do not own: full code, full test code, method bodies, or micro-decisions (variable names, private helper structure). Define the interfaces and boundaries; leave the bodies to implementation.

## Ask first — don't assume

Before planning, ask the questions that actually change the design. Don't invent requirements or pick a "reasonable default" for anything load-bearing. If you can't get an answer, record it as an **open question** instead of silently assuming.

Focus only on what moves the architecture or the scope. Typically:

- **Scope:** what must it do; what's explicitly out of scope for now?
- **Multiple features?** does the request actually contain more than one feature? If so, confirm whether to plan them together or separately.
- **Users & access:** who uses it; auth and roles/permissions?
- **Data:** core entities and relationships; rough volume/growth.
- **Scale:** expected throughput and latency targets, if any.
- **Integrations:** external systems, APIs, or events it must produce/consume.
- **AI/LLM (if relevant):** hosted vs self-hosted; data privacy; cost/latency bar.
- **Infra:** any mandated datastore, cloud, or deployment target.

Ask only what's unclear and matters. Skip what the description already answers.

## Working the tradeoffs

For each decision that actually carries weight, show the reasoning so someone can push back on your inputs:

- Name the decision and 2–3 real options.
- For each, note the axes that matter *here*: dev speed, operational complexity, scalability ceiling, latency, cost, familiarity, lock-in, failure modes.
- Where a number helps, estimate it from the **confirmed** figures (e.g. "~50 hives × N readings/day → rows/year — comfortably one Postgres table, no sharding"). If you need a number nobody gave you, ask.
- Recommend one option and state what would flip the decision ("revisit if write volume exceeds …").

Only weigh decisions that carry weight — datastore choice, sync vs async/eventing, monolith vs split, build-vs-buy for an AI layer, caching, read-model separation, auth. Skip the trivial ones.

## AI-specific (only if the request involves AI/LLM)

- Model sourcing: hosted API vs self-hosted, with the cost/latency/privacy tradeoff.
- Hide the AI capability behind an **outbound port** so the core stays provider-agnostic and testable (fake the port in tests).
- Note context strategy (stateless + history passing, retrieval, etc.), prompt/version management, rate limits, timeout/fallback behavior, cost controls.
- Say how you'd evaluate output quality, since it can't be unit-tested like deterministic logic.

## The delivery plan — order inside-out

This is the agent-facing part. Order the steps by **hexagonal dependency direction: from the inside out**. The inside depends on nothing; each outer layer depends only on what's already been built. So, per bounded context:

1. **Domain** — entities, value objects, invariants. Depends on nothing.
2. **Application / ports** — inbound use-case ports and outbound ports (interfaces in domain terms), and the use cases behind them. Depends only on the domain.
3. **Adapters** — inbound (web/controllers) and outbound (persistence, external clients) that implement the ports. Depend on the application layer.
4. **Config / wiring** — composition root, migrations, anything that assembles the above. Last.

Each step depends only on earlier steps, never on later ones. Prefer a walking skeleton first where it helps.

Each delivery step must carry enough for **another agent to pick it up and build it without re-deciding anything**. For each step include:

- **Goal** — one line.
- **Files/packages** — the bounded context and concrete packages/files it creates or touches.
- **Contracts** — the interfaces this step defines or implements: entity fields + invariants, port signatures (name, params, return, thrown exceptions), or endpoint specs (verb + versioned path, request/response DTO shapes, status codes, error cases). Specify contracts, never bodies.
- **Depends on** — which earlier steps must exist first.
- **Acceptance / tests** — the observable outcomes that prove it, and the layer that tests them (unit / `@WebMvcTest` / `@DataJpaTest` / Testcontainers).

## Required output document

Produce a concise `.txt` document with these sections:

1. **Summary** — the restated request reflecting confirmed answers, in a few sentences. If it's more than one feature, call that out here.
2. **Functionality** — the concrete list of what will be built. If multiple features, group by feature and note whether they're planned together or split.
3. **Architecture overview** — features/bounded contexts, how they map to the hexagonal layers, and where it beats the naive approach. A simple text diagram is welcome.
4. **Key decisions & tradeoffs** — one short block per decision: options, the estimate/reasoning, the recommendation, the flip condition.
5. **Risks & open questions** — what needs a human call.
6. **Delivery plan** — the dependency-ordered, inside-out build sequence per the guidance above. Precise and neutral: goal, files/packages, contracts, depends-on, acceptance/tests for each step.

Sections 1–5 read casually and stay short; depth goes into the decisions that matter, not padding. Section 6 is the agent feed — exact, unambiguous, contracts fully specified, no code bodies.

## Done when

The human can see at a glance what's being built (and whether it's really one feature or several), what's still open, and why the architecture is shaped this way — and an implement agent can walk the delivery plan top to bottom, each step depending only on earlier ones, building from the domain outward without inventing a shared contract or re-deciding anything the plan should have settled.