package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * A tonal pill for a thing that is not a filter and opens a chooser: the server switcher on the Conversations home
 * (Dart `KitStatusPill`, `lib/ui/kit/kit_status_pill.dart`). Filled, fully rounded, no outline; the fill is the style's own
 * control surface (owner, 2026-11-08: only the dot carries the status, in the theme's accent while connected, the muted text colour otherwise). It holds the status dot, the [label] and a
 * down chevron (which never mirrors), and says a [word] only when the state is not the healthy one ("Offline",
 * "Connecting…"): the dot carries "connected". The name is cut with an ellipsis; the pill never scrolls (its host keeps
 * it fixed). The tint cross-fades and the word grows in on the kit's quick motion; both are instant under reduced
 * motion. A 32 dp pill inside a 48 dp target; frame text, so it stops growing at 1.3x.
 *
 * States: ok, working, problem, off
 * Text: frame
 * Cap: none (the row gives it its share)
 * Motion: tint cross-fade and word growth QUICK; Off = instant
 * Replaces: nothing (new; the server switcher's pill beside the filters)
 */
@Composable
public fun KitStatusPill(
    label: String,
    tone: KitConnectionTone,
    spoken: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    word: String? = null,
    onClickLabel: String? = null,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val ink =
        when (tone) {
            KitConnectionTone.Ok -> colors.accent
            KitConnectionTone.Working, KitConnectionTone.Problem, KitConnectionTone.Off -> colors.text3
        }
    val dot by animateColorAsState(ink, specs.quick(), label = "kit-status-pill-dot")
    val look = kitSurfaceLook(KitSurfaceInput(role = SurfaceRole.Control, shape = KitShapeId.Pill, level = SurfaceLevel.Surface3, ownFill = colors.surface3))
    Box(
        modifier
            .heightIn(min = tokens.minTarget.dp)
            .kitTappable(onClick = onClick, shape = KitShapeId.Pill, onClickLabel = onClickLabel)
            .clearAndSetSemantics {
                contentDescription = spoken
                testTag = "kit-status-pill"
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .height(tokens.chipHeight.dp)
                .then(look.modifier)
                .padding(start = Space.S3.dp, end = Space.S2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            Box(Modifier.size(tokens.smallIcon.dp), contentAlignment = Alignment.Center) {
                if (tone == KitConnectionTone.Working && !KitMotion.state.reduced) {
                    KitWorkingMark(dot, size = tokens.smallIcon.dp)
                } else {
                    Box(Modifier.size(Space.S2.dp).background(dot, CircleShape))
                }
            }
            KitText(
                label,
                Modifier.weight(1f, fill = false),
                role = TypeRole.Secondary,
                tone = TextTone.Primary,
                kind = KitTextKind.Frame,
                maxLines = 1,
            )
            Box(Modifier.animateContentSize(specs.quick())) {
                if (word != null) {
                    KitText(
                        "· $word",
                        Modifier.testTag("kit-status-pill-word"),
                        role = TypeRole.Secondary,
                        tone = TextTone.Secondary,
                        kind = KitTextKind.Frame,
                        maxLines = 1,
                    )
                }
            }
            KitGlyph(KitIcons.chevronDown, colors.text2, tokens.smallIcon.dp)
        }
    }
}
