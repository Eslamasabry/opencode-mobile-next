package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import kotlinx.coroutines.launch

/** The running turn's Stop, as the chip shows it (plan 08 §5.2). */
public enum class KitStopState {
    /** The turn runs: neutral tone. */
    Running,

    /** A step may be stuck: the chip's tone cross-fades to amber (attention; never red). */
    Stuck,

    /** A stop is on its way: taps do nothing, the hold menu is empty, a screen reader hears it as unavailable. */
    Working,
}

/**
 * The tap guard after a send (fix `49d067f2d`, Flutter `chat_stop_guard_test.dart`): for `STOP_IGNORE_AFTER_SEND`
 * (800 ms) after a send is accepted or the chip appears, taps on Stop are ignored, so a double tap on Send can never
 * stop the turn and its sub-agents. Pure, on frame-clock nanoseconds. The chip's first frame arms it, so a tap
 * before that is ignored too.
 */
internal class StopGuard(windowMillis: Int = MotionTokens.STOP_IGNORE_AFTER_SEND) {
    private val window = windowMillis * 1_000_000L
    private var armedAt: Long? = null

    fun arm(nowNanos: Long) {
        armedAt = nowNanos
    }

    fun allows(nowNanos: Long): Boolean = armedAt.let { it != null && nowNanos - it >= window }
}

/** "Stop after this step": only where the agent can do it (a steering note); null = no hold menu. */
@Immutable
public data class KitStopAfterStep(val label: String, val onSelect: () -> Unit)

/**
 * The running-turn Stop (plan 08 §5.2, owner decision 2026-10-30): a `KitChip` with the word [label] ("Stop") and the
 * stop square at the trailing end of the composer's status strip, for the whole running turn. The chip is the look; a
 * 48 dp `KitTappable` around it is the target, the hold menu and the screen reader's name ([targetLabel], "Stop Claude
 * Code": actions name their target). No ring and no other motion on the chip; only its tone cross-fades (`QUICK`).
 *
 * Tap = stop now (`KitHaptic.Commit`), ignored for 800 ms after each [armKey] change (a send accepted). Long-press or
 * right-click opens a one-item menu with [stopAfterStep] (`KitHaptic.LongPress`), also a screen-reader action.
 *
 * Motion: fade (tone, `QUICK`; instant under Off). Hidden: the hold menu — "Stop after this step" is also a screen-reader
 * action, and the `stop-after-step` tip (teaching registry, owned by the host) anchors to this chip.
 * Replaces: `KitComposerStop` (lib/ui/kit/chat/kit_composer_status_strip.dart).
 */
@Composable
public fun KitComposerStop(
    label: String,
    targetLabel: String,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    state: KitStopState = KitStopState.Running,
    armKey: Any? = Unit,
    workingReason: String? = null,
    stopAfterStep: KitStopAfterStep? = null,
) {
    val platform = LocalKitPlatform.current
    val haptics = LocalKitAppearance.current.haptics
    val specs = KitMotion.specs
    // The guard runs on the frame clock (no timer, plan 08 R9): the send's frame arms it; a tap is judged on the next frame.
    val guard = remember { StopGuard() }
    LaunchedEffect(armKey) { withFrameNanos { guard.arm(it) } }
    val scope = rememberCoroutineScope()
    val stop by rememberUpdatedState(onStop)
    val working = state == KitStopState.Working
    var menu by remember { mutableStateOf(false) }
    val afterStep = stopAfterStep.takeUnless { working }
    if (working && menu) menu = false
    KitMenu(
        expanded = menu,
        items = afterStep?.let { listOf(KitMenuItem(it.label, onSelect = { menu = false; it.onSelect() })) }.orEmpty(),
        onDismissRequest = { menu = false },
        modifier = modifier,
    ) {
        KitTappable(
            onClick = {
                scope.launch {
                    withFrameNanos { now ->
                        if (guard.allows(now)) {
                            if (haptics) platform.haptic(KitHaptic.Commit)
                            stop()
                        }
                    }
                }
            },
            modifier = Modifier.testTag(STOP_TAG).semantics {
                contentDescription = targetLabel
                if (afterStep != null) {
                    customActions = listOf(CustomAccessibilityAction(afterStep.label) { afterStep.onSelect(); true })
                }
            },
            enabled = !working,
            disabledReason = if (working) workingReason ?: label else null,
            onLongClick = afterStep?.let { { menu = true } },
            onLongClickLabel = afterStep?.label,
            shape = KitShapeId.Pill,
        ) {
            Crossfade(state == KitStopState.Stuck, animationSpec = specs.quick(), label = "stop-tone") { stuck ->
                Box(Modifier.clearAndSetSemantics {}) {
                    KitChip(label, icon = StopGlyph, tone = if (stuck) KitChipTone.Attention else KitChipTone.Neutral)
                }
            }
        }
    }
}

/** Test handle of the Stop target. */
public const val STOP_TAG: String = "kit-composer-stop"

/** The stop square (24-unit grid, filled, slightly rounded): the same mark on the chip and the "Stop and send" pill. */
internal val StopGlyph: ImageVector =
    ImageVector.Builder("stop", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 7f)
            lineTo(16f, 7f)
            quadTo(17f, 7f, 17f, 8f)
            lineTo(17f, 16f)
            quadTo(17f, 17f, 16f, 17f)
            lineTo(8f, 17f)
            quadTo(7f, 17f, 7f, 16f)
            lineTo(7f, 8f)
            quadTo(7f, 7f, 8f, 7f)
            close()
        }
    }.build()
