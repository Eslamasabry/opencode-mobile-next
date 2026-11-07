package io.github.eslamasabry.oc.ui.kit.agent

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitMotion
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairlineBorder
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.logo.KitProviderLogoRules
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.pace
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatsNewSetUp
import org.jetbrains.compose.resources.stringResource

/** Where an agent stands on its card (Dart `KitAgentChoiceState`, lib/ui/kit/kit_agent_choice.dart). */
public enum class KitAgentChoiceState {
    /** Can be started now, not the chosen one: matte surface, hairline. */
    Available,

    /** The chosen one: a filled accent check badge in the corner and the style's chosen surface. */
    Selected,

    /** Known but not set up: dimmed, with "Set up" in place of the line; a tap opens its setup. */
    NeedsSetup,
}

/** What a screen reader hears: the name, then the line when there is one (Dart `label`). */
internal fun agentChoiceLabel(name: String, line: String): String = if (line.isEmpty()) name else "$name. $line"

/** The card's least height: two targets when it lies sideways or text is large, else three and a half. */
internal fun agentChoiceMinHeight(wide: Boolean, large: Boolean): Dp =
    if (wide || large) (FlutterKitTokens.MIN_TARGET * 2).dp // F: lib/ui/kit/kit_agent_choice.dart:259
    else (FlutterKitTokens.MIN_TARGET * 3.5f).dp // F: lib/ui/kit/kit_agent_choice.dart:260

/**
 * One agent as a large card in the start screen's grid (a port of Dart `KitAgentChoice`,
 * lib/ui/kit/kit_agent_choice.dart): its big monochrome mark ([markSlug], drawn from the app's logo loader; the
 * terminal glyph when the agent has none), its [name] in title size and a calm line of what it is good at
 * ([strength], up to two lines kept; "Set up" instead while [state] is [KitAgentChoiceState.NeedsSetup]), on a matte
 * surface with a hairline. Spanning a whole row of [KitAgentGrid] it lies sideways, the mark beside the words. The
 * chosen card shows a check badge that lands over the standard pace (instant under reduced motion). One mutually
 * exclusive choice with one tap target of at least 48 dp.
 *
 * States: none — selected, available and needs setup are looks of one card.
 */
@Composable
public fun KitAgentChoice(
    name: String,
    strength: String,
    markSlug: String,
    state: KitAgentChoiceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKitColors.current
    val selected = state == KitAgentChoiceState.Selected
    val dimmed = state == KitAgentChoiceState.NeedsSetup
    val large = kitTextScale() > 1.3f // F: lib/ui/kit/kit_agent_choice.dart:76
    val wide = LocalKitAgentGridSpan.current
    val line = if (dimmed) stringResource(Res.string.chatsNewSetUp) else strength
    val look =
        kitSurfaceLook(
            KitSurfaceInput(SurfaceRole.Content, KitShapeId.Card, level = SurfaceLevel.Surface2, ownFill = colors.surface1, selected = selected)
        )
    val solid = LocalKitStyle.current.style == Style.Solid
    val hairline = kitHairline()
    val edge = if (solid) Modifier.kitHairlineBorder(hairline, colors.hairline, look.shape) else Modifier
    // Dart `EdgeInsets.all(space5).add(decoration.padding)`: a drawn border takes its room inside the card.
    val inset = Space.S5.dp + if (solid) hairline else 0.dp
    val ink = if (selected && !solid) look.words else null
    val markBox = (FlutterKitTokens.MARK_SIZE + Space.S2).dp
    val mark: @Composable () -> Unit = {
        AgentMark(markSlug, if (dimmed) colors.text3 else colors.text1, if (wide) FlutterKitTokens.MARK_SIZE.dp else markBox)
    }
    val words: @Composable () -> Unit = {
        Words(name, line, large, wide, dimmed, ink)
    }
    KitTappable(
        onClick,
        modifier =
            modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().semantics {
                contentDescription = agentChoiceLabel(name, line)
                role = Role.RadioButton
                this.selected = selected
            },
        haptic = KitHaptic.Select,
        shape = KitShapeId.Card,
    ) {
        Box(
            Modifier.fillMaxWidth()
                .fillMaxHeight()
                .heightIn(min = agentChoiceMinHeight(wide, large))
                .then(look.modifier)
                .then(edge)
                .clearAndSetSemantics {}
        ) {
          // Compose lays out on whole pixels: the two sub-pixel hairlines of Flutter's height go to the bottom, rounded, so
          // rows of cards keep Flutter's pitch.
          Box(Modifier.fillMaxWidth().padding(start = inset, top = Space.S5.dp, end = inset, bottom = Space.S5.dp + (inset - Space.S5.dp) * 2)) { // NV: twice the hairline, top and bottom edge
            if (wide) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    mark()
                    Spacer(Modifier.width(Space.S4.dp))
                    Column(Modifier.weight(1f)) { words() }
                    Spacer(Modifier.width(Space.S2.dp))
                    Badge(selected)
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    mark()
                    Spacer(Modifier.height(Space.S5.dp))
                    words()
                }
            }
          }
            // Dart PositionedDirectional(top: -space1, end: -space1) over the padded column: placed from the card's
            // own edge here, so the badge sits in the card and not past its column.
            if (!wide) Box(Modifier.align(Alignment.TopEnd).padding(top = inset - Space.S1.dp, end = inset - Space.S1.dp)) { Badge(selected) }
        }
    }
}

