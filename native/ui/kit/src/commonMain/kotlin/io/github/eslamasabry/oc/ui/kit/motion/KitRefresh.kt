package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.workRefresh
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Where a pull to refresh is (plan 08 §5.10). */
public enum class RefreshPhase { Idle, Pulling, Armed, Refreshing, Done }

/** Plan 08 §5.10 as pure rules; `RefreshPhaseTest` (B-KIT-054) drives them. */
public object RefreshRules {
    /**
     * The next phase from the pull's [fraction] (1 = the arming line), whether the host is [refreshing], and whether the
     * done fade has finished ([doneSettled]). Released before armed returns to Idle; Done holds until its fade ends.
     */
    public fun next(prev: RefreshPhase, fraction: Float, refreshing: Boolean, doneSettled: Boolean = false): RefreshPhase =
        when {
            refreshing -> RefreshPhase.Refreshing
            prev == RefreshPhase.Refreshing -> RefreshPhase.Done
            prev == RefreshPhase.Done && !doneSettled -> RefreshPhase.Done
            fraction >= 1f -> RefreshPhase.Armed
            fraction > 0f -> RefreshPhase.Pulling
            else -> RefreshPhase.Idle
        }

    /** How much of each portal bracket is drawn: Flutter's `easeOutCubic` of the pull, whole once armed. */
    public fun bracketStroke(fraction: Float): Float = KitEasing.Enter.transform(min(1f, fraction.coerceAtLeast(0f)))

    /** The spark sits between the brackets once armed and while refreshing. */
    public fun sparkShows(phase: RefreshPhase): Boolean = phase == RefreshPhase.Armed || phase == RefreshPhase.Refreshing

    /** The portal breathes and the spark circles only while refreshing, at Full (ambient loop, MOT-6). */
    public fun breathes(state: KitMotionState, phase: RefreshPhase): Boolean =
        phase == RefreshPhase.Refreshing && state.ambientLoops
}

/**
 * Pull to refresh with the app's own portal drawing (Dart `KitRefresh`; plan 08 §5.10, owner decision D8-4 default):
 * M3's pull state with a kit indicator. The 40 dp disc follows the pull; the portal's two brackets draw in with it; the
 * spark lands when armed; while refreshing the portal breathes (Full only). When the refresh ends the disc shrinks away
 * from MARK_FROM. No haptic (D8-3). One per screen. A TalkBack "Refresh" action and Ctrl+R / F5 run the same refresh,
 * so the pull is never the only way.
 *
 * States: idle, pulling, armed, refreshing, done
 * Text: none (the action's label is the kit word "Refresh")
 * Cap: Fill
 * Motion: the pull follows the finger; spark QUICK; breath REFRESH_BREATH at Full; done QUICK from MARK_FROM;
 *   Calm = still while refreshing; Off = still, leaves at once (classes Follow + AmbientLoop)
 * Hidden: the pull itself (visible twin: the TalkBack action and Ctrl+R / F5; screens also offer a Refresh action)
 * Replaces: KitRefresh (lib/ui/kit/motion/kit_refresh.dart)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun KitRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val label = stringResource(Res.string.workRefresh)
    val state = rememberPullToRefreshState()
    val run = { if (enabled && !refreshing) onRefresh() }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = run,
        modifier =
            modifier
                .semantics { if (enabled) customActions = listOf(CustomAccessibilityAction(label) { run(); true }) }
                .onPreviewKeyEvent { e ->
                    val key = e.type == KeyEventType.KeyDown && (e.key == Key.F5 || (e.isCtrlPressed && e.key == Key.R))
                    if (key && enabled) run()
                    key && enabled
                },
        state = state,
        indicator = { if (enabled) RefreshIndicator(state, refreshing) },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.RefreshIndicator(state: PullToRefreshState, refreshing: Boolean) {
    val motion = KitMotion.state
    val specs = KitMotion.specs
    val colors = LocalKitColors.current
    var phase by remember { mutableStateOf(if (refreshing) RefreshPhase.Refreshing else RefreshPhase.Idle) }
    val leaving = remember { Animatable(1f) }
    LaunchedEffect(state, refreshing) {
        snapshotFlow { state.distanceFraction }.collect { f -> phase = RefreshRules.next(phase, f, refreshing) }
    }
    LaunchedEffect(phase) {
        when (phase) {
            RefreshPhase.Done -> {
                if (motion.reduced) leaving.snapTo(0f) else leaving.animateTo(0f, specs.pace(KitPace.Quick))
                phase = RefreshRules.next(phase, state.distanceFraction, refreshing, doneSettled = true)
            }
            else -> leaving.snapTo(1f)
        }
    }
    val spark by animateFloatAsState(if (RefreshRules.sparkShows(phase)) 1f else 0f, specs.pace(KitPace.Quick), label = "kit.refresh.spark")
    val loop = RefreshRules.breathes(motion, phase)
    val breath: () -> Float
    val turn: () -> Float
    if (loop) {
        val t = rememberInfiniteTransition(label = "kit.refresh.loop")
        val b = t.animateFloat(0f, 1f, specs.breath(MotionTokens.REFRESH_BREATH / 2), label = "kit.refresh.breath")
        val r = t.animateFloat(0f, 1f, specs.lap(MotionTokens.REFRESH_BREATH), label = "kit.refresh.turn")
        breath = { b.value }
        turn = { r.value }
    } else {
        breath = { 0f }
        turn = { 0f }
    }
    val disc = DISC_DP.dp
    val threshold = PullToRefreshDefaults.PositionalThreshold
    Box(
        Modifier.align(Alignment.TopCenter)
            .testTag("kit-refresh")
            .graphicsLayer {
                val f = if (phase == RefreshPhase.Refreshing || phase == RefreshPhase.Done) 1f else state.distanceFraction
                translationY = f * threshold.toPx() - disc.toPx()
                val l = leaving.value
                alpha = if (phase == RefreshPhase.Idle) 0f else l
                val s = MotionTokens.MARK_FROM + (1f - MotionTokens.MARK_FROM) * l
                scaleX = if (motion.travels) s else 1f
                scaleY = scaleX
            }
            .size(disc)
            .background(colors.surface2, KitShapeId.Circle.shape())
            .drawWithCache {
                val stroke = 2.dp.toPx()
                val inset = size.minDimension * 0.28f
                val arc = Size(size.width - 2 * inset, size.height - 2 * inset)
                val ink = colors.text1
                val accent = colors.accent
                onDrawBehind {
                    val pull = if (phase == RefreshPhase.Refreshing || phase == RefreshPhase.Done) 1f else state.distanceFraction
                    val sweep = BRACKET_SWEEP * RefreshRules.bracketStroke(pull)
                    val swell = 1f + 0.06f * breath()
                    val topLeft = Offset(inset + arc.width * (1 - swell) / 2, inset + arc.height * (1 - swell) / 2)
                    val box = Size(arc.width * swell, arc.height * swell)
                    // The portal: two brackets, start and end, drawing in from their middles.
                    drawArc(ink, 180f - sweep / 2, sweep, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(ink, -sweep / 2, sweep, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
                    val a = spark
                    if (a > 0f) {
                        val angle = 2 * PI * turn()
                        val r = if (turn() == 0f) 0f else arc.width * 0.18f
                        val c = Offset(size.width / 2 + (r * cos(angle)).toFloat(), size.height / 2 + (r * sin(angle)).toFloat())
                        drawCircle(accent, radius = stroke * 1.4f, center = c, alpha = a)
                    }
                }
            },
    )
}

private const val DISC_DP = 40
private const val BRACKET_SWEEP = 120f
