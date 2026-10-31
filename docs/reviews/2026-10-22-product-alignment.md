# REP product alignment interview

Date: 2026-10-22. Companion: [detailed review](2026-10-22-product-ux-review.md).

Status: **Q1–Q28 answered; no open preference questions in the scoped review.** Final consolidated shared-understanding checkpoint remains pending. The [implementation handoff](2026-10-22-implementation-handoff.md) captures the agreed behavior and verification outcomes. No production implementation has been started.

## Confirmed direction — Round 1

- **Primary audience: beginners.** The user chose beginners over the recommendation to serve both equally. Explanation, exercise identification, setup recall, and clear next actions should drive design. Fast logging and optional features remain constitutional requirements; beginner priority does not approve mandatory instruction on every set.
- **Flexible gym workflow: yes.** A member can move to another movement while keeping the original plan and rest timer intact. Q11 subsequently settled one timer, restarting for a newly logged set.
- **Personal ownership: one person per phone/account.** Shared-phone member switching is not a requirement for this review. This does not make sign-in mandatory or overturn the existing optional-account contract. Restore onto a replacement phone still needs a coherent ownership boundary.
- **Glossary:** Member now names the household person, distinct from their phone or optional account, in [CONTEXT.md](../../CONTEXT.md).

## Confirmed direction — Round 2

- **Default experience:** routine guidance, showing the current movement and next action, with freestyle available. Focused presentation uses the same plan and records; no separate three-by-twelve walkthrough defaults.
- **Normal logging:** one performed set. Bulk entry, if retained, is explicit and count-labelled; the routine's planned set count is not the number saved by a normal Log action.
- **Routine authorship:** an experienced household member/trainer prepares the initial reusable routine; the beginner can adjust today's workout. Q13 subsequently selected setup directly on the beginner's phone; remote transfer is deferred.
- **Duration:** total Start-to-Finish visit time, including timed warm-up and ordinary rests. This explicitly changes the older warm-up-excluded documentation contract; current duration calculation already matches the accepted direction. Q17/Q18 subsequently settled accidental reopening and stale-workout handling.
- **Restore:** restore that person's complete history on their current/replacement phone; merging files is deferred. File identity, replacement scope, preview, and refreshed screens must be clear. Q19 subsequently approved one validated local pre-restore safety copy.
- **Glossary:** Routine, Workout, Movement, Target, Logged Set, Planned Workout, and Focused Exercise View recorded in [CONTEXT.md](../../CONTEXT.md); Rounds 3–4 subsequently settled completion, rest, early end, and skipped outcomes.

## Confirmed direction — Round 3

- **Prefill:** last actual performance, clearly identified; target separately labelled and used when history is absent. Never infer a load. Existing history-first precedence is retained.
- **Completion:** the planned number of actual sets, not exact achievement of every rep/load target. For a three-set target, three actual recorded sets meet the planned set count. Q15 subsequently approved explicit early end/skip as distinct outcomes.
- **Rest:** one timer. Q11's proposed interaction is preserved on movement selection, restarted using the newly logged movement's rest after logging a new set; no simultaneous timers. Supersets remain a separate existing design.
- **Recovery:** Undo for set deletion and routine-item removal; reopening an accidentally finished workout. Q17 subsequently restricted this to the latest workout within ten minutes, with no intervening new workout, and preserves the original end time if no new set is recorded.
- **Routine setup:** directly on the beginner's phone initially. Remote assignment and app-provided training templates are not first-version requirements.
- **Glossary:** Movement Completion and Rest added to [CONTEXT.md](../../CONTEXT.md), with early termination kept distinct from meeting the plan.

## Confirmed direction — Round 4

