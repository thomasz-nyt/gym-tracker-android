# ADR-0013: Navigation Compose replaces state-derived routing

- **Status:** accepted
- **Date:** 2026-08-01
- **Deciders:** maintainer (chose), agent (scoped)

## Context

M1 deliberately shipped no navigation graph. `ActiveSessionScreen` says so in a comment:

> There is deliberately no navigation graph yet. Which screen you see is derived from the
> database, not from a back stack, which is what makes "reopen and you are back in your
> session" true even after the process is killed.

That was the right call for one screen and it bought a real property: US-01's "reopen the app
and you return to your session" is not a restored back stack, it is a Room query. A killed
process cannot lose it.

By the end of M1 the single screen had become four — session, exercise search, the stale
prompt, history — selected by early returns on `SessionUiState` flags. M3 adds two more:
catalog browse, and the exercise detail screen. Six destinations selected by a chain of `if`
statements, each with its own hand-written `BackHandler`, is the point where the pattern stops
paying for itself:

- `SessionUiState` carries `isSearching`, `history.isOpen` and would gain two more flags that
  have nothing to do with the active session it is named after.
- Back handling is manual and per-screen. History already needed `BackHandler` added by hand;
  every new screen needs the author to remember.
- There is no way to express "browse opened from home" versus "browse opened from a session",
  which US-12 needs — the same screen behaves differently depending on where you came from.

`tech-stack.md` has listed **Navigation Compose with type-safe routes** as the approved
navigation choice since M0. This ADR is not adopting a new dependency; it is starting to use
one the stack already names.

## Options considered

1. **Introduce Navigation Compose now.** Chosen.
2. **Keep state-derived routing, add two more flags.** No new dependency and consistent with
   what is there. Rejected: it scales by adding a boolean and a `BackHandler` per screen, and
   it cannot express the from-home/from-session distinction US-12 needs without a third flag
   describing how the second flag was set.
3. **Navigation for the new screens only, state-derived for the old ones.** Rejected as the
   worst of both: two routing mechanisms, and the back stack would not contain the screen you
   actually came from.

## Decision

Adopt Navigation Compose with type-safe routes.

**The resume property is preserved, and this is the condition of the decision.** The start
destination is *computed from the database*, not restored from a saved back stack:

```kotlin
startDestination = if (activeSession != null) Session else Home
```

So a killed process still reopens into the session, for the same reason it did before: the
answer comes from Room. What the back stack adds is only where you go when you press back
*within* a launch. Nothing about navigation may be persisted across process death — if a
future change starts restoring a saved back stack, it must not be allowed to override that
query.

Consequences for the screens that exist:

- The stale-session prompt (US-01) stays a dialog, not a destination. It is a question about
  the session you are already in, and it must not be something you can navigate back to.
- Set entry (US-03) stays a dialog for the same reason, and because the two-tap path must not
  gain a transition. `TwoTapSetLoggingTest` is the check on that.

## Consequences

- `SessionUiState` sheds `isSearching`, `query`, `results` and `history`, which belong to the
  screens that own them rather than to the active session.
- Every screen gets back handling from the graph instead of a hand-written `BackHandler`.
- US-12's two entry points become two routes to one composable, with the argument saying
  which behaviour a tap has — the distinction the previous pattern could not express.
- **The risk is the two-tap path**, which is the one thing `roadmap.md` calls "the milestone
  that decides whether the app is good". Set entry stays a dialog specifically so that path
  never crosses a navigation boundary, and `TwoTapSetLoggingTest` fails the build if it does.
- **Revisit if** the start destination ever stops being derived from Room. That is the line
  this ADR draws; crossing it silently would give back the property M1 was built around.
