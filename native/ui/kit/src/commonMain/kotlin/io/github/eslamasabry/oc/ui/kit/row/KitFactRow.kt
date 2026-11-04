package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

public enum class KitProvenance {
    Told,
    Seen,
    Guessed,
    Calculated,
}

/**
 * A fact or source hit. [observed] is a localized date from Formats, owned by the caller's clock.
 * Technical values are isolated only in RTL; source/title names retain their own first-strong
 * direction. Provenance always has words as well as an edge. Answers remain separate accessible
 * targets.
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
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val surface =
        kitSurfaceLook(
            KitSurfaceInput(SurfaceRole.Content, KitShapeId.Panel, ownFill = colors.surface1)
        )
    val border =
        when (provenance) {
            KitProvenance.Told -> colors.accent
            KitProvenance.Guessed -> colors.text2
            else -> colors.hairline
        }
    val edge = Modifier.drawWithContent {
        drawContent()
        val stroke =
            (if (provenance == KitProvenance.Told || provenance == KitProvenance.Guessed) 1.5.dp
                else 1.dp)
                .toPx()
        val inset = stroke / 2
        drawRoundRect(
            border,
            Offset(inset, inset),
            Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
            CornerRadius(18.dp.toPx()),
            style =
                Stroke(
                    stroke,
                    pathEffect =
                        if (provenance == KitProvenance.Guessed)
                            PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                        else null,
                ),
        )
    }
    Column(
        modifier
            .kitMaxWidth(CapRole.List)
            .fillMaxWidth()
            .padding(horizontal = tokens.gutter.dp, vertical = Space.S1.dp)
    ) {
        Column(
            Modifier.fillMaxWidth()
                .then(surface.modifier)
                .then(edge)
                .then(
                    if (onOpen != null)
                        Modifier.kitTappable(onClick = onOpen, shape = KitShapeId.Panel)
                    else Modifier
                )
                .padding(horizontal = Space.S4.dp, vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
            title?.let { KitText(named(it), role = TypeRole.RowTitle, maxLines = 2) }
            KitText(
                KitBidi.isolateValues(text, rtl),
                modifier = Modifier.semantics { contentDescription = text },
            )
            KitText(line, role = TypeRole.Caption, tone = TextTone.Secondary)
        }
        answers.take(2).forEach { answer ->
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
