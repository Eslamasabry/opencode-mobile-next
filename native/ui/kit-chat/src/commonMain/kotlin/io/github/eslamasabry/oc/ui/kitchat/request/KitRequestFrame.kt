package io.github.eslamasabry.oc.ui.kitchat.request

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYou
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYouReason
import io.github.eslamasabry.oc.ui.kit.style.KitSignalNeedsYou
import io.github.eslamasabry.oc.ui.kit.style.KitSignalSlip
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitNeedsYouWaiting
import io.github.eslamasabry.oc.ui.words.res.kitRequestNeedsYouLabel
import org.jetbrains.compose.resources.stringResource

/**
 * One bounded request surface for permissions and questions, with answers always outside its
 * scrolling words.
 */
@Composable
public fun KitRequestFrame(
    modifier: Modifier = Modifier,
    answers: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalKitColors.current
    val shape = KitShapeId.Card.toShape()
    val face =
        if (LocalKitAppearance.current.style == KitSurfaceStyle.Signal) Modifier
        else
            Modifier.clip(shape)
                .background(colors.surface1)
                .background(colors.attentionSurface)
                .border(1.dp, colors.attentionLine, shape)
                .padding(Space.S4.dp)
    KitSignalSlip(
        modifier.widthIn(max = 700.dp).fillMaxWidth().heightIn(max = maxCardHeight()),
        endsWithTap = true,
    ) {
        Column(
            Modifier.fillMaxWidth().then(face),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                content = content,
            )
            answers()
        }
    }
}

/** The visible waiting caption follows the current style without changing its spoken words. */
@Composable
public fun KitRequestWaitingCaption(waiting: String?, modifier: Modifier = Modifier) {
    if (LocalKitAppearance.current.style == KitSurfaceStyle.Signal) {
        val label = stringResource(Res.string.kitRequestNeedsYouLabel)
        val age = waiting?.let { stringResource(Res.string.kitNeedsYouWaiting, it) }
        KitSignalNeedsYou(if (age == null) label else "$label · $age", modifier = modifier)
    } else {
        KitNeedsYou(KitNeedsYouReason.Decision, modifier = modifier, waiting = waiting)
    }
}

/**
 * The collapsed answered row (kit_request_card.dart `_collapsed`): no card, border or attention colour. The request's glyph
 * at the start, the ask in small secondary words, and the [outcome] (the receipt, then any Undo) under it.
 */
@Composable
public fun KitRequestAnsweredRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    outcome: @Composable () -> Unit,
) {
    // kitRequestPlacement: the page gutter less the 4 dp ring across (12 dp), space1 (4 dp) above and below.
    Row(
        modifier.fillMaxWidth().padding(horizontal = Space.S3.dp, vertical = Space.S1.dp)
            .heightIn(min = LocalKitTokens.current.rowHeightOne.dp).padding(horizontal = Space.S4.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KitIcon(icon, size = KitIconSize.Small, tone = TextTone.Secondary)
        Column(Modifier.weight(1f)) {
            KitText(title, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Reading)
            outcome()
        }
    }
}
