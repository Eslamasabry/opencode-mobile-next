package io.github.eslamasabry.oc.ui.kit.agent

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
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
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatsNewSetUp
import org.jetbrains.compose.resources.stringResource

/** Where an agent stands on its card (Dart `KitAgentChoiceState`). */
public enum class KitAgentChoiceState {
    /** Can be started now, not the chosen one: matte surface, hairline. */
    Available,

    /** The chosen one: the accent check badge in the corner and the style's chosen surface. */
    Selected,

    /** Known but not set up: dimmed, with "Set up" in place of the line; a tap opens its setup. */
    NeedsSetup,
}

/**
 * The bundled agent marks (Dart `KitIcon.mark(persona.markSlug)`, `assets/marks/<slug>.svg`), one flat colour each.
 * The path data is the SVG files' at the Flutter ref, with the compact arc flags written apart (the Compose path
 * parser reads "0 013" as one number); their view box is 24 × 24.
 */
public enum class KitAgentMark(internal val slug: String, private vararg val paths: String) {
    Claude("claude", "M4.709 15.955l4.72 -2.647 .08 -.23 -.08 -.128H9.2l-.79 -.048 -2.698 -.073 -2.339 -.097 -2.266 -.122 -.571 -.121L0 11.784l.055 -.352 .48 -.321 .686 .06 1.52 .103 2.278 .158 1.652 .097 2.449 .255h.389l.055 -.157 -.134 -.098 -.103 -.097 -2.358 -1.596 -2.552 -1.688 -1.336 -.972 -.724 -.491 -.364 -.462 -.158 -1.008 .656 -.722 .881 .06 .225 .061 .893 .686 1.908 1.476 2.491 1.833 .365 .304 .145 -.103 .019 -.073 -.164 -.274 -1.355 -2.446 -1.446 -2.49 -.644 -1.032 -.17 -.619a2.97 2.97 0 0 1 -.104 -.729L6.283 .134 6.696 0l.996 .134 .42 .364 .62 1.414 1.002 2.229 1.555 3.03 .456 .898 .243 .832 .091 .255h.158V9.01l.128 -1.706 .237 -2.095 .23 -2.695 .08 -.76 .376 -.91 .747 -.492 .584 .28 .48 .685 -.067 .444 -.286 1.851 -.559 2.903 -.364 1.942h.212l.243 -.242 .985 -1.306 1.652 -2.064 .73 -.82 .85 -.904 .547 -.431h1.033l.76 1.129 -.34 1.166 -1.064 1.347 -.881 1.142 -1.264 1.7 -.79 1.36 .073 .11 .188 -.02 2.856 -.606 1.543 -.28 1.841 -.315 .833 .388 .091 .395 -.328 .807 -1.969 .486 -2.309 .462 -3.439 .813 -.042 .03 .049 .061 1.549 .146 .662 .036h1.622l3.02 .225 .79 .522 .474 .638 -.079 .485 -1.215 .62 -1.64 -.389 -3.829 -.91 -1.312 -.329h-.182v.11l1.093 1.068 2.006 1.81 2.509 2.33 .127 .578 -.322 .455 -.34 -.049 -2.205 -1.657 -.851 -.747 -1.926 -1.62h-.128v.17l.444 .649 2.345 3.521 .122 1.08 -.17 .353 -.608 .213 -.668 -.122 -1.374 -1.925 -1.415 -2.167 -1.143 -1.943 -.14 .08 -.674 7.254 -.316 .37 -.729 .28 -.607 -.461 -.322 -.747 .322 -1.476 .389 -1.924 .315 -1.53 .286 -1.9 .17 -.632 -.012 -.042 -.14 .018 -1.434 1.967 -2.18 2.945 -1.726 1.845 -.414 .164 -.717 -.37 .067 -.662 .401 -.589 2.388 -3.036 1.44 -1.882 .93 -1.086 -.006 -.158h-.055L4.132 18.56l-1.13 .146 -.487 -.456 .061 -.746 .231 -.243 1.908 -1.312 -.006 .006z"),
    Codex("codex", "M8.086 .457a6.105 6.105 0 0 1 3.046 -.415c1.333 .153 2.521 .72 3.564 1.7a.117 .117 0 0 0 .107 .029c1.408 -.346 2.762 -.224 4.061 .366l.063 .03 .154 .076c1.357 .703 2.33 1.77 2.918 3.198 .278 .679 .418 1.388 .421 2.126a5.655 5.655 0 0 1 -.18 1.631 .167 .167 0 0 0 .04 .155 5.982 5.982 0 0 1 1.578 2.891c.385 1.901 -.01 3.615 -1.183 5.14l-.182 .22a6.063 6.063 0 0 1 -2.934 1.851 .162 .162 0 0 0 -.108 .102c-.255 .736 -.511 1.364 -.987 1.992 -1.199 1.582 -2.962 2.462 -4.948 2.451 -1.583 -.008 -2.986 -.587 -4.21 -1.736a.145 .145 0 0 0 -.14 -.032c-.518 .167 -1.04 .191 -1.604 .185a5.924 5.924 0 0 1 -2.595 -.622 6.058 6.058 0 0 1 -2.146 -1.781c-.203 -.269 -.404 -.522 -.551 -.821a7.74 7.74 0 0 1 -.495 -1.283 6.11 6.11 0 0 1 -.017 -3.064 .166 .166 0 0 0 .008 -.074 .115 .115 0 0 0 -.037 -.064 5.958 5.958 0 0 1 -1.38 -2.202 5.196 5.196 0 0 1 -.333 -1.589 6.915 6.915 0 0 1 .188 -2.132c.45 -1.484 1.309 -2.648 2.577 -3.493 .282 -.188 .55 -.334 .802 -.438 .286 -.12 .573 -.22 .861 -.304a.129 .129 0 0 0 .087 -.087A6.016 6.016 0 0 1 5.635 2.31C6.315 1.464 7.132 .846 8.086 .457zm-.804 7.85a.848 .848 0 0 0 -1.473 .842l1.694 2.965 -1.688 2.848a.849 .849 0 0 0 1.46 .864l1.94 -3.272a.849 .849 0 0 0 .007 -.854l-1.94 -3.393zm5.446 6.24a.849 .849 0 0 0 0 1.695h4.848a.849 .849 0 0 0 0 -1.696h-4.848z"),
    Gemini("gemini", "M20.616 10.835a14.147 14.147 0 0 1 -4.45 -3.001 14.111 14.111 0 0 1 -3.678 -6.452 .503 .503 0 0 0 -.975 0 14.134 14.134 0 0 1 -3.679 6.452 14.155 14.155 0 0 1 -4.45 3.001c-.65 .28 -1.318 .505 -2.002 .678a.502 .502 0 0 0 0 .975c.684 .172 1.35 .397 2.002 .677a14.147 14.147 0 0 1 4.45 3.001 14.112 14.112 0 0 1 3.679 6.453 .502 .502 0 0 0 .975 0c.172 -.685 .397 -1.351 .677 -2.003a14.145 14.145 0 0 1 3.001 -4.45 14.113 14.113 0 0 1 6.453 -3.678 .503 .503 0 0 0 0 -.975 13.245 13.245 0 0 1 -2.003 -.678z"),
    Kilo("kilocode", "M0 0v24h24V0H0zm22.222 22.222H1.778V1.778h20.444v20.444zm-7.555 -4.964h2.222v1.778h-2.794L12.89 17.83v-2.794h1.778v2.222zm4 0h-1.778v-2.222h-2.222v-1.778h2.793l1.207 1.207v2.793zm-7.556 -2.591H9.333v-1.778h1.778v1.778zm-5.778 -1.778h1.778v4h4v1.778H6.54L5.333 17.46V12.89zm13.334 -3.556v1.778h-5.778V9.333h1.987V7.111h-1.987V5.333h2.558l1.206 1.207v2.793h2.014zm-11.556 -2h2.222l1.778 1.778v2H9.333v-2H7.111v2H5.333V5.333h1.778v2zm4 0H9.333v-2h1.778v2z"),
    Cline("cline", "M17.035 3.991c2.75 0 4.98 2.24 4.98 5.003v1.667l1.45 2.896a1.01 1.01 0 0 1 -.002 .909l-1.448 2.864v1.668c0 2.762 -2.23 5.002 -4.98 5.002H7.074c-2.751 0 -4.98 -2.24 -4.98 -5.002V17.33l-1.48 -2.855a1.01 1.01 0 0 1 -.003 -.927l1.482 -2.887V8.994c0 -2.763 2.23 -5.003 4.98 -5.003h9.962zM8.265 9.6a2.274 2.274 0 0 0 -2.274 2.274v4.042a2.274 2.274 0 0 0 4.547 0v-4.042A2.274 2.274 0 0 0 8.265 9.6zm7.326 0a2.274 2.274 0 0 0 -2.274 2.274v4.042a2.274 2.274 0 1 0 4.548 0v-4.042A2.274 2.274 0 0 0 15.59 9.6z", "M12.054 5.558a2.779 2.779 0 1 0 0 -5.558 2.779 2.779 0 0 0 0 5.558z"),
    OpenCode("opencode", "M16 6H8v12h8V6zm4 16H4V2h16v20z"),
    ;

