package com.gymtracker.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gymtracker.core.designsystem.theme.GymDimens

/**
 * A number with a big [−] and [+] either side of it (ADR-0016).
 *
 * The common edit between sets is small — one plate up, one rep down — and it used to cost a
 * keyboard. The field stays editable underneath, so a jump from 60 to 100 is still a typed
 * number rather than sixteen taps.
 *
 * @param onStep called with -1 or +1. What a step *means* — 2.5 kg, 5 lb, one rep — belongs to
 *   the caller's domain logic, not to a widget.
 */
@Composable
fun StepperField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Number,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap)) {
        Text(label, style = MaterialTheme.typography.titleSmall)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(glyph = "−", description = "Decrease $label", onClick = { onStep(-1) })

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder?.let { { Text(it) } },
                singleLine = true,
                textStyle =
                    MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.weight(1f),
            )

            StepButton(glyph = "+", description = "Increase $label", onClick = { onStep(1) })
        }

        supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

/**
 * A step button. The glyph is drawn as text rather than an icon so this needs no icon artifact
 * — a dependency would need an ADR (constitution §7) to draw a plus sign.
 */
@Composable
private fun StepButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        // FilledTonalButton reads CornerFull rather than the shape scale (Shape.kt's
        // documented trap), so without this it stays a stadium regardless of GymShapes.
        shape = MaterialTheme.shapes.large,
        modifier =
            Modifier
                .size(GymDimens.StepperTarget)
                .semantics { contentDescription = description },
    ) {
        Text(glyph, style = MaterialTheme.typography.titleLarge)
    }
}
