package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitMore
import org.jetbrains.compose.resources.stringResource

/**
 * The one status line (Dart `KitStatusLine.of`): the condition's glyph, its words, an optional
 * supporting line and Now line, its one action and, only when dismissing changes nothing real, a
 * dismiss. Never a card; one hairline under it. At large text (≥ 1.29×) the action moves under the
 * words. The line is the one polite live region.
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
    val stacked = TextScalePolicy.isLargeText(kitTextScale())
    val hairline = colors.hairline
    val dismiss =
        status.onDismiss?.let { onDismiss ->
            KitLineAction(
                status.dismissLabel ?: return@let null,
                onDismiss,
                testTag = "kit-status-dismiss",
            )
        }
    Column(
        modifier
            .kitMaxWidth(CapRole.Reading)
            .fillMaxWidth()
            .heightIn(min = tokens.statusLineMin.dp)
            .drawBehind {
                drawLine(
                    hairline,
                    Offset(0f, size.height - 0.5f),
                    Offset(size.width, size.height - 0.5f),
                    1f,
                )
            }
            .padding(
                start = tokens.gutter.dp,
                end = Space.S1.dp,
                top = Space.S2.dp,
                bottom = Space.S2.dp,
            )
            .testTag("kit-status-line"),
        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitStatusGlyph(status.tone.glyph, glyphColor(status.tone, colors))
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {
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
            if (!stacked)
                status.action?.let {
                    KitInlineAction(it.copy(testTag = it.testTag ?: "kit-status-action"))
                }
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
                                stringResource(Res.string.kitMore),
                                { open = true },
                                testTag = "kit-status-more",
                            )
                        )
                    },
                )
            }
            dismiss?.let { KitInlineAction(it) }
        }
        if (stacked) {
            status.action?.let {
                Row(Modifier.padding(start = (tokens.smallIcon + Space.S3).dp)) {
                    KitInlineAction(it.copy(testTag = it.testTag ?: "kit-status-action"))
                }
            }
        }
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
