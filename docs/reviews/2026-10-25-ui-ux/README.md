# REP UI/UX discussion review

Date: 2026-10-25. Application baseline: `f6deca7`; review documentation baseline: `fb4282d`.

This is a visual companion to the [43-item product review](../2026-10-22-product-ux-review.md) and [agreed product direction](../2026-10-22-implementation-handoff.md). The proposed screens are a throwaway HTML prototype with synthetic example records. They do not implement the Android changes.

## Start with the visuals

- [Three workout layout options](screenshots/proposed-layout-options.png): A, workout sheet; B, focused movement and plan drawer; C, whole-plan checklist.
- [Six-screen proposed flow](screenshots/proposed-flow.png): home, workout, entry, exercise picking, Progress, and finish.
- [Explicit routine-save choices](screenshots/proposed-routine.png): Update original / Save as new / Today only, after the workout is saved.
- [Narrow workout example](screenshots/proposed-workout-narrow.png) and [dark workout example](screenshots/proposed-workout-dark.png).
- [Interactive prototype](prototype.html?variant=A). Open the HTML locally or run `python3 -m http.server 8766 --bind 127.0.0.1` from the repository root and visit `http://127.0.0.1:8766/docs/reviews/2026-10-25-ui-ux/prototype.html?variant=A`.

The prototype uses REP's bundled Archivo font, square controls, neutral ground and red accent. Sample loads, reps and times are illustrative. Its frozen rest display demonstrates layout and selection preservation; it does not test Android timers, persistence, notifications, or accessibility services. Prototype navigation and simulated actions remain in memory.

## Physical-device capture status

A Pixel 8a running Android 17 is connected. Its existing display configuration is 1080×2400, density 420, dark mode, and font scale 1.15. REP was absent; the current debug APK was installed fresh. The phone remains locked, so there are **no physical-device app screenshots or device-observed layout claims yet**. Unlocking the phone is the prerequisite for native captures. No existing REP training records were overwritten.

## Main issues and suggested fixes

