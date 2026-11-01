package io.github.eslamasabry.oc.ui.kitchat.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor

/** Row tint strength for added and removed lines: a low wash, so the sign column, not colour, carries the meaning. */
private const val TINT_ALPHA = 0.12f

/** U+2212 MINUS SIGN, as the Flutter rows draw it (kit_diff_rows.dart:112). */
private const val MINUS = "−"

/**
 * The inline diff of one file in a tool step (plan 09 §9.3 and §9.5): a plain [Column] of rows, not lazy, cut at
 * [maxLines] lines (fold lines and hunk headers do not count, as in the Flutter view). Always left to right, mono type,
 * an old/new line-number gutter, a `+`/`−` sign column next to the low tint (never colour alone), and lines that do
 * not wrap: the whole block scrolls sideways. Every line is displayed through [LocalKitRedactor].
 *
 * No words of its own: [hunkLabel] words a hunk header from its line ranges (e.g. "Lines 12–18"), [gapLabel] a fold
 * line from the number of unchanged lines it hides.
 */
@Composable
public fun KitDiffView(
    file: DiffFile,
    maxLines: Int = 12,
    hunkLabel: (oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int) -> String,
    gapLabel: (hidden: Int) -> String,
    modifier: Modifier = Modifier,
) {
    val rows = remember(file, maxLines) { DiffModel.displayRows(file, maxLines).rows }
    val colors = LocalKitColors.current
    val mono = LocalKitTypography.current.style(TypeRole.Mono)
    val redactor = LocalKitRedactor.current
    val digits = file.maxLineNumber.toString().length
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier) {
            // Rows share one width, at least the box's, so tints run edge to edge while the block scrolls sideways.
            val least = if (constraints.hasBoundedWidth) maxWidth else 0.dp
            Column(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .widthIn(min = least)
                    .width(IntrinsicSize.Max),
            ) {
                for (row in rows) {
                    key(row.segment) {
                        when (row) {
                            is DiffRow.Line -> LineRow(row.line, digits, mono, colors, redactor::redact)
                            is DiffRow.HunkHeader -> LabelRow(
                                hunkLabel(row.oldStart, row.oldEnd, row.newStart, row.newEnd),
                                digits,
                                mono.copy(color = colors.text2),
                                background = null,
                            )
                            is DiffRow.Gap -> LabelRow(
                                gapLabel(row.hidden),
                                digits,
                                mono.copy(color = colors.text3),
                                background = colors.surface2,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LineRow(line: DiffLine, digits: Int, mono: TextStyle, colors: KitColors, redact: (String) -> String) {
    val (tint, sign, signColor) = when (line.kind) {
        DiffLineKind.Added -> Triple(colors.success.copy(alpha = TINT_ALPHA), "+", colors.success)
        DiffLineKind.Removed -> Triple(colors.attention.copy(alpha = TINT_ALPHA), MINUS, colors.attention)
        else -> Triple(Color.Transparent, " ", colors.text3)
    }
    val old = if (line.kind == DiffLineKind.Added) null else line.oldNo
    val new = if (line.kind == DiffLineKind.Removed) null else line.newNo
    Row(Modifier.fillMaxWidth().background(tint)) {
        Gutter(old?.toString().orEmpty(), new?.toString().orEmpty(), sign, digits, mono, colors.text3, signColor)
        BasicText(
            text = redact(line.text),
            style = mono.copy(color = colors.text1),
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.padding(end = Space.S2.dp),
        )
    }
}

/** A hunk header or fold line: its words start at the text column, after an empty gutter. */
@Composable
private fun LabelRow(label: String, digits: Int, style: TextStyle, background: Color?) {
    var m = Modifier.fillMaxWidth()
    if (background != null) m = m.background(background)
    Row(m.padding(vertical = Space.S1.dp)) {
        Row(Modifier.clearAndSetSemantics {}) { Gutter("", "", " ", digits, style, style.color, style.color) }
        BasicText(text = label, style = style, softWrap = false, maxLines = 1, modifier = Modifier.padding(end = Space.S2.dp))
    }
}

/** Old number, new number, sign: mono cells padded to the widest number, so every row's text starts at one column. */
@Composable
private fun Gutter(
    old: String,
    new: String,
    sign: String,
    digits: Int,
    mono: TextStyle,
    numberColor: Color,
    signColor: Color,
) {
    val numbers = mono.copy(color = numberColor)
    val cell = Modifier.padding(start = Space.S2.dp)
    BasicText(old.padStart(digits), style = numbers, maxLines = 1, softWrap = false, modifier = cell)
    BasicText(new.padStart(digits), style = numbers, maxLines = 1, softWrap = false, modifier = cell)
    BasicText(sign, style = mono.copy(color = signColor), maxLines = 1, modifier = Modifier.padding(horizontal = Space.S2.dp))
}
