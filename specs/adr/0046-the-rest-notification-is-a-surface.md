# ADR-0046: The rest notification is a surface, not a buzz

- **Status:** accepted
- **Date:** 2026-08-30
- **Deciders:** maintainer
- **Amends:** ADR-0010 (rest timer as a persisted end time plus an exact alarm)

## Context

ADR-0010 built the rest timer and gave it a notification: one static line, `Rest over`, posted at
zero by a stateless `BroadcastReceiver`. US-54 asks for two things that notification cannot do.

**It cannot be tapped.** `RestOverReceiver` never calls `setContentIntent`, so the notification
only `setAutoCancel`s itself away. This is a plain bug with a one-line fix, and it is not what
this ADR is for.

**It cannot say anything.** The receiver has no Hilt, no repositories, no domain access — by
design, since ADR-0010 wanted it thin. To show the movement, the set number, the weight and the
reps, it needs all four.

And there is a timing problem underneath both. The notification exists only *at zero*, so "how
much rest is left" has no surface to live on. Showing it means posting when the rest **starts**
and keeping it up — which is option 2, the one ADR-0010 rejected.

### What was already broken

`RestController.skip()` calls `restTimer.skip()` but never flips its `started` flag, so the
`LaunchedEffect(restStarted)` that owns the alarm never re-runs and `alarm.cancel()` is never
reached. ADR-0010's decision section says the alarm is "Cancelled on skip or on logging the next
set." The second half works — logging reschedules. The first half has never worked.

Nobody noticed because the symptom is one stray buzz a minute after you moved on. It stops being
ignorable the moment a *visible* notification is involved: a countdown that survives the skip
that was supposed to end it is not a stray buzz, it is a wrong answer left on the lock screen.
Fixing it is therefore part of this change, not a follow-up.

## Options considered

1. **A foreground service holding the rest.** ADR-0010's option 2, and the conventional answer.
   Still rejected, and for its original reason: it adds a service lifecycle to get wrong, needs
   `FOREGROUND_SERVICE` permissions and a `foregroundServiceType`, and — ADR-0010's actual
   argument — it is option 1 plus a service, because the end time still has to be persisted to
   survive process death.
2. **An ongoing notification, no service.** Post when the rest starts, cancel when it ends. A
   notification is not a service: there is nothing to start, nothing to stop, nothing to crash,
   and no new permission. The cost ADR-0010 objected to — "heavy for the core loop" — was about
   a *service*, and re-reading that section, the persistent notification was named as the
   service's symptom rather than as a cost in itself.
3. **Rich content at zero only.** Keeps ADR-0010 untouched and still fixes the tap and the
   emptiness. Rejected: it answers "what is next" but not "how long", and how long is the
   question a member actually has during a rest.
4. **A `RemoteViews` custom layout with a hand-ticked timer.** Rejected outright — see below.

## Decision

Option 2.

- **The countdown costs nothing to run.** `setUsesChronometer(true)`,
  `setChronometerCountDown(true)` and `setWhen(endsAt)` make *Android* render `1:29 → 0:00`.
  There is no ticking, no `delay(1000)`, no wakelock and no work in our process at all — which is
  what makes option 4 pointless and what makes this cheap enough to reconsider ADR-0010's
  rejection. `RestController.reading()`'s one-second tick stays exactly where it is: on screen,
  where something is actually being redrawn.
- **The stored end time is still the only source of truth.** The notification is a *view* of
  `restEndsAt`, not a second copy of the timer. Process death remains a non-event, exactly as
  ADR-0010 has it, and for the same reason.
- **`restEndsAt` becomes the sole trigger.** A process-lifetime `RestNotificationCoordinator`
  collects it: non-null schedules the alarm and posts the notification, null cancels both. Skip,
  log-next-set and end-session all already write that one value, so all three are handled by one
  rule rather than by three call sites remembering to. **This is what fixes the skip bug** — not
  another `cancel()` call in the right place, but removing the possibility of a wrong place. The
  Compose side effect that owned scheduling is retired; what is left in the UI is the one-time
  permission request, which genuinely needs an Activity.
- **Two ids, two channels.** `rest-running` (id 2, `LOW`, silent, ongoing) and the existing
  `rest-timer` (id 1, `HIGH`, auto-cancel). Changing a live notification's channel in place is
  unreliable across versions, and keeping id 1 and its channel untouched preserves whatever
  channel settings a member has already chosen. An ongoing rest that heads-up-popped every 60
  seconds would be worse than the silence it replaces.
