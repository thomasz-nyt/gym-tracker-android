# Tech Stack

Platform decision: **native Android first (Kotlin), native iOS second (Swift).**
Rejected: React Native/Expo (prior bad experience with the toolchain), Flutter
(a second UI runtime is not worth it given the watch/health integrations are the
hard part and are native on both sides anyway).

## Android

| Concern | Choice | Notes |
|---|---|---|
| Language | Kotlin 2.x | |
| Min / target SDK | min 26, target 36 | Health Connect needs 26+; the SDK-34 shim path is not supported |
| UI | Jetpack Compose + Material 3 | |
| Architecture | MVVM + UDF, `StateFlow` state holders | One immutable `UiState` per screen |
| DI | Hilt | |
| Local DB | Room (source of truth) | Offline-first is a constitutional requirement |
| Device-local prefs | DataStore (Preferences) | Unsynced, this-device-only state. ADR-0005 |
| Async | Coroutines + Flow | |
| Background sync | WorkManager | Constrained on network availability |
| Backend | Supabase (Postgres, Auth, Storage, Edge Functions) | |
| Supabase client | `supabase-kt` (jan-tennert) | Kotlin Multiplatform-ready, which helps the iOS port |
| Charts | Vico | Compose-native, no `AndroidView` wrapper |
| Images / GIF | Coil 3 with `coil-gif` | GIF is the primary exercise-demo format |
| Video | Media3 / ExoPlayer | Only pulled in at M3; do not add earlier |
| Health | `androidx.health.connect:connect-client` | Optional module — see below |
| Nav | Navigation Compose, type-safe routes | |
| Serialization | kotlinx.serialization | |
| Build | Gradle KTS, version catalog (`libs.versions.toml`) | Convention plugins in `build-logic/` |
| Lint | ktlint + detekt | Failing lint fails CI |
| Debug signing | `debug.keystore`, checked in | So CI and local builds install over each other. Not a secret: it cannot sign a release |

## Testing

| Layer | Tools |
|---|---|
| Domain (JVM, fast) | JUnit 5, kotlin.test, Turbine (Flow), MockK, kotlinx-coroutines-test |
| Data / Room (JVM) | JUnit 4 + Robolectric — Robolectric's runner is JUnit 4, so this layer does not use JUnit 5 |
| Data / Room | Room in-memory DB, instrumented on JVM via Robolectric where possible |
| ViewModels | Turbine + fake repositories (never MockK for repos — hand-written fakes) |
| Compose UI | `createComposeRule`, semantics-based assertions, no screenshot-diff at first |
| E2E | A small instrumented smoke suite: log a set → see it in history → see it on the chart |
| Backend | pgTAP or SQL assertions for RLS policies; Deno test for Edge Functions |

## Module layout

```
:app                     Compose UI, navigation, DI wiring
:core:domain             PURE KOTLIN. Models, use cases, progression math. No Android.
:core:data               Room, Supabase, repository impls, sync
:core:designsystem       Theme, tokens, shared composables
:feature:logging         Session + set logging (the core loop)
:feature:catalog         Exercise catalog + media
:feature:progress        Charts and PRs
:feature:health          Health Connect. OPTIONAL — see below
:feature:coach           AI coaching
```

`:core:domain` must compile without the Android plugin. This is enforced by a CI
check and is the single most important structural rule in the project: it is what
makes the iOS port a matter of re-writing UI rather than re-deriving logic.

## The optional-feature contract

`:feature:health` and `:feature:coach` are optional per constitution §3. The rule:

- `:app` depends on an **interface** declared in `:core:domain`
  (`HealthMetricsSource`, `CoachingSource`).
- The default binding is a no-op implementation that reports `Unavailable`.
- The real implementation is bound only when the feature is enabled at runtime.
- **Every screen must render correctly with the no-op binding.** There is a test
  variant that runs the UI suite with all optional features disabled, and it is
  part of the required CI checks.

If a screen crashes or shows an empty hole when heart rate is unavailable, that is
a constitutional violation, not a cosmetic bug.

## Backend notes

- Auth: Supabase email + password. Household membership via a `households` table
  and a `household_id` claim used by RLS.
- AI: Supabase Edge Function (Deno) calls the Anthropic API. The app never holds
  the key. The function is rate-limited per user.
- Storage: a `exercise-media` bucket for self-hosted GIFs and family-recorded clips.
- Migrations: Supabase CLI, checked into `supabase/migrations/`. No schema change
  is made through the web dashboard.

## iOS (phase 2, do not build yet)

Swift 6, SwiftUI, GRDB or SwiftData locally, `supabase-swift`, Swift Charts,
HealthKit, and a watchOS companion target that starts an `HKWorkoutSession` —
that companion is the only way to get continuous heart rate during lifting, and it
is the main reason iOS is native rather than shared-UI.
