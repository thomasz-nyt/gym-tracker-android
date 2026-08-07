# Glossary

The ubiquitous language of the gym tracker. Terms are defined here once; specs, code,
and conversation use them in exactly this sense. Implementation detail does not belong
in this file.

## Session

One visit to the gym by one member: a thin container of exercises, opened by starting a
workout and closed by finishing it (US-01, US-06). At most one is **active** per member.

## Exercise (in a session)

One appearance of a catalog exercise within a session. The same movement can appear
twice; each appearance has its own sets and its own place in the performed order
(ADR-0004).

## Finished (exercise)

An exercise appearance the member has **explicitly declared done** for this session —
by the card's toggle or by completing a guided walkthrough (US-05a). Never inferred by
the app. Logging a set against a finished exercise makes it **in progress** again: the
declaration cannot outlive evidence to the contrary (US-02d, ADR-0019). Distinct from a
**removed** exercise (US-02c), which leaves the session; a finished one stays, quieter,
at the bottom of the list.

## In progress (exercise)

Any exercise appearance in the active session that is not finished. The default state;
an exercise needs no sets to be in progress.
