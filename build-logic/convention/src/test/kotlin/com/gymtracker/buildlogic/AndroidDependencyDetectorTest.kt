package com.gymtracker.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Covers the classification logic behind `verifyDomainHasNoAndroidDeps`
 * (see `specs/adr/0003-enforcing-a-pure-domain-layer.md`).
 */
class AndroidDependencyDetectorTest {
    @Nested
    @DisplayName("applied plugins")
    inner class Plugins {
        @ParameterizedTest
        @ValueSource(
            strings = [
                "com.android.application",
                "com.android.library",
                "com.android.test",
                "com.android.dynamic-feature",
                "org.jetbrains.kotlin.android",
            ],
        )
        fun `rejects android plugins`(pluginId: String) {
            assertTrue(
                AndroidDependencyDetector.isForbiddenPlugin(pluginId),
                "expected $pluginId to be rejected",
            )
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "org.jetbrains.kotlin.jvm",
                "java-library",
                "org.jetbrains.kotlin.plugin.serialization",
                "gymtracker.pure.kotlin",
            ],
        )
        fun `allows non-android plugins`(pluginId: String) {
            assertFalse(
                AndroidDependencyDetector.isForbiddenPlugin(pluginId),
                "expected $pluginId to be allowed",
            )
        }
    }

    @Nested
    @DisplayName("resolved dependency coordinates")
    inner class Dependencies {
        @ParameterizedTest
        @ValueSource(
            strings = [
                "androidx.core:core-ktx:1.19.0",
                "androidx.annotation:annotation:1.9.1",
                "androidx.room:room-runtime:2.7.0",
                "com.android.tools.build:gradle:9.3.1",
                "com.google.android.material:material:1.12.0",
            ],
        )
        fun `rejects android artifacts`(coordinates: String) {
            assertTrue(
                AndroidDependencyDetector.isForbiddenModule(coordinates),
                "expected $coordinates to be rejected",
            )
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "org.jetbrains.kotlin:kotlin-stdlib:2.3.21",
                "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0",
                "app.cash.turbine:turbine:1.2.1",
                "com.google.dagger:dagger:2.60.1",
                "javax.inject:javax.inject:1",
            ],
        )
        fun `allows pure jvm artifacts`(coordinates: String) {
            assertFalse(
                AndroidDependencyDetector.isForbiddenModule(coordinates),
                "expected $coordinates to be allowed",
            )
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "com.androidessentials:widgets:1.0",
                "io.androidx:not-really-androidx:1.0",
                "com.google.androidfoo:bar:1.0",
            ],
        )
        fun `does not reject groups that merely start with the same letters`(coordinates: String) {
            assertFalse(
                AndroidDependencyDetector.isForbiddenModule(coordinates),
                "expected $coordinates to be allowed",
            )
        }
    }

    @Nested
    @DisplayName("source imports")
    inner class Imports {
        @Test
        fun `finds android imports`() {
            val source =
                """
                package com.gymtracker.core.domain

                import android.os.SystemClock
                import androidx.annotation.VisibleForTesting
                import java.time.Instant

                class Session
                """.trimIndent()

            assertEquals(
                listOf("android.os.SystemClock", "androidx.annotation.VisibleForTesting"),
                AndroidDependencyDetector.forbiddenImportsIn(source),
            )
        }

        @Test
        fun `finds star and aliased imports`() {
            val source =
                """
                import android.util.*
                import dalvik.system.DexFile as Dex
                import com.android.tools.Something
                """.trimIndent()

            assertEquals(
                listOf("android.util.*", "dalvik.system.DexFile", "com.android.tools.Something"),
                AndroidDependencyDetector.forbiddenImportsIn(source),
            )
        }

        @Test
        fun `ignores commented out imports`() {
            val source =
                """
                // import android.os.Bundle
                /* import androidx.core.Foo */
                import java.time.Duration
                """.trimIndent()

            assertEquals(emptyList<String>(), AndroidDependencyDetector.forbiddenImportsIn(source))
        }

        @Test
        fun `ignores an unrelated package that merely contains the word android`() {
            val source =
                """
                package com.gymtracker.core.domain

                import com.androidessentials.Widget
                import org.jetbrains.annotations.NotNull

                class Session
                """.trimIndent()

            assertEquals(emptyList<String>(), AndroidDependencyDetector.forbiddenImportsIn(source))
        }

        @Test
        fun `returns nothing for a pure domain file`() {
            val source =
                """
                package com.gymtracker.core.domain

                import java.time.Instant
                import kotlin.time.Duration

                data class WorkoutSet(val reps: Int, val startedAt: Instant)
                """.trimIndent()

            assertEquals(emptyList<String>(), AndroidDependencyDetector.forbiddenImportsIn(source))
        }
    }
}
