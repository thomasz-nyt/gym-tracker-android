# ADR-0017: Sensor-assisted rep counting

- **Status:** deferred
- **Date:** 2026-08-02
- **Deciders:** maintainer (requested), agent (scoped)

## Context

The maintainer's words: *"for each reps, if we could use the phone/watch sensors to track
progress will be great."* Recorded here rather than acted on, so the reasoning exists the next
time it comes up.

The appeal is real: counting your own sets while tired is the one part of logging that the app
cannot currently help with, and it is the only remaining input that has to be typed.

Four constraints bear on it, and they point the same way.

**Constitution §2.4** — *"Never fabricate, estimate, or interpolate a logged value. If a metric
is unavailable, show it as unavailable."* Accelerometer rep detection is inference. A count
derived from a signal is an estimate no matter how good the model is, and §2.4 does not have a
confidence-threshold clause. This is the binding objection; the rest are practical.

**Constitution §1** puts *"step counting"* on the permanently-out-of-scope list. Rep counting
is not step counting, but it is the same class of thing — motion classification producing a
count — and §1's rationale (*"Every added concept taxes [the core loop]"*) applies with equal
force.

**`tech-stack.md` approves nothing that could do this.** There is no `SensorManager` usage
anywhere, no accelerometer or gyroscope permission, and Wear OS is absent from the stack
entirely. The only watch in any spec is the **M8** watchOS companion, described there as *"the
only way to get continuous heart rate during lifting, and it is the main reason iOS is native
rather than shared-UI."* There is no Android watch story at all. Per constitution §7, any of
this needs an ADR and a dependency that does not yet exist.

**`health-connect.md` sets the house pattern for device data**, and it is the opposite of this:
*"Fitbit band data reaches us the same way Apple Watch data will on iOS: the vendor's own app
writes to the platform health store, and we read from the store. **We never talk to a device or
a vendor cloud.**"* Reading raw phone sensors directly would be the first place the app talks
to hardware itself.

## Options considered

1. **Defer with the reasoning written down.** No code, no dependency, no story. The idea
   survives in a form that can be picked up deliberately.
2. **Suggest-only: sensors propose a rep count into the set-entry field, which the member must
   confirm before anything is written.** Arguably inside §2.4, since nothing unconfirmed is
   logged — the same carve-out §6 uses for AI output ("never silently writes to the log").
   Still needs a §2.4 amendment to be safe rather than clever, plus a new dependency, a
   permission, and a story. Not rejected on merit; deferred as premature.
3. **Auto-count and log.** Rejected. It writes an inferred number into a logged value with no
   confirmation, which §2.4 forbids in as many words.
4. **Drop the idea.** Rejected: option 2 is genuinely interesting, and a flat no would lose
   that.

## Decision

Option 1. **Not before M5**, and not without the maintainer amending constitution §2.4 or
adding an explicit "suggested, never logged" carve-out to it.

Nothing is built now. No dependency is added, no permission is declared, no story is written
into `user-stories.md`, and no roadmap box appears.

## Consequences

- The remaining five ideas from the same gym session (US-02a, US-02b, US-02c, US-05a, US-06b)
  are unblocked and unaffected — none of them depends on this.
- **M5 is the earliest sensible milestone**, because that is when the app first has any
  device-signal plumbing at all (`HealthMetricsSource`, the optional-feature binding, the
  no-op default, the per-member toggle). Building sensor input before that scaffolding exists
  means building the scaffolding twice.
- If it is ever built, it inherits the optional-feature contract from `tech-stack.md` without
  negotiation: an interface in `:core:domain`, a no-op default binding, off by default, and
  every screen correct with the no-op — *"If a screen crashes or shows an empty hole when heart
  rate is unavailable, that is a constitutional violation, not a cosmetic bug."*
- Constitution §3's age constraint applies here more sharply than to Health Connect, not less:
  continuous motion capture on a minor's device is a heavier ask than reading a heart-rate
  summary, and "off by default" would not be a sufficient answer on its own.
- **Revisit when** both are true: M5's optional-feature plumbing exists, and the maintainer has
  amended §2.4 to say what an app may do with an inferred value. Until the second one happens,
  a correct implementation is still a constitutional violation, which is why this is deferred
  rather than merely unscheduled.
