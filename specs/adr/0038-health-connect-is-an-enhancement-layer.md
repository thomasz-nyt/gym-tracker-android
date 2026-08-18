# ADR-0038: Health Connect is an enhancement layer, three statuses collapse to one, and the toggle is device-local

- **Status:** accepted
- **Date:** 2026-08-18
- **Deciders:** maintainer, agent
- **Relates to:** ADR-0005 (DataStore holds what describes this install), ADR-0003 (pure
  domain layer), `specs/health-connect.md`, constitution §3 (minors, no mandatory account)
  and §5 (raw samples never leave the device), `tech-stack.md`'s optional-feature contract,
  US-20, US-21

## Context

M5 is next on `specs/roadmap.md`, and `specs/health-connect.md` already settled most of the
hard questions before any code existed: the interface shape, the permission list, what is
read and what is stored, and — the load-bearing sentence — "treat 'member cannot use it' and
'device does not have it' as the same state. There is exactly one `Unavailable` branch, and
it must be silent."

Three things still needed a decision to turn that document into code:

1. `HealthConnectClient.getSdkStatus()` returns three values (available, not installed,
   update required), and the roadmap's own checkbox reads "SDK available / update required /
   **not available**" — three words for what `health-connect.md` calls one branch.
2. `androidx.health.connect:connect-client` is the first new Gradle dependency since the
   backup work landed SAF (which cost nothing, being already present) — constitution §7
   requires an ADR for it, even though `tech-stack.md` already names it as the approved
   choice.
3. The per-member opt-in toggle needs a storage location, and ADR-0005 drew that boundary
   in general terms ("if a value only ever describes *this device or this install*, it
   belongs in DataStore") without a case in front of it yet.

## Options considered

### Collapsing the SDK status

1. **All three collapse into one `Unavailable` — chosen.** `SDK_UNAVAILABLE` and
   `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` both become `HealthStatus.Unavailable`.
2. **A fourth status, `UpdateRequired`, surfaced as a prompt.** This is what the roadmap's
   literal wording would produce. Rejected: `health-connect.md`'s own text forbids exactly
   this ("no 'install now' prompt"), and a household with a minor whose account cannot use
   Health Connect at all has no use for a prompt to update it. The roadmap line is describing
   the platform API's return type, not the app's behavior — `health-connect.md`, written
   after it and specifically about this feature, is the more specific source and wins.

### The dependency

1. **`androidx.health.connect:connect-client` — chosen**, per `tech-stack.md`'s existing row
   and its stated rejections of Google Fit (sunsetting), Fitbit Web API (retiring, forces
   re-consent) and the cloud Health API (needs Google OAuth, which constitution §3 forbids
   as a requirement). Confirmed reachable from this environment: `dl.google.com`'s Maven
   metadata for the artifact returns HTTP 200. Not yet in the local Gradle cache, so the
   first build after this ADR downloads it.
2. **No new dependency — read nothing.** Not a real option; M5 has no content without it.

### Where the toggle lives

1. **DataStore, alongside the unit preference — chosen.** ADR-0005's rule applies directly:
   this describes one member's choice on one device, has no row in `data-model.md`'s Postgres
   schema, and needs no `updated_at`/`sync_state`. It is modeled the same way
   `DataStoreUnitPreference` is — an `observe(): Flow<Boolean>` / `current()` / `set()` port
   in `:core:domain`, a DataStore-backed implementation in `:core:data`.
2. **A column on a member/profile table.** Rejected for the same reason ADR-0005 keeps
   `rest_ends_at` and the guided-flow keys out of Room: there is no `households`/`profiles`
   table before M2, and inventing one two milestones early to hold one boolean would be
   exactly the "activity type abstraction" constitution §1 warns against, aimed at a
   different feature.
3. **In the US-40/41 backup envelope, alongside `weight_unit` and `default_rest_seconds`.**
   Rejected, deliberately, even though those two keys *are* in the envelope. A restored
   backup must not silently turn health reads on for a device that has never granted
   permission — ADR-0034 already draws this line for `rest_ends_at` and the guided-flow keys
   ("describes this device or this install... no business surviving into a different
   install"); the health toggle is the same case. A device that receives someone else's
   export starts with the toggle at its own default, off.

## Decision

`HealthMetricsSource` (`:core:domain`, no Android import) is the seam, exactly as
`specs/health-connect.md` §Architecture specifies it: `status(): HealthStatus` and
`metricsFor(window): SessionMetrics?`, where `HealthStatus` is `Unavailable` /
`PermissionRequired` / `Ready` — **three cases, not four.** `getSdkStatus()`'s two negative
results and the per-member toggle being off all map to the same `Unavailable`, checked in
that order so a caller can never distinguish "not installed" from "installed but the member
opted out" — which is the entire point: neither produces UI.

The default Hilt binding is `NoOpHealthMetricsSource`, living in `:core:domain` itself (not
`:feature:health`) so `:app` can wire the no-op path without depending on the optional
module at all — the real binding, `HealthConnectMetricsSource`, is added by `:feature:health`
and swapped in only when a `BuildConfig` flag says the optional feature is enabled (see
`tech-stack.md`'s optional-feature contract; the flag's own reasoning is recorded in this
PR's DI wiring, not repeated here).

The opt-in toggle is `HealthIntegration` (`:core:domain` port, `DataStoreHealthIntegration`
implementation), stored in the same `gymTrackerPreferences` DataStore file `UnitPreference`
and `RestTimerStore` already use, key `health_integration_enabled`, **default `false`** for
every member. It is read by `status()` before any permission check and is not part of the
US-40 backup envelope.

## Consequences

- A member sees no health-related UI anywhere unless they are on a device with Health
  Connect, have explicitly turned the toggle on, and have granted at least one permission —
  three independent gates, each defaulting closed, which is what makes US-20's "no banner, no
  nag" claim mechanically true rather than a UI convention someone can forget.
- `metricsFor()` is a stub in this PR (`null`, always) — this ADR covers the seam and the
  opt-in (US-20, US-21); the read itself is US-22's PR, and this file does not re-litigate
  what `health-connect.md`'s read/store table already settled.
- The `PermissionRequired` branch is re-derived on every `status()` call rather than cached,
  per `health-connect.md`'s "always re-check, never cache a granted state across process
  death" — this is what makes revocation in system settings visible on the next read instead
  of on the next reinstall.
- Revisit if M2's `households`/`profiles` tables land and per-member settings gain a natural
  server-side home; until then, DataStore is correct by ADR-0005's own rule.
