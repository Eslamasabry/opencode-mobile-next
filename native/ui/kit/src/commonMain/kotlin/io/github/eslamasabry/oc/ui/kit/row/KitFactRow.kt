package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitEdge
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

public enum class KitProvenance {
    Told,
    Seen,
    Guessed,
    Calculated,
}

/**
 * A fact or source hit (Dart `KitFactRow`). [observed] is a localized date from Formats, owned by the caller's clock.
 * The text, then one line that always holds the provenance glyph, word and date; a guessed fact waiting for the
 * person's OK can carry its two [answers] under the line, inside the card. The border says the provenance a second
 * way (solid accent, dashed, plain hairline under the plain Solid look) but never alone. Technical values are
 * isolated only in RTL; source/title names keep their own first-strong direction.
 *
 * States: none — a fact that exists. Cap: list (960).
 */
@Composable
public fun KitFactRow(
    text: String,
    provenance: KitProvenance,
    observed: String,
    modifier: Modifier = Modifier,
    source: String? = null,
    title: String? = null,
    waitingForOk: Boolean = false,
    answers: List<KitAction> = emptyList(),
    onOpen: (() -> Unit)? = null,
    semanticsLabel: String? = null,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    fun named(value: String): String = if (rtl) KitBidi.auto(value) else value
    val from = source?.takeIf { it.isNotEmpty() }?.let(::named)
    val base =
        when (provenance) {
            KitProvenance.Told -> stringResource(Res.string.kitFactToldLine, observed)
            KitProvenance.Seen ->
                if (from == null) stringResource(Res.string.kitFactSeenNoSourceLine, observed)
                else stringResource(Res.string.kitFactSeenLine, from, observed)
            KitProvenance.Guessed ->
                if (from == null) stringResource(Res.string.kitFactGuessedLine, observed)
                else stringResource(Res.string.kitFactGuessedFromLine, observed, from)
            KitProvenance.Calculated -> stringResource(Res.string.kitFactCalculatedLine, observed)
        }
    val line = if (waitingForOk) stringResource(Res.string.kitFactWaitingLine, base) else base
    val word =
        when (provenance) {
            KitProvenance.Told -> stringResource(Res.string.kitFactToldWord)
            KitProvenance.Seen -> stringResource(Res.string.kitFactSeenWord)
            KitProvenance.Guessed -> stringResource(Res.string.kitFactGuessedWord)
            KitProvenance.Calculated -> stringResource(Res.string.kitFactCalculatedWord)
        }
    val glyph =
        when (provenance) {
            KitProvenance.Told -> KitIcons.check
            KitProvenance.Seen -> KitIcons.bookmark
            KitProvenance.Guessed -> KitIcons.sparkle
            KitProvenance.Calculated -> KitIcons.function
        }
    val words = semanticsLabel ?: listOfNotNull(title, text, word, line).joinToString(". ")
    val colors = LocalKitColors.current
    val styleState = LocalKitStyle.current
    // KitSurfaceLook.isPlain: Solid at its own defaults; any other look draws its own edge, so the plain hairline goes.
    val plain = styleState.style == Style.Solid && styleState.knobs == styleState.spec.tokens.defaults
    val surface =
        kitSurfaceLook(
            KitSurfaceInput(SurfaceRole.Content, KitShapeId.Panel, ownFill = colors.surface1)
        )
    val border = kitFactBorder(provenance, colors.accent, colors.text2, if (plain) colors.hairline else Color.Transparent)
    // The provenance ring follows the face's own shape (the panel's corner as the style and the Corners knob draw it),
    // inset by half its width as the glass rim is, so it never stands off or cuts into the face's corners (2026-11-13).
    val faceShape = surface.shape
    val edge = Modifier.drawWithContent {
        drawContent()
        if (border.color.alpha == 0f) return@drawWithContent
        val stroke = border.width.dp.toPx()
        if (size.width <= stroke || size.height <= stroke) return@drawWithContent
        inset(stroke / 2) {
            drawOutline(
                faceShape.createOutline(size, layoutDirection, this),
                border.color,
                style =
                    Stroke(
                        stroke,
                        pathEffect =
                            if (border.dashed) PathEffect.dashPathEffect(floatArrayOf(FACT_DASH.dp.toPx(), FACT_GAP.dp.toPx()))
                            else null,
                    ),
            )
        }
    }
    val tap =
        if (onOpen != null)
            Modifier.kitTappable(onClick = onOpen, role = KitTappableRole.Row, shape = KitShapeId.Panel)
                .kitEdge(KitEdge.Bleed) // a hit is a list row: its press runs the list's width (LAYOUT-RULES.md §2)
        else Modifier
    Box(
        modifier
            .kitMaxWidth(CapRole.List)
            .fillMaxWidth()
            .then(tap)
            .semantics { contentDescription = words }
    ) {
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = kitRowGutter(), vertical = Space.S1.dp)
                .then(surface.modifier)
                .then(edge)
                .padding(horizontal = Space.S4.dp, vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
            // The row speaks one sentence (words); its pieces are not read again. The answers stay their own targets.
            Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                title?.let { KitText(named(it), role = TypeRole.RowTitle, maxLines = 2) } // F: lib/ui/kit/kit_fact_row.dart:225
                KitText(KitBidi.isolateValues(text, rtl))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    // Centred on the line's first row of words (ALIGN-RULES icon-label), as the caption's line box
                    // and the small glyph differ by a pixel or two.
                    val lineBox = with(LocalDensity.current) {
                        LocalKitTypography.current.style(TypeRole.Caption).lineHeight.toDp()
                    }
                    val lift = (lineBox - KitIconSize.Small.dp.dp) / 2 // NV: centring splits the difference in half
                    KitIcon(glyph, Modifier.offset(y = lift), size = KitIconSize.Small, tone = TextTone.Secondary)
                    KitText(line, Modifier.weight(1f), role = TypeRole.Caption, tone = TextTone.Secondary)
                }
            }
            if (answers.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = Space.S2.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                ) {
                    answers.take(2).forEach { answer -> // F: lib/ui/kit/kit_fact_row.dart:257
                        KitButton(
                            answer.label,
                            { answer.onClick?.invoke() },
                            role = KitButtonRole.Secondary,
                            enabled = answer.enabled,
                            working = answer.working,
                            semanticsLabel = answer.semanticsLabel,
                        )
                    }
                }
            }
        }
    }
}

/** The provenance border of kit_fact_row.dart:189-197: its colour, width and whether it is dashed. */
public data class KitFactBorder(val color: Color, val width: Float, val dashed: Boolean)

/** Told: solid accent 1.5; guessed: dashed text2 1.5; seen and worked out: [plainEdge] at 1 (transparent off Solid). */
public fun kitFactBorder(provenance: KitProvenance, accent: Color, text2: Color, plainEdge: Color): KitFactBorder =
    when (provenance) {
        KitProvenance.Told -> KitFactBorder(accent, 1.5f, dashed = false) // F: lib/ui/kit/kit_fact_row.dart:190
        KitProvenance.Guessed -> KitFactBorder(text2, 1.5f, dashed = true) // F: lib/ui/kit/kit_fact_row.dart:191
        KitProvenance.Seen, KitProvenance.Calculated -> KitFactBorder(plainEdge, 1f, dashed = false)
    }

private const val FACT_DASH = 6f // F: lib/ui/kit/kit_fact_row.dart:330
private const val FACT_GAP = 4f // F: lib/ui/kit/kit_fact_row.dart:331
