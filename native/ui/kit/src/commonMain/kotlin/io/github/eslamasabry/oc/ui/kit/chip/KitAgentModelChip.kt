package io.github.eslamasabry.oc.ui.kit.chip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.logo.KitProviderLogo
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/**
 * Who works on it, in the foot of the start box: the agent's mark, the agent's name in the primary ink and its model in
 * the secondary one, then a chevron. One chip opens the one "Agent and model" sheet. The two names are one text that
 * wraps at its spaces (two lines when they do not fit together), so neither is ever cut mid-word.
 *
 * States: rest, pressed, expanded Text: [agent] and [model], frame, up to two lines Cap: the box's width
 */
@Composable
public fun KitAgentModelChip(
    agent: String,
    model: String?,
    markId: String?,
    spoken: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    val colors = LocalKitColors.current
    val base = LocalKitTypography.current.style(TypeRole.Secondary)
    // The face comes from the kit's surface look (K20): the same control pill as a chip, in every style.
    val look = kitSurfaceLook(KitSurfaceInput(role = SurfaceRole.Control, shape = KitShapeId.Pill, level = SurfaceLevel.Surface2))
    val text: AnnotatedString =
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.text1, fontWeight = FontWeight.SemiBold)) { append(agent) }
            if (!model.isNullOrBlank()) {
                append(" ")
                withStyle(SpanStyle(color = colors.text2)) { append(model) }
            }
        }
    KitTappable(
        onClick = onClick,
        onClickLabel = spoken,
        shape = KitShapeId.Pill,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = spoken; testTag = "kit-agent-model-chip" },
    ) {
        Row(
            Modifier.fillMaxWidth()
                .kitSurface(look)
                .padding(horizontal = Space.S3.dp, vertical = Space.S2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            if (!markId.isNullOrBlank()) KitProviderLogo(markId, agent, agent = true, markSize = 18.dp, slot = 18.dp)
            BasicText(
                text,
                Modifier.weight(1f),
                style = base,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            KitIcon(KitIcons.chevronDown, size = KitIconSize.Small, tone = TextTone.Secondary)
        }
    }
}
