# ADR-0030: Three tabs, a hand-built bottom bar, and a routine on Train home

- **Status:** accepted
- **Date:** 2026-08-12
- **Deciders:** maintainer (chose the direction, authorised the custom widget), agent (scoped it)
- **Supersedes:** ADR-0024's tab count and bar implementation. ADR-0024's other decisions —
  History (now Progress) and workout detail as real destinations, the bar hidden during a
  session, what stays out of the graph — are unchanged.
- **Amends:** ADR-0028's claim that `RoutineOrigin.id` is "written but never read" — see
  Decision, third bullet.

## Context

`Redesign.dc.html`'s section 2a is explicit and goes further than ADR-0024 could when it was
written:

> **Three tabs, not four.** Routines is a setup surface you touch monthly, not a daily
> destination — it does not earn permanent real estate. Routines gets exactly one fixed entry
> point: an outlined `ROUTINES` button in the Train header, top right, present on every Train
> state including the empty first-run screen. **Replace `NavigationBar`, do not restyle it.**

`roadmap.md`'s audit section had this recorded as closed against a lower bar: *"the nav pill is
not closed... fixing it means reimplementing `NavigationBarItem` from primitives, which is a
custom widget — out of bounds per the redesign brief's own constraints... Revisit if Material3
ever adds the hook, or if a custom widget is explicitly authorised."* The brief authorises it in
as many words — *"Material 3 + Compose components only, with one deliberate exception: the
bottom bar (see section 2a)"* — which is the condition that reopens this.

Two consequences follow that are not purely cosmetic:

1. Removing Routines as a tab makes it unreachable unless something else takes its place. The
   redesign audit's finding 01 — *"a session has no plan... every session opens empty"* — is also
   still open on Train home specifically: `NoSession` says nothing about which routine, if any,
   is due.
2. Train home showing *which* routine is next means picking one. There is no schedule or split
   model in the data (`Routine` carries only a name and a position — no weekday, no cadence), and
   inventing one is a larger, undecided product question the design's "next in your split"
   framing does not settle. What the data *can* answer honestly is "which routine have you done
   least recently" — a real signal, not a fabricated one.

## Options considered

1. **Leave the bar as `NavigationBar`, accept the pill.** Rejected — the brief now explicitly
   authorises the fix ADR-0024 was blocked on.
2. **Four tabs, restyle the indicator some other way.** Rejected on the same evidence
   `roadmap.md` already recorded: `NavigationBarItem` exposes no `shape` parameter, full stop.
