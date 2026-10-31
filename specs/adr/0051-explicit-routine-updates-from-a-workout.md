# ADR-0051: Routine changes from a workout require an explicit save

- **Status:** accepted — the user requested the capability and agreed timing, destination, and scope in Q26–Q28. Implementation authorized on 2026-10-29 through ADR-0052; the separate post-save operation is now being verified with domain, Room and physical-device tests.
- **Date:** 2026-10-22
- **Deciders:** maintainer (requested capability and selected the interaction), agent (documented boundary and alternatives)
- **Relates to:** ADR-0020, ADR-0027, ADR-0028; [product review](../../docs/reviews/2026-10-22-product-ux-review.md), [alignment Q26–Q28](../../docs/reviews/2026-10-22-product-alignment.md)

## Context

The user requested that adding/removing exercises during a workout started from a saved routine should lead to an offer to save the changed routine for future use.

Today, starting a routine copies exercises/targets into a workout. Editing the workout never updates the reusable routine. ADR-0028 additionally treats its routine ID as provenance and uses a structural test to forbid a use case combining a routine origin with a routine repository. That protects historical display and logged values, but also forbids the explicit save now requested.

An exercise change because a machine is occupied can be useful only today or useful next week. Applying every such change automatically would corrupt the reusable plan; never offering reuse forces the member to repeat the same edits in the routine editor. This boundary deserves an ADR because persistent template changes can be difficult to reconstruct and future readers otherwise have good reason to reject the deliberate exception to the old contract.

## Options considered

1. **Keep edits session-only with no save offer.** Preserves the old guarantee literally, but does not meet the new request.
2. **Automatically synchronize workout changes into the routine.** Avoids a prompt but turns temporary equipment constraints and omissions into future plans without consent. Not recommended.
3. **A separate explicit routine-save operation — chosen.** Offers reuse with reviewed consent while preserving immutable workout snapshots and normal session-only edits.

## Decision

Retain one-way routine copying and isolate any template update in a separate explicit save operation; ordinary logging, selection, skip/early-end, and workout rendering never write back into a routine.

The user accepted this interaction in Q26–Q28:

- Ask once at finish, after the workout is saved, only when net exercise-list changes remain.
- Preview changes and offer Update original, Save as new, or Today only. Dismissal changes no routine.
- Limit reuse to additions/removals/replacements and deliberate reordering. Do not convert actual performed values into targets or remove a future exercise because it was skipped today.
- Keep existing targets attached to the same unchanged movement occurrences. Never inherit a load merely because a different exercise occupies the same position.

## Consequences

- A routine-save use case may deliberately resolve provenance to a current routine **only for the consented template operation**. A future implementation must narrow ADR-0028's structural enforcement around that exception; it must not enable live routine joins for workout rendering or prefill.
- Past workout names, targets, and actual Logged Sets remain snapshots. Updating a routine does not rewrite today's or older workout history.
- Cancelling or failing the routine save must not roll back an already saved workout. Save failure needs an actionable retry and leaves the original routine intact.
- Compare net structural changes rather than prompting merely because the editor was opened. Reversible edits that return to the original list should not trigger the offer.
- Use occurrence identity/order deliberately: the same catalog exercise can appear twice. Exact storage, baseline/diff strategy, schema, sync behavior, and acceptance tests belong to the implementation story after product alignment; none is silently selected here.
- If the source routine is deleted or has changed since the workout began, do not blindly overwrite it. Preserve the workout and offer an appropriate reviewed fallback, such as Save as new.
- A later Undo finish must not imply undoing a separately confirmed routine update. Re-finishing without further exercise-list changes should not repeatedly ask to save the same change. Verify these interactions in the story.
- This product decision added no dependency and changed no constitution rule. Production implementation was separately authorized on 2026-10-29 through ADR-0052.