- **Advance explicitly:** after meeting the planned set count, offer Next movement as the primary action; preserve rest and keep Extra set secondary. Never automatically finish the workout.
- **Incomplete work:** allow Ended early and Skipped, separately labelled from Plan met. No invented sets, no automatic routine changes, and no requirement to meet every target before finishing the workout.
- **Progress:** recent actual weights/reps/sets first, with a trained-exercise selector. Estimates/PRs secondary; bodyweight progress includes recorded sets/reps without invented external-load volume.
- **Undo finish:** latest workout, within ten minutes, provided no new workout has started. Older workouts remain editable in history. Reopen then finish without another set preserves the original end time; recording new sets resumes the visit under the total-duration definition.
- **Stale active workouts:** keep the four-hour inactivity threshold. Offer Continue with full elapsed duration explained, or confirmed Finish at last logged set. If no sets exist, offer discard. No silent end-time inference or loss of records.
- **Restore safety:** retain one complete, validated local safety copy before replacement; refuse to proceed if it cannot be saved. It supplements an external backup and is not a reinstall/device-loss guarantee.
- **Glossary:** Ended Early and Skipped added to [CONTEXT.md](../../CONTEXT.md).

## Confirmed direction — Round 5

- **First use:** an obvious Create first routine path, with exercise inspection and optional targets; freestyle and backup restoration remain available. Contextual explanations, no mandatory tour/account.
- **Settings while training:** available without ending the workout or losing timer/selection. Permission and band recovery should have explicit actions. Destructive restore remains blocked while active.
- **Charts:** actual date/time spacing, units, inspectable values and linked workouts; same-day workouts remain distinguishable; recorded values accessible as a list.
- **Weekly reporting:** Monday-based calendar weeks, full oldest displayed week, current week labelled partial. No workouts and workouts lacking computable external-load volume are different states.
- **Untargeted movement completion:** explicit Done after performed work; no invented three-set target. An unperformed movement can be Skipped.
- **Delivery priority:** records/recovery → coherent workout/rest flow → trustworthy Progress → routine/first-use refinement. Accessibility throughout. Larger separately scoped commitments stay in the backlog. Agreement on priority does not itself request production implementation.

## Additional requirement from the user — save routine changes

After agreeing Q20–Q25, the user added: when a member adds/removes exercises during a workout started from a saved routine, ask whether to save the changed routine for future use.

**Accepted requirement:** offer an explicit opportunity to retain exercise-list changes. **Preserved boundary:** ordinary workout editing still does not automatically mutate the saved routine or past workouts. Q26–Q28 settled the interaction: ask once after saving the finished workout; offer Update original / Save as new / Today only; reuse only net exercise-list/order changes, never actual performed values or skip/early-end status. This intentionally extends the original one-way-copy contract.

## Confirmed direction — Round 6

- **Timing:** one offer at finish, after the workout itself is safely saved, only for net exercise-list changes. Undone edits do not trigger it; dismissal preserves the routine.
- **Destination:** Update original, Save as new, or Today only, with a change preview and an explicit selection before modifying a template. Missing original falls back to Save as new.
- **Scope:** add/remove/replace and deliberate reordering. Ordinary selection, Skipped/Ended early status, actual reps/load, and rest elapsed are not future-template edits. Preserve unchanged occurrences' authored targets; author new targets explicitly.
- **Boundary:** routine-save cancellation/failure does not remove the saved workout or partially overwrite the original routine. Past workout snapshots remain unchanged. [ADR-0051](../../specs/adr/0051-explicit-routine-updates-from-a-workout.md) records the accepted explicit-save exception.
- **Glossary:** Routine Update added to [CONTEXT.md](../../CONTEXT.md).

## Existing ground to preserve

These are current repository commitments, not newly agreed interview answers:

