# ADR-0002: java.time for timestamps in :core:domain

- **Status:** accepted
- **Date:** 2026-07-26
- **Deciders:** maintainer

## Context

`data-model.md` models timestamps as `Instant`. `:core:domain` is pure Kotlin
(constitution §7) and no dependency may be added without an ADR. Min SDK is 26,
so `java.time` is available natively on Android with no desugaring.

## Options considered

1. **`java.time`** — in the JDK, zero new dependencies, complete API. Not
   multiplatform: would need replacing if `:core:domain` is extracted to a KMP
   shared module at M8.
2. **`kotlinx-datetime`** — multiplatform-ready, which helps the M8 KMP option,
   but a new dependency today whose JVM implementation delegates to `java.time`
   anyway.

## Decision

Use `java.time` (`Instant`, `Duration`) throughout `:core:domain` until M8.

## Consequences

- Zero added dependencies; the domain module stays pure JVM Kotlin.
- If M8 chooses KMP extraction, timestamps migrate to `kotlinx-datetime` then.
  The mapping is mechanical (`Instant` ↔ `Instant`) and is protected by the
  existing domain test suite.
- Revisit at M8 alongside ADR-0001's deferred KMP decision.
