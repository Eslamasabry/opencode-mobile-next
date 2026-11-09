package io.github.eslamasabry.oc.ui.kit.foundation

import io.github.eslamasabry.oc.ui.kit.row.kitContainer
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** The retired tinted look's colours (Dart `KitPanel.borderOf` and the tonal wash), apart from the drawing. */
public object KitPanelRules {
    /** The border alpha of a tinted, non-attention tone. */
    public const val BORDER_ALPHA: Float = .45f // F: lib/ui/kit/kit_panel.dart:63

    /** The wash a tinted, non-attention tone lays over `surface1`. */
    public const val WASH_ALPHA: Float = .06f // F: lib/ui/kit/kit_panel.dart:163

    /** None (transparent) when neutral, since surface steps carry depth; `attentionLine` for needs you. */
    public fun border(colors: KitColors, tone: StatusTone): Color =
        when (tone) {
            StatusTone.Neutral -> Color.Transparent
            StatusTone.Attention -> colors.attentionLine
            else -> colors.tone(tone.text).copy(alpha = BORDER_ALPHA)
        }

    /** The panel's fill: `attentionSurface` over `surface1` for needs you, else the tone's wash over it. */
    public fun fill(colors: KitColors, tone: StatusTone): Color =
        (if (tone == StatusTone.Attention) colors.attentionSurface else colors.tone(tone.text).copy(alpha = WASH_ALPHA))
            .compositeOver(colors.surface1)
}

/**
 * A block of content the person works with (Dart `KitPanel`; design standard §3): one `surface1` panel, 18 dp corners,
 * no border and no shadow, 16 dp inside, with an optional header (a small [icon] and a headline [title]). With [tone]
 * neutral it is [KitSurfacePanel]'s look; with the default [padding] and no [onClick] it forwards to it directly. A
 * [tone] other than neutral keeps the retired tinted-border, tonal-wash look for its existing callers; new code names
 * `KitNeedsYou`, a request card or a notice for the attention look, or [KitSurfacePanel] for a plain grouped panel.
 * [onClick] makes the whole panel, padding band included, one target.
 *
 * States: none (a panel only draws its own fill, border and header)
 * Text: frame (title)
 * Cap: Reading
 * Motion: the kit press only
 * Hidden: none
 * Replaces: KitPanel (lib/ui/kit/kit_panel.dart)
 */
@Composable
public fun KitPanel(
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Neutral,
    icon: ImageVector? = null,
    title: String? = null,
    padding: PaddingValues? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val capped = modifier.kitContainer().kitMaxWidth(CapRole.Reading) // F: lib/ui/kit/kit_panel.dart:69
    if (tone == StatusTone.Neutral) Neutral(capped, icon, title, padding, onClick, content)
    else Retired(capped, tone, icon, title, padding, onClick, content)
}

@Composable
private fun Neutral(
    modifier: Modifier,
    icon: ImageVector?,
    title: String?,
    padding: PaddingValues?,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    if (onClick == null && padding == null) {
        KitSurfacePanel(modifier, title = title, icon = icon, content = content)
        return
    }
    val inside = padding ?: PaddingValues(LocalKitTokens.current.panelPadding.dp)
    // The tap sits outside the caller's padding, so the whole panel takes it and the press fills the panel shape.
    KitSurface(modifier, shape = KitShapeId.Panel, padding = KitSurfacePadding.None) {
        val body: @Composable () -> Unit = { Box(Modifier.fillMaxWidth().padding(inside)) { Header(icon, title, null, content) } }
        if (onClick == null) body() else KitTappable(onClick, Modifier.fillMaxWidth(), role = KitTappableRole.Row, shape = KitShapeId.Panel) { body() }
    }
}

@Composable
private fun Retired(
    modifier: Modifier,
    tone: StatusTone,
    icon: ImageVector?,
    title: String?,
    padding: PaddingValues?,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val colors = LocalKitColors.current
    val attention = tone == StatusTone.Attention
    val shapeId = if (attention) KitShapeId.Card else KitShapeId.Panel // F: lib/ui/kit/kit_panel.dart:121
    val shape = shapeId.shape()
    val fill = KitPanelRules.fill(colors, tone)
    // The tone's wash stays under every style.
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Content, shapeId, SurfaceLevel.Surface1, ownFill = fill, color = fill))
    val inside = padding ?: PaddingValues(LocalKitTokens.current.panelPadding.dp)
    val box = modifier.then(look.modifier).border(kitHairline(), KitPanelRules.border(colors, tone), shape).clip(shape)
    val body: @Composable () -> Unit = { Box(Modifier.fillMaxWidth().padding(inside)) { Header(icon, title, colors.tone(tone.text), content) } }
    Box(box) { if (onClick == null) body() else KitTappable(onClick, Modifier.fillMaxWidth(), role = KitTappableRole.Row, shape = shapeId) { body() } }
}

/** The panel's header: a small icon and a headline title, a label gap, then the content; [tint] null is the plain look. */
@Composable
private fun Header(icon: ImageVector?, title: String?, tint: Color?, content: @Composable () -> Unit) {
    if (icon == null && title == null) {
        content()
        return
    }
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            if (icon != null) {
                KitGlyph(icon, tint ?: colors.text2, tokens.smallIcon.dp)
                Spacer(Modifier.width(tokens.labelGap.dp))
            }
            if (title != null) {
                Box(Modifier.weight(1f)) {
                    if (tint == null) KitText(title, role = TypeRole.Headline)
                    else androidx.compose.foundation.text.BasicText(
                        title,
                        style = io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography.current.style(TypeRole.Headline).copy(color = tint),
                    )
                }
            }
        }
        Spacer(Modifier.height(tokens.labelGap.dp))
        content()
    }
}
