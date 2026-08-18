# ADR-0039: A live band is not Health Connect

- **Status:** proposed
- **Date:** 2026-08-18
- **Deciders:** maintainer (requested), agent (scoped)

## Context

The maintainer's words: wants to see heart rate (and other basic Fitbit data) live,
during a session, on every screen — "instead of checking another app or the
fitbit/watch devices." Two PRs are already in flight for M5's real scope: #59
(US-20/21, availability and opt-in) and #60 (US-22, reading avg HR / max HR / active
kcal for the session window, after the session ends). Neither is live, and neither
was meant to be — `health-connect.md` says "Read for the session window only, after
the session ends" as a design premise, not an oversight.

**Health Connect cannot serve a live readout, and this is a fact about the API, not
a missing feature.** The compiled `connect-client-1.1.0.aar` was inspected directly:
`HealthConnectClient` exposes `readRecords`, `aggregate`, `aggregateGroupByDuration`,
`aggregateGroupByPeriod`, and a `getChangesToken`/`getChanges` pair meant for
periodic background sync, not display-rate updates. The `datanotification` package
contains exactly one method, `DataNotification.from(Intent)`, which parses a
system-sent intent — there is no registration call anywhere in the SDK for an app to
ask to be notified. Polling `readRecords` in a tight loop would not fix this either:
Fitbit deliberately delays continuous heart rate into Health Connect to save the
band's battery, so even a fast poll would surface a reading that is minutes old.
Displaying a stale number as if it were current is exactly what **constitution
§2.4** ("Never fabricate, estimate, or interpolate a logged value") forbids — this
is the same clause that got **ADR-0018** (sensor-assisted rep counting) deferred.

The one mechanism that produces real, sub-second heart rate is the **Bluetooth
Heart Rate Profile** (service UUID `0x180D`, measurement characteristic `0x2A37`),
which Fitbit Charge 6, Fitbit Air, and Pixel Watch 2/3/4 broadcast natively. This is
confirmed by Google's own real-time-heart-rate-sharing documentation, which lists
those four devices and names the Bluetooth Heart Rate Profile as the mechanism.
Older bands — including a Fitbit Inspire, which the maintainer also wears — do not
broadcast it and will not appear in a scan. Nothing in this decision changes that.

`health-connect.md`'s own design premise is the direct obstacle: *"Fitbit band data
reaches us the same way Apple Watch data will on iOS: the vendor's own app writes to
the platform health store, and we read from the store. **We never talk to a device
or a vendor cloud.**"* That sentence is what deferred ADR-0018, and it forbids this
feature by name unless it is narrowed.

## Options considered

1. **Poll Health Connect faster and call it live.** Rejected outright — it is not
   technically possible (no streaming API) and even if it were, the data itself
   lags by minutes at the source. This would be §2.4 dishonesty dressed as a
   feature, not a shortcut.
2. **Talk to the band directly over Bluetooth Heart Rate Profile (BLE GATT),
   scanning and connecting from the phone app.** Delivers real ~1 Hz BPM. Requires
   narrowing `health-connect.md`'s "never talk to a device" rule, a new domain port,
   and `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` permissions. No new Gradle dependency —
   `android.bluetooth` is a platform API. Chosen.
