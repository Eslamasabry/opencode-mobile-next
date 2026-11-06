package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.layout.padding
import io.github.eslamasabry.oc.ui.kit.foundation.kitShadowRoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.frame.kitScrollFade
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import kotlin.math.max

/**
 * Flutter KitComposerStatusStrip: chips scroll within their share, the model uses the remaining room,
 * and Stop never scrolls or shrinks. The composer host supplies the strip's outer gutter.
 */
@Composable
public fun KitComposerStatusStrip(
    hasChips: Boolean,
    modifier: Modifier = Modifier,
    model: (@Composable () -> Unit)? = null,
    stop: (@Composable () -> Unit)? = null,
    chips: @Composable RowScope.() -> Unit,
) {
    if (!hasChips && model == null && stop == null) return
    Row(modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val availableWidth = maxWidth
            val scroll = rememberScrollState()
            val tokens = LocalKitTokens.current
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val labelWidth = with(density) {
                measurer.measure("000000…", LocalKitTypography.current.style(TypeRole.Secondary), softWrap = false, maxLines = 1).size.width.toDp()
            }
            val glyph = tokens.smallIcon.dp * density.fontScale.coerceAtMost(tokens.maxIconScale)
            // The scroller clips; the chips' style shadows keep their room above and below.
            val room = io.github.eslamasabry.oc.ui.kit.foundation.kitShadowReach()
            val modelMin = Space.S3.dp * 2 + glyph * 2 + Space.S2.dp + Space.S1.dp + labelWidth + 1.dp // F: lib/ui/kit/chat/kit_composer_status_strip.dart:303-308
            when {
                !hasChips -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { model?.invoke() }
                model == null -> Row(
                    Modifier.fillMaxWidth().kitShadowRoom(room).kitScrollFade(scroll).horizontalScroll(scroll).padding(vertical = room).testTag("composer-status-scroll"),
                    horizontalArrangement = Arrangement.spacedBy(Space.S2.dp), content = chips,
                )
                else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val chipMax = max(availableWidth.value * .5f, availableWidth.value - Space.S2 - modelMin.value).coerceAtLeast(0f).dp // F: lib/ui/kit/chat/kit_composer_status_strip.dart:212-217
                    Row(
                        Modifier.widthIn(max = chipMax).kitShadowRoom(room).kitScrollFade(scroll).horizontalScroll(scroll).padding(vertical = room).testTag("composer-status-scroll"),
                        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp), content = chips,
                    )
                    Spacer(Modifier.width(Space.S2.dp))
                    Box(Modifier.weight(1f, fill = false)) { model() }
                }
            }
        }
        stop?.let {
            Spacer(Modifier.width(Space.S2.dp))
            it()
        }
    }
}
