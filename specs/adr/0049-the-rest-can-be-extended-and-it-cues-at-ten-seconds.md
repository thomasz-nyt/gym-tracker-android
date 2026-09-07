# ADR-0049: The rest can be extended by thirty seconds, and it cues at ten seconds and at zero

- **Status:** accepted
- **Date:** 2026-09-05
- **Deciders:** maintainer (decided both, 2026-09-04), agent (scoped)
- **Amends:** ADR-0010 (a second exact alarm per rest), US-05 (two amendments — see the story)
- **Relates to:** ADR-0016, ADR-0023, ADR-0029, ADR-0036, ADR-0047 (each deferred `+30s` and the cue),
  ADR-0048 (the notification this adds a third action to)

## Context

Five ADRs in a row drew the rest band without two controls the design bundle had on it — `+30s`
and a `CUE AT 0:10 & 0:00` label — each saying the same thing: both amend US-05, so both are the
maintainer's call, and a control that renders but does nothing is worse than one that is absent.
`specs/roadmap.md` carried them under "needs the maintainer's call" from ADR-0016 (2026-08-02)
until the UI/UX and trainer review put them to the maintainer again on 2026-09-04, who decided:
**`+30s`, yes; the cue, yes — a haptic pulse always, with a tone behind a Settings toggle.**

The trainer's reasoning for both is the same one ADR-0023 gave for the rest panel: the rest is when
the phone is actually in the hand — or, more often, on the floor beside the bench or in a pocket
with earbuds in. A heavy set sometimes earns fifteen more seconds, and the only ways to get them
today are to let the timer run out and count in your head, or to skip it and lose the count
entirely. And a countdown that only signals by turning its numeral red at 0:10 (ADR-0036) is a
signal for a phone you are looking at, which during a rest you mostly are not.

What constrains the shape: ADR-0010's mechanism — the rest is a stored end time, remaining time
is a question you ask the clock, and an exact alarm at that end time is only ever a notification
trigger, never the timer; ADR-0048's notification, which carries `LOG SET` and `SKIP REST` and
has one action slot free (US-56 named it and declined to fill it without a decision); and
constitution §2.3 — nothing here may become mandatory or gate logging.

## Options considered

1. **Cue from the in-app tick only.** `RestController.reading()` already ticks each second; a
   pulse when `remaining` crosses ten seconds costs nothing. Rejected as the whole answer: it
   fires only with the screen on and the app in front, which is the one situation where the red
   numeral already works. The pocket is the case that needs the cue.
2. **A second exact alarm at `endsAt − 10 s`, a receiver that pulses — chosen.** The same
   mechanism ADR-0010 already runs for the end of the rest, once more, ten seconds earlier. The
   zero-second cue rides the existing `RestOverReceiver`. Works with the screen off, in the
   pocket, and needs no service.
3. **A foreground service ticking the rest.** Rejected for the reasons ADR-0048 already gave when
   it kept the notification without one: a service is a new permission surface and a persistent
   notification of its own, for a problem two alarms solve.

For the extension: `+30s` is a fixed step, not an adjustable duration. US-05's rest default is
already adjustable in Settings (US-42); what the band needs is one tap that buys a little more
without leaving the countdown, and thirty seconds is the design bundle's own figure. A `−30s` is
not added — `SKIP REST` already ends a rest, and the case for shortening one *slightly* did not
come up in real use.

## Decision

**`RestTimer.extend(by)`** moves the running rest's end time *and* its pinned total by [by],
atomically, through the same `RestTimerStore.setRest` that `start()` uses. Moving both is what
keeps the progress bar honest: a rest started at 1:00 and extended reads "0:45 of 1:30", not a
bar that jumps past full. With no rest running it does nothing — there is nothing to extend, and
inventing a rest from a stray tap would be the app starting a rest nobody earned. Thirty seconds
is `RestController`'s constant; the domain takes a duration.

**`+30S`** joins `SKIP REST` and `Add set` on the rest band's secondary row (ADR-0047's
`label.caps` row, now three), and becomes the resting notification's third action
(`ACTION_EXTEND_REST`, handled by `RestActionReceiver` exactly as `SKIP REST` is: one write to the
stored end time, after which `RestNotificationCoordinator` re-posts the countdown and reschedules
the alarms on its own — no call site learns anything new).

**The cue.** `RestCueSchedule.cueAt(endsAt) = endsAt − 10 s` is the one pure rule.
`RestNotificationCoordinator` schedules a second alarm at that instant whenever it is still ahead
of the clock (a rest shorter than ten seconds, or one already inside its last ten, gets no
pre-cue), and cancels it exactly when it cancels the first. `RestCueReceiver` fires the pulse at
ten; `RestOverReceiver` fires it at zero, before posting "Rest over". The pulse itself is
`RestCue`: a short haptic pattern through the platform `Vibrator` — always — and, when the
member has turned the tone on, a short `ToneGenerator` beep on the notification stream, skipped
whenever the ringer is not in normal mode. A cue is not a notification, so it is not gated on
`POST_NOTIFICATIONS`; the alarm that carries it is scheduled regardless of that permission, unlike
the rest-over alarm, whose only job is a notification.

**Settings** gains one toggle, `Sound a tone with the rest cue`, default **off** — the haptic is
the maintainer's "always", the tone is the opt-in. Device-local (ADR-0005), neither synced nor
backed up, the same class of preference as US-59's screen hold.

**One new manifest entry:** `android.permission.VIBRATE`, a normal permission with no runtime
prompt. `USE_EXACT_ALARM` already covers the second alarm on the same terms ADR-0010 recorded.

## Consequences

- Two exact alarms per rest instead of one. Both are still only triggers: a missed cue costs a
  buzz, never the timer, which stays the stored end time.
- The rest band's secondary row carries three controls. At 320dp the three `label.caps` labels
  fit on one line; `TurnFourLayoutTest`'s rest-panel assertion is the tripwire if that stops
  being true.
- The tone is off by default, so a member who never opens Settings gets exactly the haptic the
  maintainer asked for and nothing they did not.
- ADR-0010's "USE_EXACT_ALARM … if this app is ever published, this is the line that has to
  change" now applies to two alarms. Same line, same cost, recorded here so it is not a surprise.
- `specs/roadmap.md`'s two standing "needs the maintainer's call" items are closed by this ADR
  and move out of that list.
- **Revisit if** real use finds thirty seconds the wrong step (make it the rest default's own
  step, 5 s? or two taps of 15?) — a constant change, not a redesign — or if the haptic proves too
  easy to miss through a pocket, at which point the tone's default is the thing to reconsider,
  not the mechanism.
