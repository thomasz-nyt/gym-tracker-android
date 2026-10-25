# REP agreed product direction and implementation handoff

Date: 2026-10-22. Source baseline: `main` at `f6deca70e3c07920c18719b24d582e26969da462`.

**Status:** all 28 interview questions answered. This document consolidates their decisions for one final shared-understanding confirmation. Review/documentation only; production implementation has not started.

Companions: [43-item evidence-linked review](2026-10-22-product-ux-review.md), [answers and decision tree](2026-10-22-product-alignment.md), [domain glossary](../../CONTEXT.md), [accepted routine-save boundary, ADR-0051](../../specs/adr/0051-explicit-routine-updates-from-a-workout.md).

## Product and ownership

REP primarily serves **beginners in one household**, each with their own phone and personal training identity/account. An experienced household member or trainer prepares the initial routine directly on the beginner's phone. Shared-phone switching, remote routine assignment, and app-authored starter training programs are outside this first improvement scope.

Routine guidance is the default experience. Freestyle remains available, including before a routine exists. No mandatory account, introductory tour, wearable, coaching, or network connection is introduced. Preserve the app's indoor strength-training scope, current monochrome/red identity, offline operation, honest logged data, and fast prefilled logging.

## Agreed behavior

| Area | Required behavior | Decisions |
|---|---|---|
| First use | Offer Create first routine: name → inspect/add exercises → optional targets. Freestyle and backup restoration remain available; unfamiliar terms get contextual help. | Q1, Q6, Q13, Q20 |
| Plan and focused view | Both present the same workout plan and actual records. Focused mode does not start a separate walkthrough with its own targets. | Q4 |
| Set logging | A normal Log action records one actual set. Bulk entry, if retained, is explicit and count-labelled. Prevent duplicate submissions without adding another confirmation. | Q5; REP-02/05 |
| Prefill | Last actual performance on that exercise first; target separately labelled and used when history is absent. Never infer/copy load from another exercise. | Q9 |
| Completion | Planned set count determines Plan met, even when actual reps/load differ. With no planned count, performed work is explicitly marked Done. | Q10, Q24 |
| Deviations | Support Ended early and Skipped as distinct outcomes; do not invent missing sets or silently change the routine. Finish remains possible with unfinished work. | Q15 |
| Advancement | Offer Next movement as the primary action when the current plan is met; Extra set is secondary. Do not automatically finish the workout. | Q14 |
| Rest | One countdown accompanies the plan. Selecting/inspecting another movement preserves it; logging a new set starts that movement's rest. Retain extension/cues and access to corrections. | Q2, Q11; existing rest commitments |
| Duration | Total Start-to-Finish visit duration, including timed warm-up and ordinary rests. Reconcile older warm-up-exclusion documentation. | Q7 |
| Undo and reopening | Undo set deletion and routine-item removal. Reopen the latest finished workout within ten minutes only if no new workout has started. If no new set is recorded, re-finishing preserves the original end time; new sets resume the visit. Older history remains editable. | Q12, Q17 |
| Forgotten workout | After more than four hours without a set, offer Continue with full elapsed time explained, or confirmed Finish at last logged set. With no sets, offer discard. Preserve records and use explicitly described endpoints. | Q18 |
| Progress | Selectable trained exercises, actual recent load/reps/sets first, estimates and PRs secondary. Bodyweight sets/reps still count as recorded training; absent load volume is not no training. Refresh derived data after corrections/deletion/Undo. | Q16; REP-19/20/23 |
| Charts and dates | Actual date/time spacing, units, inspectable values linked to workouts, distinguishable same-day workouts, accessible numerical records. Include years where needed. | Q22; REP-24 |
| Weeks | Monday-based calendar weeks; include the full oldest displayed week and label the current week partial. | Q23 |
| Settings/integrations | Quiet settings access while training, preserving workout/timer/selection; explicit permission/band recovery. Destructive restore remains blocked while active. | Q21 |
| Restore | Complete personal restore on current/replacement phone; no merge feature. Preview file age/content/ownership, validate completely, report errors, refresh all member-dependent screens. Keep one complete validated local safety copy before replacement; if copy creation fails, stop with an explanation. | Q3, Q8, Q19; REP-04/06/33/34 |

