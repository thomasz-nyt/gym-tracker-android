package com.gymtracker.core.designsystem.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * A line that mixes digits and words — "100 lb x 8", "52m x 18 sets" — with every run of
 * digits bumped to [FontWeight.ExtraBold] (ADR-0019: "Numbers carry weight 800 so a load reads
 * across a gym floor").
 *
 * A [Typography][androidx.compose.material3.Typography] role cannot carry this the way
 * ADR-0011's sizes can, because the weight applies to a substring, not a whole line. This
 * composable is the mechanism; [boldDigits] is the part of it that is plain data and testable
 * without a Compose UI test runner.
 */
@Composable
fun NumeralText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = boldDigits(text),
        modifier = modifier,
        color = color,
        style = style,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
    )
}

/**
 * Wraps every maximal run of ASCII digits in [text] with an [FontWeight.ExtraBold] span,
 * leaving everything else — and the plain [AnnotatedString.text] itself — untouched.
 *
 * Kept separate from [NumeralText] so it can be unit-tested as data: the instrumented suite's
 * `onNodeWithText` matches against the plain string a `Text` composable was given, and adding a
 * span style never changes that string, only how it draws.
 */
fun boldDigits(text: String): AnnotatedString =
    AnnotatedString
        .Builder(text)
        .apply {
            var runStart = -1
            for (index in text.indices) {
                val isDigit = text[index].isDigit()
                if (isDigit && runStart == -1) {
                    runStart = index
                } else if (!isDigit && runStart != -1) {
                    addStyle(SpanStyle(fontWeight = FontWeight.ExtraBold), runStart, index)
                    runStart = -1
                }
            }
            if (runStart != -1) {
                addStyle(SpanStyle(fontWeight = FontWeight.ExtraBold), runStart, text.length)
            }
        }.toAnnotatedString()
