package com.gymtracker.core.designsystem.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ADR-0011's Turn 4 amendment: the ten roles from `Redesign.dc.html`, frame `4f`, each carrying
 * its own `maxLines` so a call site cannot forget one the way 205 raw `Text()` calls did before
 * this existed. A product decision, pinned the same way [GymTypographyTest] pins the original
 * scale — this is additive to it, not a replacement (see the amendment's "An additive scale, not
 * a value change"), so nothing here duplicates or contradicts that file's assertions.
 */
class GymTextRoleTest {
    @Test
    fun `every role is set in Archivo`() {
        allRoles().forEach { (name, role) ->
            assertEquals(ArchivoFamily, role.style.fontFamily, "$name is not set in Archivo")
        }
    }

    @Test
    fun `every role declares a maxLines a call site cannot forget`() {
        allRoles().forEach { (name, role) ->
            assertTrue(role.maxLines >= 1, "$name has no line ceiling")
        }
    }

    @Test
    fun `display timer is 88sp, ExtraBold, one line, clipped rather than ellipsised`() {
        val role = GymTextRoles.DisplayTimer
        assertEquals(88.sp, role.style.fontSize)
        assertEquals(FontWeight.ExtraBold, role.style.fontWeight)
        assertEquals(1, role.maxLines)
        // A countdown never has room to overflow in practice, but if it somehow did, an
        // ellipsis mid-digit reads as broken in a way a clipped tail does not.
        assertEquals(TextOverflow.Clip, role.overflow)
    }

    @Test
    fun `numeral roles are digits-only, ExtraBold, one line`() {
        assertEquals(34.sp, GymTextRoles.NumeralLg.style.fontSize)
        assertEquals(24.sp, GymTextRoles.NumeralMd.style.fontSize)
        listOf(GymTextRoles.NumeralLg, GymTextRoles.NumeralMd).forEach { role ->
            assertEquals(FontWeight.ExtraBold, role.style.fontWeight)
            assertEquals(1, role.maxLines)
            assertEquals(TextOverflow.Clip, role.overflow)
        }
    }

    @Test
    fun `title roles are two lines, ExtraBold`() {
        assertEquals(22.sp, GymTextRoles.TitleLg.style.fontSize)
        assertEquals(17.sp, GymTextRoles.TitleMd.style.fontSize)
        listOf(GymTextRoles.TitleLg, GymTextRoles.TitleMd).forEach { role ->
            assertEquals(FontWeight.ExtraBold, role.style.fontWeight)
            assertEquals(2, role.maxLines)
            assertEquals(TextOverflow.Ellipsis, role.overflow)
        }
    }

    @Test
    fun `word unit is 20sp, ExtraBold, one line, clipped like the numerals it stands beside`() {
        val role = GymTextRoles.WordUnit
        assertEquals(20.sp, role.style.fontSize)
        assertEquals(FontWeight.ExtraBold, role.style.fontWeight)
        assertEquals(1, role.maxLines)
        assertEquals(TextOverflow.Clip, role.overflow)
    }

    @Test
    fun `body is 15sp Medium, two lines`() {
        val role = GymTextRoles.Body
        assertEquals(15.sp, role.style.fontSize)
        assertEquals(FontWeight.Medium, role.style.fontWeight)
        assertEquals(2, role.maxLines)
    }

    @Test
    fun `meta is 13sp SemiBold, one line`() {
        val role = GymTextRoles.Meta
        assertEquals(13.sp, role.style.fontSize)
        assertEquals(FontWeight.SemiBold, role.style.fontWeight)
        assertEquals(1, role.maxLines)
    }

    @Test
    fun `label caps is 12sp Bold with tracking, one line`() {
        val role = GymTextRoles.LabelCaps
        assertEquals(12.sp, role.style.fontSize)
        assertEquals(FontWeight.Bold, role.style.fontWeight)
        assertEquals(0.12.em, role.style.letterSpacing)
        assertEquals(1, role.maxLines)
    }

    @Test
    fun `tag caps is 11sp ExtraBold with tracking, one line — the floor, nothing smaller ships`() {
        val role = GymTextRoles.TagCaps
        assertEquals(11.sp, role.style.fontSize)
        assertEquals(FontWeight.ExtraBold, role.style.fontWeight)
        assertEquals(0.06.em, role.style.letterSpacing)
        assertEquals(1, role.maxLines)
        allRoles().forEach { (name, role2) ->
            assertTrue(role2.style.fontSize.value >= role.style.fontSize.value, "$name is smaller than the floor")
        }
    }

    @Test
    fun `line height never clips the text it holds, except display timer's deliberately tight set`() {
        // display.timer is the one role the design draws tighter than its own font size — a
        // huge tabular countdown, set the way the redesign's own display type is throughout
        // Redesign.dc.html (its H1 uses a sub-1 line-height ratio the same way). Every other
        // role keeps GymTypographyTest's usual "line height >= font size" rule.
        allRoles().forEach { (name, role) ->
            if (name == "DisplayTimer") return@forEach
            assertTrue(
                role.style.lineHeight.value >= role.style.fontSize.value,
                "$name would clip: ${role.style.fontSize} text in a ${role.style.lineHeight} line",
            )
        }
    }

    private fun allRoles(): Map<String, GymTextRole> =
        mapOf(
            "DisplayTimer" to GymTextRoles.DisplayTimer,
            "NumeralLg" to GymTextRoles.NumeralLg,
            "NumeralMd" to GymTextRoles.NumeralMd,
            "TitleLg" to GymTextRoles.TitleLg,
            "TitleMd" to GymTextRoles.TitleMd,
            "WordUnit" to GymTextRoles.WordUnit,
            "Body" to GymTextRoles.Body,
            "Meta" to GymTextRoles.Meta,
            "LabelCaps" to GymTextRoles.LabelCaps,
            "TagCaps" to GymTextRoles.TagCaps,
        )
}
