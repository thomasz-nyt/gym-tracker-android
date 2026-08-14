# ADR-0032: Progress rows earn a hierarchy, and a purpose-built PR read

- **Status:** accepted
- **Date:** 2026-08-14
- **Deciders:** maintainer (chose the row shape and the PR-badge scope), agent (scoped it)
- **Relates to:** ADR-0025 (what counts as a personal record), ADR-0029 (the ruled-sheet
  precedent this row now follows), US-33 (which deferred the badge with the reason quoted below)

## Context

`Redesign.dc.html`'s section 5 and audit finding 06 both target the same row: a `ListItem` with
the routine name, the date, and a four-metric summary line — `"3m · 3 exercises · 34 sets ·
16,069 lb"` — all at equal visual weight across three lines. Two separable complaints:

1. **No hierarchy.** Four numbers at the same size and weight read as noise, which is what let
   `"47m · 1 exercise · 1 set · 960 lb"` and `"3m · 34 sets"` both pass review unremarked.
2. **No PR badge.** US-33 built the rest of Progress but named this gap explicitly and left it:

   > "Deferred, not built: a 'PR' badge on session rows. `PersonalRecordsAchievedIn` reads the
   > member's entire session history per row it is asked about — fine for
   > `FinishSummaryScreen`'s one row, not for every visible row of a 200-session list. Needs a
   > purpose-built O(sets) read."

This ADR answers both, and only both — it does not touch the duration/volume *computation* audit
finding 06 also names. `"3m · 34 sets"` is what bulk set entry (ADR-0009) against a real
`startedAt`/`endedAt` window actually produces; the number is honest, and the fix is presentation,
not arithmetic. Making it a smaller, muted second line is that fix.

## Decision

### The row becomes two lines, not three

Line one: the routine name at `titleSmall` (20sp, ExtraBold), with `· Tue 4 Aug` appended in the
same `Text` as a lighter `SpanStyle` span — one text node, two weights, not two rows. Line two:
the existing four-metric summary (`SessionSummary.describe`, unchanged) at `bodySmall`, muted.
The row itself moves from `ListItem` to a plain ruled `Row` on the bare ground with a `GymDivider`
beneath it, matching ADR-0029's ruled-sheet precedent rather than `ListItem`'s implicit surface.

No new `Typography` role is added. Material 3's `Typography` has exactly fifteen slots and
ADR-0029 already filled every one that was unused; `titleSmall` and `bodySmall` are the closest
existing roles to the design's literal 18sp/14sp, and `Type.kt`'s own class doc already
established the precedent for this exact trade — the 15sp meta-text case that settled for
`bodySmall`'s 16sp "one pixel below the design and above ADR-0011's own floor."

### `SessionsWithRecords`: one pass, not one query per set

A new `:core:domain` class answers "which finished sessions contain a personal record" for every
row visible at once, in one read: every loaded set the member has ever logged, grouped by
(exercise, reps), walked in chronological order, marking a session the instant one of its sets
strictly beats the running max for that group — the same "first time is not a record, beating
must be strict" rule `DetectPersonalRecord` already defines (ADR-0025), computed once instead of
per set. `HistoryViewModel` loads it once per `open()`, alongside the session list, not woven
into `HistoryController`'s own state — it answers a different question (all-time history) than
what makes a row's fields load (recent state), and there is no reason to recompute it on every
delete or undo.

The badge itself is outlined, never filled, in `MaterialTheme.colorScheme.primary` — the accent
stays spent on the log button elsewhere in the app (ADR-0029's "exactly one filled accent
element" rule); a badge is emphasis, not the screen's one action.

## Options considered

1. **Keep `ListItem`, add the badge to `trailingContent`.** Fixes the deferred badge without
   touching the hierarchy complaint at all — the four metrics stay equal-weight. Rejected: it
   leaves finding 06 half-answered.
2. **Rebuild the row as a ruled two-line `Row`, plus the badge — chosen.** Answers both
   complaints with one change, since they are the same row.
3. **Invent two new named type roles** (18sp/800, 14sp/600) to match the design pixel-for-pixel.
   Rejected: there is nowhere left in `Typography` to put them without overloading a role every
   other screen already depends on, which is a materially bigger and riskier change than the row
   itself for a 2sp/1sp difference nobody will notice standing at a machine.

## Consequences

- `HistoryUiState` and `HistoryState` both gain a `sessionsWithRecords: Set<SessionId>` field;
  `HistoryViewModel`'s constructor gains `SessionsWithRecords`, wired in `DataModule.kt` the same
  way every other `:core:domain` use case already is.
- `WorkoutHistoryTest` covers the badge with a two-session case: a heavier, later set badges its
  own session, an earlier one does not. `SessionsWithRecordsTest` covers the merge rule itself in
  isolation — first-appearance-is-not-a-record, ties are not records, a different rep count is a
  separate track, bodyweight sets never count — table tests against the same rule
  `DetectPersonalRecord` already defines, so the two cannot silently disagree.
- `GymDimens` gains `MinListRowHeight` (72dp), pinned in `GymDimensTest`, rather than a private
  literal in `HistoryScreen.kt` — the same escape PR A of this audit's own follow-up work is
  independently closing elsewhere in the app.
- **Revisit if** a future screen needs a badge like this one (a workout-log or exercise-log row,
  per the design's other frames) — the current implementation is `HistoryScreen`-local; a second
  call site is the signal to promote `PrBadge` into `:core:designsystem`, not before.
