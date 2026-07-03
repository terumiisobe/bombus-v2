---
description: Review phase — analyze the current branch against the plan it implements, flag inconsistencies and unnecessary/duplicate code, and sanity-check it against architecture, Kotlin/Spring, API, and testing guidelines before merge
globs:
alwaysApply: false
---

# Skill: Review

Use this when reviewing a change before merge. **Analyze the current branch, compare it against the Refine plan it claims to implement, and call out where they diverge.** Judge it against the reference docs below — don't restate their rules, follow them.

Write the review in a **casual tone**, like you're leaving quick review comments for a teammate, not filing a formal report. Keep it short. **Don't over-explain findings** — a line each is plenty; the author can dig in from there.

**If the PR is good, just say so in one phrase: `Good to go 👍`** — don't pad out sections or invent nitpicks to look thorough. Only write up findings when there's something real worth fixing.

Inputs: the **current branch / diff**, and the **deliverable from the Refine plan** it implements (goal, scope, ports & adapters it said were needed, what should be reused vs. new, acceptance criteria).

Reference docs — the source of truth for the architecture, Kotlin/Spring, API, and testing rules: `architecture-rules.md`, `kotlin-springboot-best-practices.md`, `api-best-practices.md`, `testing-best-practices.md`. Judge the change against these and **follow them rather than any copy pasted here**, since they can change. Walk them mentally and surface only what fails — plus the three plan-specific checks below, which aren't in those docs.

## The three things to always check

Beyond what the reference docs cover, always answer these:

1. **Does the branch match the plan?** Check what the plan said this deliverable would do, touch, and reuse against what the branch actually did. Anything extra the plan didn't ask for, anything missing, or any deviation that wasn't signed off — flag it.
2. **Was the new code actually needed?** For anything new the branch adds (file, port, adapter, entity, function), check it isn't just duplicating something that already exists. If an existing port/adapter/domain type already covered it, it should've been reused or extended. We want the change concise — no parallel implementations, no speculative stuff the deliverable didn't need.
3. **Is everything testable, and is anything untested?** Check each new function can actually be tested — logic reachable without spinning up the world, dependencies behind ports so they can be faked, no hidden statics/`new`/clock/random calls that make it impossible to assert. Then check what's missing: which functions or paths have no test, and which acceptance criteria aren't covered. Untestable code is a design smell — flag it, not just the missing test.

## Output

If it's clean: **`Good to go 👍`** and nothing else.

Otherwise, keep it tight and casual. Lead with the call, then just the findings that matter:

- **The call** — `Good to go`, `Fix the blockers first`, or `Needs rework`, plus a quick reason.
- **🔴 Blockers** — must fix before merge, one line each with the location. Anything that breaks the reference-doc rules (architecture/security/testing) counts, plus the review-specific ones: missing/stubbed acceptance criteria, unsigned-off plan deviations, new code that duplicates existing functionality, functions that can't be tested as written, missing tests for new behavior or bug fixes.
- **🟡 Should fix** — non-blocking, one line each: guideline violations that still work, weak coverage on edge/failure paths, out-of-scope code, redundant or speculative code that could just be dropped.
- **🟢 Minor** — nits, optional. Naming, dead code, leftover debug logging, stale docs. One line, grouped where you can.

Then, only if there's something worth saying (skip the ones that are fine):

- **Plan match** — one line on anything that drifted from the plan and whether it needs sign-off.
- **Duplication** — one line if any new code should've reused something that already exists.
- **Tests & testability** — one line on anything that's hard to test as written, plus the most important function or path left untested.
- **Tradeoffs** — one line on any real shortcut/deviation worth a sign-off.

Don't write a section just to say "all good" — silence means fine. The whole thing should read in a few seconds.

## Keeping findings ("keep")

If the user asks you to **keep** the findings (e.g. "keep these", "save the todos"), append them as a todo list to the open-issues doc at **`/docs/planning/open-issues.md`** — create the file if it doesn't exist yet.

- Add each blocker / should-fix / minor as an unchecked todo (`- [ ] …`), keeping its severity marker and the file/location so it's actionable later.
- Group the batch under a short heading with the branch/PR and date so it's clear where they came from; append to the existing list rather than overwriting it.
- Leave anything already in the doc untouched. If there's nothing to keep (clean review), say so instead of writing an empty entry.

## Done when

You've compared the branch against its plan, checked it against the reference docs and the three plan-specific checks, and either said **`Good to go 👍`** or left a short, casual list of what to fix. Any blocker — a missing in-scope item, an unsigned-off plan deviation, or new code that duplicates what already exists — has to be resolved or agreed before merge.