package com.gymtracker.core.designsystem.theme

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * ADR-0011's Turn 4 amendment: cause 2 of the wrapping bug was that nothing in the app declared
 * `maxLines`, so Compose wrapped forever instead of truncating in place — 205 raw `Text()` calls
 * in the feature modules, none of them passing it. [GymText] exists so a call site names a
 * [GymTextRole][com.gymtracker.core.designsystem.theme.GymTextRole] and gets the ceiling for
 * free, but nothing stops a new screen from reaching for `Text()` directly instead. This test is
 * that stop: it walks every feature module's `main` source tree and fails on a raw `Text(` call
 * that passes no `maxLines` argument, unless the file is named in [ALLOWLIST].
 *
 * The allowlist is deliberately not a blanket exemption — it names files one at a time, so it
 * shrinks visibly in a diff as each screen migrates to [GymText] (ADR-0011's amendment, section
 * 2), rather than silently covering whatever is left. A file drops off it only when every raw
 * `Text(` call it makes either passes `maxLines` or has been replaced by `GymText`/`NumeralText`
 * (both of which already carry their own `maxLines`, so a call to either never matches the raw
 * `Text(` this check looks for in the first place).
 *
 * This is a plain JVM test — no Robolectric, no Compose runtime, just a source-tree walk and a
 * bracket-matched regex — so it runs under `testDebugUnitTest` like every other test in this
 * module, and it needs no new dependency (the same reasoning [GymTypographyTest] gives for why
 * this module needs no Robolectric).
 */
class NoTextWithoutMaxLinesTest {
    @Test
    fun `every raw Text call in a feature module either passes maxLines or is in the allowlist`() {
        val violations =
            featureMainSourceFiles()
                .filterNot { it.name in ALLOWLIST }
                .flatMap { file -> rawTextCallsMissingMaxLines(file.readText()).map { "${file.path}: $it" } }

        assertTrue(
            violations.isEmpty(),
            "Text( call(s) with no maxLines, outside the allowlist:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `the allowlist only names files that still need it`() {
        // The inverse check: a file NOT calling a raw Text( at all (already fully migrated to
        // GymText/NumeralText, or never called Text() to begin with) has no reason to sit on the
        // allowlist — keeping it there would let a future regression in that exact file hide
        // behind a name that no longer needs to be on the list.
        val stillNeeded =
            featureMainSourceFiles()
                .filter { it.name in ALLOWLIST }
                .filter { rawTextCallsMissingMaxLines(it.readText()).isNotEmpty() }
                .map { it.name }
                .toSet()

        val stale = ALLOWLIST - stillNeeded
        assertTrue(stale.isEmpty(), "Allowlist entries with nothing left to allow: $stale")
    }

    private fun featureMainSourceFiles(): List<File> {
        val featureRoot = File(repoRoot(), "feature")
        return featureRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { "${File.separator}src${File.separator}main${File.separator}" in it.path }
            .toList()
    }

    /** Walks up from the working directory to the checkout root, marked by `settings.gradle.kts`. */
    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("settings.gradle.kts not found above ${File(".").absolutePath}")
        }
        return dir
    }

    /**
     * Every call to `Text(` — the negative lookbehind excludes `NumeralText(`/`GymText(`, which
     * are different functions and carry their own `maxLines` — whose argument list, matched by
     * counting parenthesis depth from the call's own opening paren, contains no `maxLines` token.
     */
    private fun rawTextCallsMissingMaxLines(source: String): List<String> {
        val callStart = Regex("(?<![A-Za-z0-9_])Text\\(")
        val matches =
            callStart.findAll(source).mapNotNull { match ->
                val argsEnd = matchingParenIndex(source, match.range.last)
                val args = source.substring(match.range.last, argsEnd + 1)
                if (Regex("\\bmaxLines\\s*=").containsMatchIn(args)) {
                    null
                } else {
                    val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                    "line $line"
                }
            }
        return matches.toList()
    }

    /** [openParenIndex] must index the `(` itself. Returns the index of its matching `)`. */
    private fun matchingParenIndex(
        source: String,
        openParenIndex: Int,
    ): Int {
        var depth = 0
        for (i in openParenIndex until source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return source.length - 1
    }

    companion object {
        /**
         * Files still calling `Text(` without `maxLines` somewhere in a feature module's `main`
         * source tree, as of ADR-0011's Turn 4 amendment. Remove a name once its screen migrates
         * to [GymText]
         * (ADR-0011's amendment, section 2) and every remaining raw `Text(` call in it passes
         * `maxLines` explicitly.
         */
        private val ALLOWLIST =
            setOf(
                "BrowseScreen.kt",
                "ExerciseDetailScreen.kt",
                "FinishSummaryScreen.kt",
                "GuidedExerciseScreen.kt",
                "HistoryScreen.kt",
                "SetSheets.kt",
                "WorkoutDetailScreen.kt",
                "RestPanel.kt",
                "SessionAlerts.kt",
                "SessionMovements.kt",
                "SessionScaffold.kt",
                "SessionUndoBars.kt",
                "ExerciseProgressScreen.kt",
                "WeeklyVolumeScreen.kt",
                "RoutineEditorScreen.kt",
                "RoutinesScreen.kt",
                "SettingsScreen.kt",
            )
    }
}
