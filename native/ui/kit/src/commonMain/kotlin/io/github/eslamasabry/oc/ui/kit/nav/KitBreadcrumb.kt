package io.github.eslamasabry.oc.ui.kit.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val ELLIPSIS = "…" // F: lib/ui/kit/kit_breadcrumb.dart:21 (the one ellipsis, not a number)

/**
 * The folder trail (Dart `KitBreadcrumb`, kit_breadcrumb.dart): the root, the folders below it and the current folder
 * last, each ancestor one tap back. When the trail does not fit, the middle folders fold into one "…" crumb that opens
 * a menu of them. [onSelected] gets -1 for the root and i for `segments[i]`, never the current folder.
 *
 * Text: chrome. Cap: list (960).
 */
@Composable
public fun KitBreadcrumb(
    rootLabel: String,
    segments: List<String>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trailLabel = stringResource(Res.string.kitBreadcrumb)
    BoxWithConstraints(
        modifier.kitMaxWidth(CapRole.List).semantics { contentDescription = trailLabel },
    ) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val typography = LocalKitTypography.current
        val plain = typography.style(TypeRole.Secondary)
        val semibold = plain.copy(fontWeight = FontWeight.SemiBold) // F: lib/ui/kit/kit_breadcrumb.dart:310
        fun natural(value: String, style: androidx.compose.ui.text.TextStyle): Float =
            kotlin.math.ceil(measurer.measure(KitBidi.auto(value), style, softWrap = false, maxLines = 1).size.width.toFloat() / density.density)

        val minTarget = FlutterKitTokens.MIN_TARGET.toFloat()
        val padding = Space.S2 * 2f // F: lib/ui/kit/kit_breadcrumb.dart:94
        val separatorWidth = KitIconSize.Small.dp + Space.S1 * 2f // F: lib/ui/kit/kit_breadcrumb.dart:95
        val maxWidth = constraints.maxWidth / density.density
        fun nameOf(i: Int) = if (i < 0) rootLabel else segments[i]
        fun crumbWidth(text: Float) = maxOf(minTarget, text + padding)

        if (segments.isEmpty()) {
            Trail { Current(rootLabel, semibold, Modifier.weight(1f, fill = false)) }
            return@BoxWithConstraints
        }

        val count = segments.size
        val parentIndex = count - 2 // F: lib/ui/kit/kit_breadcrumb.dart:113
        val middle = if (count >= 3) count - 2 else 0 // F: lib/ui/kit/kit_breadcrumb.dart:115
        val naturalText = (-1..parentIndex).associateWith { minOf(natural(nameOf(it), plain), FlutterKitTokens.CRUMB_MAX_WIDTH.toFloat()) }
        val moreWidth = crumbWidth(natural(ELLIPSIS, plain))
        val currentNatural = natural(segments.last(), semibold) + padding

        fun shownFor(hidden: Int) = listOf(-1) + (hidden until middle).toList() + (if (parentIndex >= 0) listOf(parentIndex) else emptyList())
        fun fixedWidth(shown: List<Int>, hidden: Int, text: Map<Int, Float>): Float {
            val crumbs = shown.size + (if (hidden > 0) 1 else 0)
            return separatorWidth * crumbs + shown.sumOf { crumbWidth(text.getValue(it)).toDouble() }.toFloat() + (if (hidden > 0) moreWidth else 0f)
        }

        var hidden = middle
        if (maxWidth.isFinite()) {
            for (h in 0..middle) {
                if (fixedWidth(shownFor(h), h, naturalText) + currentNatural <= maxWidth) { hidden = h; break }
            }
        } else hidden = 0
        val shown = shownFor(hidden)

        val textWidth = naturalText.toMutableMap()
        val minCurrent = minOf(currentNatural, minTarget * 2f) // F: lib/ui/kit/kit_breadcrumb.dart:159
        if (maxWidth.isFinite() && fixedWidth(shown, hidden, textWidth) + minCurrent > maxWidth) {
            val crumbs = shown.size + (if (hidden > 0) 1 else 0)
            val budget = maxWidth - minCurrent - separatorWidth * crumbs - (if (hidden > 0) moreWidth else 0f)
            val each = maxOf(minTarget - padding, kotlin.math.floor(budget / shown.size - padding))
            for (i in shown) textWidth[i] = minOf(textWidth.getValue(i), each)
        }

        Trail {
            var first = true
            fun sep() { if (!first) Separator(); first = false }
            for (i in shown) {
                sep()
                Ancestor(nameOf(i), i < 0, textWidth.getValue(i), plain, measurer) { onSelected(i) }
                if (i < 0 && hidden > 0) {
                    sep()
                    MoreCrumb((0 until hidden).toList(), segments, onSelected)
                }
            }
            sep()
            Current(segments.last(), semibold, Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun Trail(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.heightIn(min = FlutterKitTokens.MIN_TARGET.dp), // F: lib/ui/kit/kit_breadcrumb.dart:235
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun Separator() {
    KitIcon(
        KitIcons.chevronRight,
        Modifier.padding(horizontal = Space.S1.dp).clearAndSetSemantics { },
        size = KitIconSize.Small,
        tone = TextTone.Tertiary,
    )
}

@Composable
private fun Ancestor(
    name: String,
    isRoot: Boolean,
    textWidth: Float,
    style: androidx.compose.ui.text.TextStyle,
    measurer: androidx.compose.ui.text.TextMeasurer,
    onClick: () -> Unit,
) {
    val label = if (isRoot) stringResource(Res.string.kitBreadcrumbOpenRoot, name) else stringResource(Res.string.kitBreadcrumbOpen, name)
    val display = middleCut(name, textWidth, LocalDensity.current.density) {
        measurer.measure(KitBidi.auto(it), style, softWrap = false, maxLines = 1).size.width.toFloat()
    }
    KitTappable(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = label },
        shape = KitShapeId.Button,
        ink = TextTone.Secondary,
    ) {
        KitText(
            KitBidi.auto(display),
            Modifier.padding(horizontal = Space.S2.dp).widthIn(max = (textWidth + Space.S2 * 2f).dp), // F: lib/ui/kit/kit_breadcrumb.dart:266
            role = TypeRole.Secondary,
            tone = TextTone.Secondary,
            kind = KitTextKind.Frame,
            maxLines = 1,
        )
    }
}

@Composable
private fun MoreCrumb(hidden: List<Int>, segments: List<String>, onSelected: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = pluralStringResource(Res.plurals.kitBreadcrumbMore, hidden.size, hidden.size)
    KitMenu(
        expanded = open,
        items = hidden.map { i -> KitMenuItem(segments[i], onSelect = { open = false; onSelected(i) }) },
        onDismissRequest = { open = false },
        title = label,
    ) {
        KitTappable(
            onClick = { open = true },
            modifier = Modifier.semantics { contentDescription = label },
            shape = KitShapeId.Button,
            ink = TextTone.Secondary,
        ) {
            KitText(
                ELLIPSIS,
                Modifier.padding(horizontal = Space.S2.dp),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
                kind = KitTextKind.Frame,
                maxLines = 1,
            )
        }
    }
}

/** The current folder: last, semibold in the primary tone, not tappable (kit_breadcrumb.dart:255-285). */
@Composable
private fun Current(name: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier) {
    val label = stringResource(Res.string.kitBreadcrumbCurrent, name)
    val color = LocalKitColors.current.tone(TextTone.Primary)
    BasicText(
        KitBidi.auto(name),
        modifier
            .heightIn(min = FlutterKitTokens.MIN_TARGET.dp) // F: lib/ui/kit/kit_breadcrumb.dart:300
            .padding(horizontal = Space.S2.dp)
            .clearAndSetSemantics { contentDescription = label; selected = true },
        style = style.copy(color = color, textDirection = TextDirection.Content),
        maxLines = 2, // F: lib/ui/kit/kit_breadcrumb.dart:314
        overflow = TextOverflow.Ellipsis,
    )
}

/** `_KitCrumbMeasure.middleCut`, one line: [name] whole, or its start and end around one "…" (kit_breadcrumb.dart:446). */
private fun middleCut(name: String, maxWidth: Float, density: Float, widthPx: (String) -> Float): String {
    if (widthPx(name) <= maxWidth * density) return name
    var low = 1
    var high = name.length - 1
    var best = ELLIPSIS
    while (low <= high) {
        val kept = (low + high) / 2 // F: lib/ui/kit/kit_breadcrumb.dart:448
        val head = kept - kept / 2 // F: lib/ui/kit/kit_breadcrumb.dart:449
        val tail = kept / 2 // F: lib/ui/kit/kit_breadcrumb.dart:450
        val candidate = name.take(head) + ELLIPSIS + name.takeLast(tail)
        if (widthPx(candidate) <= maxWidth * density) { best = candidate; low = kept + 1 } else high = kept - 1
    }
    return best
}
