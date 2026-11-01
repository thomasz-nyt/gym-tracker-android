# REP product, UI, UX, and workflow review

Date: 2026-10-22. Baseline: `main`, commit `f6deca70e3c07920c18719b24d582e26969da462`.

Status: 43 review findings documented; Q1–Q28 answered. The consolidated shared-understanding checkpoint remains pending in the [implementation handoff](2026-10-22-implementation-handoff.md). No production code, acceptance criteria, constitution, or roadmap commitments have been changed.

Round 1 alignment: the user selected **beginners** as the primary audience, **flexible movement order preserving the plan/rest timer**, and **one person per phone/account**. Review recommendations should now be evaluated against those answers. Beginner explanations, trustworthy guidance, and recovery receive greater emphasis; shared-phone member switching is not a requirement. Optional sign-in remains an existing constraint. See [the alignment document](2026-10-22-product-alignment.md) for settled answers and pending questions.

Round 2 alignment: **routine guidance by default with freestyle available; one performed set per normal Log; an experienced household member/trainer prepares the initial routine; total Start-to-Finish duration including warm-up/rest; complete personal backup restore with merging deferred.** These settle the product direction for REP-02 and REP-11. For REP-16, preserve current total-duration behavior and reconcile the historical warm-up-exclusion promise rather than implementing subtraction. Transfer/assignment of routines, prefill precedence, completion, recovery, and safety snapshots remain open.

Round 3 alignment: **history-first prefill with targets distinct; planned set count determines movement completion; one rest timer; Undo for set deletion/routine-item removal and reopening accidental finish; routine setup directly on the beginner's phone.** These settle key directions for REP-03, REP-08, REP-27 and the ownership/setup scope. Early termination, next-movement transition, reopening age/duration, progress priorities, and restore safety are the next decisions. Remote routine assignment is deferred.

Round 4 alignment: **explicit Next movement with rest preserved; distinct Ended early/Skipped outcomes; actual performance first in Progress; Undo finish for the latest workout within ten minutes with no intervening workout; four-hour inactivity prompt offering Continue or confirmed Finish at last logged set; one validated local pre-restore safety copy.** Prior “pending” statements in the round summaries above describe the interview at that point; the alignment document records the current answers.

Round 5 alignment: **first-routine setup from home; settings/recovery during a workout; actual-time chart spacing; Monday calendar weeks; explicit Done without a set target; the proposed delivery order.** The user additionally requested an offer to retain workout exercise-list changes in a saved routine for future use, recorded as REP-43.

Round 6 alignment: **one review after the workout is saved, for net exercise-list changes; Update original / Save as new / Today only; structural changes only, preserving surviving targets and historical records.** Q26–Q28 are answered and [ADR-0051 is accepted](../../specs/adr/0051-explicit-routine-updates-from-a-workout.md). Cancellation or template-save failure preserves the finished workout and original routine. Earlier round summaries record the interview at that point; the handoff consolidates the current direction.

## Assessment

REP's strongest qualities are its narrow purpose, offline storage, quick prefilled logging, large primary actions, visible individual sets, and optional integrations. Preserve those. The monochrome/red visual identity is already a deliberate choice; changing colors would not resolve the main problems found here.

The largest opportunity is making every surface agree about what the member is doing. The app currently has several overlapping answers to “guided,” “next set,” “done,” and “nothing logged.” These disagreements can produce incorrect-looking summaries, accidental batch logging, unnecessary navigation, and uncertainty about whether saved data is trustworthy.

Agreed priority order (Q25): protect saved data and recover from mistakes; reconcile logging/rest/plan behavior; improve access to useful progress; then refine setup and routine reuse. Accessibility and visual consistency apply throughout. Agreement on priority is not a request to start production implementation.

## Review basis and limits

- Inspected current navigation, design system, feature screens/ViewModels/controllers, and relevant domain/data paths against the constitution, stories, roadmap, and ADR amendments.
- Walked the source paths for first use, freestyle, routine creation, starting a plan, selecting movements, logging/correcting/deleting sets, rest, focused exercise mode, warm-up, finishing, history, progress, backup/restore, and optional health/band setup.
- Inspected six archived screenshots for historical design context. They predate current changes: for example, one still shows four tabs. They do **not** establish current visual defects.
- ADB detected a physical phone, but it was locked. No phone navigation, training-data modification, installation, or restore was performed. The local APK is dated August 23 and was not treated as the current build.
- No new build, automated tests, TalkBack session, large-text rendering, or timing/race reproduction was run. “Confirmed source behavior” below means the current code establishes the behavior; it does not mean a device reproduction was performed. Layout and race consequences are explicitly marked as needing verification.

Severity: **P1** = potential lost/incorrect records or an interrupted core task; **P2** = substantial confusion, misleading information, or repeated friction; **P3** = clarity/polish. Confidence: **C** = confirmed source behavior; **D** = deliberate existing behavior whose tradeoff should be revisited; **V** = runtime/visual consequence needs verification; **R** = newly requested capability, distinct from a defect against the previous contract. Priorities reflect user impact, not implementation size.

## Issue index