/** The name in title size (headline at large text) and the line under it, which keeps room for two lines when stacked. */
@Composable
private fun Words(name: String, line: String, large: Boolean, wide: Boolean, dimmed: Boolean, ink: Color?) {
    val density = LocalDensity.current
    val rowTitle = LocalKitTypography.current.style(TypeRole.RowTitle)
    val lineHeight = if (rowTitle.lineHeight.isSpecified) rowTitle.lineHeight else rowTitle.fontSize * 1.4f // F: lib/ui/kit/kit_agent_choice.dart:162
    val twoLines = with(density) { (lineHeight * 2).toDp() } // F: lib/ui/kit/kit_agent_choice.dart:160
    Words(name, if (large) TypeRole.Headline else TypeRole.Title, if (dimmed) TextTone.Secondary else TextTone.Primary, 2, ink) // F: lib/ui/kit/kit_agent_choice.dart:170
    if (line.isNotEmpty()) {
        Spacer(Modifier.height(Space.S1.dp))
        Box(Modifier.heightIn(min = if (wide || large) 0.dp else twoLines)) {
            Words(line, TypeRole.RowTitle, if (dimmed) TextTone.Tertiary else TextTone.Secondary, 3, ink) // F: lib/ui/kit/kit_agent_choice.dart:183
        }
    }
}

/** Hook H3: on the chosen card of a style that sets its words in reverse, the words take that colour. */
@Composable
private fun Words(text: String, role: TypeRole, tone: TextTone, maxLines: Int, ink: Color?) {
    if (ink == null) {
        KitText(text, role = role, tone = tone, maxLines = maxLines)
    } else {
        BasicText(
            text,
            style = LocalKitTypography.current.style(role).copy(color = ink),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The agent's monochrome mark scaled to fill [box], or the terminal glyph while it has none. */
@Composable
private fun AgentMark(slug: String, tint: Color, box: Dp) {
    val loader = LocalKitLogoLoader.current
    val platform = LocalKitPlatform.current
    var svg by remember(slug, loader) { mutableStateOf<String?>(null) }
    if (slug.isNotEmpty()) LaunchedEffect(slug, loader) { svg = KitProviderLogoRules.load(loader, slug, agent = true) }
    val pixels = with(LocalDensity.current) { box.roundToPx() }.coerceIn(1, 256) // NV: the platform SVG decoder's pixel range (KitPlatform.svgPainter)
    val painter = remember(svg, platform, pixels) { svg?.let { platform.svgPainter(it, pixels) } }
    val shown = painter ?: if (slug.isEmpty()) rememberVectorPainter(KitIcons.terminal) else null
    // Dart KitIcon.mark: the picture is 0.84 of its square, centred; FittedBox scales that square to the box.
    val drawn = box * 0.84f // F: lib/ui/kit/kit_icon.dart:252
    Box(Modifier.size(box), contentAlignment = Alignment.Center) {
        if (shown != null) Image(shown, null, Modifier.size(if (painter != null) drawn else box), colorFilter = ColorFilter.tint(tint))
    }
}

/** A filled accent badge with the check that lands from slightly smaller when the card is chosen. */
@Composable
private fun Badge(selected: Boolean) {
    val colors = LocalKitColors.current
    val specs = KitMotion.specs
    val scale by animateFloatAsState(if (selected) 1f else FlutterKitMotion.MARK_FROM, specs.pace(KitPace.Standard), label = "kit.agent.badge.scale")
    val alpha by animateFloatAsState(if (selected) 1f else 0f, specs.pace(KitPace.Quick), label = "kit.agent.badge.alpha")
    val accent = colors.accent
    // Fully faded: nothing to draw (Dart's AnimatedOpacity at 0 paints nothing either).
    if (alpha == 0f) {
        Spacer(Modifier.size((FlutterKitTokens.ICON_TILE_SIZE - Space.S1).dp))
        return
    }
    Box(
        Modifier.size((FlutterKitTokens.ICON_TILE_SIZE - Space.S1).dp)
            .scale(scale)
            .alpha(alpha)
            .drawBehind { drawCircle(accent) },
        contentAlignment = Alignment.Center,
    ) {
        KitIcon(KitIcons.check, size = KitIconSize.Small, tone = TextTone.OnAccent)
    }
}
