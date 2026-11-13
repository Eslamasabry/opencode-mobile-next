package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitGlassTuning
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.KitStyleState
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import kotlinx.coroutines.launch

/** How far a [KitTuningDrawer] is open: 0 = only its handle shows, 1 = fully open. */
@Stable
public class KitTuningDrawerState internal constructor(open: Float) {
    internal val open: Animatable<Float, androidx.compose.animation.core.AnimationVector1D> = Animatable(open)

    /** How far it is open now (0-1). */
    public val fraction: Float get() = open.value
}

@Composable
public fun rememberKitTuningDrawerState(open: Boolean = true): KitTuningDrawerState = remember { KitTuningDrawerState(if (open) 1f else 0f) }

/**
 * A solid panel of controls at the bottom of a page (the Glass lab, 2026-11-13): always drawn in the Solid style, so it
 * never looks like glass beside the glass it tunes. Its handle drags it down to a thin strip, so the whole page above
 * shows, and back up; a tap on the handle opens or closes it. [header] stays visible at every height below the handle
 * while it is open; [content] scrolls under it. [height] is its open height (half the window by default).
 *
 * States: open, between (dragging), closed to the handle
 * Text: none of its own ([handleLabel] is spoken)
 * Motion: settles to open or closed on release (quick)
 */
@Composable
public fun KitTuningDrawer(
    handleLabel: String,
    openWord: String,
    closedWord: String,
    modifier: Modifier = Modifier,
    state: KitTuningDrawerState = rememberKitTuningDrawerState(),
    height: Dp = (LocalKitWindow.current.heightDp * .5f).dp,
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val px = with(LocalDensity.current) { height.toPx() }.coerceAtLeast(1f)
    val quick = KitMotion.specs.quick<Float>()
    fun settle() = scope.launch { state.open.animateTo(if (state.open.value >= .5f) 1f else 0f, quick) }
    fun toggle() = scope.launch { state.open.animateTo(if (state.open.value >= .5f) 0f else 1f, quick) }
    // Solid, whatever the app's style: the controls are not glass and never read as glass.
    CompositionLocalProvider(LocalKitStyle provides KitStyleState.Solid, LocalKitGlassTuning provides null) {
        KitSurface(modifier.fillMaxWidth().testTag("kit-tuning-drawer"), level = SurfaceLevel.Surface2, shape = KitShapeId.Sheet, padding = KitSurfacePadding.None) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                val colors = LocalKitColors.current
                Box(
                    Modifier.fillMaxWidth()
                        .height(HANDLE_DP.dp)
                        .pointerInput(px) {
                            detectVerticalDragGestures(
                                onDragEnd = { settle() },
                                onDragCancel = { settle() },
                                onVerticalDrag = { change, dy ->
                                    change.consume()
                                    scope.launch { state.open.snapTo((state.open.value - dy / px).coerceIn(0f, 1f)) }
                                },
                            )
                        }
                        .pointerInput(Unit) { detectTapGestures { toggle() } }
                        .semantics {
                            contentDescription = handleLabel
                            stateDescription = if (state.open.value >= .5f) openWord else closedWord
                            onClick { toggle(); true }
                        }
                        .testTag("kit-tuning-drawer-handle"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = (Space.S6 + Space.S2).dp, height = Space.S1.dp).background(colors.text3, RoundedCornerShape(Space.S1.dp)))
                }
                val open = state.open.value
                if (open > 0f) {
                    Column(Modifier.fillMaxWidth().height(height * open)) {
                        header()
                        Column(
                            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = Space.S4.dp),
                            content = content,
                        )
                    }
                }
            }
        }
    }
}

/** The handle strip's height, dp: a thumb-sized target that is all that shows when the drawer is closed. */
private const val HANDLE_DP: Int = 28
