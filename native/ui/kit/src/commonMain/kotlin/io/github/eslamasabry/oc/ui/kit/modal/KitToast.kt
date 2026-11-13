package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.pace
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlinx.coroutines.delay

/** One short confirmation: a glyph and up to four words ("Copied", "Key hidden", "Script pasted"). */
public data class KitToast(val message: String, val icon: ImageVector? = null, internal val id: Long = 0)

/**
 * The window's toast slot (owner reference 2026-11-07). [show] replaces whatever shows; it holds for [HOLD_MS]
 * (TalkBack reads it politely). The app's shell provides one for its window as [LocalKitToasts]; a page frame
 * (`KitScreen`, `KitPaneScreen`) with no slot above it provides its own, so every window that shows a kit page (the
 * widget panels, Android's Manage space page) has one.
 */
@Stable
public class KitToastState {
    internal var current: KitToast? by mutableStateOf(null)
        private set
    internal var shown: Boolean by mutableStateOf(false)
    private var next = 0L

    public fun show(message: String, icon: ImageVector? = null) {
        current = KitToast(message, icon, ++next)
        shown = true
    }

    internal fun hide(id: Long) {
        if (current?.id == id) shown = false
    }

    public companion object {
        public const val HOLD_MS: Long = 1800L
    }
}

public val LocalKitToasts: ProvidableCompositionLocal<KitToastState?> = staticCompositionLocalOf { null }

/**
 * The toast pill (owner reference 2026-11-07): it drops in from under the status bar on the Spatial spring (from 96 %
 * of its size), holds, then lifts out on QUICK. It never takes touches and never covers more than its pill. Calm: fades
 * only. Off: appears and leaves at once.
 *
 * States: hidden, arriving, holding, leaving
 * Text: frame, one line
 * Cap: 360 dp
 * Motion: drop Spatial + fade STANDARD, lift QUICK; Calm = fade; Off = instant (class Travel)
 */
@Composable
public fun KitToastHost(state: KitToastState, modifier: Modifier = Modifier) {
    val toast = state.current
    LaunchedEffect(toast?.id) {
        val id = toast?.id ?: return@LaunchedEffect
        delay(KitToastState.HOLD_MS)
        state.hide(id)
    }
    val motion = KitMotion.state
    val specs = KitMotion.specs
    Box(modifier.fillMaxWidth().statusBarsPadding().padding(top = Space.S2.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = state.shown && toast != null,
            enter =
                if (motion.travels)
                    slideInVertically(specs.spatial<IntOffset>()) { -it } + fadeIn(specs.pace(KitPace.Standard)) +
                        scaleIn(specs.spatial(), initialScale = 0.96f)
                else fadeIn(specs.pace(KitPace.Standard)),
            exit =
                if (motion.travels) slideOutVertically(specs.pace<IntOffset>(KitPace.Quick)) { -it } + fadeOut(specs.exit())
                else fadeOut(specs.exit()),
            label = "kit.toast",
        ) {
            val shown = toast ?: return@AnimatedVisibility
            ToastPill(shown)
        }
    }
}

@Composable
private fun ToastPill(toast: KitToast) {
    val colors = LocalKitColors.current
    val shape = KitShapeId.Pill.shape()
    Row(
        Modifier.widthIn(max = MAX_DP.dp)
            .clip(shape)
            .background(colors.surface3)
            .border(1.dp, colors.hairline, shape)
            .padding(horizontal = Space.S4.dp, vertical = Space.S2.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("kit-toast"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        toast.icon?.let { KitGlyph(it, colors.accent, 18.dp) }
        KitText(toast.message, role = TypeRole.Secondary, kind = KitTextKind.Frame, maxLines = 1)
    }
}

private const val MAX_DP = 360
