# Health Connect Integration (M5)

Read this before writing any code in `:feature:health`.

## Design premise

**Health Connect is assumed absent.** It is an enhancement layer bolted onto a
complete app, not a dependency. Two independent reasons:

1. A household member may be a minor whose Google account cannot use it, or who
   simply should not be asked.
2. Health Connect availability varies by device and OS version.

Both collapse to the same handling, which keeps the code simple: **treat "member
cannot use it" and "device does not have it" as the same state.** There is exactly
one `Unavailable` branch, and it must be silent — no banner, no "install now"
prompt, no settings row that leads nowhere.

## Architecture

```kotlin
// :core:domain — no Android, no Health Connect imports
interface HealthMetricsSource {
    suspend fun status(): HealthStatus
    suspend fun metricsFor(window: ClosedRange<Instant>): SessionMetrics?
}

sealed interface HealthStatus {
    data object Unavailable : HealthStatus       // device or account — indistinguishable
    data object PermissionRequired : HealthStatus
    data object Ready : HealthStatus
}
```

- Default Hilt binding: `NoOpHealthMetricsSource` → always `Unavailable`, always
  `null` metrics.
- Real binding: `HealthConnectMetricsSource` in `:feature:health`, active only when
  the runtime check passes **and** the member's per-profile toggle is on.
- The toggle defaults to **off** for every member, including you.

## Which API, and why not the others

Use `androidx.health.connect:connect-client` — the on-device API. Do **not** use:

- **Google Fit APIs** — deprecated; Google is supporting them only until the end of
  2026 and new developer sign-ups closed in May 2024.
- **Fitbit Web API** — being retired in September 2026 in favour of the Google
  Health API, and existing OAuth tokens do not carry over, so every user has to
  reconsent. Avoid the whole migration by not going through the cloud.
- **Google Health API** (`health.googleapis.com/v4/`) — the correct choice for
  cloud/web integrations; wrong for a mobile-first app, and it would require Google
  OAuth, which constitution §3 forbids as a requirement.

Fitbit band data reaches us the same way Apple Watch data will on iOS: the vendor's
own app writes to the platform health store, and we read from the store. We never
talk to a device or a vendor cloud — **for anything that ends up in this document's
"What we read and what we store" table, or in Room.**

**This rule does not extend to a live, on-screen-only reading.** ADR-0039 narrows it:
the app may hold a direct Bluetooth Heart Rate Profile connection to a paired band
for a transient, display-only value, because Health Connect has no streaming API and
Fitbit's sync into it is deliberately battery-delayed — a poll would be honest about
neither being live nor being current (constitution §2.4). That path is a separate
domain port (`LiveHeartRateSource`, not `HealthMetricsSource`), documented in
ADR-0039, not in this file: nothing below this point changes as a result, and
nothing a live band reading returns is ever written to Room or the backup envelope.

## Permissions

Request only these, and only at the moment they are first needed:

```
android.permission.health.READ_HEART_RATE
android.permission.health.READ_ACTIVE_CALORIES_BURNED
android.permission.health.READ_EXERCISE
```

- One at a time, each with a plain-language reason on screen first.
- Any denial is final for that run; do not re-prompt.
- Google Play requires a Health Connect declaration form at review, and the data
  types requested must match what is declared. Keep the list minimal for that
  reason as well as the privacy one.
- Handle permission revocation between app launches — always re-check, never cache
  a granted state across process death.

## What we read and what we store

Read for the session window only, after the session ends:

| Read | Stored |
|---|---|
| `HeartRateRecord` samples | `avg_hr`, `max_hr` (ints) |
| `ActiveCaloriesBurnedRecord` | `active_kcal` (int) |
| `ExerciseSessionRecord` | used only to refine the window; not stored |

**Raw samples never leave the device and are never written to Room.** Aggregate in
memory, persist the summary, discard the rest (constitution §5).

If the window contains no samples, store `null`. Never zero, never interpolated
(constitution §2.4). The UI shows "not recorded".

## We never write

The app has no write permissions and creates no records in Health Connect. If that
ever changes it needs an ADR and a constitution amendment.

## Test matrix for M5

| Scenario | Expected |
|---|---|
| Health Connect not installed | No health UI anywhere; zero crashes; no prompts |
| Installed, toggle off | Identical to above |
| Toggle on, all permissions denied | Rest of app fully functional; single non-blocking explanation |
| Toggle on, partial permissions | Only granted metrics shown; others "not recorded" |
| Granted, no samples in window | Nulls stored, "not recorded" displayed |
| Granted, samples present | Correct avg/max against a fixture sample set |
| Permission revoked in system settings between launches | Detected on next read; degrades silently |
| Toggle turned off after use | Reads stop; offer to delete imported metrics (US-23) |
