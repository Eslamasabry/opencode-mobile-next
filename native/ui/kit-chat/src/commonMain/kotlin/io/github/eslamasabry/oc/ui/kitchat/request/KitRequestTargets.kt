package io.github.eslamasabry.oc.ui.kitchat.request

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
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
public fun KitRequestTargets(targets: List<KitRequestTarget>, modifier: Modifier = Modifier) {
    if (targets.isEmpty()) return
    var expanded by remember(targets) { mutableStateOf(false) }
    val colors = LocalKitColors.current
    val shape = KitShapeId.Code.toShape()
    val hidden = (targets.size - TARGET_LIMIT).coerceAtLeast(0)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.ground)
            .border(1.dp, colors.hairline, shape)
            .padding(Space.S3.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        (if (expanded) targets else targets.take(TARGET_LIMIT)).forEach { target ->
            KitMonoLtr(target.text, Modifier.fillMaxWidth(), TextTone.Primary)
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
