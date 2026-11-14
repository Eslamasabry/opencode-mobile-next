package io.github.eslamasabry.oc.ui.kitchat.diff

import androidx.compose.foundation.background
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitDressedLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitToolOpenDetails
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.ceil
import org.jetbrains.compose.resources.stringResource

/**
 * A few lines of what a file write or edit changed, for the work timeline's preview card (Flutter `KitStepPreview`,
 * lib/ui/kit/chat/kit_step_timeline.dart:44-176): at most [MAX_SHOWN] lines with their numbers, and whether [more]
 * follow (the card then fades its last lines). Built by the host from the call's own text; pure Kotlin, total.
 */
public class KitDiffPreview(
    public val lines: ImmutableList<DiffLine>,
    public val more: Boolean = false,
) {
    public val isEmpty: Boolean get() = lines.isEmpty()

    /** The gutter word of each line: "+N" added, "−N" removed, "N" context (kit_step_timeline.dart:1102-1106). */
    public fun gutter(line: DiffLine): String =
        when (line.kind) {
            DiffLineKind.Added -> "+${line.newNo ?: ""}"
            DiffLineKind.Removed -> "−${line.oldNo ?: ""}"
            else -> "${line.newNo ?: line.oldNo ?: ""}"
        }

    override fun equals(other: Any?): Boolean = other is KitDiffPreview && other.lines == lines && other.more == more

    override fun hashCode(): Int = lines.hashCode() * 31 + more.hashCode()

    override fun toString(): String = "KitDiffPreview(${lines.size} lines, more=$more)"

    public companion object {
        /** The most lines a card shows (kit_step_timeline.dart:166). */
        public const val MAX_SHOWN: Int = 6

        private val HUNK_HEADER = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""")

        /**
         * A unified patch: its first change, with one line of context before it; hunk headers, the file preamble and
         * `\ No newline` notes are skipped (kit_step_timeline.dart:53-114).
         */
        public fun fromPatch(patch: String, maxLines: Int = MAX_SHOWN): KitDiffPreview {
            val lines = ArrayList<DiffLine>()
            var lead: DiffLine? = null
            var changed = false
            var inHunk = false
            var oldNo = 0
            var newNo = 0
            var more = false
            for (line in patch.split('\n')) {
                if (line.startsWith("@@")) {
                    HUNK_HEADER.find(line)?.let {
                        oldNo = it.groupValues[1].toInt()
                        newNo = it.groupValues[2].toInt()
                    }
                    inHunk = true
                    continue
                }
                if (!inHunk || line.startsWith("\\")) continue
                val next: DiffLine
                when {
                    line.startsWith("+") -> {
                        next = DiffLine(line.substring(1), DiffLineKind.Added, newNo = newNo++)
                        changed = true
                    }
                    line.startsWith("-") -> {
                        next = DiffLine(line.substring(1), DiffLineKind.Removed, oldNo = oldNo++)
                        changed = true
                    }
                    else -> {
                        if (line.isEmpty()) continue
                        next =
                            DiffLine(
                                if (line.startsWith(" ")) line.substring(1) else line,
                                DiffLineKind.Context,
                                oldNo = oldNo++,
                                newNo = newNo++,
                            )
                        if (!changed) {
                            lead = next
                            continue
                        }
                    }
                }
                if (lead != null && lines.isEmpty()) lines.add(lead)
                if (lines.size >= maxLines) {
                    more = true
                    break
                }
                lines.add(next)
            }
            return KitDiffPreview(lines.toImmutableList(), more)
        }

        /** A whole new file: every line added, numbered from 1 (kit_step_timeline.dart:116-140). */
        public fun fromText(text: String, maxLines: Int = MAX_SHOWN): KitDiffPreview {
            val lines = ArrayList<DiffLine>()
            var more = false
            var at = 0
            var number = 1
            while (at < text.length) {
                var end = text.indexOf('\n', at)
                if (end < 0) end = text.length
                if (lines.size >= maxLines) {
                    more = true
                    break
                }
                lines.add(DiffLine(text.substring(at, end), DiffLineKind.Added, newNo = number++))
                at = end + 1
            }
            return KitDiffPreview(lines.toImmutableList(), more)
        }

        /**
         * A before and after pair with no file around it (an edit that names the old and the new text): the changed
         * lines with a sign and no number, since the call does not say where in the file they sit; one line of context
         * before the first change (kit_step_timeline.dart:142-163).
         */
        public fun fromTexts(before: String?, after: String?, maxLines: Int = MAX_SHOWN): KitDiffPreview {
            val file = DiffModel.fromBeforeAfter("", before, after)
            val all =
                file.segments.flatMap { segment ->
                    when (segment) {
                        is DiffLine ->
                            if (segment.kind == DiffLineKind.Hunk) emptyList()
                            else listOf(DiffLine(segment.text, segment.kind))
                        // Flutter keeps only the line segments; a folded run of context is not part of the card.
                        is DiffGap -> emptyList()
                    }
                }
            val firstChange = all.indexOfFirst { it.kind != DiffLineKind.Context }
            val from = if (firstChange > 0) firstChange - 1 else 0
            val rest = all.subList(from, all.size)
            return KitDiffPreview(rest.take(maxLines).toImmutableList(), rest.size > maxLines)
        }

        public val Empty: KitDiffPreview = KitDiffPreview(persistentListOf())
    }
}

/** The added and removed line tints (design Roles.kt:40-41: `codeAddedSurface`, `codeRemovedSurface`). */
private const val PREVIEW_TINT_ALPHA = 0.10f

/** One mono digit is about 0.62 em of the 13 dp mono role (kit_step_timeline.dart:1118). */
private const val GUTTER_DP_PER_CHAR = 8.1f

/**
 * The preview card under a Write or Edit step (Flutter `_PreviewCard`, lib/ui/kit/chat/kit_step_timeline.dart:1090-1230):
 * a `surface1` card with the code radius (14), 8 dp inner padding, at most [KitDiffPreview.MAX_SHOWN] mono lines, each
 * with an end-aligned `+N` / `−N` / `N` gutter (success / secondary / tertiary ink) and the added (green) or removed
 * tint across the row. Left to right in any language, one line each with an ellipsis; when more lines follow, the last
 * ones melt into the card. The whole card is one button: [label] names it and [onOpen] opens the step's details.
 * Text is redacted like every mono chat surface (R18).
 *
 * API for feature/chat (C-005, later): route a write's content through [KitDiffPreview.fromText], an edit's patch through
 * [KitDiffPreview.fromPatch] (or its old/new strings through [KitDiffPreview.fromTexts]) and draw this card under the
 * step instead of sending the output to Markdown.
 */
@Composable
public fun KitDiffPreviewCard(
    preview: KitDiffPreview,
    label: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (preview.isEmpty) return
    val colors = LocalKitColors.current
    val mono = LocalKitTypography.current.style(TypeRole.Mono)
    val redactor = LocalKitRedactor.current
    val hint = stringResource(Res.string.kitToolOpenDetails)
    val gutters = remember(preview) { preview.lines.map(preview::gutter) }
    val width = gutters.maxOf { it.length }
    val fontScale = LocalDensity.current.fontScale
    val gutterWidth = ceil(width * GUTTER_DP_PER_CHAR * fontScale).dp
    val shape = KitShapeId.Code.toShape()
    // Brutal and Paper: the style's code face (its edge and corner, no shadow); null keeps the card's own.
    val dressed = kitDressedLook(KitSurfaceInput(role = SurfaceRole.Code, shape = KitShapeId.Code, color = colors.surface1))
    KitTappable(
        onOpen,
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.S1.dp)
            .testTag("kit-diff-preview")
            .semantics { contentDescription = label },
        onClickLabel = hint,
        shape = KitShapeId.Code,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            // The card's room above its first line and below its last lies inside those lines' rows, so an added or
            // removed line's tint runs to the card's edge and takes its shape (audit 2026-11-14, c21: a square-ended
            // green strip inside the rounded card, with dark slivers above and below it).
            Box(Modifier.fillMaxWidth().then(dressed?.let { Modifier.kitSurface(it) } ?: Modifier.clip(shape).background(colors.surface1)).testTag("kit-diff-preview-face")) {
                // The lines are read as the card's one name, not one by one.
                Column(
                    Modifier.fillMaxWidth()
                        .semantics { hideFromAccessibility() }
                        .then(if (preview.more) Modifier.fadeLastLines() else Modifier)
                ) {
                    val last = preview.lines.lastIndex
                    preview.lines.forEachIndexed { index, line ->
                        PreviewRow(
                            line, gutters[index], gutterWidth, redactor::redact, colors, mono,
                            top = if (index == 0) Space.S2.dp else 0.dp,
                            bottom = if (index == last) Space.S2.dp else 0.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewRow(
    line: DiffLine,
    gutter: String,
    gutterWidth: Dp,
    redact: (String) -> String,
    colors: KitColors,
    mono: TextStyle,
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
) {
    val (tint, number) =
        when (line.kind) {
            DiffLineKind.Added -> colors.success.copy(alpha = PREVIEW_TINT_ALPHA) to colors.success
            DiffLineKind.Removed -> colors.text2.copy(alpha = PREVIEW_TINT_ALPHA) to colors.text2
            else -> Color.Transparent to colors.text3
        }
    Row(
        Modifier.fillMaxWidth().testTag("kit-diff-preview-row").background(tint)
            .padding(start = Space.S2.dp, end = Space.S2.dp, top = top, bottom = bottom),
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        BasicText(
            gutter,
            Modifier.width(gutterWidth).testTag("kit-diff-preview-gutter"),
            style = mono.copy(color = number, textAlign = TextAlign.End),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            redact(line.text),
            Modifier.weight(1f),
            style = mono.copy(color = colors.text1),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The last lines melt into the card: opaque to 45 % of the height, then fading to nothing at the bottom. */
private fun Modifier.fadeLastLines(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.verticalGradient(0f to Color.Black, 0.45f to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
        }
