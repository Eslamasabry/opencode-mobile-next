package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** What the server pill's mark says (Dart `AppStatusTone` as the pill draws it). */
public enum class KitConnectionTone {
    /** Connected: a green dot. */
    Ok,

    /** Connecting, reconnecting, switching: the working ring in the accent. */
    Working,

    /** Not answering, or the password was refused: an amber dot (never red). */
    Problem,

    /** Nothing to connect to yet: a quiet dot. */
    Off,
}

/**
 * The server pill (Dart `KitShellControls` pill, `kit_top_bar_pill.dart`): the state mark, "name · state" on one line
 * and the switcher chevron, on a `surface2` stadium the height of a touch target. The state word is never cut: a long
 * name gives way first. Tapping opens the server switcher; a screen reader hears "name, state, Switch server". The
 * mark cross-fades when the state changes (150 ms; still under reduced motion). The chevron points down in both
 * directions.
 *
 * States: ok, working, problem, off
 * Text: frame
 * Cap: none (the bar gives it its share)
 * Motion: mark cross-fade QUICK; Off = instant
 * Replaces: KitShellControls pill (lib/ui/kit/kit_top_bar.dart, kit_top_bar_pill.dart)
 */
@Composable
public fun KitConnectionPill(
    server: String,
    status: String,
    tone: KitConnectionTone,
    switchLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val solid = LocalKitStyle.current.style == io.github.eslamasabry.oc.ui.design.style.Style.Solid
    val look =
        kitSurfaceLook(
            KitSurfaceInput(SurfaceRole.Control, KitShapeId.Pill, SurfaceLevel.Surface2, ownFill = colors.surface2),
        )
    val spoken = "$server, $status, $switchLabel"
    Row(
        modifier
            .heightIn(min = tokens.minTarget.dp)
            .then(look.modifier)
            .then(
                if (solid) Modifier.border(kitHairline(), colors.hairline, CircleShape)
                else Modifier
            )
            .kitTappable(onClick = onClick, shape = KitShapeId.Pill, onClickLabel = switchLabel)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(start = Space.S3.dp, end = Space.S2.dp)
            .testTag("kit-connection-pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        val specs = KitMotion.specs
        AnimatedContent(
            targetState = tone,
            transitionSpec = { fadeIn(specs.quick()) togetherWith fadeOut(specs.quick()) },
            label = "kit-connection-mark",
        ) { shown -> ConnectionMark(shown) }
        // "name · state": the name may be cut, the state word never is.
        Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
            KitText(
                server,
                Modifier.weight(1f, fill = false),
                role = TypeRole.Secondary,
                tone = TextTone.Primary,
                kind = KitTextKind.Frame,
                maxLines = 1,
            )
            KitText(
                " · $status",
                role = TypeRole.Secondary,
                tone = TextTone.Primary,
                kind = KitTextKind.Frame,
                maxLines = 1,
            )
        }
        KitGlyph(KitIcons.chevronDown, colors.text2, tokens.smallIcon.dp)
    }
}

@Composable
private fun ConnectionMark(tone: KitConnectionTone) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    Box(Modifier.size(tokens.smallIcon.dp), contentAlignment = Alignment.Center) {
        if (tone == KitConnectionTone.Working && !KitMotion.state.reduced) {
            KitWorkingMark(colors.accent, size = tokens.smallIcon.dp)
        } else {
            val dot =
                when (tone) {
                    KitConnectionTone.Ok -> colors.success
                    KitConnectionTone.Working -> colors.accent
                    KitConnectionTone.Problem -> colors.attention
                    KitConnectionTone.Off -> colors.text3
                }
            Box(Modifier.size(Space.S2.dp).background(dot, CircleShape))
        }
    }
}

/**
 * The shell's bar on a phone (Dart `KitTopBar.shell` + `KitShellControls`, bar layout): the server pill at the start,
 * then the bar's round icon actions at the end, one gap apart — Search and Settings on the Conversations home. No
 * overflow: the bar holds only what is always needed (owner rule, obvious options first). Pass no [connection] when
 * nothing is saved yet; the actions then sit alone at the end.
 *
 * States: with server, without server
 * Text: frame
 * Cap: Fill
 * Motion: the pill's mark only
 * Replaces: KitTopBar.shell (lib/ui/kit/kit_top_bar.dart)
 */
@Composable
public fun KitShellBar(
    actions: List<KitLineAction>,
    modifier: Modifier = Modifier,
    connection: (@Composable (Modifier) -> Unit)? = null,
) {
    val tokens = LocalKitTokens.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = tokens.gutter.dp, end = tokens.gutter.dp, top = Space.S2.dp, bottom = Space.S2.dp)
            .semantics { isTraversalGroup = true }
            .testTag("kit-shell-bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { connection?.invoke(Modifier) }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S3.dp), verticalAlignment = Alignment.CenterVertically) {
            actions.forEach { action ->
                val glyph = action.icon ?: KitIcons.more
                KitIconButton(
                    icon = glyph,
                    label = action.semanticsLabel ?: action.label,
                    onClick = action.onClick,
                    modifier = action.testTag?.let { Modifier.testTag(it) } ?: Modifier,
                    enabled = KitEnabled.of(action.enabled, action.disabledReason),
                    round = true,
                    // Optically level with the gear: the lens reads larger at the same size (Dart kit_top_bar.dart).
                    size = if (glyph == KitIcons.search && actions.size > 1) 22 else 24,
                )
            }
        }
    }
}
