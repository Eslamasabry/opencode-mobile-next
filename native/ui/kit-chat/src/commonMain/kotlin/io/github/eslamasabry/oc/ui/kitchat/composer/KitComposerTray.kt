package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/**
 * One round button of the composer's tray (Dart `KitComposerTrayAction`): a kit glyph, its one word, and the reason
 * it is off ([disabledReason], e.g. "Available when the current run finishes"). The caller closes the tray before
 * [onPick] runs.
 */
public class KitComposerTrayAction(
    public val label: String,
    public val icon: ImageVector,
    public val onPick: () -> Unit,
    public val disabledReason: String? = null,
    public val testTag: String? = null,
)

/**
 * What "+" opens (Flutter `_ComposerTray`, plus-menu concept C, owner 20 Oct 2026): the message box grows upward by
 * ONE compact row of round matte buttons with a word under each (Photos, Camera, File, More), and a hairline between
 * the row and the field. No sheet, no glass. It comes and goes with [KitReveal]'s fold and fade (instant with reduced
 * motion). At a large text size on a narrow phone the words shrink together until the longest fits its share of the
 * row: whole words in one size, never "Ph…".
 *
 * States: closed, open, a button off with its reason
 * Text: frame (one line, shared fit)
 * Motion: KitReveal (fold + fade STANDARD in, QUICK out); Off = instant
 * Replaces: _ComposerTray, _TrayButton (lib/ui/kit/chat/kit_composer_tray.dart)
 */
@Composable
public fun KitComposerTray(open: Boolean, actions: List<KitComposerTrayAction>, modifier: Modifier = Modifier) {
    KitReveal(open && actions.isNotEmpty(), modifier) {
        Column(Modifier.fillMaxWidth().testTag("kit-composer-tray")) {
            TrayRow(actions, Modifier.fillMaxWidth().padding(top = Space.S3.dp, bottom = Space.S2.dp))
            KitDivider(Modifier.padding(horizontal = Space.S3.dp))
        }
    }
}

/** The one size factor every label of the row shares (Flutter `_ComposerTray`): 1 when the widest word fits. */
public fun kitTrayLabelFit(widest: Float, room: Float): Float =
    if (widest <= 0f || widest <= room) 1f else (room / widest).coerceIn(0f, 1f)

@Composable
private fun TrayRow(actions: List<KitComposerTrayAction>, modifier: Modifier) {
    val app = LocalKitAppDensity.current ?: LocalDensity.current
    val base = LocalKitTypography.current.style(TypeRole.Secondary)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        // KitText's frame size for this role (min(platform size, 1.3 × spec)), measured once per label set and width.
        val specSp = base.fontSize.value
        val frame = TextScalePolicy.frameDp(specSp, with(app) { specSp.sp.toDp().value })
        val unit = Density(app.density, 1f)
        val roomPx = constraints.maxWidth.toFloat() / actions.size - 2 * Space.S1 * app.density
        val fit =
            remember(actions.map { it.label }, constraints.maxWidth, frame, base) {
                val k = frame / specSp
                val style =
                    base.copy(
                        fontSize = frame.sp,
                        lineHeight = (base.lineHeight.value * k).sp,
                        letterSpacing = (base.letterSpacing.value * k).sp,
                    )
                val widest =
                    actions.maxOfOrNull {
                        measurer.measure(it.label, style, maxLines = 1, softWrap = false, density = unit).size.width
                    } ?: 0
                // A pixel of slack keeps rounding from ever cutting the longest word.
                kitTrayLabelFit(widest.toFloat(), (roomPx - 1f).coerceAtLeast(0f))
            }
        // Under the shrunk scale KitText's frame size is exactly frame × fit (a linear density never meets the cap).
        val labelDensity = if (fit < 1f) Density(app.density, frame * fit / specSp) else null
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            actions.forEach { action ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    TrayButton(action, labelDensity)
                }
            }
        }
    }
}

@Composable
private fun TrayButton(action: KitComposerTrayAction, labelDensity: Density?) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val enabled = action.disabledReason == null
    // Every style shows the word under its glyph (coordinator 2026-11-14, audit finding 20: Glass showed bare glyphs while
    // Solid named them). On the slab of glass the disc is the slab's quiet tint, never an opaque plate.
    val slab = LocalKitComposerSlab.current
    KitTappable(
        onClick = action.onPick,
        modifier = if (action.testTag != null) Modifier.testTag(action.testTag) else Modifier,
        enabled = enabled,
        disabledReason = action.disabledReason,
        onClickLabel = action.label,
    ) {
        Column(
            Modifier.padding(Space.S1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
            // A round matte disc (Solid's control face), the glyph at the card-mark size.
            Box(
                Modifier.size(tokens.minTarget.dp)
                    .background(if (slab) colors.text1.copy(alpha = TRAY_SLAB_TINT) else colors.surface3, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                KitIcon(
                    action.icon,
                    size = KitIconSize.Medium,
                    tone = if (enabled) TextTone.Primary else TextTone.Tertiary,
                )
            }
            val label: @Composable () -> Unit = {
                KitText(
                    action.label,
                    role = TypeRole.Secondary,
                    tone = if (enabled) TextTone.Primary else TextTone.Tertiary,
                    kind = KitTextKind.Frame,
                    // The column centres the word; centring inside the text too would lay it out in the full slot
                    // and push its last pixels past a word-sized box.
                    maxLines = 1,
                )
            }
            if (labelDensity == null) label()
            else CompositionLocalProvider(LocalKitAppDensity provides labelDensity, content = label)
        }
    }
}

/** A tray disc's face on the slab of glass: the words' ink at this alpha, as the slab's chips. */
private const val TRAY_SLAB_TINT = .08f