3. **Fitbit Web API / cloud polling.** Rejected independently of the live-data
   question: `health-connect.md` already puts the Fitbit Web API on its do-not-use
   list (retiring September 2026, forces OAuth re-consent), and constitution §3
   forbids requiring a third-party account. Constitution §2.2 ("the gym has no
   signal") also rules out any design that needs a network round-trip mid-set.
4. **Defer, as ADR-0018 did.** Rejected as the default, unlike ADR-0018: that
   decision was deferred because inferring reps from raw accelerometer data is
   estimation with no honest source of truth. A band's own heart-rate broadcast is
   not an estimate — it is the same signal the band's own screen shows, read one
   hop earlier than Health Connect would relay it. The objection that deferred
   ADR-0018 does not apply here with the same force.

## Decision

**Option 2.** Add a second, independent domain port — not a new method on
`HealthMetricsSource` — backed by a direct Bluetooth Heart Rate Profile connection
to the band, gated behind its own per-member opt-in, off by default, absent
whenever no reading exists.

`health-connect.md`'s "we never talk to a device or a vendor cloud" is narrowed:
it continues to govern everything **persisted** — Health Connect remains the only
path for anything written to the `sessions` table, and this ADR authorizes nothing
about storage. It is amended to say the app may read a live, transient value
directly from a paired band for on-screen display only, never for anything that
touches Room or the backup envelope.

A new port, not an extension of `HealthMetricsSource`, because the two are
different shapes with different failure modes: `HealthMetricsSource.metricsFor()`
is a one-shot `suspend` call over a closed window that already happened;
`LiveHeartRateSource` is a `Flow` over a connection that can drop and reconnect
mid-observation. Folding a streaming case into a `suspend` interface would either
break `HealthMetricsSource`'s contract (which `health-connect.md` pins verbatim and
#59/#60 already implement against) or force every existing caller to handle a case
that cannot occur for them.

**API 31+ only.** Scanning for Bluetooth LE devices on API 26–30 requires
`ACCESS_FINE_LOCATION`, which this app has never requested and should not start
requesting for a heart-rate reading. Android 12 (API 31) introduced
`BLUETOOTH_SCAN` with `android:usesPermissionFlags="neverForLocation"`, which drops
the location requirement entirely provided the scan never returns any location.
Gating below API 31 to `Unavailable` — the same silent absence Health Connect uses
below its own minimum — keeps location permission out of the app for good, which
matters directly under constitution §3 ("assume minors will use it") and §5.

**No foreground service in v1.** The connection lives only while the app is
foregrounded; leaving a session screen drops it. A background-surviving connection
needs a `connectedDevice`-type foreground service and its own Play Console
declaration — real scope, deferred rather than smuggled into this change.

**Nothing new is persisted.** The live reading is display-only and is never written
to Room or the backup envelope (constitution §5 already limits what Health Connect
metrics may be stored, and this ADR adds nothing to that list).

## Consequences

- `health-connect.md` gains a short amendment narrowing its device-access rule and
  pointing here; its `HealthMetricsSource` interface block is untouched, so #59 and
  #60 remain correct as written and unblocked by this decision.
- `tech-stack.md` gains a row for `android.bluetooth` (platform, no new artifact,
  API 31+) and a note that the optional-feature contract now governs two ports.
- New stories US-46 … US-49 and a new `roadmap.md` section, M5a, run alongside M5 on
  the same terms M3b and M3c ran alongside M4 (`roadmap.md`'s own precedent): this
  work touches no table M5 reads, and M5's own scaffolding (`:feature:health`, the
  no-op default binding pattern, the per-member toggle shape) is what M5a reuses
  rather than rebuilds. Per the roadmap's sequential rule, **M5a's code does not
  start until #59 and #60 have merged** — building the optional-feature scaffolding
  before it exists in `main` means building it twice.
- The Bluetooth pairing walk in Settings mirrors #59's Health Connect permission
  walk exactly: one permission at a time, a plain-language reason first, silence
  when unavailable, no retry affordance in v1.
- **Fitbit Inspire is explicitly not supported**, and no future PR should treat that
  as a bug to fix — it is a hardware limitation named here so it is not rediscovered
  and re-investigated later.
- **Revisit when** background/screen-off delivery is wanted: that needs its own ADR
  covering the foreground-service type, its Play declaration, and the battery
  tradeoff, none of which this decision authorizes.
- This ADR does not touch ADR-0018's deferral. Reading raw phone/watch motion
  sensors to infer reps remains deferred on §2.4 and §1 grounds untouched by
  anything decided here — a band's own heart-rate broadcast is a measured value at
  its source, not an inference from a different signal.