3. **Three tabs; a hand-built bar; Train home shows a full weekly-split hero exactly matching the
   frame.** Rejected for now — modeling a split (which days, which routine) is undecided product
   work the brief itself leaves open in places ("whether a swap made three times should offer to
   update the routine" is a similarly-flagged open question elsewhere in the same document), and
   building it here would be inventing acceptance criteria rather than reading them off a spec.
4. **Three tabs; a hand-built bar; Train home names the least-recently-performed routine and
   offers to start it — chosen.** Answers what the data can honestly answer, gets Routines a real
   entry point, and stays inside what the brief actually specifies for section 2a. The full
   per-movement hero (numbered rows with targets) is left for a later pass once a split model
   exists to justify the "next in your split" framing; the current Train home already lists
   movements once a session exists, so nothing about "seeing the plan" is lost, only deferred to
   after Start.

## Decision

**Three top-level destinations: Train, Exercises, Progress.** `TopLevelDestination` in
`GymTrackerNavHost.kt` drops `ROUTINES`. The `Routines` composable destination itself is
unchanged — it becomes a drill-down, reached by push rather than tab-switch, and gains a
`DrillDownTopBar` the same way `RoutineEditor` already has one, since it no longer has the bar to
exit through.

**A hand-built `GymNavigationBar`, in `:core:designsystem`, replaces `NavigationBar`.** Not a
restyle — a `Row` of equal-weight cells, each a `Column` of a 24dp icon over a 12sp/800 label,
built from primitives so every corner is actually 0dp rather than the token Material's
`NavigationBarItem` never reads. This is the one exception the brief and the design-system's own
"custom widgets are not [allowed], unless a Material component hardcodes a shape the design
system forbids" carve-out both name. Selected state reads `colorScheme.primaryContainer` /
`colorScheme.primary` (background / content) and unselected reads `colorScheme.outline` — the
frame's literal `#FFE0D9` / `#AE1800` / `#605D5D` values are, respectively, `primaryContainer`,
`primary`, and `outline` already, or close enough that reusing the tested tokens beats adding a
new ungated color for one component. The three icons (a filled play triangle, a bulleted list, a
clock) are hand-authored vector drawables — no new dependency; the same reasoning
`StepperField`'s +/− glyphs and `DrillDownTopBar`'s "Back" label already use to avoid one.

**Routines gets one fixed entry point: an outlined `Routines` button in the Train header,
top-right, on every Train state.** Reached by `navController.navigate(Routines)` — a push, not a
tab switch, matching how `RoutineEditor` and `Browse(pickForSession = true)` already reach
drill-downs from Train.

**Train home names the routine due next, and offers to start it.** A new domain class,
`NextRoutineToTrain`, ranks the member's routines by how long it has been since each was last
performed — never performed sorts first, ties break by list position — and returns the one at
the top, or null with no routines at all. It is the first reader of
`WorkoutSession.routine?.id` (`RoutineOrigin`, ADR-0028), which that ADR wrote once at session
start and predicted would eventually be read exactly this way: *"a per-routine count, a 'last
run of this routine' comparison"* is the ADR's own example of the sanctioned future use. The id
is used only to match a session back to a routine's identity — never rendered; what renders is
the routine's current `name`, read fresh, not the session's (possibly stale) copy of it. This
**amends** ADR-0028's "written but never read" claim to "written, and read only for identity
matching, never as a display value or a foreign key" — the narrower rule ADR-0028 actually
protects (see that ADR's §Consequences: *"anything wanting `routine_id` as a live pointer"* is
the revisit trigger, and identity matching for a ranking is not a live pointer in the sense that
sentence means: nothing here joins on it to fetch rows, it is compared for equality only).

**No schedule, no per-movement hero, no invented cadence.** `NoSession` shows the next-up
routine's name and a `Start <name>` primary action beside the existing `Freestyle` (unchanged
"Start workout") action, when a routine exists; the pre-existing generic "Start workout" copy is
kept unchanged when there are no routines at all, so first-run behaviour — and every existing
instrumented test that depends on it — is untouched. The numbered movement-list-with-targets the
frame shows on Train home is deferred; it already renders once a workout is running
(`SessionPlan`), so no plan is permanently hidden, only shown one tap later than the frame draws
it.

## Consequences

- `RoutineDeletionTest` needed no code change: its `ROUTINES_TAB = "Routines"` constant matches
  the new header button's label by coincidence of wording, not by design intent to keep tests
  passing unedited the way `TwoTapSetLoggingTest` must — this is a genuine behaviour change
  (push instead of tab-switch) that happens not to break that one assertion.
- `ActiveSessionViewModel` gains one more fire-and-forget action, `onStartFromRoutine`, mirroring
  `onStartWorkout` exactly — no change to its `combine` chain, which is where its complexity
  actually lives (see that class's own doc comment on why that chain is one `combine`, not
  several).
- A small `TrainHomeViewModel`, not folded into `ActiveSessionViewModel`, carries the "what's
  next" read — `SessionPresenceViewModel`'s own doc comment states the same principle this
  follows: "the smallest slice of the signal, lifted to where it is needed."
- **This PR is at the ~400-line threshold `CLAUDE.md` asks to split at, and is not split.** The
  three pieces — dropping the tab, adding its replacement entry point, and giving Train home
  something to say instead of the tab it lost — are the same change seen from three sides:
  splitting any one out would ship a state where Routines is either unreachable or duplicated.
- **Revisit when a split/schedule model exists.** At that point "next in your split" can mean
  what the frame's eyebrow literally says, and the full per-movement hero becomes buildable
  without inventing the model it depends on.