## Saving changes back to a routine

The user explicitly requested this capability and agreed all three details in Q26–Q28:

1. When a saved-routine workout finishes, persist the workout first. If net exercise-list changes remain, show one review prompt. No prompt after every edit or when edits were undone.
2. Offer **Update original / Save as new / Today only**. Dismissal keeps the original. Preview the differences before an explicit save.
3. Reuse additions/removals/replacements and deliberate ordering changes only. Ordinary selection, Skipped/Ended early, and performed load/reps do not automatically become template changes. Preserve surviving movement occurrences' authored targets.
4. A template-save failure or cancellation preserves the saved workout and original routine. Updating a routine does not rewrite historical names, targets, or actual sets. If the original is missing or independently changed, avoid a blind overwrite and offer a reviewed fallback.
5. Undo finish does not implicitly undo a separately confirmed routine save, and re-finishing without more list changes should not repeat an already handled prompt. Verify this interaction during story refinement.

Example: Upper A contains bench press, row, and leg press. During today's workout, the member adds leg curl and removes leg press. The workout is saved with today's actual data; the summary asks whether to update Upper A, save a new routine, or keep the change for today only. Merely skipping leg press or recording fewer bench reps does not remove leg press or lower future targets.

This is an explicit template-save exception to the old one-way-copy model, documented in ADR-0051. It does not create automatic synchronization between a live workout and its template.

## Delivery order and issue coverage

| Batch | Scope and concrete result | Review items |
|---|---|---|
| A — Record protection and recovery | Recoverable invalid input, one submission per logical Log, working past-set/routine-item Undo, valid names, complete restore validation, safety copy, consistent member refresh and replacement scope. | REP-01–06, 27–28, 33–34 |
| B — Coherent workout flow | Single plan across modes, one-set default, accessible Log/Next actions, rest with plan/correction access, honest completion/summary/numbering, full previous performance, reliable selection/resume/stale handling and bounded Undo finish. | REP-02, 07–18, 30, 41 |
| C — Useful trustworthy Progress | Correct derived data after edits; trained-exercise selection; actual performance first; truthful bodyweight/absence states; accurate date ranges, chart information, and years. | REP-19–24, 40 |
| D — Preparation, reuse, and optional setup | Clear catalog browse/pick meanings, inspect-before-add, routine creation straight into editing, first-use path, post-workout routine-save choices, explicit permission/band management, efficient rest setup, optional RPE help. | REP-25–26, 29, 31–32, 38, 42–43 |
| Every batch | Accessible field names, reachable primary action, adaptive large-text/narrow layouts, complete information through readable expansion, and visible exits/errors. | REP-14–15, 35–37, 39 |

Overlap is intentional: one behavior may involve storage and screens, but it should be implemented once and traced to the relevant story. The record-protection work precedes changes that depend on trustworthy records; first-use/routine reuse builds on the settled workout-finish contract. Accessibility is not a final optional polish phase.

These are scoped implementation batches, not a request to implement all of them immediately or skip the repository's milestone restrictions. During implementation planning, map each batch to existing story amendments/new stories and document any required milestone exception before production changes. Constitution amendments remain human-only.

## Measurable outcomes