| ID | Priority | Area | Finding | Basis |
|---|---|---|---|---|
| REP-01 | P1 | Entry | Negative numeric weight reaches a throwing conversion while rendering | C |
| REP-02 | P1 | Logging | “Add set” can default to saving an entire batch again | C/D |
| REP-03 | P1 | Recovery | Deleting a past set has no visible Undo | C |
| REP-04 | P1 | Restore | Broken backup relationships can fail without recoverable feedback | C/V |
| REP-05 | P1 | Logging | Submission paths lack protection against repeated taps | C/V |
| REP-06 | P1 | Restore | Changing member identity does not explicitly refresh dependent screens | C/V |
| REP-07 | P2 | Rest | Rest replaces the plan, set history, and correction tools | C/D |
| REP-08 | P2 | Plan | Progress counts a movement as done after one set | C/D |
| REP-09 | P2 | Plan | Rest keeps offering a completed movement, including “4 of 3” | C |
| REP-10 | P2 | Logging | Next-set preview and saved set numbering disagree after deletion | C |
| REP-11 | P2 | Guidance | Two “guided” concepts disagree about routine targets | C/D |
| REP-12 | P2 | Summary | Focused exercise summary presents target reps as performed reps | C |
| REP-13 | P2 | Guidance | Focused mode loses correction, effort, and rest capabilities | C/D |
| REP-14 | P2 | Ergonomics | The main log action follows all other movements in the scroll | C/D |
| REP-15 | P2 | Comparison | Full previous-session performance is truncated without expansion | C |
| REP-16 | P2 | Timing | Warm-up contributes to workout duration despite the documented promise | C |
| REP-17 | P2 | Resume | Persisted focused mode can precede the stale-workout decision | C/V |
| REP-18 | P2 | Resume | Reopening can select a different movement from the one last used | C/D |
| REP-19 | P2 | Progress | Derived estimates, charts, and PR badges can stay stale after edits | C/V |
| REP-20 | P2 | Volume | Bodyweight training is described as “Nothing logged” | C |
| REP-21 | P2 | Volume | Oldest displayed calendar week is silently incomplete | C |
| REP-22 | P2 | Charts | Trend spacing and missing point detail limit interpretation | C/D |
| REP-23 | P2 | Progress | Other lifts are difficult to reach; featured empty state can mislead | C |
| REP-24 | P2 | History | Dates omit the year | C |
| REP-25 | P2 | Catalog | Browsing shows an add symbol but opens information | C |
| REP-26 | P2 | Catalog | Picker offers no inspection path before adding | C |
| REP-27 | P2 | Routines | Removing a movement loses its target without recovery | C |
| REP-28 | P2 | Routines | Rename can persist a blank name | C |
| REP-29 | P2 | Setup | Creating a routine stops before the editor | C |
| REP-30 | P2 | Navigation | Workout state removes access to Settings and Routines | C/D |
| REP-31 | P2 | Permissions | Denial states explain absence without offering recovery | C |
| REP-32 | P2 | Band | Device management is coupled to enablement and permissions | C |
| REP-33 | P2 | Restore | Confirmation lacks backup age and useful identity/completeness context | C |
| REP-34 | P2 | Restore | “Replace everything on this device” differs from replacement scope | C |
| REP-35 | P2 | Accessibility | Editable stepper values lack an associated field label | C/V |
| REP-36 | P2 | Layout | Routine actions and several dialogs/headers need adaptive layouts | C/V |
| REP-37 | P2 | First use | Home content has no overflow strategy for large text/short screens | C/V |
| REP-38 | P3 | Rest setup | A common rest change can require 24 presses | C/D |
| REP-39 | P3 | Error states | Missing/deleted records appear as endless loading | C |
| REP-40 | P3 | Copy | Estimated-1RM explanation names the wrong source set | C |
| REP-41 | P2 | Finish | Empty-session outcome and irreversible finish need clearer context | C/D |
| REP-42 | P2 | Learning | RPE presents specialist notation without explanation | C |
| REP-43 | P2 | Routine reuse | Workout exercise-list changes cannot be offered for future routine use | R/C |

## Findings and proposed improvements

The following delivery sequence was agreed in Q25, with the routine-save interaction settled in Q26–Q28. It groups related findings without treating every implementation detail as accepted:

| Batch | Concrete outcome | Findings | Main acceptance evidence |
|---|---|---|---|
| A — Protect records and recovery | Invalid input cannot terminate entry; one logical tap saves one submission; past-set/routine-item Undo works; restore validates, preserves safety data, and refreshes ownership | REP-01–06, 27–28, 33–34 | Delayed writes/double taps, invalid values, deletion/Undo, cross-install and invalid-file restores against isolated real storage |
| B — Reconcile workout flow | One-set entry, a single plan across views, accessible log action, rest with inspection/switching, honest progress/summary, correct resume/finish | REP-02, 07–18, 30, 41 | End-to-end beginner routine, deviations/extra sets, rest switching, process recreation, stale and ten-minute reopen cases; visible action reachability |
| C — Make Progress trustworthy/useful | Corrections update all derived views; selectable trained exercises show actual results; bodyweight and dates are honest | REP-19–24, 40 | Edit/delete/Undo and return paths, partial-week boundaries, sparse/bodyweight histories, chart-to-record consistency |
| D — Improve preparation, reuse, and optional setup | Routine creation continues into editing; inspect before add; first-use path; explicit routine-save offer; direct permission/band recovery; usable rest setup | REP-25–26, 29, 31–32, 38, 42–43 | Beginner builds a routine, decides whether today's list changes should affect future use, and recovers optional integration setup; cancellation/save failure preserves history/template |
| Across all batches — Accessible coherent screens | Clear field names, adaptive layout, preserved visible exits and error states | REP-14–15, 35–37, 39 | Current-build screenshots at narrow/large text, keyboard/long names, light/dark and TalkBack checks |

The batches overlap deliberately where a single behavior spans records and screens; implement it once with clear story traceability. They are a review handoff proposal, not a bypass of the repository's milestone/spec-first/test-first requirements. Account/sync, supersets, exact-machine guides, set types, and setup notes remain separately tracked commitments.

### Data entry, protection, and recovery

**REP-01 — Invalid weight can interrupt the workout (P1, C).** A typed/pasted `-5` is parsable, so supporting conversion text calls `UnitConverter.toKilograms`, whose nonnegative requirement throws. This happens during rendering, before Save can protect the user. Entry/edit and focused setup share the weakness. Accepting numeric text is not sufficient validation; finite, nonnegative values need the same rule everywhere, with an inline explanation that preserves the draft. Verify negative input, nonfinite pasted values, decimal separator behavior, and recovery in all load fields.