    /** The mark as a vector in its 24 × 24 view box (the SVG's `viewBox="0 0 24 24"`). */
    public val vector: ImageVector by lazy {
        val box = 24f // NV: the view box of every assets/marks/*.svg
        ImageVector.Builder("mark.$slug", box.dp, box.dp, box, box)
            .apply { for (d in paths) addPath(addPathNodes(d), fill = SolidColor(Color.Black)) }
            .build()
    }
}

/** Set by [KitAgentGrid] around a card that spans the whole row (Dart `KitAgentGridSpan`). */
internal val LocalKitAgentGridSpan = staticCompositionLocalOf { false }

/** Agent cards in a grid lay out stacked, or sideways when they span the row (Dart `KitAgentGridSpan.wideOf`). */
@Composable
internal fun KitAgentGridSpan(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKitAgentGridSpan provides true, content = content)
}

/**
 * One agent as a large card in the start screen's grid (Dart `KitAgentChoice`, lib/ui/kit/kit_agent_choice.dart): its
 * big monochrome mark, its name in title size and a calm line (up to two) of what it is good at, on a matte surface
 * with a hairline. Spanning a whole row of a [KitAgentGrid] it lays out sideways, the mark beside the words. The
 * chosen card wears the accent check badge, which lands over STANDARD (instantly under reduced motion); the surface
 * comes from the style resolver. One tap target of at least 48 dp; a mutually exclusive choice.
 *
 * States: none — [KitAgentChoiceState.Selected], [KitAgentChoiceState.Available] and
 * [KitAgentChoiceState.NeedsSetup] are looks of one card.
 * Text: reading
 * Cap: Reading (720)
 * Motion: badge scale STANDARD + fade QUICK; Off = instant
 * Replaces: KitAgentChoice (lib/ui/kit/kit_agent_choice.dart)
 *
 * [name] is the agent's name; [line] its persona strength (empty for an agent with no persona of its own); [mark] the
 * agent's bundled mark, null for the terminal glyph.
 */
