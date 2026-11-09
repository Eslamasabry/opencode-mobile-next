package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
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
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.LocalKitGrowHost
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.RouteMotion
import io.github.eslamasabry.oc.ui.shell.nav.ShellMotion.forward
import io.github.eslamasabry.oc.ui.shell.nav.ShellMotion.pop

/**
 * The container transform runs on tap, on button-back and under a predictive back gesture (motion lead 2026-11-07,
 * replacing option A): the gesture seeks the same transform, so a chat shrinks toward its row with the finger, a cancel
 * grows it back and a commit finishes the flight into the row. Other routes keep the Material predictive preview. The
 * gate is cleared by state (entries composed ≤ 1), never by a timer.
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
    // Follow the same resolved policy as kit parts: the person's motion choice, system Remove animations, Saver and
    // heat all apply to navigation too. Calm (and power saver, heat) cross-fades pages; only Off is instant (M-013).
    val effectiveLevel = shellLevel(level, KitMotion.state.level)
    val levelState = rememberUpdatedState(effectiveLevel)
    val signals = remember { GestureSignals() }
    // Which pages the transition in flight joins: rows pair with a page only when one of them grows from a row.
    val growRoutes = remember { GrowRoutes(backStack.lastOrNull()) }
    growRoutes.onTop(backStack.lastOrNull())

    // GestureStarted / GestureCancelled from the dispatcher NavDisplay registers on (plan 08 §5.4).
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    if (dispatcher != null) {
        LaunchedEffect(dispatcher, gate) {
            var wasInProgress = false
            dispatcher.transitionState.collect { t ->
                val inProgress = t is NavigationEventTransitionState.InProgress
                if (t is NavigationEventTransitionState.InProgress) signals.progress = t.latestEvent.progress
                if (inProgress && !wasInProgress) {
                    // A gesture pops the top page: only it and the page beneath take part from here.
                    growRoutes.settle()
                    gate.on(GateEvent.GestureStarted)
                }
                if (!inProgress && wasInProgress) gate.on(GateEvent.GestureCancelled) // no-op after a commit
                wasInProgress = inProgress
            }
        }
    }
    // One entry composed: the page that left is gone, so the pair it formed with the top page is over.
    LaunchedEffect(growRoutes) {
        snapshotFlow { signals.composedEntries <= 1 }.collect { settled -> if (settled) growRoutes.settle() }
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
    val previewCornerPx = with(density) { ShellMotion.PREVIEW_CORNER_DP.dp.toPx() }

    val withMotion: (NavKey) -> NavEntry<NavKey> = remember(entryProvider) {
        { key ->
            val entry = entryProvider(key)
            val motion = (key as? AppRoute)?.motion
            if (motion == null) entry
            else NavEntry(key, entry.contentKey, entry.metadata + (ShellMotion.MOTION_KEY to motion)) { entry.Content() }
        }
    }

    // Flutter's fade-forwards paints the page colour under both pages (its backgroundColor): while the covered page has
    // faded and the arriving one is still a quarter width short and half transparent, the ground shows there, never
    // the window (dark under a light theme: the "weird" grey flash of owner 2026-11-09).
    SharedTransitionLayout(Modifier.background(LocalKitColors.current.ground.copy(alpha = 1f))) {
        val sharedScope = this
        val growDecorator = remember(sharedScope, gate) {
            NavEntryDecorator<NavKey>(
                decorate = { entry ->
                    val contentScope = LocalNavAnimatedContentScope.current
                    DisposableEffect(Unit) {
                        signals.composedEntries++
                        onDispose { signals.composedEntries-- }
                    }
                    val host =
                        remember(contentScope) { ShellGrowHost(sharedScope, contentScope, gate, levelState, growRoutes) }
                    val grows = (entry.metadata[ShellMotion.MOTION_KEY] as? RouteMotion) == RouteMotion.GrowFromRow
                    Box(Modifier.predictiveCorners(contentScope, gate, signals, previewCornerPx, levelState.value, grows)) {
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
            transitionSpec = { forward(targetState.motion(), levelState.value) },
            popTransitionSpec = { pop(initialState.motion(), levelState.value) },
            predictivePopTransitionSpec = { edge ->
                ShellMotion.predictivePop(
                    edge,
                    levelState.value,
                    edgeShiftPx,
                    initialState.motion(),
                    keep = ExitTransition.KeepUntilTransitionsFinished,
                )
            },
            entryProvider = withMotion,
        )
    }
}

private fun androidx.navigation3.scene.Scene<NavKey>.motion(): RouteMotion? =
    entries.lastOrNull()?.metadata?.get(ShellMotion.MOTION_KEY) as? RouteMotion

/**
 * The pages the navigation in flight joins, for the row ↔ page transform: the top page and, until the transition
 * settles, the page that was on top before it. Updated in composition, before NavDisplay starts the transition, so the
 * shared bounds see it on their first frame.
 */
@Stable
internal class GrowRoutes(top: NavKey?) {
    private var last: NavKey? = top
    private var current: NavKey? by mutableStateOf(top)
    private var from: NavKey? by mutableStateOf(null)

    fun onTop(top: NavKey?) {
        if (top == last) return
        from = last
        last = top
        current = top
    }

    fun settle() {
        from = null
    }

    /** A page that grows from a row is on top, or is the one the transition leaves or covers. */
    val involved: Boolean
        get() = current.grows() || from.grows()

    private fun NavKey?.grows(): Boolean = (this as? AppRoute)?.motion == RouteMotion.GrowFromRow
}

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
    private val routes: GrowRoutes,
) : KitGrowHost {
    override val transformEnabled: Boolean
        get() = sharedBoundsEnabled(gate.state, level.value)

    override val opening: Boolean
        get() = gate.opening

    override val rowTransformInFlight: Boolean
        get() = routes.involved
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
    level: ShellMotionLevel,
    grows: Boolean,
): Modifier = graphicsLayer {
    val leaving = scope.transition.targetState == EnterExitState.PostExit
    val previewing = gate.state == GateState.Gesture || gate.state == GateState.GestureSettling
    // A page that grew from a row is rounded by its own transform (the row's corners) while the finger shrinks it.
    if (leaving && previewing && level == ShellMotionLevel.Full && !grows) {
        val r = cornerPx * ShellMotion.PredictiveBack.transform(signals.progress.coerceIn(0f, 1f))
        shape = RoundedCornerShape(r)
        clip = true
    } else {
        clip = false
    }
}
