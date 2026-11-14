package io.github.eslamasabry.oc.ui.kitchat.request

import androidx.compose.foundation.background
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitDressedLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.BasicText
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.rememberKitRedactedCopyAction
import io.github.eslamasabry.oc.ui.kitchat.tool.KitMonoLtr
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetailsHide
import io.github.eslamasabry.oc.ui.words.res.kitRequestMoreTargets
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The request's exact command or file, with the change size when it was supplied by the server. */
public data class KitRequestTarget(val text: String, val change: String? = null)

/**
 * Three targets in place; the visible Details action reveals every remaining target inside the
 * bounded card.
 */
@Composable
public fun KitRequestTargets(
    targets: List<KitRequestTarget>,
    modifier: Modifier = Modifier,
    copyLabel: String? = null,
    copiedLabel: String? = null,
) {
    if (targets.isEmpty()) return
    var expanded by remember(targets) { mutableStateOf(false) }
    val colors = LocalKitColors.current
    val shape = KitShapeId.Code.toShape()
    val hidden = (targets.size - TARGET_LIMIT).coerceAtLeast(0)
    // Brutal and Paper: the style's code face (its edge and corner, no shadow); null keeps the box's own.
    val dressed = kitDressedLook(KitSurfaceInput(role = SurfaceRole.Code, shape = KitShapeId.Code, color = colors.ground))
    Column(
        modifier
            .fillMaxWidth()
            .then(dressed?.let { Modifier.kitSurface(it) } ?: Modifier.clip(shape).background(colors.ground).border(1.dp, colors.hairline, shape))
            .padding(Space.S3.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        // Copies every command or path as asked (the full text, not the shown lines) with any key, token or password
        // masked (Flutter KitCopy.copy, redact: true; E15-S04): a compact copy mark in the box's corner beside the first
        // line, like a code block's, never a long button under the command.
        val copy =
            if (copyLabel != null && copiedLabel != null)
                rememberKitRedactedCopyAction(copyLabel, copiedLabel, { targets.joinToString("\n") { it.text } })
            else null
        (if (expanded) targets else targets.take(TARGET_LIMIT)).forEachIndexed { index, target ->
            if (index == 0 && copy != null) {
                // One line sits level with the copy mark (it sat high, over an empty lower half); several lines keep
                // the mark at the first line's corner.
                val oneLine = '\n' !in target.text
                Row(verticalAlignment = if (oneLine) Alignment.CenterVertically else Alignment.Top) {
                    KitMonoLtr(
                        target.text,
                        Modifier.weight(1f).padding(top = if (oneLine) 0.dp else Space.S2.dp),
                        TextTone.Primary,
                    )
                    KitIconButton(
                        icon = if (copy.label != copyLabel) KitIcons.check else KitIcons.copy,
                        label = copy.label,
                        onClick = copy.onClick ?: {},
                        // The copied name is announced once, saying when a password or key was masked.
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        tone = TextTone.Secondary,
                    )
                }
            } else KitMonoLtr(target.text, Modifier.fillMaxWidth(), TextTone.Primary)
            target.change?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
        }
        if (hidden > 0) {
            if (!expanded)
                KitText(
                    pluralStringResource(Res.plurals.kitRequestMoreTargets, hidden, hidden),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                )
            KitButton(
                stringResource(if (expanded) Res.string.kitDetailsHide else Res.string.kitDetails),
                { expanded = !expanded },
                role = KitButtonRole.Tertiary,
            )
        }
    }
}

private const val TARGET_LIMIT: Int = 3

/** A single command wraps to at most this many lines in the question block (kit_request_question.dart:309). */
private const val SINGLE_TARGET_LINES: Int = 3

/**
 * The question look's block (Flutter `_targetBlock`, kit_request_question.dart:247-437): what the request will run or
 * touch in one bordered mono block on the ground, code radius, a hairline between lines. A single command wraps to three
 * lines with its change size and the [onDetails] link ("Details", accent, underlined) on its own line's end; several
 * show the first three, one line each, then a footer with "+n more" and Details. With no [onDetails], Details only
 * appears when lines are hidden, and shows them in place. No copy mark: the request's details hold the copy.
 */
@Composable
public fun KitRequestTargetBlock(
    targets: List<KitRequestTarget>,
    modifier: Modifier = Modifier,
    onDetails: (() -> Unit)? = null,
) {
    if (targets.isEmpty()) return
    var expanded by remember(targets) { mutableStateOf(false) }
    val colors = LocalKitColors.current
    val shape = KitShapeId.Code.toShape()
    val dressed = kitDressedLook(KitSurfaceInput(role = SurfaceRole.Code, shape = KitShapeId.Code, color = colors.ground))
    val single = targets.size == 1
    val shown = if (expanded) targets else targets.take(TARGET_LIMIT)
    val hidden = targets.size - shown.size
    val details: (() -> Unit)? = onDetails ?: if (targets.size > TARGET_LIMIT) ({ expanded = !expanded }) else null
    val detailsWord = stringResource(if (onDetails == null && expanded) Res.string.kitDetailsHide else Res.string.kitDetails)
    val link: @Composable () -> Unit = { if (details != null) DetailsLink(detailsWord, details) }
    Column(
        modifier
            .fillMaxWidth()
            .testTag("kit-request-target-block")
            .then(dressed?.let { Modifier.kitSurface(it) } ?: Modifier.clip(shape).background(colors.ground).border(kitHairline(), colors.hairline, shape)),
    ) {
        shown.forEachIndexed { index, target ->
            if (index > 0) KitDivider()
            Row(
                Modifier.fillMaxWidth().padding(start = Space.S3.dp, end = if (single && details != null) Space.S1.dp else Space.S3.dp),
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KitMonoLtr(
                    target.text,
                    Modifier.weight(1f).padding(vertical = Space.S3.dp),
                    TextTone.Primary,
                    maxLines = if (single) SINGLE_TARGET_LINES else 1,
                )
                target.change?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, tabular = true) }
                if (single) link()
            }
        }
        if (!single && (hidden > 0 || details != null)) {
            KitDivider()
            Row(
                Modifier.fillMaxWidth().padding(start = Space.S3.dp, end = Space.S2.dp),
                horizontalArrangement = if (hidden > 0) Arrangement.SpaceBetween else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hidden > 0)
                    KitText(
                        pluralStringResource(Res.plurals.kitRequestMoreTargets, hidden, hidden),
                        Modifier.padding(vertical = Space.S3.dp),
                        role = TypeRole.Secondary,
                        tone = TextTone.Secondary,
                    )
                link()
            }
        }
    }
}

/** "Details" as the block's own link: secondary size, accent ink, underlined (kit_request_question.dart:262-281). */
@Composable
private fun DetailsLink(word: String, onClick: () -> Unit) {
    val colors = LocalKitColors.current
    KitTappable(onClick, Modifier.testTag("kit-request-details"), onClickLabel = word) {
        BasicText(
            word,
            Modifier.padding(horizontal = Space.S1.dp, vertical = Space.S3.dp),
            style =
                LocalKitTypography.current.style(TypeRole.Secondary)
                    .copy(color = colors.accent, textDecoration = TextDecoration.Underline),
        )
    }
}
