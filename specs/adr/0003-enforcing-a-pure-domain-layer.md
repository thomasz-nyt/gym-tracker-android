# ADR-0003: Enforcing the pure-Kotlin domain layer in the build

- **Status:** accepted
- **Date:** 2026-07-26
- **Deciders:** maintainer

## Context

Constitution §7 and `tech-stack.md` both state that `:core:domain` is pure Kotlin
with no Android dependencies, and `tech-stack.md` calls this "the single most
important structural rule in the project" because it is what makes the M8 iOS
port a UI rewrite rather than a logic re-derivation.

`testing-strategy.md` already fixes the CI entry point:

```
./gradlew verifyDomainHasNoAndroidDeps
```

What it does not fix is *how* the rule is checked. The obvious cheap answer —
"`:core:domain` applies `org.jetbrains.kotlin.jvm`, not `com.android.library`,
so it cannot compile against Android" — is not actually true. A pure JVM module
can still declare a dependency on `androidx.annotation`, `com.google.android.*`,
or any other Android-namespaced artifact that ships a plain jar, and it will
compile. The rule needs to be asserted, not assumed.

## Options considered

1. **Rely on the module type alone.** Zero code. Rejected: does not catch
   Android-namespaced jar dependencies, and gives no failure message pointing at
   the constitution when someone does add one.
2. **A ktlint/detekt custom rule banning `android.*` imports.** Catches source
   imports only. Misses a transitive Android dependency that has not been
   imported yet, and hides a structural rule inside a style tool.
3. **A dedicated Gradle verification task.** Checks three things independently:
   applied plugins, resolved dependency coordinates, and source imports. Fails
   with a message that names the offending artifact and cites the constitution.
   More build-logic code, and it is build code, so it needs its own tests.
4. **A published third-party module-boundary plugin** (e.g. a dependency-analysis
   or module-graph-assertion plugin). Rejected for M0: constitution §7 requires
   an ADR per dependency, and this rule is ~100 lines that we want to be able to
   read and change without tracking an upstream project.

## Decision

Enforce the rule with a purpose-built Gradle task, `verifyNoAndroidDeps`,
registered by the `gymtracker.pure.kotlin` convention plugin and aggregated by a
root lifecycle task named `verifyDomainHasNoAndroidDeps` to match the CI command
already written in `testing-strategy.md`.

The task fails if any of the following is true for a module it guards:

1. An Android Gradle plugin (`com.android.*`) or `org.jetbrains.kotlin.android`
   is applied to the module.
2. Any module on `compileClasspath`, `runtimeClasspath`, `testCompileClasspath`,
   or `testRuntimeClasspath` has a group under `com.android.`, `androidx.`, or
   `com.google.android.`.
3. Any Kotlin or Java source file in the module imports `android.`, `androidx.`,
   `com.android.`, or `dalvik.`.

The classification logic lives in a plain object,
`com.gymtracker.buildlogic.AndroidDependencyDetector`, so it is unit-testable
without a Gradle build; the task is the wiring around it.

## Consequences

- The rule is checked from three independent angles, so weakening any one of
  them (say, adding a jar dependency without importing it yet) still fails.
- `build-logic` gains a test source set and a CI step of its own
  (`./gradlew -p build-logic test`). Build code is now held to the same
  test-first standard as production code.
- The task guards *any* module tagged `gymtracker.pure.kotlin`, not only
  `:core:domain`. If a second pure module appears, it is covered by adding one
  plugin line — but the root aggregate task must be updated to depend on it, and
  that is deliberate rather than automatic so the CI contract stays explicit.
- The forbidden-group list is a denylist, so an Android library published under
  an unrelated group would slip past checks 1 and 2. Check 3 (source imports) is
  the backstop, since such a library is only useful once imported.
- Revisit if `:core:domain` is extracted to a KMP module at M8 (see ADR-0001):
  the Android-target source set would then legitimately see Android APIs, and
  this task would need to be scoped to `commonMain`.
