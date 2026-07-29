# ADR-0010: Rest timer as a persisted end time plus an exact alarm

- **Status:** accepted
- **Date:** 2026-07-28
- **Deciders:** maintainer

## Context

US-05 asks for four things at once:

- a timer that starts automatically after a set, at a configurable default (90s)
- that **keeps running when the app is backgrounded and notifies at zero**
- that still works, in-app, when notification permission is denied — asked once, never
  re-prompted
- that never blocks logging the next set

`tech-stack.md` approves WorkManager for *background sync*, "constrained on network
availability". Nothing in it sanctions a mechanism for firing something at an exact
moment, and WorkManager is explicitly the wrong tool: it batches and defers, which is
correct for sync and useless for a 90-second rest.

Two further constraints come from elsewhere. Constitution §2 says the app is fully
functional offline and the local database is the source of truth — a timer that only
exists in a running process is not that. `data-model.md` § Units aside, the rest default
is device-local unsynced state, which ADR-0005 already routes to DataStore.

## Options considered

1. **Persist the end time; schedule an exact alarm for the notification.** The timer is
   a stored `Instant`, not a countdown. The UI derives remaining time from the clock, so
   process death is a non-event — reopening mid-rest just recomputes. `AlarmManager`
   with `setExactAndAllowWhileIdle` fires the notification. Costs the
   `USE_EXACT_ALARM` permission.
2. **A foreground service holding a countdown.** The most conventional answer for
   fitness apps and it needs no exact-alarm permission. Rejected: a persistent
   notification for every 90-second rest is heavy for the core loop this app is built
   around, and it adds a service lifecycle to get wrong. It also still needs the end time
   persisted to survive process death, so it is option 1 plus a service.
3. **`WorkManager`.** Rejected: minimum periodic interval and deferral policy make it
   unable to fire at a specific second.
4. **In-app only.** Simplest, and would require amending US-05 to drop "keeps running
   when backgrounded and notifies at zero". Rejected by the maintainer.

## Decision

Option 1.

- The timer is `restEndsAt: Instant?` in DataStore. Remaining time is
  `Duration.between(now, restEndsAt)`, computed from an injected `Clock`. There is no
  counter to lose.
- `AlarmManager.setExactAndAllowWhileIdle` schedules a broadcast at `restEndsAt`; the
  receiver posts the "Rest over" notification. Cancelled on skip or on logging the next
  set.
- `USE_EXACT_ALARM` is declared. **This is a deliberate trade**: it is granted on
  install for sideloaded apps, which is how this household installs, but Google Play
  restricts it to alarm-and-timer apps. If this app is ever published, this is the line
  that has to change — most likely to option 2. Recorded here so that is a known cost
  rather than a surprise.
- `POST_NOTIFICATIONS` is requested the first time a timer starts, once. A
  `notificationPermissionAsked` flag in DataStore makes "never re-prompted" true across
  restarts rather than only within a process.
- Denied permission degrades to in-app only: the countdown still runs on screen. The
  alarm is simply not scheduled, because a notification nobody can see is not worth a
  wakeup.
- The timer is display state. It gates nothing — "Add set" is never disabled while it
  runs, which is how "it never blocks logging the next set" is satisfied structurally
  rather than by remembering to allow it.

Adds `USE_EXACT_ALARM` and `POST_NOTIFICATIONS` to `tech-stack.md`'s approved list.

## Consequences

- Surviving process death costs nothing: it falls out of storing an end time rather than
  a countdown. The same shape will suit any future timer.
- The rest default and the permission-asked flag join the unit preference in DataStore,
  which is now the established home for device-local state (ADR-0005, ADR-0008).
- An exact alarm is a real permission with a real policy attached. The escape hatch, if
  the app is ever published, is a foreground service — option 2 — and the persisted end
  time is reusable as-is under it.
- The `BroadcastReceiver` and the notification are untestable glue in unit tests; the
  logic worth testing (when the timer ends, how much is left, what a denial does) is all
  in the domain and is covered there.
- **Revisit if** the app moves to Play distribution, or if a household member finds the
  notification unwanted — in which case denying the permission is already a supported,
  fully functional path.
