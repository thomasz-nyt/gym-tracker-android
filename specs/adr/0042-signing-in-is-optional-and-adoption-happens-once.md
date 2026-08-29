# ADR-0042: Signing in is optional, and a device adopts local data exactly once

- **Status:** accepted
- **Date:** 2026-08-29
- **Deciders:** maintainer, agent
- **Relates to:** ADR-0005 (DataStore holds what describes this install), ADR-0034
  (backup is a file you own — the same "install-scoped, does not travel" boundary),
  ADR-0038 (the closest precedent for an optional feature with a no-op binding),
  `specs/data-model.md` §Identity before M2, constitution §3 (no feature may require a
  third-party account; assume minors will use the app), US-07, US-09

## Context

M1 through M5a shipped an app that needs no account at all: `local_member_id`
(`DataStoreCurrentMember`) is a UUID generated on first launch, and every table filters on
it. M2 introduces real accounts and a household, and two questions have to be settled
before any auth code is written, because the second one is destructive if answered wrong.

1. **Does M2 put a login wall in front of the app?** The roadmap's own M2 section already
   says "the household does not need accounts to start using the app," and constitution §3
   forbids requiring a third-party account for any feature. But nothing yet states whether
   *this app's own* account is required once it exists.
2. **What happens to the rows already sitting under `local_member_id` when someone signs
   in?** `data-model.md` §Identity before M2 already answers half of this: "the id names
   the member, not the install, which is why one UPDATE can re-assign it to a Supabase user
   at sign-in." What it does not say is *when* that UPDATE is allowed to run. A gym-tracker
   app is used on a shared household phone as often as a personal one (constitution §3
   again: "the app serves adults and minors in one household"). If the UPDATE runs on every
   sign-in unconditionally, the first time a second family member signs in on a device that
   already holds someone else's real training history, that history becomes theirs.

## Options considered

### Whether sign-in is required

1. **Optional — chosen.** No login wall, ever. Settings gains a "Sign in to sync" row;
   every M1–M5a feature keeps working exactly as today with no account.
2. **Required on first launch.** Simpler engine — there is always an `auth.uid()`, so the
   adoption question below never arises. Rejected: it puts a login wall in front of a gym
   floor with no signal, and contradicts M1's stated goal of a two-tap, no-account core
   loop the maintainer explicitly built and tested that way.
3. **Required only to unlock household features.** The app works signed out, but Routines,
   Progress and the rest stay local-only until sign-in. Rejected as unnecessary complexity:
   it creates two classes of feature with different availability rules for no benefit this
   milestone needs, and every one of those features already works fully offline today.

### When local rows may be re-keyed to an account

1. **Adopt once, then never again — chosen.** The re-key UPDATE runs only if this install
   has never completed a sign-in before, tracked by a new DataStore boolean
   (`has_completed_first_sign_in`, alongside `local_member_id` in the same preferences
   file, per ADR-0005's own rule: this describes the install, not a row any table holds).
   Once set, it is never cleared by anything short of an uninstall.
2. **Always ask before adopting.** Reuse US-41's import-confirm pattern: a dialog naming
   real counts ("Claim 34 workouts and 5 routines already on this device?") on every
   first-time sign-in, regardless of history. Rejected for the maintainer's own case, which
   is the common one: a single-user phone signing in for the first time should not need a
   confirmation for data that is unambiguously theirs. The "once" rule already produces the
   dialog-worthy case — a *second* sign-in on a device with pending local rows — as an
   explicit, separate state (see Decision).
3. **Never adopt; sign-in always starts empty.** No ambiguity, but strands the maintainer's
   own real training history — logged for the entire span of M1 through M5a on this exact
   device — behind an export-then-import round trip through US-40/US-41, which replaces
   everything rather than merging. Rejected: it contradicts `data-model.md`'s own stated
   intent for `local_member_id` and punishes the primary, common case to guard against a
   rare one that option 1 already guards.

## Decision

**Sign-in is optional for the lifetime of the app.** No screen, feature, or flow shipped
before M2 gains a requirement to authenticate. `AuthSource.session(): Flow<Session?>`
(`:core:domain`) is read only by the sync engine (ADR-0043) and by Settings' "Sign in to
sync" row; nothing else in the codebase may branch on it.

**A device adopts its local rows into an account exactly once.** On sign-in:

- If `has_completed_first_sign_in` is `false` (the default — a fresh install, or one that
  has only ever used the app signed out): run the re-key UPDATE `data-model.md` names,
  moving every row from `local_member_id` to the newly-signed-in `auth.uid()`. Set
  `has_completed_first_sign_in = true` in the same transaction the sign-in itself commits.
- If `has_completed_first_sign_in` is already `true`: the device has adopted an account
  before (whether or not that sign-in is still active). Local rows are left exactly where
  they are, under whatever id they already carry, and the newly signed-in member starts
  with an empty household view. Nothing is deleted; nothing is merged; nothing is asked.

This makes "sign out, sign into a different account, sign back into the first one" and
"a second family member signs in on my phone" behave identically from the data's point of
view: the second sign-in in either sequence adopts nothing. The boolean does not care which
account did the adopting, only that adoption has already happened on this install.

A member who wants their own data on a *different* device is not served by this ADR at
all — that is what the sync engine (ADR-0043) exists to do once they are signed in.

## Consequences

- The maintainer's own multi-week training history, currently living only under a local
  UUID on one device, becomes theirs on that device's first sign-in with no dialog, no data
  loss, and no export/import round trip.
- A family member signing in on a shared device after that point gets a correctly empty
  household view rather than someone else's log — the failure mode this ADR exists to rule
  out by construction rather than by relying on a confirmation dialog nobody is required to
  read carefully.
- `has_completed_first_sign_in` is permanent for the life of the install; there is no UI
  path to reset it, matching ADR-0034's treatment of other install-scoped state (US-41's
  import explicitly excludes `local_member_id` and its siblings from restoration for the
  same reason).
- A device that is reinstalled starts this flag at `false` again, same as `local_member_id`
  itself — a reinstall is a new install by this repo's existing convention, and re-adopting
  once more on that fresh install is correct, not a gap.
- Revisit if a future story asks for one device to knowingly hold two members' local data
  side by side before either signs in (nothing today does); this ADR assumes exactly one
  local identity per install, which `DataStoreCurrentMember`'s existing shape already
  assumes for everything else.