| ID | Issue and user cost | Suggested fix | Existing review / evidence |
|---|---|---|---|
| UI-01 | Home emphasizes the absence of a workout, with routine preparation reached through a secondary top control. A beginner has no obvious guided first step. | Empty home: Create first routine as the primary action, then freestyle and restore. Once a routine exists, show its name/movements and Start; keep setup secondary. The pictured home uses an existing sample routine. | REP-29/37; [NoSession](../../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/SessionScaffold.kt#L193) |
| UI-02 | The current movement's log action is after every other movement in the scroll. More exercises or recorded sets increase the distance to the frequent action. | Put one Log/Next action in a fixed bottom area; scroll the plan and actual records above it. Keep load/reps visible in the action. Preserve both the two-tap entry path and one-tap logging. | REP-14; [SessionPlan](../../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/SessionMovements.kt#L112) |
| UI-03 | Rest replaces the plan and its correction/switching tools, making recovery time a separate navigation state. | Keep one compact persistent countdown above the plan, retain +30s/Skip/cues, and let selection/corrections happen underneath. Restart rest only when a new set is logged. | REP-07/13; [ActiveSession](../../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/SessionScaffold.kt#L305), [RestingBody](../../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/RestPanel.kt#L132) |
| UI-04 | “Add set” and “Save set” can write a prefilled batch, so the label and the number of actual records disagree. | A normal action saves exactly one actual set. Move bulk entry behind an explicit option with a count-labelled save. Keep history prefill and the target visibly distinct. | REP-02/05; [SetEntrySheet](../../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/SetSheets.kt#L74), [ResolveSetPrefill](../../../core/domain/src/main/kotlin/com/gymtracker/core/domain/set/ResolveSetPrefill.kt#L28) |
| UI-05 | Movement progress and “next” do not consistently express the remaining planned set count. Logging one set can suggest that a movement is done; rest may still offer extra sets beyond the plan. | Show “2 of 3 actual sets”, then Plan met after three actual sets. Offer Next movement as the primary action, Extra set as secondary, and explicit End early/Skip. Without a set target, use explicit Done. | REP-08/09; [SessionProgress](../../../core/domain/src/main/kotlin/com/gymtracker/core/domain/session/SessionProgress.kt), [UpNext](../../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/RestPanel.kt) |
| UI-06 | “Start exercise” opens a separately configured guided flow, while the plan already names targets. The member must reconcile two representations of the same workout. | Treat focus as a presentation of the existing workout and actual sets. No new 3×12 setup or independent completion logic. Preserve corrections, optional effort and rest controls. | REP-11/12/13; [GuidedExerciseScreen](../../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/GuidedExerciseScreen.kt) |
| UI-07 | Catalog rows display a plus in browse mode even though tapping opens information. In picker mode, tapping adds without a way to inspect first. | Browsing: chevron/View exercise. Picking: separate inspect and Add actions, with clear added feedback and a fixed Done action. Preserve intentional duplicate exercise occurrences. | REP-25/26; [Results / AddedCell](../../../feature/catalog/src/main/kotlin/com/gymtracker/feature/catalog/BrowseScreen.kt#L286) |
| UI-08 | Long movement names and compact comparison lines compete with important records. Full previous performance has no expansion. | Allow names to wrap where space permits and expose full text on detail. Show actual set rows and expandable full previous performance. Reduce decorative/meta competition before shrinking useful data. | REP-15/35/36; [CurrentMovement](../../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/SessionMovements.kt#L246) |
| UI-09 | Progress leads with an automatically selected estimated lift; other trained exercises are difficult to reach. A beginner sees an estimate before their recent actual work. | Lead with a trained-exercise selector, last actual load/reps/sets, and linked dated records. Keep estimates and PRs secondary and explicitly labelled. Bodyweight sets/reps remain valid progress. | REP-19/20/23; [FeaturedLift](../../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/HistoryScreen.kt#L171) |
| UI-10 | Chart spacing, limited point inspection, missing years, and partial-week boundaries make trends harder to interpret accurately. | Actual-time horizontal spacing, units and inspectable values linked to workouts. Distinguish same-day workouts. Include years where needed and label the current Monday-based week partial. | REP-21/22/24; [ExerciseProgressScreen](../../../feature/progress/src/main/kotlin/com/gymtracker/feature/progress/ExerciseProgressScreen.kt), [WeeklyVolumeScreen](../../../feature/progress/src/main/kotlin/com/gymtracker/feature/progress/WeeklyVolumeScreen.kt) |
| UI-11 | A workout removes access to Settings, including permission/band recovery. Recovering an optional feature can require leaving the training context. | A quiet settings entry that preserves selected movement, workout and countdown. Keep destructive restore blocked while active. Optional setup failures must leave logging usable. | REP-30/31/32; [SessionScaffold](../../../feature/logging/src/main/kotlin/com/gymtracker/feature/logging/session/SessionScaffold.kt) |
| UI-12 | Finish/reuse does not yet express the agreed distinction between performed results, ended-early/skipped movements, and reusable routine changes. | Finish saves the workout first. Read back actual reps/counts and honest statuses. For net list changes, preview Update original / Save as new / Today only; preserve surviving targets and never turn actuals into targets automatically. | REP-12/41/43; [accepted ADR-0051](../../../specs/adr/0051-explicit-routine-updates-from-a-workout.md) |

These are interaction and information-hierarchy problems. The color identity is already decided; changing it would not resolve them. Source-backed issues above remain distinct from device-observed layout consequences.

## Which workout layout to discuss

| Option | Structure | Benefit | Tradeoff |
|---|---|---|---|
| A — Workout sheet, recommended starting point | Selected movement + actual set table + expandable last time + other movements, persistent rest and bottom Log/Next | Keeps plan, comparison and correction together with a reachable action | Long plans/history still scroll; fixed action area must adapt to IME and large text |
| B — Focus + plan drawer | One movement dominates; full plan is a secondary drawer | Fewer simultaneous decisions; larger current values | Adds a step to inspect/switch the wider plan |
| C — Whole-plan checklist | Workout is the main view; current movement expands in place | Strong overview; straightforward selection | Dense on a small screen; grows substantially with long routines |

Start discussion with A. The user has already settled the behavior: flexible selection, one timer, one actual set, history-first prefill, honest completion and explicit routine reuse. Choosing a layout should not reopen those decisions. Useful feedback is which information must remain visible at rest, and whether the full plan should be inline, behind a drawer, or the main surface.

## Verification and reproduction

`node docs/reviews/2026-10-25-ui-ux/capture-prototype.mjs` regenerates the proposed screenshots using installed Chromium and no external assets. [Recorded prototype checks](prototype-checks.json) cover all three variants at 320px and 390px, no horizontal page overflow, visible fixed Log/Next, invalid-load refusal with draft preservation, one simulated set per Log, Next after planned count, independent selected-movement values, preserved rest state on selection and URL-based variant switching. These checks validate the prototype only.

Android production source is unchanged. `./gradlew assembleDebug --console=plain` passed for the current app. The earlier required full local checks and all three CI jobs on PR #84 passed on the same production source baseline. New proposed screens need real implementation stories, failing tests, current-build device verification, TalkBack, IME and large-text checks before they can be described as shipped or accessible.

The separate `prototype/rep-ui-ux-discussion` branch preserves the throwaway alternatives for discussion. Do not merge prototype code into the production Android source. A selected direction must be implemented through the repository's spec-first/test-first process.
