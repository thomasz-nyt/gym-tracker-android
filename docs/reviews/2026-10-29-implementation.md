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

## Integrated foundations (verification in progress)

The implementation now includes one-set prefill and a shared pending-write guard, bodyweight
history preservation, finite numeric validation, atomic Room set-index allocation, a v12
workflow migration, persisted selection/outcomes and bounded reopen rules, complete table graph
validation, a private rolling recovery file, transactional restore checks/rollback, and reactive
member and Progress reads. These are slices of the stories above; their unchecked acceptance
criteria remain required. Routine template reuse is implemented in the checkpoint below; the remaining preparation
and integration UI is still required.

Meaningful red cases included delayed duplicate submission, concurrent append numbering,
restore identity/scope and storage rollback, retained identity observers, incomplete planned
movement counts, reopen endpoints, and stale-workout continuation. Domain/Room targeted checks
were green before integration. The Pixel 8a (Android 17) passed both initial native Focus tests;
the numeric field label test failed before adding accessible labels and then passed. Ordinary
entry's visible bulk count was reproduced by a failing physical-device test before hiding it
behind explicit Bulk entry. Current full gates and final UI variants are still being run.

Device QA uses `-Pgymtracker.review=true` (separate `com.gymtracker.review`) and in-memory test
persistence. The opt-in `reviewShowWhenLocked` instrumentation argument shows only test windows
over keyguard; the phone remains locked, and the normal `com.gymtracker` installation is retained.
An initial full gate crashed in JVM ZIP compression; the kernel reported a simultaneous swap
read error. No OOM kill was established. Subsequent verification uses one worker and a 2 GiB
Gradle heap; no host configuration was changed.

The first integrated full gate passed (`/tmp/rep-full-gates-3.log`): ktlintCheck, detekt, domain
and Android unit tests, app lint and domain dependency verification. Native suite adaptation,
screenshots and the unchecked story criteria remain in progress.

## 2026-10-31 continuation

Implemented and individually checked the explicit routine-reuse domain operation, atomic Room
replacement/review marker, and post-save UI. Domain tests cover net structure, authored target
preservation, Today only, changed-original fallback and failures; Room tests cover commit,
conflict, rollback/outbox and a reopened-workout guard. The finish summary now shows actual
sets and labels unfinished/unperformed work. The routine-review ViewModel guards duplicate
pending saves.

The connected Pixel 9a received the committed `981222a` build after a private full-data and APK
backup. Its signing certificate and app UID matched; the snapshot was restored and checked
byte-for-byte before launch. Schema 11→12 migrated successfully with integrity OK, every old
column/value preserved, and DataStore preferences identical. Personal backup files and screenshots
stay outside the repository. Broader redesign work remains in progress.

Testing subsequently switched to the connected Pixel 8a. Seven focused native checks passed,
including both explicit routine-save choices and the actual saved-workout summary. Native
synthetic-fixture screenshots were retrieved and inspected; platform-window transitions were
allowed to settle before recapture. See [the device checkpoint](2026-10-31-device/README.md).

Remaining work includes: restore metadata/recovery-copy UI; preparation/catalog flows; actual
Progress/selector, bodyweight, time-axis/week/date fixes; reopened-workout UI and atomic lifecycle
boundaries; warm-up access, rest origin/notification completion, numeric edge cases and past-set
Undo; optional integration recovery; missing states and adaptive layouts; full native suite
adaptation to the shared-plan design, optional-features-off run, final screenshots and CI.

The 2026-10-31 routine/Focus checkpoint passed all full local gates, including app lint and
pure-domain dependency verification (`/tmp/rep-pixel8a-checkpoint-gates2.log`). Local unit result
files total 1,074 tests, zero failures and errors. Ten focused native tests passed together on
Pixel 8a (`/tmp/rep-pixel8a-direct-native.log`). The keyboard regression was also run against
the old dialog and failed on actual native window bounds before passing with keyboard insets.
The return-from-history test exposed retained scroll before page-specific scroll was applied.
Six settled native screenshots are attached in the device review directory. Full native
regression adaptation, optional-features-off and the story checklist remain open.
