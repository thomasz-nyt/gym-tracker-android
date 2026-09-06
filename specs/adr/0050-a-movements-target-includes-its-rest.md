# ADR-0050: A movement's target includes the rest that follows each set

- **Status:** accepted
- **Date:** 2026-09-05
- **Deciders:** maintainer (approved the review's Tier 1 on 2026-09-04), agent (scoped)
- **Amends:** ADR-0027 (a target gains a fourth field), US-05 (the rest's length), US-30 (what a
  target is)
- **Relates to:** ADR-0010 (the rest is a stored end time), ADR-0023 (the rest panel), ADR-0049
  (`+30s` — the in-the-moment extension this is the planned counterpart of), ADR-0034 and ADR-0043
  (the backup and sync payloads the new column travels in)

## Context

US-05 gives the rest between sets one length: the default in Settings, sixty seconds until
changed. The 2026-09-04 review put the trainer's objection plainly — *three minutes for squats,
sixty seconds for curls; one number cannot serve both* — and the maintainer approved building a
per-exercise rest as part of Tier 1. Settings is also unreachable mid-workout (ADR-0030's deliberate
choice), so today the only way to rest longer than the default is to let the timer run out and
count in your head, or, since ADR-0049, to tap `+30S` a few times — which is the right answer to
"this set earned more" and the wrong one to "this movement always needs three minutes".

The rest is a property of the *movement in the plan*: squats need it because they are squats, not
because of anything about today's session. The app already has exactly one place that describes a
movement in a plan — the target ADR-0027 gave a routine item, copied one-way into the session
exercise when the routine starts.

## Options considered

1. **A per-exercise rest stored on the catalog exercise.** One value for "squat" everywhere.
   Rejected: the catalog is bundled and re-seeded on migration (`exercises` is outside sync and
   backup), a household of two would share one number, and it makes rest a fact about the exercise
   rather than about a member's plan for it.
2. **A rest override on the session exercise only, set from the rest band mid-workout.** The
   "quiet ±15 s adjust" the review floated. Rejected as the primary shape: it has to be re-set every
   session, it never reaches the routine, and ADR-0049's `+30S` already covers the in-the-moment
   case. Not built.
3. **A fourth nullable field on `MovementTarget`, `restSeconds`, carried on both `routine_items`
   and `session_exercises`, copied at start like the other three — chosen.** It rides ADR-0027's
   whole apparatus: the one-way copy, the editor, the labelling rule, the codecs. Null means the
   default rest, exactly as a null load means "load unrecorded".
4. **A separate `rest_seconds` concept beside the target, with its own use case and editor.**
   Rejected: it is the same table row, the same copy, the same dialog; a second concept would exist
   only to keep the word "target" pure, and the rest is as much a part of "3 × 8 at 105, two
   minutes between" as the load is.

## Decision

**`MovementTarget` gains `restSeconds: Int?`** — the rest to take after each set of this
movement, in whole seconds, floor one (`TargetValidation`, mirroring the sets and reps floor). Null
means the member's default from Settings, not "no rest". A target that names only a rest is a
target: the four all-null-means-absent readers (the two Room mappers, the backup codec) count the
fourth field, and a test on each pins it.

**Migration v10 → v11**, additive: `target_rest_seconds INTEGER` on `routine_items` and on
`session_exercises`. Every existing target reads back with no rest named — the default — so nothing
a device already has changes meaning. `StartSessionFromRoutine` copies it with the other three
fields without knowing it exists (it copies the whole `MovementTarget`). The sync payload and the
backup envelope each carry it as a nullable field with a default; the backup format version does
not move (`BackupEnvelope`'s own rule: a new nullable field never invalidates a file).

**The rest timer takes it.** `RestTimer.start(rest: Duration? = null)` uses the length it is given,
else the default; either way that length is pinned as the total, so the band's "of 1:30" is the
rest actually taken. Every path that starts a rest after a set passes the movement's own:
`SetEntryController` and `GuidedController` through `RestController.startAfterSet(rest)` with the
appearance looked up by id; the one-tap button and the notification's `LOG SET` through
`UpNextSet.rest`, which `DetermineUpNextSet` reads off the appearance it already has, so
`LogUpNextSet` — the one place both one-tap callers share — cannot fall back to the default while
the sheet takes the target's. Guided mode's own targets (typed in its start dialog) are a separate
concept and unchanged; the rest it starts is the session's.

**It is entered where the target is and read where the target is.** The routine editor's target
dialog gains a fourth field, `Rest (seconds)`, typed as whole seconds with the same per-field
refusal message the other three have (`Rest needs a whole number of seconds, 1 or more.`). The
target line reads `Target 3 × 8 · 105 lb · 1:30 rest` in the routine editor and under the open
movement on the session screen, through one `m:ss` formatter (`MinutesSeconds`, lifted into
`:core:domain` from `:feature:logging`'s `Durations.kt`, which now delegates to it). The still-to-
come rows' compact `3×10 · 90 lb` line does not add it — a queue row says what the movement is,
not how long to rest after it — and the rest panel does not label it either: the band's total is
already the movement's rest, and "of 1:30" says so.

**ADR-0027's rules hold unchanged.** A rest is never written to `sets`; no derived number reads
it; it is rendered as part of the target, labelled as one. It is not a §2.4 hazard in the way a
load is — nobody lifts a rest — but it is a planned number beside real ones, and the labelling
convention is the same.

## Consequences

- A routine arrives at the gym knowing how long to rest after each movement, and the session
  honours it from every button that logs a set. `+30S` (ADR-0049) remains the in-the-moment
  extension on top of whatever length the rest started at.
- Two tables gain one nullable column each (v11); the sync and backup payloads gain one nullable
  field each; no format version moves. Every `MovementTarget(` call site keeps compiling — the
  field is trailing and defaulted — which is also why each all-null-means-absent reader needed its
  own test: the compiler would not have caught a reader that ignored the fourth field.
- `RestTimer.start()`'s signature changes from no arguments to one optional argument; both prior
  callers now pass a value. `RestController.startAfterSet()` likewise.
- The target dialog is four typed fields. Tier 3's inline target steppers (fifteen-second steps for
  the rest) are the natural next shape and are unaffected in scope by this.
- **Not built, deliberately:** a rest of zero ("no rest for this movement") — that is a superset,
  which is its own story and needs a pairing model this field does not have; a per-session rest
  override from the band — `+30S` covers the moment, and a routine's rest covers the plan.
- **Revisit if** a household member wants the rest to vary by *set* within a movement (a longer
  rest before the top set). That is a per-set plan, which ADR-0009 and ADR-0017 both refused as the
  prescription entity, and it should reopen those rather than add a list here.
