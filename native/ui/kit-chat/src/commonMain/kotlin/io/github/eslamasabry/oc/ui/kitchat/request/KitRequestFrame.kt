package io.github.eslamasabry.oc.ui.kitchat.request

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYou
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