- **Actions do not open the app.** `LOG SET` and `SKIP REST` are handled by a receiver that
  writes and re-posts. Nothing starts an Activity, so this does not meet Android 12's
  notification-trampoline restriction. The confirmation a member gets is the notification
  updating in place.
- **The decisions live in `:core:domain`.** `DescribeRestNotification` computes what to say and
  `LogUpNextSet` performs the action, both in pure Kotlin with no Android dependency. ADR-0010
  conceded that "the `BroadcastReceiver` and the notification are untestable glue"; this keeps
  that concession honest by leaving nothing in the glue worth testing. `LogUpNextSet` is shared
  with the screen's one-tap button so the two paths cannot drift apart.
- **The launcher intent, not an explicit one.** `getLaunchIntentForPackage` rather than
  `Intent(context, MainActivity::class.java)`: `:feature:logging` cannot see `MainActivity`
  (`:app` depends on it, not the reverse), and the launcher intent's
  `FLAG_ACTIVITY_RESET_TASK_IF_NEEDED` resumes the existing task instead of stacking a second
  activity — so no `launchMode` change is needed either. No deep link is needed: the start
  destination is `Logging` and `LoggingRoute` derives home-vs-session from Room (ADR-0013), so a
  tap already lands in the running session.

No new permissions. `USE_EXACT_ALARM` and `POST_NOTIFICATIONS` are already declared, so
`tech-stack.md` is unchanged.

### Deliberately not decided

- **`+30s` and an audio cue at 0:10/0:00.** Both are listed in `specs/roadmap.md` under "needs
  the maintainer's call", both were put to the maintainer while this was being planned, and both
  were left open. Recorded here because this ADR gives them an obvious home — the notification
  has a third action slot free — and an obvious home is not a decision.
- **Whether the notification should be suppressed while the app is foregrounded.** It is not.
  Suppressing it would mean tracking process lifecycle state to save a redundancy that costs
  nothing, and the lock screen is the case this feature exists for.

## Two things only a device said

Both were found by installing and using this, with a green suite in hand. Recorded because
neither is obvious from the design, and both would have shipped.

- **A new rest did not clear the previous rest's "Rest over".** The two sat in the shade
  together, and the older one was by then stale — it named the set that had just been logged
  while the countdown beside it named the one after. `showRestOver` already dismissed the
  countdown; the mirror of that was simply missing. Two notifications disagreeing about the same
  question is worse than either alone.
- **Granting notification permission mid-rest left that rest with nothing.** US-05 asks for the
  permission *during* the member's very first rest, and whether we can post is not part of
  `restEndsAt` — so nothing about the stored end time changes when the answer arrives, and the
  collection above never fires again. The member's first ever rest got no notification and no
  buzz. The code this replaced handled it by re-scheduling in the permission callback; the
  rewrite dropped that, which makes it a regression rather than a gap.

  Fixed with `reapply()`, called from `MainActivity.onResume`, which re-applies the running rest
  without waiting for it to change. Deliberately not keyed to the permission result: resuming is
  the more general fact, and it covers a member turning notifications back on in system Settings
  and coming back just as well.

## Consequences

- The rest becomes usable without unlocking the phone, which is the actual ergonomic win: the
  common case is a member with the phone in a pocket between sets.
- ADR-0010's escape hatch is now partly walked. If this app ever goes to Play,
  `USE_EXACT_ALARM` still has to become a foreground service — but the notification content,
  the actions and the coordinator all survive that move unchanged, because none of them knows
  what schedules the alarm. The migration got smaller, not larger.
- One more thing can be wrong on a lock screen. A stale notification is now a visible bug rather
  than a missing buzz, which raises the cost of getting `restEndsAt` handling wrong — and is
  exactly why that handling collapsed to a single rule.
- `RestController.restStarted` keeps one job and loses the other. It no longer drives
  scheduling, but it still gates the one-time permission request — and it has to, because
  `restEndsAt` is the wrong signal for that: it can go non-null from a notification action while
  the app is backgrounded, and popping a permission dialog at that moment would be wrong. "A
  rest started *on this screen*" and "a rest is running" turned out to be genuinely different
  questions, which was not obvious until the two were separated.
- **Revisit if** a member reports the ongoing notification as unwanted noise, in which case
  degrading to option 3 (rich content at zero only) is a small change and needs no new mechanism,
  or if Play distribution forces the foreground service after all.