Evidence: [SetSheets.kt:342–345](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/SetSheets.kt#L342), [UnitConverter.kt:54–65](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/units/UnitConverter.kt#L54), [GuidedExerciseScreen.kt:424–428](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/GuidedExerciseScreen.kt#L424).

**REP-02 — Per-set editing unexpectedly becomes batch logging (P1, C/D).** A routine target of three sets prefills the entry sheet with three, every time it opens. Even a rest-only target defaults the count to three. The adjacent one-tap action logs one, but “Add set” → “Save set” can log three identical sets. The count is visible, so this is a dangerous default and label mismatch rather than a hidden operation. Someone changing the next set's load may record sets they have not performed. Recommend one as the default for per-set entry and a deliberate bulk mode with a count-aware action, such as “Save 3 identical sets.” Agree whether bulk logging is actually a frequent household task before redesigning it.

Evidence: [ResolveSetPrefill.kt:28–58](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/set/ResolveSetPrefill.kt#L28), [SetSheets.kt:77 and 256](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/SetSheets.kt#L77), [SetEntryController.kt:166–186](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/SetEntryController.kt#L166).

**REP-03 — Past-set deletion has no usable recovery (P1, C).** Progress → past workout → set → Delete set immediately removes the row and closes the editor. The controller keeps an Undo snapshot, but workout detail never displays it or wires an action. A mistaken deletion therefore loses a real record despite recovery already existing underneath. Surface Undo consistently with active-set and workout deletion. Verification should delete and restore a past set with its load, reps, RPE, index, and timestamp intact.

Evidence: [WorkoutDetailScreen.kt:91–101](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/WorkoutDetailScreen.kt#L91), [HistoryViewModel.kt:117–135](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/HistoryViewModel.kt#L117), [SetEditController.kt:174–195](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/SetEditController.kt#L174).

**REP-04 — A broken backup can pass preview and fail without recovery UI (P1, C/V).** Backup validation checks catalog exercise IDs but does not establish the complete parent/child graph. A valid JSON file whose set references an absent session-exercise can reach insertion and fail a Room constraint. Import does not translate that failure into a recoverable screen state. The SQL transaction rolls back; loss of existing SQL data is not established. The expected outcome should be a clear refusal, preserved current data, and a way to choose another file. Validate relationship integrity and catch write failures; reproduce using a deliberately malformed file in isolated storage.

Evidence: [ValidateBackup.kt:32–40](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/backup/ValidateBackup.kt#L32), [RoomBackupStore.kt:71–100](../../core/data/src/main/kotlin/com/gymtracker/core/data/backup/RoomBackupStore.kt#L71), [SettingsViewModel.kt:264–293](../../feature/settings/src/main/kotlin/com/gymtracker/feature/settings/SettingsViewModel.kt#L264).

**REP-05 — Double submission can produce additional records (P1, C/V).** Manual, one-tap, and focused logging launch writes without a common in-flight submission guard. Set index allocation reads the next index separately from insertion, and there is no uniqueness constraint for an appearance/index pair. Rapid taps during delayed persistence may therefore create extra sets or duplicate indices. This consequence needs a controlled delayed-write reproduction. Protect one logical submission, visibly acknowledge saving when needed, and ensure numbering is allocated consistently; do not add an extra confirmation to every set.

Evidence: [SetEntryController.kt:166–187](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/SetEntryController.kt#L166), [ActiveSessionViewModel.kt:650–658](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/ActiveSessionViewModel.kt#L650), [GuidedController.kt:317–333](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/GuidedController.kt#L317), [LogSet.kt:41–50](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/set/LogSet.kt#L41), [SetEntity.kt](../../core/data/src/main/kotlin/com/gymtracker/core/data/set/SetEntity.kt).

**REP-06 — Successful restore has no explicit identity-refresh boundary (P1, C/V).** Restore changes the current member ID, while several screens obtain that ID from a one-emission flow. A retained subscription can continue observing the previous member and show empty/old data after a successful import. Some flows restart after being unsubscribed for five seconds, so this is not a claim that all screens stay wrong until relaunch. The globally collected presence signal deserves particular attention. Define restore completion to include refreshed member-dependent screens, either through observable identity or a deliberate scope reset. Test from a fresh-install identity with populated Train/Progress tabs already retained.

Evidence: [RoomBackupStore.kt:102](../../core/data/src/main/kotlin/com/gymtracker/core/data/backup/RoomBackupStore.kt#L102), [ActiveSessionViewModel.kt:384](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/ActiveSessionViewModel.kt#L384), [SessionPresenceViewModel.kt:35–39](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/SessionPresenceViewModel.kt#L35), [SettingsViewModel.kt:162–165](../../feature/settings/src/main/kotlin/com/gymtracker/feature/settings/SettingsViewModel.kt#L162).

### Workout, plan, rest, and focused exercise flow

**REP-07 — Rest excludes useful between-set work (P2, C/D).** The session shows either the rest body or the movement plan. During recovery, a member cannot inspect/correct logged sets or change the open movement through the ordinary selector while preserving the countdown. “Add exercise” remains available; the issue is specifically losing the plan and its tools. Recommend a compact persistent timer with access to the plan underneath, or another explicit way to inspect and return without skipping rest. This revisits ADR-0029's exclusive-screen design.

Evidence: [SessionScaffold.kt:319–359](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/SessionScaffold.kt#L319), [RestPanel.kt:138–151](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/RestPanel.kt#L138).

**REP-08 — “Done” means only one set has been recorded (P2, C/D).** Six movements targeted at three sets each reach six-of-six movement progress after only six sets. The underlying rule is deliberate, but “done”/filled progress implies a stronger completion claim. Resolve whether completion means target achieved, explicit member intent, or simply started. Until then, label the existing calculation as movements started. Avoid enforcing targets as mandatory: shortened sessions still need to finish normally.

Evidence: [SessionProgress.kt:44–51](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/session/SessionProgress.kt#L44), [SessionScaffold.kt:408–413 and 619](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/SessionScaffold.kt#L408).

**REP-09 — “Up next” disregards completed plan targets (P2, C).** Rest selects the most recently logged movement and increments its count even after its planned sets are reached. The UI can present “SET 4 OF 3” and make another set the primary action. Mid-set has an EXTRA distinction, so the two states disagree. Recommend offering the next planned movement when the current target is met, while keeping extra sets and manual switching explicit. For freestyle, retaining the last movement can still be sensible.

Evidence: [DetermineUpNextSet.kt:66–83](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/rest/DetermineUpNextSet.kt#L66), [RestPanel.kt:403](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/RestPanel.kt#L403), [SessionMovements.kt:529–537](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/SessionMovements.kt#L529).

**REP-10 — The button promises a different set number than storage creates (P2, C).** Log sets 1, 2, 3, then delete set 2: preview uses remaining count + 1 and says set 3; persistence uses maximum index + 1 and saves set 4. Choose dense display order or stable numbering, then use that rule consistently in previews, rows, editing, and restoration.

Evidence: [ActiveSessionViewModel.kt:532](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/ActiveSessionViewModel.kt#L532), [DetermineUpNextSet.kt:72–77](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/rest/DetermineUpNextSet.kt#L72), [RoomSetRepository.kt:56–57](../../core/data/src/main/kotlin/com/gymtracker/core/data/set/RoomSetRepository.kt#L56).

**REP-11 — A planned session and an exercise walkthrough are both “guided” (P2, C/D).** A routine-backed workout is labelled GUIDED, yet “Start exercise” launches a separate focused flow with its own three-by-twelve defaults and history-derived load. A planned 4×6 exercise can become a 3×12 walkthrough. This is intentional legacy behavior, not an accidental missing assignment. Recommend distinguishing a planned workout from focused exercise presentation and making the latter inherit the current movement's plan. Preserve the distinction between a target and the values actually performed.

Evidence: [SessionScaffold.kt:570–582](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/SessionScaffold.kt#L570), [GuidedController.kt:168–175 and 363–370](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/GuidedController.kt#L168), [ActiveSessionViewModel.kt:728–733](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/ActiveSessionViewModel.kt#L728).

**REP-12 — Focused completion misstates actual reps (P2, C).** Target twelve; record 12, 10, and 8. The saved sets preserve actual reps, but the completion screen says “3 sets of 12.” Show actual per-set values or a truthful total, with the target separately labelled. This is a data-presentation correction even if the focused mode remains otherwise unchanged.

Evidence: [GuidedController.kt:319–329](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/GuidedController.kt#L319), [GuidedExerciseScreen.kt:346–348](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/GuidedExerciseScreen.kt#L346).

**REP-13 — Choosing guidance removes existing capabilities (P2, C/D).** Focused mode lacks optional RPE entry, the ordinary one-tap Undo, direct correction of the last set, and skip/extend-rest controls. It writes null RPE and also bypasses the ordinary inline PR path. Some omissions were deliberate before these capabilities evolved elsewhere. Recommend one minimal capability contract across logging modes, with presentation differences rather than different meanings or recovery rules. Showing fewer controls by default can remain a valid beginner choice.

Evidence: [GuidedExerciseScreen.kt:163–165 and 229–281](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/GuidedExerciseScreen.kt#L163), [GuidedController.kt:329](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/GuidedController.kt#L329), [ActiveSessionViewModel.kt:433–438](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/ActiveSessionViewModel.kt#L433).

**REP-14 — The most frequent action sits after the least frequent content (P2, C/D).** The scroll order is current movement/history/sets, every other movement, then the log bar. A long routine pushes Log below the fold. Existing tap-count tests permit scrolling and therefore do not establish quick visible access from app open. Recommend keeping the logging action reachable while content scrolls, then test visible reachability as well as number of taps. The source documents a previous fixed-control testing problem; solve that interaction without allowing test structure to dictate product ergonomics.

Evidence: [SessionMovements.kt:78–87 and 112–173](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/SessionMovements.kt#L112), [testing-strategy.md, two-tap assertion and scroll trap](../../specs/testing-strategy.md).

**REP-15 — “Last time, in full” is not accessible in full (P2, C).** All previous sets are composed into one string but capped at two lines with ellipsis. Later sets disappear at longer histories or larger text with no expansion action. Offer a compact expandable list, or align the previous set beside today's corresponding set. Keep the detailed comparison available during rest.

Evidence: [SessionMovements.kt:408–440](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/SessionMovements.kt#L408).

**REP-16 — Workout duration includes warm-up despite the older documentation (P2, C; Q7 settled).** Workout start is recorded before the warm-up, and duration is end minus start. An eight-minute warm-up plus thirty minutes lifting therefore reports approximately 38 minutes. ADR-0021 and the route documentation promise no contribution to session duration. The review initially identified this definition mismatch; Q7 below resolves the intended behavior. Reconcile documentation and presentation with that decision. Do not subtract time based on an estimate.

Decision Q7: **total Start-to-Finish duration accepted**, including timed warm-up and ordinary rests. The remaining improvement is accurate copy/documentation and consistent presentation; warm-up subtraction is not desired.

Evidence: [StartSession.kt:52–62](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/session/StartSession.kt#L52), [SessionSummary.kt:31–33](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/session/SessionSummary.kt#L31), [ActiveSessionRoute.kt:224–229](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/ActiveSessionRoute.kt#L224), [ADR-0021](../../specs/adr/0021-a-warm-up-timer-that-records-nothing.md).

**REP-17 — Resumed focused mode can hide stale-workout reconciliation (P2, C/V).** A persisted focused exercise takes routing priority, while the abandoned-session prompt belongs to the ordinary session screen. Reopen the next day and the old focused flow can appear before the finish/discard decision. Verify that scenario and put reconciliation ahead of every workout presentation. Whether a stale workout should also offer Resume is a separate product decision.

Evidence: [ActiveSessionRoute.kt:275–279 and 394–400](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/ActiveSessionRoute.kt#L275), [ActiveSessionViewModel.kt:758–769](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/ActiveSessionViewModel.kt#L758), [SessionAlerts.kt:137–145](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/SessionAlerts.kt#L137).

**REP-18 — Reopen does not necessarily restore the member's chosen movement (P2, C/D).** Explicit selection is in memory. Recreation falls back to the highest-position movement with any sets, which differs from the most recently performed movement when someone goes back to an earlier exercise. Rest can refer to one movement while the ordinary screen selects another. Preserve the chosen movement or derive a consistent fallback from the latest performed set.

Evidence: [ExerciseSelectionController.kt:24–30](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/ExerciseSelectionController.kt#L24), [ActiveSessionViewModel.kt:499–502](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/ActiveSessionViewModel.kt#L499).

### Progress, history, and interpretation

**REP-19 — Correcting history does not consistently refresh derived progress (P2, C/V).** The workout list/detail observe rows, but the featured estimate and exercise trend/log/records use one-time loads; PR badges refresh on `open()`. Delete a record workout or return quickly from editing a set and the remaining list/graph can disagree with corrected data. Subscription restarts can eventually refresh some values, so duration of staleness depends on navigation timing. Recompute on relevant writes/resume or observe the underlying changes. Verify edits and delete/undo across every route back to Progress.

Evidence: [HistoryController.kt:109–120](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/HistoryController.kt#L109), [HistoryViewModel.kt:139–157](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/HistoryViewModel.kt#L139), [ExerciseProgressViewModel.kt:109–125 and 143–145](../../feature/progress/src/main/kotlin/com/gymtracker/feature/progress/ExerciseProgressViewModel.kt#L109).

**REP-20 — “Nothing logged” erases legitimate bodyweight training (P2, C).** The volume calculation correctly excludes missing external loads instead of inventing weight. The display incorrectly equates that absence with no logging. A completed bodyweight-only week can instruct the member to finish a workout they already finished. Distinguish no workouts, workouts with no computable external-load volume, and loaded volume; show recorded sets/reps where useful without estimating bodyweight load.

Evidence: [WeeklyVolumeByBodyPart.kt:85–104](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/progress/WeeklyVolumeByBodyPart.kt#L85), [WeeklyVolumeScreen.kt:101–108 and 173–175](../../feature/progress/src/main/kotlin/com/gymtracker/feature/progress/WeeklyVolumeScreen.kt#L101).

**REP-21 — Calendar-week comparison silently excludes early days (P2, C).** On a Thursday, the eight-week range starts seven Thursdays earlier, but the first block is labelled with that week's Monday. Monday–Wednesday workouts in that block are excluded. Start at the earliest displayed Monday, or explicitly describe and label a rolling-day window. Also label the current week as partial when comparing totals.

Evidence: [WeeklyVolumeViewModel.kt:131–134](../../feature/progress/src/main/kotlin/com/gymtracker/feature/progress/WeeklyVolumeViewModel.kt#L131), [WeeklyVolumeByBodyPart.kt:74–89](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/progress/WeeklyVolumeByBodyPart.kt#L74), [WeeklyVolumeScreen.kt:168–170](../../feature/progress/src/main/kotlin/com/gymtracker/feature/progress/WeeklyVolumeScreen.kt#L168).

**REP-22 — Trends look more precise than they are easy to interpret (P2, C/D).** Points are evenly spaced by session index; a one-day gap and a three-month gap look equal. There is no date axis, unit label on the axis, or explicit point inspection linking a value/date to its workout. The endpoint date range helps but does not explain intermediate values. Decide whether horizontal distance means sessions or elapsed time and label it honestly. Add a readable numerical alternative; use a detailed log rather than requiring someone to infer a value from a line.

Evidence: [ExerciseProgressScreen.kt:259–314](../../feature/progress/src/main/kotlin/com/gymtracker/feature/progress/ExerciseProgressScreen.kt#L259).

**REP-23 — Progress is organized around an automatic guess rather than the intended lift (P2, C).** Only one chosen exercise is featured. Reaching another lift means browsing/searching the general catalog, opening detail, then progress. If the first performed movement in the latest session is bodyweight, the featured section can instead imply there is insufficient training history even when earlier loaded lifts have meaningful data. Recommend a small list/selector of the member's trained exercises and accurate sparse-data copy. Whether its headline is estimated 1RM, last performance, reps, or consistency should follow actual household use; no streak mechanic is proposed.

Evidence: [HistoryScreen.kt:134–200](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/HistoryScreen.kt#L134), [MostRecentlyTrainedExercise.kt:27–36](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/progress/MostRecentlyTrainedExercise.kt#L27), [TopLiftLoader.kt:24–27](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/TopLiftLoader.kt#L24).

**REP-24 — History dates cannot identify the year (P2, C).** Both list and detail use day/month formats without a year. Older restored history and repeated dates across years become ambiguous. Group by month/year and show the year in detail and older rows.

Evidence: [HistoryScreen.kt:486–489](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/HistoryScreen.kt#L486), [WorkoutDetailScreen.kt:381–383](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/WorkoutDetailScreen.kt#L381).

### Catalog and routine setup

**REP-25 — “+” has two incompatible meanings (P2, C).** Every catalog row renders an add cell. In the normal Exercises tab, tapping it opens detail rather than adding anything. Use the plus only in picking mode; use an information/navigation affordance or omit the trailing cell while browsing.

Evidence: [BrowseScreen.kt:315–363](../../feature/catalog/src/main/kotlin/com/gymtracker/feature/catalog/BrowseScreen.kt#L315), [GymTrackerNavHost.kt:315–328](../../app/src/main/kotlin/com/gymtracker/app/GymTrackerNavHost.kt#L315).

**REP-26 — A novice cannot inspect a candidate inside the picker (P2, C).** Picking rows immediately add the exercise; there is no separate detail action. Similar machine/exercise names require leaving the building workflow or choosing before understanding. Provide a distinct inspect action that returns to the same query, filters, and accumulated choices. Do not make the normal add operation require confirmation.

Evidence: [BrowseScreen.kt:286–319](../../feature/catalog/src/main/kotlin/com/gymtracker/feature/catalog/BrowseScreen.kt#L286), [GymTrackerNavHost.kt:319–328](../../app/src/main/kotlin/com/gymtracker/app/GymTrackerNavHost.kt#L319).

**REP-27 — Routine-item removal is less protected than routine deletion (P2, C).** Remove immediately deletes an item and its target; re-adding the movement does not recover sets/reps/load/rest or position. Whole-routine deletion has confirmation. Prefer an Undo that restores the complete item, rather than adding a confirmation to every edit.

Evidence: [RoutineEditorScreen.kt:337–344](../../feature/routines/src/main/kotlin/com/gymtracker/feature/routines/RoutineEditorScreen.kt#L337), [RoutineEditorViewModel.kt:212–214](../../feature/routines/src/main/kotlin/com/gymtracker/feature/routines/RoutineEditorViewModel.kt#L212).

**REP-28 — Rename can create an unnamed routine (P2, C).** Create validates a trimmed nonblank name, but Rename writes each keystroke without the same rule. Clear the field and leave: the blank value persists and may be copied into a new session. Keep a draft, explain invalid input, and commit a valid name while preserving the old one on cancellation.

Evidence: [RoutinesScreen.kt:289–290](../../feature/routines/src/main/kotlin/com/gymtracker/feature/routines/RoutinesScreen.kt#L289), [RoutineEditorViewModel.kt:194–197](../../feature/routines/src/main/kotlin/com/gymtracker/feature/routines/RoutineEditorViewModel.kt#L194), [RenameRoutine.kt:9–13](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/routine/RenameRoutine.kt#L9).

**REP-29 — Creation stops one step before the member's actual task (P2, C).** Naming a new routine inserts an empty list item and leaves the member on the list. They must find it and tap Edit to add movements. Open the created routine's editor directly. Keep multi-add; avoid forcing one round trip per movement or copying targets without explicit intent.

Evidence: [RoutinesScreen.kt:131–136](../../feature/routines/src/main/kotlin/com/gymtracker/feature/routines/RoutinesScreen.kt#L131), [RoutinesViewModel.kt:99–102](../../feature/routines/src/main/kotlin/com/gymtracker/feature/routines/RoutinesViewModel.kt#L99).

### Navigation, integrations, and backup decisions

**REP-30 — Starting a workout removes access to useful settings (P2, C/D).** Settings and Routines are only linked from idle Train; the bottom bar disappears on an active workout. A member discovering they need to reconnect a band, change screen-awake behavior, or adjust the default rest cannot reach these controls from the session. An overflow/settings entry can preserve workout focus while allowing a reversible visit. This was already identified as intentional ADR-0030 behavior in the September review; it is a deliberate design reconsideration, not an unreported bug.

Evidence: [SessionScaffold.kt:193–220 and 271–359](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/SessionScaffold.kt#L193), [GymTrackerNavHost.kt:362–370](../../app/src/main/kotlin/com/gymtracker/app/GymTrackerNavHost.kt#L362), [roadmap.md:1325–1335](../../specs/roadmap.md#L1325).

**REP-31 — Permission denial is a description rather than a recovery workflow (P2, C).** Health/band sections can stay enabled but say nothing is read after permission is denied. The user has to discover off/on as the retry mechanism, and permanent denial may need Android Settings. Show a context-specific Continue/Retry/Manage permissions action and the actual state. Declining remains fully supported.

Evidence: [SettingsScreen.kt:460–469 and 532–542](../../feature/settings/src/main/kotlin/com/gymtracker/feature/settings/SettingsScreen.kt#L460), [HeartRateBandViewModel.kt:100–104](../../feature/settings/src/main/kotlin/com/gymtracker/feature/settings/HeartRateBandViewModel.kt#L100).

**REP-32 — Changing a band is entangled with feature activation (P2, C).** Scanning follows the permission walk after toggling on. There are no explicit Change band, Search again, or Forget actions; the selection display uses an address instead of a friendly identity. Separate feature enablement, permissions, and device management. Combined with REP-30, connection recovery is especially awkward when training has already begun.

Evidence: [HeartRateBandViewModel.kt:62–118](../../feature/settings/src/main/kotlin/com/gymtracker/feature/settings/HeartRateBandViewModel.kt#L62), [SettingsScreen.kt:524–567](../../feature/settings/src/main/kotlin/com/gymtracker/feature/settings/SettingsScreen.kt#L524).

**REP-33 — Restore confirmation cannot identify an older/incomplete backup (P2, C).** Counts of workouts and routines do not reveal that a file predates recent corrections, contains fewer sets, or belongs to another identity. Export metadata is not retained for preview. Show export date, latest workout date, set count, and appropriate ownership context before replacement. Retaining one local pre-restore snapshot is an additional decision; it is not assumed to be approved.

Evidence: [SettingsScreen.kt:725–729](../../feature/settings/src/main/kotlin/com/gymtracker/feature/settings/SettingsScreen.kt#L725), [PreviewBackupImport.kt:13–19](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/backup/PreviewBackupImport.kt#L13), [BackupCodec.kt:48–69](../../core/data/src/main/kotlin/com/gymtracker/core/data/backup/BackupCodec.kt#L48).

**REP-34 — Restore scope differs from its explanation (P2, C).** “Replace everything on this device” actually deletes rows for the incoming backup's member ID, inserts that file, and switches the current identity. When the IDs differ, the previous member's rows remain stored but become hidden. Decide whether import means restoring this member, replacing this installation, or changing people. Implement and describe that exact boundary; settle it before account adoption/sync makes ownership more complex.

Evidence: [SettingsScreen.kt:380–382 and 725–729](../../feature/settings/src/main/kotlin/com/gymtracker/feature/settings/SettingsScreen.kt#L380), [RoomBackupStore.kt:68–73 and 102](../../core/data/src/main/kotlin/com/gymtracker/core/data/backup/RoomBackupStore.kt#L68).

### Design consistency, accessibility, and explanatory states

**REP-35 — Stepper values lack associated accessible labels (P2, C/V).** Weight/Reps/Sets labels are separate preceding Text nodes. The editable OutlinedTextField does not receive a label or explicit content description. Plus/minus buttons are named, but focusing the value itself may leave a TalkBack user with a number and no field identity. Associate the visible label with the editable value and verify focus order and announcements using TalkBack. Do not treat a preceding text node as proof of an accessible field name.

Evidence: [StepperField.kt:59–101](../../core/designsystem/src/main/kotlin/com/gymtracker/core/designsystem/component/StepperField.kt#L59).

**REP-36 — Several control groups assume generous width/height (P2, C/V).** Up/Down/Set target/Remove are in one nonwrapping row; the routine header places an unconstrained title before Finish; some switch rows use long unweighted labels; guided setup and target editing stack several fields without their own adaptive scrolling. These are confirmed structures, not measured clipping claims. Test at 320dp and 130%/200% text, with long routine/exercise names, landscape, and keyboard visible. Use wrapping action groups, constrained labels, and scrollable dialog content where the measurements require them.

Evidence: [RoutineEditorScreen.kt:307–345 and 465–479](../../feature/routines/src/main/kotlin/com/gymtracker/feature/routines/RoutineEditorScreen.kt#L307), [SessionScaffold.kt:531–564](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/SessionScaffold.kt#L531), [SettingsScreen.kt:286–327](../../feature/settings/src/main/kotlin/com/gymtracker/feature/settings/SettingsScreen.kt#L286), [GuidedExerciseScreen.kt:410–448](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/GuidedExerciseScreen.kt#L410).

**REP-37 — Home has no small-screen/large-text overflow strategy (P2, C/V).** Idle Train is a fixed full-height Column with a mascot band, header controls, duplicated catalog/history shortcuts, and up to four stacked actions when a routine exists. There is no scroll container. Long labels, 200% text, a persistent heart-rate header, or a short viewport can compress the layout. Measure before claiming specific hidden controls. Preserve Start as the easy thumb action, allow overflow, and consider moving redundant tab shortcuts out of the main action stack. A first-use explanation of a routine versus freestyle should be evaluated alongside the M7 onboarding commitment.

Evidence: [SessionScaffold.kt:202–260](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/SessionScaffold.kt#L202), [LiveHeartRateChip.kt:49–98](../../app/src/main/kotlin/com/gymtracker/app/LiveHeartRateChip.kt#L49), [GymButtons.kt:43 and 158](../../core/designsystem/src/main/kotlin/com/gymtracker/core/designsystem/component/GymButtons.kt#L43).

**REP-38 — Rest setup is unnecessarily repetitive (P3, C/D).** Default rest is read-only and changes in five-second increments, so 60 → 180 seconds takes 24 presses. This was an intentional repair of a misleading editable-looking field. Offer common presets plus fine adjustment or validated minutes/seconds entry. Keep per-movement rest targets and the already-shipped +30-second action.

Evidence: [SettingsScreen.kt:263–272](../../feature/settings/src/main/kotlin/com/gymtracker/feature/settings/SettingsScreen.kt#L263), [SettingsViewModel.kt:317–320 and 389–390](../../feature/settings/src/main/kotlin/com/gymtracker/feature/settings/SettingsViewModel.kt#L317).

**REP-39 — Missing content looks like loading forever (P3, C).** Exercise detail treats a missing exercise as an indefinite spinner; workout detail treats missing/deleted data the same way and returns before rendering its visible Back bar. Separate loading, missing, and error states. Always preserve a visible exit and explain the unavailable record.

Evidence: [ExerciseDetailScreen.kt:92–100](../../feature/catalog/src/main/kotlin/com/gymtracker/feature/catalog/ExerciseDetailScreen.kt#L92), [WorkoutDetailScreen.kt:81–88](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/WorkoutDetailScreen.kt#L81).

**REP-40 — Estimated-1RM copy describes a different calculation (P3, C).** The explanation says “your heaviest set,” but the calculation uses the maximum Epley estimate across the session's sets. A lighter set with more reps can win. Say “Highest estimate from your recorded sets,” retain the estimate label, and keep the original performed values visible.

Evidence: [ExerciseProgressScreen.kt:319–320](../../feature/progress/src/main/kotlin/com/gymtracker/feature/progress/ExerciseProgressScreen.kt#L319), [ExerciseTrendOf.kt:94–99](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/progress/ExerciseTrendOf.kt#L94).

**REP-41 — Finish does not fully explain the actual outcome (P2, C/D).** The confirmation says the workout moves to history, but an empty workout is deleted. A targeted workout has no context about unfinished sets, and finish has no resume/reopen recovery. Recommend accurate empty-session wording and a clear saved outcome. Whether a short Undo finish or Reopen workout is needed depends on how the household handles accidental finish and interrupted visits; do not add a mandatory completion gate.

Evidence: [SessionAlerts.kt:83–84](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/SessionAlerts.kt#L83), [EndSession.kt:47–49](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/session/EndSession.kt#L47), [SessionScaffold.kt:511–513](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/SessionScaffold.kt#L511).

**REP-42 — RPE is operable without being understandable to a beginner (P2, C).** The entry/editor says only “RPE (optional)” and offers `@5` through `@10` chips. It does not expand the term, explain the scale, or tell someone that tapping a selected value clears it. Add optional plain-language help and a clear way to remove a value, while leaving logging fully useful without RPE. This becomes more important if beginners are a primary audience; it does not justify turning effort entry into a required step.

Evidence: [SetSheets.kt:287–312](../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/SetSheets.kt#L287).

**REP-43 — No reuse offer after changing a routine's exercises during a workout (P2, R/C).** The user explicitly requested this capability after Q25. Today, starting a routine makes a one-way copy of its movements/targets, and workout changes remain session-only. That was intentional protection, not a breach of the original spec. Repeating the same additions/removals next visit requires editing the saved routine separately. Offer an explicit way to retain net exercise-list changes without silently modifying the original or rewriting logged history. Accepted interaction (Q26–Q28): one review after the finished workout is saved, with Update original, Save as new, and Today only; net structural edits only, not skipped sets or performed load/reps. Protect surviving targets, distinguish duplicate exercise occurrences, and preserve the saved workout if the template save is cancelled/fails. ADR-0051 records this explicit-save boundary.

Evidence: user requirement after Q25; [StartSessionFromRoutine.kt:14–30 and 52–60](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/routine/StartSessionFromRoutine.kt#L14), [ADR-0028:107–132](../../specs/adr/0028-a-session-remembers-its-routine.md#L107). Boundary proposal: [ADR-0051](../../specs/adr/0051-explicit-routine-updates-from-a-workout.md).

## Feature gaps already recorded in the repository

These should not be presented as new discoveries or reopened for approval without a reason.

| Commitment | Current evidence | Review implication |
|---|---|---|
| Optional accounts, household, sync, status, account deletion | M2 remains in progress; local outbox/adoption are groundwork | Important rollout gaps; do not claim working cross-device household behavior |
| Session-only machine substitution | Designed, not built; needs a story | Retain the existing direction: today's swap does not silently edit the routine |
| Supersets | Designed as pairs, not arbitrary groups; needs a story | Current single-movement rest model does not yet serve the promised workflow |
| Set types: warm-up, drop, failure | Approved in the September 4 review, not present in current set model | Carry forward as accepted backlog; distinguish lifting warm-up sets from the separate timed warm-up |
| Persistent per-exercise setup note | Approved September 4; no per-session note | Carry forward the decision; useful for seat/pin/setup recall without adding generic journaling |
| +30 seconds and cues at ten seconds/zero | Already shipped | Preserve them and reconcile focused mode's capability gap; do not request permission again |
| Exact-machine form guides | M4b awaits exact assets/documentation and human review | Treat missing guides as a scoped dependency; do not substitute generic animations |
| Household media, AI coaching, onboarding/accessibility | M2a, M6, M7 commitments | Separate roadmap work from defects in today's useful offline app |
| Custom household exercises | Explicitly deferred in the September review | Do not assume authorization to expand the catalog model |

Evidence: [roadmap.md:106–230](../../specs/roadmap.md#L106), [roadmap.md:618–641](../../specs/roadmap.md#L618), [roadmap.md:843–869](../../specs/roadmap.md#L843), [roadmap.md:1384–1392](../../specs/roadmap.md#L1384), [roadmap.md:1582–1604](../../specs/roadmap.md#L1582), [ExerciseSet.kt](../../core/domain/src/main/kotlin/com/gymtracker/core/domain/model/ExerciseSet.kt).

## Agreed workflow for implementation planning

1. **Prepare:** create/select a routine; identify an exercise before adding it; optionally record its exact machine setup. Templates describe intent, never performed data.
2. **Start/resume:** return to the right member, workout, and selected movement. Reconcile an old workout before restoring a focused view. Make the meaning of duration explicit.
3. **Perform/log:** show target and relevant prior actual values distinctly. Log one actual set with one primary action; adjust through the same entry semantics in every presentation. Bulk entry is explicit.
4. **Recover/rest:** preserve the countdown while checking/correcting a set or selecting another movement. A machine being busy changes today's workout without silently changing its template.
5. **Advance:** show honest progress against the plan, retain control over extra/skipped movements, and avoid prescribing another set merely because it was logged last.
6. **Finish/review:** accurately describe what was saved or discarded, offer the agreed bounded Undo finish, and update every derived view when the log changes. After saving a routine workout with net exercise-list changes, preview Update original / Save as new / Today only without changing performed values or historical snapshots.
7. **Protect data:** make backup age, ownership, replacement scope, and restore outcome understandable. Integration denial/failure always leaves logging usable.

This summarizes Q1–Q28 for implementation planning. It does not replace existing stories or mark the consolidated shared-understanding checkpoint complete.

## Verification needed before implementation is called complete

- Use a current build in an isolated installation with no real training data. Cover empty, sparse, long-history, routine-target, rest-only-target, and bodyweight cases.
- Measure real visible logging access from app open and after returning from rest; count scrolling and visual search as friction even where tap-count tests pass.
- Exercise all logging modes with delayed writes, rapid taps, invalid/pasted loads, correction, deletion/Undo, and process recreation.
- Restore valid and structurally invalid files from another installation identity; verify ownership, retained screens, rollback, and useful error messages.
- Correct/delete/undo a record-setting set and follow every return path to the list, featured estimate, badges, graph, log, and volume.
- Render light/dark, 320dp, 130%/200% font scales, long names, landscape, IME visible, and live-HR states. Check field names and navigation with TalkBack.
- Follow repository spec-first/test-first rules for any production change, including the required gates. No production implementation was undertaken during this review.

Product questions and the evolving glossary/decision tree live in [the alignment document](2026-10-22-product-alignment.md).