@Composable
public fun KitAgentChoice(
    name: String,
    line: String,
    state: KitAgentChoiceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mark: KitAgentMark? = null,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val selected = state == KitAgentChoiceState.Selected
    val dimmed = state == KitAgentChoiceState.NeedsSetup
    val large = kitTextScale() > 1.3f // F: lib/ui/kit/kit_agent_choice.dart:76
    val wide = LocalKitAgentGridSpan.current
    val words = if (dimmed) stringResource(Res.string.chatsNewSetUp) else line
    val specs = KitMotion.specs
    val look = kitSurfaceLook(
        KitSurfaceInput(SurfaceRole.Content, KitShapeId.Card, level = SurfaceLevel.Surface2, ownFill = colors.surface1, selected = selected),
    )
    val solid = LocalKitStyle.current.style == Style.Solid
    val edge = if (solid) Modifier.border(kitHairline(), colors.hairline, look.shape) else Modifier
    // A drawn border takes its room inside the card, as a container's does (Dart `.add(decoration.padding)`).
    val inset = Space.S5.dp + if (solid) kitHairline() else 0.dp

    @Composable
    fun Words(text: String, role: TypeRole, tone: TextTone, maxLines: Int, textModifier: Modifier = Modifier) {
        // Hook H3: on the chosen card the words take the colour the style says reads on its chosen face.
        val ink = if (selected) look.words else null
        if (ink == null) {
            KitText(text, textModifier, role = role, tone = tone, maxLines = maxLines)
        } else {
            BasicText(text, textModifier, style = LocalKitTypography.current.style(role).copy(color = ink), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
    }

    // The mark is the card's hero: the 24 dp designed mark drawn a little larger than a mark tile.
    val markBox: Dp = (FlutterKitTokens.MARK_SIZE + Space.S2).dp
    val markSide = if (wide) FlutterKitTokens.MARK_SIZE.dp else markBox
    val markInk = if (dimmed) colors.text3 else colors.text1
    val markPart: @Composable () -> Unit = {
        Box(Modifier.size(markSide), contentAlignment = Alignment.Center) {
            if (mark != null) {
                KitGlyph(mark.vector, markInk, markSide * 0.84f) // F: lib/ui/kit/kit_icon.dart:252
            } else {
                KitGlyph(KitIcons.terminal, markInk, markSide)
            }
        }
    }
    val badgeScale by animateFloatAsState(if (selected) 1f else MotionTokens.MARK_FROM, specs.enter(), label = "kit.agent.badge.scale")
    val badgeAlpha by animateFloatAsState(if (selected) 1f else 0f, specs.quick(), label = "kit.agent.badge.alpha")
    val badge: @Composable () -> Unit = {
        val side = (tokens.iconTile - Space.S1).dp
        Box(
            Modifier.size(side)
                .graphicsLayer { scaleX = badgeScale; scaleY = badgeScale; alpha = badgeAlpha }
                .background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            KitGlyph(KitIcons.check, colors.onAccent, KitIconSize.Small.dp.dp)
        }
    }
    val rowTitle = LocalKitTypography.current.style(TypeRole.RowTitle)
    val twoLines = with(LocalDensity.current) { rowTitle.lineHeight.toDp() } * 2 // F: lib/ui/kit/kit_agent_choice.dart:159-160
    val wordsPart: @Composable () -> Unit = {
        Words(name, if (large) TypeRole.Headline else TypeRole.Title, if (dimmed) TextTone.Secondary else TextTone.Primary, maxLines = 2) // F: lib/ui/kit/kit_agent_choice.dart:170
        if (words.isNotEmpty()) {
            Spacer(Modifier.height(Space.S1.dp))
            // The stacked card keeps room for two lines, so a pair of cards stands equally tall.
            Words(
                words,
                TypeRole.RowTitle,
                if (dimmed) TextTone.Tertiary else TextTone.Secondary,
                maxLines = 3, // F: lib/ui/kit/kit_agent_choice.dart:183
                textModifier = Modifier.heightIn(min = if (wide || large) 0.dp else twoLines),
            )
        }
    }
    val label = if (words.isEmpty()) name else "$name. $words"
    KitTappable(
        onClick,
        modifier = modifier.kitMaxWidth(CapRole.Reading).semantics {
            contentDescription = label
            this.selected = selected
            role = Role.RadioButton
        },
        shape = KitShapeId.Card,
    ) {
        Box(
            Modifier.fillMaxWidth()
                .heightIn(min = (tokens.minTarget * if (wide || large) 2f else 3.5f).dp) // F: lib/ui/kit/kit_agent_choice.dart:259-260
                .then(look.modifier)
                .then(edge)
                .padding(inset)
                .clearAndSetSemantics { },
        ) {
            if (wide) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    markPart()
                    Spacer(Modifier.width(Space.S4.dp))
                    Column(Modifier.weight(1f)) { wordsPart() }
                    Spacer(Modifier.width(Space.S2.dp))
                    badge()
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth()) { markPart() }
                    Spacer(Modifier.height(Space.S5.dp))
                    wordsPart()
                }
                Box(Modifier.align(Alignment.TopEnd).offset(x = Space.S1.dp, y = -Space.S1.dp)) { badge() }
            }
        }
    }
}
