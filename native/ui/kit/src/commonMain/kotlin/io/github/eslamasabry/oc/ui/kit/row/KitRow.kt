package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitInlineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.motion.kitGrowSource
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** The supporting lines a row allows (Dart A11Y-8): 1 at normal size, 2 from 1.3×, 3 from 2.0×. */
public fun kitRowSupportingLines(textScale: Float, asked: Int = 1): Int =
    when {
        textScale >= 2.0f -> maxOf(asked, 3)
        TextScalePolicy.isLargeText(textScale) -> maxOf(asked, 2)
        else -> asked
    }

/** A row title: one line below 1.3×, two from 1.3× (the full value stays in semantics). */
public fun kitRowTitleLines(textScale: Float, asked: Int = 1): Int = if (TextScalePolicy.isLargeText(textScale)) maxOf(asked, 2) else asked

/**
 * A file name that wraps only after `_`, `-` or before the extension's dot, never mid-word (Dart `kitBreakableFileName`).
 * The break chances are zero-width spaces; semantics read the plain name.
 */
public fun kitBreakableFileName(name: String): String {
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: -1
    val out = StringBuilder()
    name.forEachIndexed { i, c ->
        if (i == dot) out.append('​')
        out.append(c)
        if (c == '_' || c == '-') out.append('​')
    }
    return out.toString()
}

/**
 * The one list row (Dart `KitRow`; plan 06 §16.2): what it is, one supporting line, and its value, chevron or one act.
 * 56 dp (one line) or 64 dp (two lines), growing with text; the title wraps from 1.3× and the supporting line takes 2
 * lines from 1.3× and 3 from 2.0×. A disabled row says why: [disabledReason] replaces the supporting line and is told
 * to TalkBack once; title and glyph go to `text3` (never through alpha). [selected] is the row shown in the detail
 * pane. [server] prefixes the supporting line ("laptop · …"). A long-press opens the row's menu (S1-KITA2's
 * `KitMenu`); every menu item must also be reachable another way.
 *
 * States: default, two-line, disabled, selected
 * (A destructive row goes in `KitRowGroup`'s destructive slot, last; its words name the act — no red, LOOK-5.)
 * Text: reading
 * Cap: List
 * Motion: pressed fill ≥ PRESS_HOLD (KitTappable); with [growKey] the opened page grows out of the row (Full only)
 * Hidden: long-press when [onLongClick] is set (the caller registers its tip and its visible twin)
 * Replaces: KitRow (lib/ui/kit/kit_row.dart)
 */
@Composable
public fun KitRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: KitRowLeading? = null,
    trailing: KitRowTrailing = KitRowTrailing.None,
    server: String? = null,
    titleIsFileName: Boolean = false,
    supportingMaxLines: Int = 1,
    enabled: Boolean = true,
    disabledReason: String? = null,
    selected: Boolean = false,
    growKey: Any? = null,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    kitAssert(title.isNotBlank()) { "KitRow: a row needs a title" }
    kitAssert(enabled || !disabledReason.isNullOrBlank()) { "KitRow: a disabled row must say why (B-KIT-051)" }
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val scale = kitTextScale()
    val large = TextScalePolicy.isLargeText(scale)
    val supportLine =
        when {
            !enabled -> disabledReason
            server != null && supporting != null -> "$server · $supporting"
            server != null -> server
            else -> supporting
        }
    val value = trailing as? KitRowTrailing.Value
    val action = (trailing as? KitRowTrailing.Action)?.action?.let {
        if (enabled) it else it.copy(enabled = false, disabledReason = disabledReason, onClick = {})
    }
    val actionBelow = action != null && large
    val tappable = onClick != null || onLongClick != null
    val base =
        modifier
            .kitMaxWidth(CapRole.List)
            .fillMaxWidth()
            .kitGrowSource(growKey)
            .then(
                if (tappable) {
                    Modifier.kitTappable(
                        onClick = onClick ?: {},
                        enabled = enabled,
                        disabledReason = disabledReason,
                        role = KitTappableRole.Row,
                        onClickLabel = onClickLabel,
                        onLongClick = onLongClick,
                        onLongClickLabel = onLongClickLabel,
                        shape = KitShapeId.Square,
                    )
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                if (selected) this.selected = true
                if (titleIsFileName) contentDescription = title
            }
            .then(if (selected) Modifier.background(colors.surface3) else Modifier)
            .heightIn(min = (if (supportLine != null) tokens.rowHeightTwo else tokens.rowHeightOne).dp)
            .padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp)
            .testTag("kit-row")
    Row(base, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        leading?.let { RowLeadingTile(it, dim = !enabled) }
        BoxWithConstraints(Modifier.weight(1f)) {
            val measurer = rememberTextMeasurer()
            val typography = LocalKitTypography.current
            val density = LocalDensity.current
            // Budget the actual title, value, gap and chevron after the leading tile has been measured.
            val valueBelow = value != null && (large || run {
                val titleWidth = measurer.measure(
                    title, style = typography.style(TypeRole.RowTitle).copy(textDirection = TextDirection.Content),
                    softWrap = false, maxLines = 1,
                ).size.width
                val valueWidth = measurer.measure(
                    value.text, style = typography.style(TypeRole.Secondary).copy(textDirection = TextDirection.Content),
                    softWrap = false, maxLines = 1,
                ).size.width
                val chevron = if (value.chevron) {
                    (Space.S1 + tokens.smallIcon * minOf(scale, TextScalePolicy.FRAME_CAP)).dp
                } else 0.dp
                val gapsAndChevron = with(density) { (Space.S3.dp + chevron).roundToPx() }
                titleWidth.toLong() + valueWidth + gapsAndChevron > constraints.maxWidth
            })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    KitText(
                        if (titleIsFileName) kitBreakableFileName(title) else title,
                        Modifier.testTag("kit-row-title"),
                        role = TypeRole.RowTitle,
                        tone = if (enabled) TextTone.Primary else TextTone.Tertiary,
                        maxLines = if (titleIsFileName && large) Int.MAX_VALUE else kitRowTitleLines(scale),
                    )
                    supportLine?.let {
                        KitText(
                            it,
                            Modifier.testTag("kit-row-supporting"),
                            role = TypeRole.Secondary,
                            tone = TextTone.Secondary,
                            maxLines = if (!enabled) Int.MAX_VALUE else kitRowSupportingLines(scale, supportingMaxLines),
                        )
                    }
                    if (valueBelow) RowValue(value)
                    if (actionBelow) KitInlineAction(action)
                }
                when {
                    value != null && !valueBelow -> RowValue(value)
                    action != null && !actionBelow -> KitInlineAction(action)
                    trailing == KitRowTrailing.Chevron -> RowChevron()
                    else -> Unit
                }
            }
        }
    }
}
