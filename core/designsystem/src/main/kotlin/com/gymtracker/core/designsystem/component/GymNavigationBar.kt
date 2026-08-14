package com.gymtracker.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gymtracker.core.designsystem.theme.GymDimens

/**
 * The app's bottom navigation, built from primitives rather than `NavigationBar` (ADR-0030).
 *
 * `NavigationBarItem`'s selected-item indicator reads a fixed `CornerFull` token and exposes no
 * `shape` parameter to override it — confirmed against the compiled `material3-api.jar`, not
 * assumed. `Shapes()`'s five roles cannot reach it, so a mono, zero-radius system cannot restyle
 * that component; it can only replace it. This is the one deliberate exception `Redesign.dc.html`
 * names to its own "Material 3 + Compose components only" rule.
 *
 * Selected/unselected colour reuses existing, already-contrast-gated tokens rather than adding
 * one: `primaryContainer`/`primary` for the selected cell (the design's literal `#FFE0D9` /
 * `#AE1800`), `outline` for unselected content (the design's literal `#605D5D` — an exact match,
 * not an approximation). The structural rule above the bar is solid `onSurface` at
 * [GymDimens.StructuralRuleThickness], the same convention ADR-0029 uses under the session
 * header.
 */
@Composable
fun GymNavigationBar(
    items: List<GymNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = GymDimens.StructuralRuleThickness,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(GymDimens.NavigationBarHeight),
        ) {
            items.forEachIndexed { index, item ->
                GymNavigationBarItem(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GymNavigationBarItem(
    item: GymNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .let {
                    if (selected) {
                        it.background(MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        it
                    }
                }.selectable(selected = selected, onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CELL_GAP, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(item.icon),
            // Decorative: the label below carries the accessible name, exactly as GymBottomBar's
            // text-only tabs did before this replaced it.
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(GymDimens.NavigationBarIconSize),
        )
        Text(
            text = item.label,
            // labelSmall's 12sp/16sp is the design's own size; weight and tracking are
            // overridden locally to ExtraBold/0.06em to match the frame exactly, the same
            // pattern GymButtons.kt's ButtonLabel uses on top of a shared role rather than
            // adding a new one for a single component's letter-spacing.
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.06.em,
                ),
            color = contentColor,
        )
    }
}

private val CELL_GAP = 5.dp