- One household; indoor equipment-based strength training; fully useful offline; logging stays fast; accounts, health, and coaching remain optional.
- Targets describe intention and actual logged sets describe performance. Never invent loads or claim a target was performed.
- The current monochrome/red identity is deliberate. Improve hierarchy, labels, reachability, and consistency before assuming a visual rebrand is needed.
- September 4 already approved warm-up/drop/failure set types and persistent per-exercise setup notes, with no per-session note. Custom household exercises remain deferred.
- Rest extension and cues are already shipped. Session-only machine substitution and paired supersets already have a design direction but still need stories.
- Source: [constitution](../../specs/constitution.md), [roadmap, prior review decisions](../../specs/roadmap.md#L1384), [roadmap, designed workflows](../../specs/roadmap.md#L1582).

## Round 1 — answered

❓ **Q1 — Primary user:** Who should drive REP's design tradeoffs: experienced household members who know their exercises, beginners needing setup/workout guidance, or both equally?

➡️ **Recommendation:** serve both, with fast logging as the default and guidance available on demand. A beginner should receive more explanation without a different definition of a set or target.

Answer: **Beginners.** The initial recommendation above was not selected.

---

❓ **Q2 — Real gym workflow:** When a machine is occupied, should a planned workout allow another movement while preserving the original plan and the rest timer?

➡️ **Recommendation:** yes. The existing session-only swap direction already supports flexibility; this question checks real usage and the timer/selection relationship rather than asking for renewed approval of swaps.

Answer: **Yes.** Preserve the plan and rest timer when switching movements. Prior related commitment: session-only substitution is designed, not built.

---

❓ **Q3 — Data ownership:** Does each household member use their own phone, or must several people take turns on one phone?

➡️ **Recommendation:** prioritize each person's own phone unless shared-phone use is a real requirement. Existing account-adoption rules still apply; sharing a household does not by itself mean sharing one person's logging identity.

Answer: **One person per phone/account.** No shared-phone switching requirement; optional sign-in remains unchanged.

## Round 2 — answered

❓ **Q4 — Default workout experience:** Should a beginner normally enter a routine-led view showing the current movement, its target, and the next action, with freestyle still available, or choose manually from an open workout list?

➡️ **Recommendation:** routine-led by default, with a focused view of the same plan and logged sets. Avoid a separately configured walkthrough that resets the routine to different targets. First-use behavior when no routine exists depends on Q6.

Answer: **Agreed.** Routine guidance by default; freestyle available; focused view shares the same plan and records.

---

❓ **Q5 — Logging one set:** Should the normal Log action always record one performed set, with bulk logging behind an explicit separate action, or should entering several identical sets stay the default for a targeted movement?

➡️ **Recommendation:** one performed set by default. If bulk entry is kept, label its count explicitly. Example: a three-set plan should not make a load adjustment record three sets at once.

Answer: **Agreed.** One performed set by default; bulk entry explicit.

---

❓ **Q6 — Who prepares the plan:** Who normally chooses exercises and sets/reps/load/rest targets for the beginner: an experienced household member or trainer, the beginner, or the app using starter templates?

➡️ **Recommendation:** an experienced household member/trainer prepares a reusable routine initially; the beginner can inspect and adjust today's workout. This does not assume household-admin roles or remote routine assignment are required. If app-supplied templates are desired, their content and suitability need a separate discussion.

Answer: **Agreed.** An experienced household member/trainer prepares the initial routine; the beginner can adjust today's workout. Remote setup/transfer is not implied.

---

❓ **Q7 — Meaning of duration:** Should workout duration mean the complete visit from Start to Finish, including timed warm-up and ordinary rests, or lifting time excluding the separate timed warm-up?

➡️ **Recommendation:** complete visit duration, clearly labelled, with ordinary rests included. That matches current behavior but would explicitly revise the older promise that warm-up contributes nothing to duration. This is a proposal, not a settled documentation change.

Answer: **Yes.** Total Start-to-Finish duration, including warm-up and ordinary rests. The prior recommendation is accepted; the older excluded-warm-up promise is superseded as product direction, pending scoped reconciliation of historical specs.

---

❓ **Q8 — Restore scope:** Should backup import restore one person's complete history onto their current/replacement phone, or also support combining records from different files?

➡️ **Recommendation:** complete restore for that person; keep merging out of scope for now. Import should clearly identify the file and what it replaces. A pre-restore safety snapshot remains a separate recovery decision.

Answer: **Agreed.** Complete restore for the person; merging deferred. Safety snapshot remains undecided.

## Round 3 — answered

❓ **Q9 — Prefill when history differs from the target:** The plan says twelve reps, but the last performed set was eight. Should editable logging values begin at the last actual performance, or the planned target?

➡️ **Recommendation:** last actual performance, clearly labelled, with the target shown separately; use the target only if no history exists. Never infer a load. This retains ADR-0031 and the constitution's previous-values prefill requirement. A target-first choice would require explicitly addressing that existing contract before implementation.

Answer: **Agreed.** Last actual performance first, target separate and fallback only when history is absent.

---

❓ **Q10 — Movement completion:** With three planned sets, what counts as complete: recording three actual sets even if reps differ, explicitly marking the movement done, or hitting every target exactly?

➡️ **Recommendation:** three recorded sets satisfy the planned set count; fewer reps remain honest actual values and do not force another attempt. Allow an explicit Finish movement early or Skip, shown distinctly from meeting the plan. Extra sets remain optional. The exact next-movement transition comes after this definition is settled.

Answer: **“3 sets.”** Completion follows the planned set count even if actual reps differ. Early finish/skip is a separate pending choice in Q15.

---

❓ **Q11 — Timer after switching and logging:** Selecting movement B preserves movement A's countdown. If the member then logs a set of B before it ends, should the app replace the countdown with B's rest, keep A's rest, or track two timers?

➡️ **Recommendation:** one visible rest timer; preserve it during selection/inspection, then start B's rest when B's set is recorded. Do not introduce simultaneous timers. Superset pairs remain their separately designed workflow.

Answer: **“One timer.”** Record one countdown, with selection preserving it and a new logged set starting the corresponding movement's rest as recommended. No multi-timer feature.

---

❓ **Q12 — Recoverable mistakes:** Should the app offer Undo for set deletion/routine-item removal and a way to reopen an accidentally finished workout, or keep finish final?

➡️ **Recommendation:** Undo deletions/removals and allow the latest finished workout to reopen if no new workout is running. Reopening duration semantics will be resolved after this capability decision. Avoid extra confirmation on every logged set.

Answer: **Yes.** Undo deletions/removals and reopen accidental finish. Reopen age/duration boundary remains pending in Q17.

---

❓ **Q13 — Where routine preparation happens:** Does the household member/trainer prepare the beginner's routine directly on the beginner's phone, or must they send/assign it from their own phone?

➡️ **Recommendation:** direct setup on the beginner's phone for the first version. Remote assignment is a distinct household workflow, not established by agreeing who authors the routine; if it is needed, specify it explicitly before rollout planning.

Answer: **Agreed.** Initial routine preparation on the beginner's phone; remote assignment deferred.

## Round 4 — answered

❓ **Q14 — Advancing through the plan:** When the planned set count is reached, should REP automatically open the next movement, or offer a clear Next movement action?

➡️ **Recommendation:** offer Next movement as the primary action while preserving rest. Extra set stays available as a secondary action. Do not automatically finish the workout. Selection follows the member's choice and the remaining plan.

Answer: **Agreed.** Explicit Next movement primary; rest preserved; Extra set secondary; no automatic workout finish.

---

❓ **Q15 — Fewer sets than planned:** If the plan calls for three sets but the member only records two, may they explicitly Finish movement early or Skip the remaining work?

➡️ **Recommendation:** yes. Show “Ended early · 2 of 3 sets” separately from “Plan met · 3 of 3.” A zero-set movement can be skipped; neither outcome fabricates sets or silently changes the routine. Whole-workout finish remains possible with outstanding work.

Answer: **Yes.** Explicit early end/skip, with actual set counts and distinct outcomes.

---

❓ **Q16 — Progress landing screen:** Should beginners primarily see their recent actual weights/reps/sets for selectable trained exercises, or estimated 1RM and PRs?

➡️ **Recommendation:** actual recent performance first, with an easy trained-exercise selector. Keep estimates/PRs secondary and labelled. Bodyweight movements should still show recorded sets/reps, without estimated external-load volume. Chart/range details follow after this information priority is settled.

Answer: **Agreed.** Actual performance and trained-exercise selection first; estimates secondary; bodyweight sets/reps retained.

---

❓ **Q17 — Reopening boundary:** Is reopening for correcting a recent accidental Finish, or should it also resume a workout many hours later?

➡️ **Recommendation:** Undo finish for the latest workout within ten minutes, provided no new workout has started. Older workouts remain editable through history; continuing training starts a new workout. If reopened and finished again without recording a new set, retain the original end time. This is a proposed boundary, not a claim that a time limit was already approved in Q12.

Answer: **OK.** Latest workout within ten minutes and no new workout; no-new-set reopen preserves original end time; older history stays editable.

---

❓ **Q18 — Forgotten active workout:** After more than four hours without a set, should the app offer Continue as well as finishing at the last recorded set?

➡️ **Recommendation:** yes, with the elapsed time visible. Continue preserves the existing visit and therefore its full elapsed duration; “Finish at last logged set” requires confirmation and labels that exact endpoint. If no sets exist, offer discard. Do not silently invent a finish time or discard records. The current four-hour threshold is retained unless the user wants to change it.

Answer: **OK.** Continue or confirm Finish at last logged set after four hours of inactivity; elapsed-time consequence explained; discard offered when no sets exist.

---

❓ **Q19 — Restore safety:** Before replacing a person's history from a backup, should REP automatically keep one local safety copy of their current data?

➡️ **Recommendation:** yes, complete and validated before replacement. If that copy cannot be saved, stop and explain rather than proceeding silently. Keep one rolling pre-restore copy, and explain that a copy on the same phone does not replace an external backup. Location/lifecycle follow once the capability is accepted.

Answer: **Yes.** One validated local pre-restore safety copy; failure blocks replacement with an explanation.

## Round 5 — answered

❓ **Q20 — First use without a routine:** Should the empty home screen lead into creating the initial routine, with freestyle still immediately available, or leave setup hidden in Routines?

➡️ **Recommendation:** a short Create first routine path: name → inspect/add exercises → optional targets. An experienced person can do it on the beginner's phone. Keep Try freestyle and restoration available; explain unfamiliar terms in context instead of requiring a tour or account.

Answer: **Yes.** Create first routine on empty home; freestyle/restoration available; contextual guidance.

---

❓ **Q21 — Settings during training:** Should Settings remain accessible during a workout for rest defaults, screen behavior, permissions, and band recovery?

➡️ **Recommendation:** yes, through a quiet entry that preserves the workout/timer/selection. Provide direct permission recovery and Change/Search again/Forget band actions. Destructive restore stays unavailable while a workout runs, consistent with the existing protection.

Answer: **Yes.** Settings and integration recovery during training, preserving the active context; restore blocked while active.

---

❓ **Q22 — Trend meaning:** Should horizontal distance represent actual dates/time, or simply successive workouts regardless of their spacing?

➡️ **Recommendation:** actual dates/time, with readable units and point inspection showing the performed values and linked workout. Keep individual same-day workouts distinguishable rather than silently merging records. Recorded values remain available in an accessible list.

Answer: **OK.** Actual-time spacing, readable/inspectable values, linked workouts, separate same-day records.

---

❓ **Q23 — Weekly reporting:** Should “eight weeks” mean eight Monday-based calendar weeks, including the current partial week, or a rolling 56-day period?

➡️ **Recommendation:** eight calendar weeks, labelled by their dates, with the current week explicitly marked partial. Include the full oldest displayed week. No recorded workouts and recorded workouts without computable load volume have different messages.

Answer: **OK.** Monday-based calendar weeks; current partial week labelled; oldest week fully included.

---

❓ **Q24 — No planned set count:** If a movement has no set target, should the member explicitly mark it Done, or should REP assume a default count?

➡️ **Recommendation:** explicit Done; do not invent a three-set target. The member can keep logging until they choose to move on. Zero-set Skip remains distinct from performed work.

Answer: **OK.** Explicit Done without a set-count target; no assumed count; unperformed Skip distinct.

---

❓ **Q25 — Order of work:** Should the first implementation pass stabilize records/restore and the core workout flow, followed by Progress and setup, while keeping larger separate features in their existing backlog?

➡️ **Recommendation:** yes: record protection/recovery → coherent workout/rest/plan flow → trustworthy useful Progress → routine/first-use refinement. Accessibility, clear labels, and reachable controls are requirements in every touched screen. Keep sync/accounts, supersets, exact-machine guides, and already-approved set-type/setup-note work explicitly tracked as separate scoped stories; do not silently add them to a bug-fix release or discard them. This priority proposal does not bypass repository milestone/spec/test rules or authorize code changes.

Answer: **OK**, plus a new requirement: offer to save a routine for future use after adding/removing its exercises during a workout. Details follow in Q26–Q28.

## Round 6 — answered

❓ **Q26 — Prompt timing:** Should REP ask after every exercise edit, when the member finishes the workout, or only through a manual Save routine action?

➡️ **Recommendation:** once at workout finish, after the workout itself is safely saved. Show a compact summary of net exercise-list changes; changes that were undone should not trigger it. Closing the prompt keeps today's workout saved and leaves the routine unchanged. No extra interruption between sets.

Answer: **OK.** Once at finish, after workout persistence, for net changes only; no mid-set prompt.

---

❓ **Q27 — Save destination:** Should the member update the original routine, create a new routine, or have both choices?

➡️ **Recommendation:** three clear options: Update original, Save as new, and Today only. Require an explicit choice before changing the template; default dismissal is Today only. Preview what changes. If the original no longer exists, offer Save as new rather than guessing a different routine.

Answer: **Agreed.** Update original / Save as new / Today only; dismissal preserves original; preview changes.

---

❓ **Q28 — What gets saved:** Should the offer apply to exercise-list changes only, or also turn actual weights/reps into new routine targets?

➡️ **Recommendation:** additions/removals/replacements and deliberate reordering only. Skipped/Ended early status and ordinary exercise selection do not remove/reorder future movements; actual logged weight/reps never become targets automatically. Preserve existing targets for unchanged movement occurrences; authoring new targets remains explicit.

Answer: **Agreed.** Exercise membership/order changes only; preserve surviving targets; no automatic target updates from actuals or removal based on skipped work.

## Applied design defaults from the agreed direction

These are concrete implementation proposals that follow the accepted direction, not claims of shipped behavior:

- Keep the current monochrome/red visual identity. The primary Log/Next action remains reachable while content scrolls; rest accompanies inspection/correction rather than replacing the plan.
- Retain full prior performance through expansion or a readable set list, explain RPE/bodyweight/load fields on demand, and associate accessible labels with editable values.
- Use adaptive layouts and verified 48dp-or-larger touch targets, long-name handling, narrow screens, 130%/200% text, light/dark, and TalkBack checks. These carry forward existing usability/accessibility commitments rather than reopening color preferences.
- Store the single safety copy in app-private local storage, with visible export/recovery controls. Explain that uninstalling/clearing app data removes it. Never overwrite the valid safety copy with a broken import; preserve it during a recovery attempt. Verify its lifecycle and account-deletion behavior in the restore story.
- Reproduce source-based crash/race/layout findings in an isolated current build before calling them device-verified; tests and delivery evidence remain distinct.

## Design tree and next frontier

**Frontier: empty for this review's agreed scope.** Q1–Q28 are settled. Verification, story refinement, and separately deferred features are implementation/backlog work, not reasons for additional preference rounds. The [handoff](2026-10-22-implementation-handoff.md) is ready for the final shared-understanding checkpoint.

| Branch | Prerequisites | Decisions to resolve | Recommended starting point |
|---|---|---|---|
| Logging semantics | Q4/Q5/Q9 settled | Remaining presentation and validation details | One performed set; history prefill; target separate; same plan in focused view |
| Completion/progression | Q10/Q14/Q15/Q24 settled | Verification and copy | Plan met vs ended early/skipped; explicit Done without count target; Next; extras |
| Rest and interruption | Q2/Q11/Q18 settled | Verification and wording | Preserve on selection, restart after a new set; one timer; explicit stale-session choices |
| Timing/finish | Q7/Q12/Q17/Q18 settled | Story/test details for reopening timestamps | Total visit; ten-minute latest-workout Undo finish; preserved end if no new sets |
| Routine preparation | Q6/Q13/Q20/Q26–Q28 settled | Verification, diff/snapshot implementation and story refinement | Initial setup on beginner's phone; explicit post-workout save for net list changes |
| Progress | Q16/Q22/Q23 settled | Verification and accessible rendering | Actual performance first; time-based charts; calendar weeks; honest bodyweight data |
| Ownership/restore | Q3/Q8/Q19 settled | Replacement and safety-copy lifecycle verification | Complete personal restore plus validated local recovery copy |
| Recovery | Q12/Q17/Q19 settled | Story/test detail and explicit error states | Undo deletions/removals; bounded Undo finish; protected replacement |
| Integrations | Q21 settled | Verification and explicit recovery states | Quiet active-workout entry; recoverable services; restore stays blocked while active |
| Accessibility/design | Existing constraints and agreed beginner audience | Current-build visual/TalkBack verification | Reachable primary action, meaningful labels, adaptive text/layout |
| Delivery priority | Q25–Q28 settled | Scoped implementation and review | Protect records → reconcile workout flow → improve progress → refine setup/reuse |

## Glossary status

The existing Machine Guide, Machine Variant, and Form Guide terms remain unchanged. Member was added following Q3; Round 2 settled Routine, Workout, Movement, Target, Logged Set, Planned Workout, and Focused Exercise View. Round 3 settled planned-set-count Movement Completion and one-countdown Rest; Round 4 settled Ended Early and Skipped; Q24 adds explicit Done without a planned count. These are recorded in [CONTEXT.md](../../CONTEXT.md). The new routine-save workflow preserves the distinction between today's Workout and the reusable Routine. Keep CONTEXT.md a glossary; review findings stay in the review document.

## Decision record policy

The routine-save requirement warrants [accepted ADR-0051](../../specs/adr/0051-explicit-routine-updates-from-a-workout.md), following the user's Q26–Q28 answers: it adds an explicit write-back boundary to a model whose existing ADRs structurally forbid it, and a mistaken overwrite can destroy a reusable plan. Acceptance records this design decision; it does not start production implementation. Routine copy/layout refinements remain in review/stories. Changes to the constitution remain proposals to the maintainer.

## Agreement checkpoint

Before implementation, summarize accepted definitions, workflow decisions, issue priorities, deliberately deferred items, and measurable outcomes; ask the user to confirm that shared understanding. This checkpoint comes from the invoked grilling skill (`/home/thomas/.agents/skills/grilling/SKILL.md`), which says: “Do not act on it until the user confirms you have reached a shared understanding.” It does not prevent the requested review, fact finding, interview documentation, or publishing those documents in a commit and PR.

Checkpoint status: **awaiting confirmation of the consolidated handoff**, not answers to further preference questions. The original task is review and alignment; implementation is a separate next step.
