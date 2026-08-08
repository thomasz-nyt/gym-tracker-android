# ADR-0024: Bottom navigation, and the end of the "Done" button

- **Status:** accepted
- **Date:** 2026-08-08
- **Deciders:** maintainer (chose the direction), agent (scoped it)
- **Completes:** ADR-0013's remaining work, and with it M3's last roadmap checkbox.

## Context

Finding 06 of the redesign audit:

> Browse, detail and history each end in a full-width tonal **Done** and offer no other exit.
> There is no persistent navigation, so the catalog is a modal side-trip rather than a place.

The roadmap says the same thing from the other end. ADR-0013 adopted Navigation Compose and got
most of the way: home/session, browse and detail are destinations. Three screens are still
selected by `when` inside the logging route — history, the workout detail reached from it
(US-06b), and the guided flow (US-05a). ADR-0017 already settled that the guided flow **stays**
out of the graph on purpose. The other two are the leftovers.

These are the same problem. History is a dead end *because* it is a state flag rather than a
place: there is nowhere for it to be except on top of the session, so the only way out is a
button that turns the flag off.

## Options considered

1. **Graph work only, no bottom bar.** Closes the roadmap checkbox and leaves every screen
   still exiting through "Done". Rejected: the checkbox is not the point, the dead end is.
2. **A navigation drawer.** More room for future destinations, and it is a two-hand gesture on
   a screen ADR-0016 spent an entire ADR making one-handed. Rejected.
3. **Bottom navigation, three destinations — chosen.** Thumb-reachable, always visible, and it
   makes "where am I" answerable without reading the screen.

## Decision

**Three top-level destinations**, in a bottom bar: **Train**, **Exercises**, **History**.

**History and workout detail become real destinations**, with their own ViewModel rather than
riding on `ActiveSessionViewModel`. That class already carries a comment saying the next thing
added should split it rather than pile on; this is that split, and it removes two of the three
`when` branches the roadmap is counting.

**The "Done" buttons go.** Browse, detail and history each exit through the bar or the system
back gesture, like every other Android screen.

### The third tab is called History, not Progress

The redesign labels it *Progress* and draws an estimated-1RM chart above the past-workout list.
The list exists today (US-06); the chart is US-16 at **M4**, which has not started. A tab called
Progress that contains no trend would be promising something the app cannot do — the same class
of overclaim ADR-0023 refused for "set 3 of 3". It is called History because that is what is
in it. **When M4 lands, it gains the charts and the name.**

### The bar is hidden while a workout is running

The session screen's bottom belongs to logging — ADR-0016 put the one primary action there and
ADR-0023 put the rest panel above it. A tab bar under that would sit between the thumb and the
most-tapped control in the app, and offer to navigate away mid-set. Train, Exercises and History
show the bar; an active session does not.

This is the one place the bar is not persistent, and it is deliberate: constitution §2 says the
core loop is sacred, and a workout in progress is the core loop.

### What stays out of the graph

- **The guided flow** (US-05a), for ADR-0017's reasons, unchanged.
- **Set entry, the set editor, and the stale-session prompt.** ADR-0013 already ruled these out:
  they are questions about the screen you are on, and a navigation boundary in the middle of the
  two-tap path is exactly what US-03 forbids.
- **Which of home and session you see.** Still derived from Room, never from a restored back
  stack. That is ADR-0013's condition for adopting navigation at all, and it is what makes
  "reopen and you are back in your session" survive a process kill (US-01).

## Consequences

- M3's last checkbox closes, and the milestone's exit criteria can be assessed.
- `ActiveSessionViewModel` sheds history and the workout detail. It still drives the session,
  set entry, the set editor, removal, the rest panel and the guided flow — smaller, not small.
- Every secondary screen gains a real exit, and the catalog becomes a place rather than a
  side-trip. Browse keeps both its modes (US-12): as a tab it browses, and reached from a
  session it picks, where the bar is hidden anyway.
- Three destinations is a bar that looks sparse next to the four the redesign draws. Routines
  (ADR-0020) is the fourth and is not built; adding a tab later is cheaper than shipping an
  empty one now.
- **Revisit when M4 lands** — History becomes Progress and gains its charts — or if Routines
  makes four tabs crowd the bar, at which point Train absorbs it.
