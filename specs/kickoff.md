# Kickoff — how to start with Claude Code

## 1. Repo setup

```
gym-tracker/
├── CLAUDE.md
├── specs/
│   ├── constitution.md
│   ├── roadmap.md
│   ├── tech-stack.md
│   ├── user-stories.md
│   ├── data-model.md
│   ├── testing-strategy.md
│   ├── health-connect.md
│   ├── kickoff.md
│   └── adr/
│       ├── 0000-template.md
│       └── 0001-native-android-first.md
└── .gitignore
```

Commit the docs **before** any code. First commit message: `docs: initial specs`.
The point of spec-driven development is that the specs predate the repo's code
history, so every commit is traceable to a document that already existed.

## 2. First session prompts

Run these one at a time. Do not batch them — you want to review between steps.

**Prompt 1 (orientation, no code):**
> Read CLAUDE.md and everything in specs/. Summarise the current milestone, list its
> exit criteria, and tell me every place the specs are ambiguous or contradictory.
> Do not write any code yet.

Fix the ambiguities it finds before proceeding. This is the highest-value ten
minutes in the project.

**Prompt 2 (M0):**
> Implement M0 from specs/roadmap.md. Follow the loop in CLAUDE.md. Start with the
> Gradle skeleton and the CI check that :core:domain has no Android dependency —
> write that check's failing test first.

**Prompt 3 (first real story):**
> Implement US-01 and US-03 from specs/user-stories.md. Write the tests first and
> show me the failing run before implementing. Domain and Room only — no UI yet.

**Prompt 4 (the loop, for real):**
> Build the active-session UI for US-01 through US-03, then write the instrumented
> two-tap assertion from specs/testing-strategy.md §2.

## 3. Habits that make this work

- **Re-anchor every session.** Start with "read CLAUDE.md and specs/roadmap.md,
  tell me the current milestone." Context drifts; the specs do not.
- **Tick roadmap checkboxes in the same commit as the work.** The roadmap file is
  the shared state between you and the agent across sessions.
- **When the agent proposes something not in the specs, one of two things is true:**
  the spec is wrong, or the proposal is. Resolve it in the doc, then continue.
  Never let the code become the only place a decision lives.
- **Keep milestones shippable.** If M1 takes more than a few weekends, cut scope
  from M1 rather than moving on with it half-done.

## 4. Seed data

The exercise catalog comes from `free-exercise-db` (public domain, ~800 exercises
with JSON metadata). Bundle the JSON in the app rather than fetching it, so the
catalog works on first launch with no network.

For GIFs, mirror only the ones you actually use into Supabase Storage. Do not
hotlink a free API endpoint — those carry rate limits and no uptime guarantee, and
your app should not break because someone else's demo endpoint went away.

## 5. Before M6

Set up the Supabase Edge Function and the Anthropic API key as a function secret
**before** writing any coaching code, and confirm with a trivial function that the
key is not reachable from the app. Constitution §4 is the rule most likely to be
violated by accident under time pressure.