| Scenario | Required observation |
|---|---|
| Enter/paste an invalid load | No entry-screen termination; useful validation and preserved draft; no invalid row saved. |
| Three-set routine; open entry to adjust weight | Saving creates one performed set. Rapid repeated input during a delayed write does not create unintended duplicates. |
| Target 3×12, actual 12/10/8 | Three actual sets and their real reps remain visible; plan set count is met; the summary does not say three sets of twelve. |
| Change exercise selection during rest | Countdown continues. Log the selected movement and one timer starts its rest; no second timer or unexpected movement selection. |
| End after two of three sets / skip an unperformed movement | Early end/skip are honest distinct statuses; no fabricated sets, forced target matching, or automatic template edit. |
| No planned set count | Member can log actual sets and explicitly choose Done; no artificial three-set plan appears. |
| Delete a past set or routine item | Undo restores all relevant data, including targets/position for a routine item and actual values/timestamp for a set. |
| Finish accidentally, reopen promptly, finish without new sets | Eligible only for the latest workout within ten minutes with no new workout started; original end time retained. |
| Reopen app after four-hour inactivity | Reconciliation precedes focused mode; Continue explains elapsed time, and finish/discard choices describe the actual outcome. |
| Correct/delete/undo a record set | Detail, list totals, featured performance, PR badges, trend/log, and volume consistently reflect the updated data after navigation. |
| Bodyweight week or oldest range week | Recorded training is not called absent; oldest calendar week includes all its days; current week visibly partial. |
| Import valid/invalid backup from another installation | Complete validation/error handling; safety copy before replacement; consistent current identity/screens after success; existing data intact on validation/write failure. |
| Finish a routine with net added/removed exercises | One post-save prompt with Update original / Save as new / Today only. Undone edits, actual rep/load changes alone, and skip status alone do not trigger a structural routine update. |
| Cancel/fail a routine update | Finished workout remains saved; original template remains intact. Past history is unchanged after successful template updates too. |
| Narrow screen, large text, keyboard, long names, TalkBack | Primary action and Finish remain reachable; fields have meaningful names; text/details remain accessible; Back/error recovery remains available. |

## Verification and repository requirements

The review is source-based, supported by historical screenshots for context. It did not establish current-build device reproduction, TalkBack behavior, large-text rendering, or timing/race behavior. Those are required verification work, not completed evidence.

Use an isolated installation and realistic empty, sparse, bodyweight, routine-target, duplicate-exercise, and long-history fixtures. Preserve real household training data. Reproduce static failure/layout hypotheses before describing them as device-verified.

For production changes, follow `CLAUDE.md`: update story/spec traceability, write meaningful failing tests first, implement the minimum, and run the required full gates:

```text
./gradlew ktlintCheck detekt :core:domain:test testDebugUnitTest :app:lintDebug
```

Complete the other repository-required domain/persistence/UI/instrumented checks appropriate to the change. Keep tap-count protection and additionally verify real visible reachability/scrolling friction. UI changes need current screenshots; CI success alone is not physical-device proof.

Implementation must reconcile the prior warm-up-duration promise, batch-count prefill default, guided defaults, first-set completion rule, irreversible-finish contract, and ADR-0028's provenance enforcement with the decisions above. The review has not silently rewritten historical specs or modified the constitution.

## Deferred scope and future decisions

- Accounts/sync/household rollout remain M2 work; this review does not claim they function or remove their exit criteria.
- Supersets, exact-machine guides, already-approved set types and persistent setup notes remain separate scoped commitments. Their detailed questions, such as which future set types count toward a working-set target, belong to those stories; no silent answer is assumed here.
- Remote routine assignment, shared-phone switching, backup merging, app-authored starter programs, and custom household exercises are not added to this improvement scope. Existing custom-exercise deferral remains in place.
- No new general fitness, social, nutrition, or engagement-mechanic scope is introduced.
- Low-level storage/diff strategies, migrations, conflict handling, and sync integration require design/tests when their story is implemented. They do not require another general preference interview now.

## Shared-understanding checkpoint

Confirm whether this handoff accurately captures the agreed product direction, delivery priority, exclusions, and verification bar. This confirms alignment for the review; a subsequent implementation request can choose the first scoped batch/story. Publishing the review documents in a commit and PR preserves the work; it does not start production implementation or complete this checkpoint.
