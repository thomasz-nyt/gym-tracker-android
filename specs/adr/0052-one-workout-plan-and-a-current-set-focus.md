# ADR-0052: One workout plan, with Focus on the current set

- **Status:** accepted — implementation explicitly requested by the maintainer on 2026-10-29 after reviewing the revised Focus demo.
- **Date:** 2026-10-29
- **Relates to:** ADR-0017, ADR-0027–0033, ADR-0045–0051; US-62–67.

## Decision

The active workout, copied targets and actual sets are the single source for both Focus and the workout overview. Focus is the default in-session presentation: selected exercise, current set, load/reps, compact rest, and one Log/Next action. History, target details, corrections and completion choices open explicitly; the overview handles selection, additions/removals and deliberate ordering. There is no separately configured guided workout or fabricated default set-count target.

A normal Log or entry confirmation writes one actual set. Repeated submission while a write is in flight is ignored; bulk entry is explicitly selected and count-labelled. Actual performance on the same exercise prefills before targets; a bodyweight record's absent load remains absent. Plan met means the planned count of actual sets exists. Explicit Done without a count, Ended early and Skipped remain distinct and persist across recreation.

One persisted rest end time continues across selection, inspection and settings, and rest restarts only for a newly logged set. Current-exercise Focus and its correction/history surfaces share the existing workout rather than the old GuidedController's independent setup/queue. Start-to-Finish duration includes warm-up and rest.

Workout and routine changes remain separate. Starting from a routine records the authored occurrence identities and a structural baseline. The separate ADR-0051 operation saves only reviewed net membership/order changes after workout persistence, preserves surviving occurrence targets, checks that the original is unchanged, and records which structural change was handled. No live routine join supplies historical names, targets or performed values.

Reopening is limited to the latest finished workout within ten minutes, with no intervening workout. Preserve its original end when re-finished without new actual sets. Restore validates the complete graph, creates and validates a rolling private safety copy before replacement, and refreshes all member-dependent state after success. Neither operation invents records.

## Scope and sequencing

The maintainer requested completion of the agreed review and redesign, including all four handoff batches. This is an explicit exception to the pending M2 sequence for repairs and approved improvements to the existing offline app. Accounts/sync transport, supersets, exact-machine assets, media, AI, set types and setup notes remain separate backlog items. The constitution is unchanged; no dependency is added.

Implementation proceeds in reviewable vertical slices: records/recovery; shared-plan Focus and recovery; actual Progress; preparation/reuse and optional setup. US-62–67 map the accepted acceptance criteria to existing domain, repository, ViewModel and Compose test boundaries. The prototype is retained on its discussion branch and is not production code.
