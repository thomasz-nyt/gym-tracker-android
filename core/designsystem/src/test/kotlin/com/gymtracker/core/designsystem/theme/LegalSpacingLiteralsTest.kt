package com.gymtracker.core.designsystem.theme

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * ADR-0044, Turn 5's `01-insets-and-spacing.md`: vertical space in the feature modules' main
 * source tree comes from four values (4, 12, 20, 32) and row height from four more (44, 56, 64,
 * 80) — the entire legal vocabulary. This test is [NoTextWithoutMaxLinesTest]'s pattern applied
 * to that rule: a source-tree walk, no Detekt plugin module needed for one check.
 *
 * Scoped to the two categories the file's own prose names — a `.height(...)` value, a vertical
 * `Arrangement.spacedBy(...)`, and vertical padding (`top`/`bottom`/`vertical`, or a bare
 * `.padding(N.dp)` which applies to every side) — not every `.dp` literal in the module. Icon
 * sizes, stroke widths, and horizontal-only padding sit outside what the doc calls "vertical
 * space" and "row height"; see ADR-0044's Decision for why the gate table's broader assertion
 * 1.5 wording is not followed literally. A named `GymDimens` token is exempt by construction —
 * this only matches a raw numeric `.dp` literal, never an identifier.
 *
 * No allowlist: every one of these call sites already reads a `GymDimens` token (`GymDimens.kt`'s
 * own class doc predates this rule), so unlike [NoTextWithoutMaxLinesTest] this starts, and is
 * meant to stay, at zero.
 */
class LegalSpacingLiteralsTest {
    @Test
    fun `every vertical spacing or row-height dp literal in feature main sources is in the legal set`() {
        val violations =
            featureMainSourceFiles().flatMap { file ->
                illegalSpacingLiterals(file.readText()).map { "${file.path}: $it" }
            }

        assertTrue(
            violations.isEmpty(),
            "Vertical spacing / row-height .dp literal(s) outside {2, 4, 12, 20, 32, 44, 56, 64, 80}:\n" +
                violations.joinToString("\n"),
        )
    }

    /**
     * `00-gate.md` assertion 1.6: "lint rule exists and fails on a deliberately-added 17.dp."
     * Exercises the detector directly against synthetic source, independent of the real tree —
     * this is the test that proves the regex actually catches something, not just that the real
     * tree happens to be clean today.
     */
    @Test
    fun `a deliberately illegal height literal is caught`() {
        val violations = illegalSpacingLiterals("Box(modifier = Modifier.height(17.dp))")
        assertTrue(violations.isNotEmpty(), "Expected 17.dp to be flagged as illegal")
    }

    @Test
    fun `a deliberately illegal vertical spacedBy literal is caught`() {
        val violations =
            illegalSpacingLiterals("Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {}")
        assertTrue(violations.isNotEmpty(), "Expected 17.dp to be flagged as illegal")
    }

    @Test
    fun `a deliberately illegal vertical padding literal is caught`() {
        assertTrue(illegalSpacingLiterals("Modifier.padding(top = 17.dp)").isNotEmpty())
        assertTrue(illegalSpacingLiterals("Modifier.padding(17.dp)").isNotEmpty())
    }

    @Test
    fun `legal values on the same call shapes are not flagged`() {
        assertTrue(illegalSpacingLiterals("Box(modifier = Modifier.height(56.dp))").isEmpty())
        assertTrue(
            illegalSpacingLiterals("Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {}").isEmpty(),
        )
        assertTrue(illegalSpacingLiterals("Modifier.padding(vertical = 20.dp)").isEmpty())
    }

    @Test
    fun `a named GymDimens token is exempt regardless of its value`() {
        // Not a digit literal, so the regex never matches it in the first place — this pins that
        // behaviour rather than assuming it.
        assertTrue(illegalSpacingLiterals("Box(modifier = Modifier.height(GymDimens.PhotoHeight))").isEmpty())
    }

    @Test
    fun `horizontal-only spacing and icon sizes are out of scope, by design`() {
        // ADR-0044's Decision: this rule is scoped to vertical space and row height, the two
        // categories file 01 itself names — not assertion 1.5's broader literal wording. An
        // illegal icon size or horizontal padding value is a real gap this rule does not cover,
        // deliberately, not a false negative in what it does cover.
        assertTrue(illegalSpacingLiterals("Icon(modifier = Modifier.size(17.dp))").isEmpty())
        assertTrue(
            illegalSpacingLiterals("horizontalArrangement = Arrangement.spacedBy(17.dp)").isEmpty(),
        )
        assertTrue(illegalSpacingLiterals("Modifier.padding(horizontal = 17.dp)").isEmpty())
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

    private fun illegalSpacingLiterals(source: String): List<String> {
        val violations = mutableListOf<String>()
        for (pattern in SPACING_PATTERNS) {
            for (match in pattern.findAll(source)) {
                val value = match.groupValues[1].toDouble()
                if (value !in LEGAL_VALUES) {
                    val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                    violations += "line $line: ${match.groupValues[1]}.dp"
                }
            }
        }
        return violations
    }

    companion object {
        private val LEGAL_VALUES = setOf(2.0, 4.0, 12.0, 20.0, 32.0, 44.0, 56.0, 64.0, 80.0)

        private const val DP_LITERAL = "(\\d+(?:\\.\\d+)?)\\.dp"

        private val SPACING_PATTERNS =
            listOf(
                // .height(N.dp) — a fixed row/element height. The lookahead excludes .heightIn(
                // (a floor, not a fixed row height the design pins), not preceded-by-word-char —
                // ".height(" is itself always preceded by a receiver like "Modifier".
                Regex("\\.height(?!In)\\($DP_LITERAL\\)"),
                // A vertical gap between elements.
                Regex("verticalArrangement\\s*=\\s*Arrangement\\.spacedBy\\($DP_LITERAL\\)"),
                // Vertical padding, named or the bare all-sides form.
                Regex("padding\\(\\s*(?:top|bottom|vertical)\\s*=\\s*$DP_LITERAL"),
                Regex("\\.padding\\($DP_LITERAL\\)"),
            )
    }
}
