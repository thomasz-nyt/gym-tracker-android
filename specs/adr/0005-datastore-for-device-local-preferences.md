# ADR-0005: DataStore for device-local preferences

- **Status:** accepted
- **Date:** 2026-07-26
- **Deciders:** maintainer

## Context

`data-model.md` § "Identity before M2" already specifies the mechanism:

> On first launch the app generates one **local member UUID** (stored in DataStore)
> and stamps it on every session and set as `user_id`.

US-01 needs that member id before it can create a session, so this is the first
story that has to make it real. But `tech-stack.md` — the approved-dependency
list that CLAUDE.md points at — does not mention DataStore, and constitution §7
says no dependency is added without an ADR. The spec mandates a library the
dependency list does not sanction, so the two documents disagree.

Later M1 stories need the same store: the kg/lb unit preference (roadmap M1), the
rest-timer default and the "notification permission already requested" flag
(US-05).

## Options considered

1. **`androidx.datastore:datastore-preferences`** — what `data-model.md` already
   names. Coroutine- and Flow-native, so it composes with the repository layer,
   and it is the replacement Google ships for SharedPreferences.
2. **A one-row Room table.** Zero new dependencies, since Room is already
   approved. But it puts device-local settings — which are explicitly *not* synced
   and have no `updated_at` or `sync_state` — into the database whose entire
   schema is a mirror of the Postgres one. It would be the only table with no
   counterpart in `data-model.md`'s Postgres section.
3. **`SharedPreferences`** — no new dependency either, but a blocking API that has
   to be bridged to Flow by hand, and it is the thing DataStore exists to replace.

## Decision

Use `androidx.datastore:datastore-preferences` for device-local, unsynced state,
and add it to `tech-stack.md`. Room stays the source of truth for anything that is
a domain entity or that will eventually sync.

## Consequences

- The boundary is a rule, not a judgement call: if a value has a row in
  `data-model.md`'s Postgres schema, it belongs in Room; if it only ever describes
  *this device or this install*, it belongs in DataStore.
- The local member UUID lands behind a `CurrentMember` interface declared in
  `:core:domain`, so the domain never learns that DataStore exists and the M2
  migration to a Supabase user id is a change of implementation only.
- One more dependency in the app. It is small, it is an official AndroidX
  artifact, and three separate M1 stories need it.
- Revisit at M2, when the member id stops being device-local and becomes an
  authenticated identity. The `CurrentMember` seam is where that change lands.
