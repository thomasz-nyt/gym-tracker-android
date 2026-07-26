# ADR-0001: Native Android first, native iOS second

- **Status:** accepted
- **Date:** 2026-07-26
- **Deciders:** maintainer

## Context

The app must run on Android and iOS for a household, must work offline in a gym,
and must read wearable data (Fitbit band via Health Connect on Android, Apple Watch
via HealthKit on iOS). The maintainer has prior negative experience with the
Expo/React Native toolchain. Development capacity is one person, part-time.

## Options considered

1. **React Native / Expo** — rejected on prior experience with the build toolchain
   and native module story.
2. **Flutter** — one codebase, good developer loop, mature. But the two hardest
   integrations (Health Connect, HealthKit + a watchOS `HKWorkoutSession` companion)
   are native on both sides regardless, so the shared-UI benefit shrinks exactly
   where the work is. Adds Dart, a language used nowhere else in this stack.
3. **Kotlin Multiplatform** — shared logic, native UI. Attractive, but still two
   UIs to hand-write, which is the thing that stalls solo projects.
4. **Native Android then native iOS** — chosen.

## Decision

Build native Android (Kotlin, Compose) to a usable household release, then build
native iOS (Swift, SwiftUI) including a watchOS companion.

## Consequences

- Two UI codebases eventually. Accepted, because a continuous heart rate during
  lifting requires a watchOS `HKWorkoutSession` companion anyway, which no
  cross-platform framework removes.
- `:core:domain` is kept as pure Kotlin with no Android dependencies, enforced in
  CI. This preserves the option of extracting it to a KMP shared module at M8
  instead of re-writing it in Swift. That call is deferred to its own ADR.
- Android ships and gets real household use before any iOS work begins. If the app
  does not stick with the family, we will not have paid for two platforms.
- Revisit if: the iOS port has not started within six months, or Compose
  Multiplatform's iOS story makes the watch companion the only native surface.
