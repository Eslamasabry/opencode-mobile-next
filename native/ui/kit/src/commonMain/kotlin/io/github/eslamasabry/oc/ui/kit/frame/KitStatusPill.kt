package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
        val gap = with(androidx.compose.ui.platform.LocalDensity.current) { Space.S2.dp.roundToPx() }
        // The name comes first and is never cut to make room for a transient word: the word is drawn only when it fits
        // whole beside the name; while connecting the spinner says it (the word is in the spoken label).
        val shownWord = word.takeIf { tone != KitConnectionTone.Working }
        androidx.compose.ui.layout.Layout(
            content = {
                Box(Modifier.size(tokens.smallIcon.dp), contentAlignment = Alignment.Center) {
                    if (tone == KitConnectionTone.Working && !KitMotion.state.reduced) {
                        KitWorkingMark(dot, size = tokens.smallIcon.dp)
                    } else {
                        Box(Modifier.size(Space.S2.dp).background(dot, CircleShape))
                    }
                }
                KitText(
                    label,
                    role = TypeRole.Secondary,
                    tone = TextTone.Primary,
                    kind = KitTextKind.Frame,
                    maxLines = 1,
                )
                if (shownWord != null) {
                    KitText(
                        "\u00b7 $shownWord",
                        Modifier.testTag("kit-status-pill-word"),
                        role = TypeRole.Secondary,
                        tone = TextTone.Secondary,
                        kind = KitTextKind.Frame,
                        maxLines = 1,
                    )
                }
                KitGlyph(KitIcons.chevronDown, colors.text2, tokens.smallIcon.dp)
            },
            modifier =
                Modifier.height(tokens.chipHeight.dp)
                    .then(look.modifier)
                    .padding(start = Space.S3.dp, end = Space.S2.dp),
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val mark = measurables[0].measure(loose)
            val chevron = measurables.last().measure(loose)
            val room = (constraints.maxWidth - mark.width - chevron.width - gap * 2).coerceAtLeast(0)
            val name = measurables[1].measure(loose.copy(maxWidth = room))
            val wordPlaceable =
                if (measurables.size > 3) measurables[2].measure(loose.copy(maxWidth = Int.MAX_VALUE / 4)) else null
            val wordFits = wordPlaceable != null && name.width + gap + wordPlaceable.width <= room
            val width = mark.width + gap + name.width + (if (wordFits) gap + wordPlaceable!!.width else 0) + gap + chevron.width
            val height = constraints.maxHeight.takeIf { it != androidx.compose.ui.unit.Constraints.Infinity } ?: maxOf(mark.height, name.height)
            layout(width, height) {
                var x = 0
                mark.placeRelative(x, (height - mark.height) / 2); x += mark.width + gap
                name.placeRelative(x, (height - name.height) / 2); x += name.width + gap
                if (wordFits) { wordPlaceable!!.placeRelative(x, (height - wordPlaceable.height) / 2); x += wordPlaceable.width + gap }
                chevron.placeRelative(x, (height - chevron.height) / 2)
            }
        }
    }
}
