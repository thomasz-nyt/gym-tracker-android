# Agreed REP redesign — implementation record

Authorized 2026-10-29: finish the agreed review, with the revised minimal Focus as the default in-session view. Production baseline `f6deca7`; approved handoff and ADR-0051 are retained; the HTML discussion prototype remains on PR #85.

## Scope and delivery

Implement all 43 findings through US-62–67 / ADR-0052. Work proceeds in four batches: record protection/recovery; coherent workout/Focus; Progress; preparation/reuse and optional setup. The restore and accessibility work spans batches and remains part of completion. Deferred cloud/accounts, media, exact-machine assets, supersets, set types/setup notes and AI are outside this agreed review.

## Verification boundaries

Use existing domain public use cases/models, real Room repository tests, ViewModel state flows and instrumented Compose/navigation. Write and observe meaningful failing tests before each production slice. Required full gate: `./gradlew ktlintCheck detekt :core:domain:test testDebugUnitTest :app:lintDebug`; additionally verify domain dependencies, instrumented optional-feature variants, migration/backup compatibility and current screenshots as applicable. Preserve phone training data and distinguish CI from physical-device evidence.

## Progress

- [ ] US-62: single-set history-first entry, numeric validation, submission protection, Undo and valid names.
- [ ] US-66: graph validation, safe replacement/recovery copy and reactive member identity.
- [ ] US-63: shared-plan Focus, rest/selection, explicit persisted completion, stale recovery and reopening.
- [ ] US-64: reactive actual Progress, bodyweight/date/week/chart correctness.
- [ ] US-65: first routine, catalog inspection, explicit reviewed routine reuse.
- [ ] US-67: optional setup recovery, accessible adaptive layouts and clear missing/error states.
- [ ] Full local gates, CI, screenshots and delivery verified.

Detailed failing/passing evidence and delivered commits are added as slices complete. An unchecked item is remaining work, not implicitly complete.
