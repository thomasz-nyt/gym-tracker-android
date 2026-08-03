# CLAUDE.md — Agent Operating Instructions

This repo is developed **spec-first, test-first**. You are not authorized to write
production code that is not traceable to a spec document and covered by a failing
test written beforehand.

## Read order at the start of every session

1. `specs/constitution.md` — non-negotiable rules. If a request conflicts with the
   constitution, stop and say so rather than silently complying.
2. `specs/roadmap.md` — find the **current milestone**. Do not implement work from
   future milestones, even if it seems trivial to add.
3. `specs/tech-stack.md` — approved dependencies. Adding a dependency not listed
   here requires an ADR (see below).
4. `specs/user-stories.md` — acceptance criteria for the story you are working on.
5. `specs/data-model.md` — schema of record.
6. `specs/testing-strategy.md` — what kind of test a given change requires.

## The loop you must follow

For every unit of work:

1. **Restate** the user story ID (e.g. `US-03`) and its acceptance criteria.
2. **Write the test(s) first.** Run them. Show that they fail for the right reason.
3. **Implement the minimum** to make them pass.
4. **Run the full check**: `./gradlew ktlintCheck detekt testDebugUnitTest`.
5. **Refactor** with tests green.
6. **Commit** with `feat(US-03): ...` / `test(US-03): ...` / `refactor(US-03): ...`.

Never skip step 2. If asked to "just add" something, write the test first anyway
and say that you are doing so.

## Rules of engagement

- **One story per branch, where that is practical.** Branch name: `us-03-log-a-set`.
  Relaxed 2026-07-26: rapid iteration makes strict separation impractical, and forcing
  it can be actively harmful — splitting work that shares a Room migration chain means
  renumbering versions across branches, which risks a real migration bug for a
  cosmetic gain. Prefer one story per branch; when work is genuinely coupled, keep it
  together and say so in the PR description.
- **Open pull requests ready for review, never as drafts.** Added 2026-08-01.
  CI only runs on `pull_request` (see `.github/workflows/ci.yml`), so a branch with no PR
  open gets no build, no tests and no APK — which makes the PR the only way to get a
  build onto a phone. Open it anyway when the work is unfinished; say in the description
  where it stops.
- **Never commit secrets.** No API keys in the app, in tests, or in `gradle.properties`
  that is tracked. See `specs/constitution.md` §4.
- **Do not modify `specs/constitution.md`.** Propose changes to the human instead.
- **Architecture decisions** go in `specs/adr/NNNN-title.md` using the template in
  `specs/adr/0000-template.md`. Write the ADR *before* the code.
- **When the spec is ambiguous, stop and ask.** Do not invent acceptance criteria.
  A question costs a minute; a wrong assumption costs a milestone.
- **Do not generate large amounts of unrequested code.** If a task looks like it
  needs more than ~400 lines of new production code, propose a split first.

## Definition of done for a story

- [ ] Acceptance criteria in `user-stories.md` all covered by an automated test
- [ ] Unit tests pass; lint and static analysis clean
- [ ] No new lint suppressions without an inline comment justifying each
- [ ] Public functions in `:core:domain` have KDoc
- [ ] `specs/roadmap.md` checkbox ticked in the same commit
- [ ] Screenshots attached to the PR for any UI change
