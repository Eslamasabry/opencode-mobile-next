package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowHost
import io.github.eslamasabry.oc.ui.kit.motion.LocalKitGrowHost
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.RouteMotion
import io.github.eslamasabry.oc.ui.shell.nav.ShellMotion.forward
import io.github.eslamasabry.oc.ui.shell.nav.ShellMotion.pop

/**
 * Option A (ADR §9.4, plan 08 §5.4): the container transform runs on tap and on button-back; while a back gesture is
 * in progress (and until its transition settles) the shared bounds are off and the Material predictive preview runs.
 * The gate is cleared by state (entries composed ≤ 1), never by a timer.
 */
@Composable
public fun ShellNavDisplay(
    backStack: List<NavKey>,
    gate: RowTransformGate,
    level: ShellMotionLevel,
    onSystemBack: () -> Unit,
    baseDecorators: List<NavEntryDecorator<NavKey>>,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
) {
    val levelState = rememberUpdatedState(level)
    val signals = remember { GestureSignals() }

    // GestureStarted / GestureCancelled from the dispatcher NavDisplay registers on (plan 08 §5.4).
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    if (dispatcher != null) {
        LaunchedEffect(dispatcher, gate) {
            var wasInProgress = false
            dispatcher.transitionState.collect { t ->
                val inProgress = t is NavigationEventTransitionState.InProgress
                if (t is NavigationEventTransitionState.InProgress) signals.progress = t.latestEvent.progress
                if (inProgress && !wasInProgress) gate.on(GateEvent.GestureStarted)
                if (!inProgress && wasInProgress) gate.on(GateEvent.GestureCancelled) // no-op after a commit
                wasInProgress = inProgress
            }
        }
    }
    // TransitionSettled: one entry composed means no entry transition is running (the popped or covered one left).
    LaunchedEffect(gate) {
        snapshotFlow { gate.state to (signals.composedEntries <= 1) }
            .collect { (state, settled) ->
                if (settled && state != GateState.Idle && state != GateState.Gesture) gate.on(GateEvent.TransitionSettled)
            }
    }

    val density = LocalDensity.current
    val width = LocalWindowInfo.current.containerSize.width
    val edgeShiftPx = ShellMotion.edgeShiftPx(width, density.density)
    val travelPx = with(density) { ShellMotion.PAGE_TRAVEL_DP.dp.roundToPx() }
    val previewCornerPx = with(density) { ShellMotion.PREVIEW_CORNER_DP.dp.toPx() }

    val withMotion: (NavKey) -> NavEntry<NavKey> = remember(entryProvider) {
        { key ->
            val entry = entryProvider(key)
            val motion = (key as? AppRoute)?.motion
            if (motion == null) entry
            else NavEntry(key, entry.contentKey, entry.metadata + (ShellMotion.MOTION_KEY to motion)) { entry.Content() }
        }
    }

    SharedTransitionLayout {
        val sharedScope = this
        val growDecorator = remember(sharedScope, gate) {
            NavEntryDecorator<NavKey>(
                decorate = { entry ->
                    val contentScope = LocalNavAnimatedContentScope.current
                    DisposableEffect(Unit) {
                        signals.composedEntries++
                        onDispose { signals.composedEntries-- }
                    }
                    val host = remember(contentScope) { ShellGrowHost(sharedScope, contentScope, gate, levelState) }
                    Box(Modifier.predictiveCorners(contentScope, gate, signals, previewCornerPx)) {
                        CompositionLocalProvider(LocalKitGrowHost provides host) { entry.Content() }
                    }
                },
            )
        }
        NavDisplay(
            backStack = backStack,
            onBack = {
                val gesture = gate.state == GateState.Gesture || gate.state == GateState.GestureSettling
                gate.on(if (gesture) GateEvent.GestureCommitted else GateEvent.ButtonBack)
                onSystemBack()
            },
            entryDecorators = baseDecorators + growDecorator,
            sharedTransitionScope = sharedScope,
            transitionSpec = { forward(targetState.motion(), levelState.value, travelPx) },
            popTransitionSpec = { pop(initialState.motion(), levelState.value, travelPx) },
            predictivePopTransitionSpec = { edge -> ShellMotion.predictivePop(edge, levelState.value, edgeShiftPx) },
            entryProvider = withMotion,
        )
    }
}

private fun androidx.navigation3.scene.Scene<NavKey>.motion(): RouteMotion? =
    entries.lastOrNull()?.metadata?.get(ShellMotion.MOTION_KEY) as? RouteMotion

@Stable
internal class GestureSignals {
    var composedEntries by mutableIntStateOf(0)
    var progress by mutableFloatStateOf(0f)
}

/** The per-entry host the kit's grow parts read (`LocalKitGrowHost`). */
@Stable
internal class ShellGrowHost(
    override val sharedTransitionScope: SharedTransitionScope,
    override val animatedVisibilityScope: AnimatedVisibilityScope,
    private val gate: RowTransformGate,
    private val level: androidx.compose.runtime.State<ShellMotionLevel>,
) : KitGrowHost {
    override val transformEnabled: Boolean
        get() = sharedBoundsEnabled(gate.state, level.value)

    override val opening: Boolean
        get() = gate.opening
}

/**
 * The preview's corners (plan 08 §5.4): the page leaving under the finger rounds to 28 dp with eased progress. Read in
 * the draw phase only; the page underneath is untouched.
 */
private fun Modifier.predictiveCorners(
    scope: AnimatedVisibilityScope,
    gate: RowTransformGate,
    signals: GestureSignals,
    cornerPx: Float,
): Modifier = graphicsLayer {
    val leaving = scope.transition.targetState == EnterExitState.PostExit
    val previewing = gate.state == GateState.Gesture || gate.state == GateState.GestureSettling
    if (leaving && previewing) {
        val r = cornerPx * ShellMotion.PredictiveBack.transform(signals.progress.coerceIn(0f, 1f))
        shape = RoundedCornerShape(r)
        clip = true
    } else {
        clip = false
    }
}
