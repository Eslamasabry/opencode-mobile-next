package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitInlineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitMore
import org.jetbrains.compose.resources.stringResource

/**
 * The one status line (Dart `KitStatusLine.of`): the condition's glyph, its words, an optional
 * supporting line and Now line, its one action and, only when dismissing changes nothing real, a
 * dismiss. Never a card; one hairline under it. At large text (≥ 1.29×), or when the words and the controls do
 * not fit on one line, the action, More and dismiss move together onto a row under the words, so the words keep the
 * full width (B-094). The line is the one polite live region.
 *
 * States: condition, working, now-line, error, disabled (action) Text: reading Cap: Reading Motion:
 * none of its own (the slot folds it in and out) Hidden: none Replaces: KitStatusLine
 * (lib/ui/kit/kit_status_line.dart)
 */
@Composable
public fun KitStatusLine(status: KitStatus, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    kitAssert(status.kind != KitStatusKind.RiskySwitch || status.onDismiss == null) {
        "KitStatusLine: a risky switch is never dismissible (SEC-9)"
    }
    val largeText = TextScalePolicy.isLargeText(kitTextScale())
    val hairline = colors.hairline
    val dismiss =
        status.onDismiss?.let { onDismiss ->
            KitLineAction(
                status.dismissLabel ?: return@let null,
                onDismiss,
                testTag = "kit-status-dismiss",
            )
        }
    BoxWithConstraints(
        modifier
            .kitMaxWidth(CapRole.Reading)
            .fillMaxWidth()
            .heightIn(min = tokens.statusLineMin.dp)
            .drawBehind {
                drawLine(
                    hairline,
                    Offset(tokens.gutter.dp.toPx(), size.height - 0.5f),
                    Offset(size.width - tokens.gutter.dp.toPx(), size.height - 0.5f),
                    1f,
                )
            }
            .padding(
                start = tokens.gutter.dp,
                end = tokens.gutter.dp,
                top = Space.S2.dp,
                bottom = Space.S2.dp,
            )
            .testTag("kit-status-line"),
    ) {
        val measurer = rememberTextMeasurer()
        val typography = LocalKitTypography.current
        val density = LocalDensity.current
        val moreLabel = stringResource(Res.string.kitMore)
        val controlLabels =
            listOfNotNull(
                status.action?.label,
                moreLabel.takeIf { status.more.isNotEmpty() },
                dismiss?.label,
            )
        // B-094: the controls move under the words when both do not fit on one line (long words, large text),
        // instead of squeezing the words into a narrow column beside them (Flutter `_stacks`).
        val stacked =
            largeText ||
                controlLabels.isNotEmpty() &&
                    with(density) {
                        fun oneLine(text: String, role: TypeRole) =
                            measurer.measure(text, typography.style(role), softWrap = false, maxLines = 1).size.width
                        val gap = Space.S3.dp.roundToPx()
                        statusControlsStack(
                            available = constraints.maxWidth,
                            lead = tokens.smallIcon.dp.roundToPx() + gap,
                            message = oneLine(status.message, TypeRole.Body),
                            controls =
                                controlLabels.map { oneLine(it, TypeRole.Button) + 2 * Space.S3.dp.roundToPx() },
                            gap = gap,
                        )
                    }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            Row(
                // Stacked, the glyph stays with the first line of words, not centred on words and buttons.
                verticalAlignment = if (stacked) Alignment.Top else Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
            ) {
                if (status.icon == null) KitStatusGlyph(status.tone.glyph, glyphColor(status.tone, colors))
                else io.github.eslamasabry.oc.ui.kit.icon.KitIcon(
                    status.icon, size = io.github.eslamasabry.oc.ui.kit.icon.KitIconSize.Small,
                    tone = when (status.tone) {
                        StatusTone.Neutral -> TextTone.Secondary
                        StatusTone.Progress -> TextTone.Accent
                        StatusTone.Ok -> TextTone.Success
                        StatusTone.Attention, StatusTone.Failure -> TextTone.Primary
                    }, growsWithText = true,
                )
                // E20-S08: the words and the stacked controls share one start edge. The controls sat on a third edge
                // (their row was padded to the words, then each button padded its own words again).
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                    Column(
                        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                            liveRegion = LiveRegionMode.Polite
                        },
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        KitText(
                            status.message,
                            Modifier.testTag("kit-status-message"),
                            role = TypeRole.Body,
                        )
                        status.supporting?.let {
                            KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary)
                        }
                        status.next?.let {
                            KitText(
                                it,
                                Modifier.testTag("kit-status-next"),
                                role = TypeRole.Secondary,
                                tone = TextTone.Secondary,
                            )
                        }
                    }
                    if (stacked && controlLabels.isNotEmpty()) {
                        Row(
                            // The buttons' own padding moves out of the edge, so their words start on the words'.
                            Modifier.offset(x = -Space.S3.dp).testTag("kit-status-controls"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            status.action?.let { KitInlineAction(it.copy(testTag = it.testTag ?: "kit-status-action")) }
                            StatusMore(status, moreLabel)
                            dismiss?.let { KitInlineAction(it) }
                        }
                    }
                }
                if (!stacked)
                    status.action?.let {
                        KitInlineAction(it.copy(testTag = it.testTag ?: "kit-status-action"))
                    }
                if (!stacked) StatusMore(status, moreLabel)
                if (!stacked) dismiss?.let { KitInlineAction(it) }
            }
        }
    }
}

/**
 * Whether a status line's controls go under its words (B-094, Flutter `KitStatusLine._stacks`): the words on one
 * line, the glyph in front and every control with its gap do not fit in [available] (all in px).
 */
internal fun statusControlsStack(available: Int, lead: Int, message: Int, controls: List<Int>, gap: Int): Boolean =
    controls.isNotEmpty() && lead.toLong() + message + controls.sumOf { it.toLong() + gap } > available

/** The line's More menu, when the status has more than one action. */
@Composable
private fun StatusMore(status: KitStatus, moreLabel: String) {
    if (status.more.isNotEmpty()) {
        var open by remember(status.id) { mutableStateOf(false) }
        KitMenu(
            expanded = open,
            items =
                status.more.map {
                    KitMenuItem(
                        it.label,
                        it.onClick,
                        enabled =
                            io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled.of(
                                it.enabled,
                                it.disabledReason,
                            ),
                    )
                },
            onDismissRequest = { open = false },
            anchor = {
                KitInlineAction(
                    KitLineAction(
                        moreLabel,
                        { open = true },
                        testTag = "kit-status-more",
                    )
                )
            },
        )
    }
}

/**
 * The glyph's colour: amber is for needs-you only, so an attention line is drawn in text1 (LOOK-4);
 * failure too (LOOK-5).
 */
internal fun glyphColor(tone: StatusTone, colors: KitColors) =
    when (tone) {
        StatusTone.Neutral -> colors.text2
        StatusTone.Progress -> colors.accent
        StatusTone.Ok -> colors.success
        StatusTone.Attention,
        StatusTone.Failure -> colors.text1
    }
