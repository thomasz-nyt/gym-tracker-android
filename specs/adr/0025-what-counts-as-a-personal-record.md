# ADR-0025: What counts as a personal record

- **Status:** accepted
- **Date:** 2026-08-08
- **Deciders:** maintainer (chose the rule), agent (framed the options)

## Context

US-18 says "a PR is detected on save and shown inline at the moment it happens" and "a PR list
per exercise with dates". It never says what a PR *is*, and three sessions in a row deferred
the story rather than guess — `CLAUDE.md` forbids inventing an acceptance criterion, and this
one decides what the app celebrates.

The tension is between two things the app already believes.

Constitution §2.4 says **"never fabricate, estimate, or interpolate a logged value."** Read
strictly, that governs *logged* values, and a banner is not one — US-16 already computes an
Epley estimate and puts it on a chart, labelled, without violating anything. So an
estimate-based PR is not forbidden by the letter of §2.4. What it does instead is announce a
record for a weight nobody has ever lifted, which is the same instinct §2.4 is protecting even
where the rule does not literally reach. ADR-0023 refused "set 3 of 3" for overclaiming; this
is that class of decision.

Against that, the obvious honest rule — heaviest set ever lifted — throws information away.
After a 105x1, a later 100x8 is a better performance on almost any reading, and an app that
stays silent about it is not measuring what the member actually did.

## Options considered

1. **Heaviest set lifted.** One record per exercise, always a real lift, trivial to build,
   explain and test. Rejected: it is silent about the 100x8 above, which is the single most
   common way a household lifter actually improves.
2. **Estimated 1RM (Epley).** Catches rep improvements in one number, and the machinery already
   exists from US-16. Rejected by the maintainer: the celebrated number was never performed,
   and a banner saying "new PR: 127 kg" to someone who has never touched 127 kg is the
   overclaim above, whatever the label next to it says.
3. **Heaviest set plus best session volume**, as two separately-labelled records. Both real,
   no estimate. Rejected: it captures "I did more work" but still not "I did the same weight
   for more reps", which was the original complaint.
4. **Rep-max records — chosen.** One record per (exercise, rep count).

## Decision

**A personal record is the heaviest load ever lifted for a given exercise at a given rep
count.** Bench at 5 reps and bench at 8 reps keep separate records. Every record is therefore a
set that actually happened, and a 100x8 sets an 8-rep record without having to beat a 105x1.

**The first time a rep count is performed is not a record.** A record requires a previous value
at the same (exercise, reps) to beat. Without this, every first set of every new exercise fires
a celebration — noise on day one, and the fastest way to teach the household to ignore the
banner. "You have not done this before" is a fact, not an achievement.

**Bodyweight sets set no records.** They have no load to compare, and treating a missing weight
as zero would make every bodyweight set tie for last place forever. This is the same rule
`WeeklyVolumeByBodyPart` and `ExerciseTrendOf` already apply.

**Ties are not records.** Equalling a record is not beating it. Strictly greater, so repeating
the same working weight every week does not fire a banner every week.

## Consequences

- Every number the app ever calls a record is a lift that was performed, so no PR banner needs
  a disclaimer. Contrast US-16's Epley figure, which carries one everywhere it appears.
- **The record space is larger than one row per exercise** — it is one row per exercise per rep
  count. For a household that works in 5s and 8s this is small; for someone doing AMRAP sets at
  arbitrary reps it is sparser and each record is individually less meaningful. Accepted: the
  sparseness is honest, where collapsing rep counts would not be.
- **The PR list per exercise now has an obvious shape:** rep count, load, date, ordered by rep
  count. That is the second half of US-18 and it falls out of this decision rather than needing
  another.
- Detection compares one saved set against that exercise's history at the same rep count, so it
  is a lookup rather than a recomputation of anything. **This matters because detection sits on
  the save path, and constitution §2.1 makes the two-tap loop sacred** — the detection must not
  be allowed to delay the set being committed or the sheet closing. How that is wired is a UI
  decision and is deliberately left to the story's second PR.
- RPE is not part of a record. It is in the domain and not in the UI (M1), and a record that
  depended on it could not be computed for most of the existing history.
- **Revisit if** the household starts training at genuinely arbitrary rep counts, at which
  point rep-*banding* (1-3, 4-6, 7-10, 11+) becomes worth its complexity. Not now: banding
  invents boundaries nobody measured, and this ADR would rather ship the sparse honest version
  first and see whether it actually bites.
