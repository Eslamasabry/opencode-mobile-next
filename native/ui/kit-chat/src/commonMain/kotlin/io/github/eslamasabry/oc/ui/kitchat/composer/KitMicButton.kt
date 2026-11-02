package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.foundation.KitFillIndication
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * The composer's mic (plan 06 §7.8, Dart `KitComposerMicHold`): a 48 dp round target beside Send. A tap calls [onTap]
 * (talk until Done, or until a pause when "Send when I stop talking" is on); holding it [KitMicPress.HOLD_AFTER_MS]
 * calls [onHoldStart] and letting go calls [onHoldEnd] — `cancelled` when the finger slid
 * [KitMicPress.CANCEL_DISTANCE_DP] away first ([onArmedChanged] reports crossing that line both ways). A screen reader
 * hears [label] and [holdLabel] as the hint; holding is never the only way (a tap does the same work, ended by
 * Done).
 *
 * States: idle, [active] (filled accent while voice runs), [enabled] false (dimmed, says [disabledReason]).
 * Motion: platform ripple; flat fill under reduced motion. Haptic: LongPress when the hold starts.
 */
@Composable
public fun KitMicButton(
    label: String,
    onTap: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: (cancelled: Boolean) -> Unit,
    onArmedChanged: (armed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    stateLabel: String? = null,
    holdLabel: String? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val platform = LocalKitPlatform.current
    val haptics = LocalKitAppearance.current.haptics
    val reduced = LocalKitMotion.current.reduced
    val tap by rememberUpdatedState(onTap)
    val holdStart by rememberUpdatedState(onHoldStart)
    val holdEnd by rememberUpdatedState(onHoldEnd)
    val armedChanged by rememberUpdatedState(onArmedChanged)
    val interactions = remember { MutableInteractionSource() }
    val ink = if (active) colors.onAccent else if (enabled) colors.text1 else colors.text3
    val indication = remember(platform, ink, reduced) { if (reduced) KitFillIndication(ink) else platform.pressIndication(ink) }
    val press =
        remember {
            KitMicPress(
                onHoldStart = {
                    if (haptics) platform.haptic(KitHaptic.LongPress)
                    holdStart()
                },
                onHoldEnd = { cancelled -> holdEnd(cancelled) },
                onArmedChanged = { armed -> armedChanged(armed) },
            )
        }
    Box(
        modifier
            .sizeIn(minWidth = tokens.minTarget.dp, minHeight = tokens.minTarget.dp)
            .clip(CircleShape)
            .background(if (active) colors.accent else Color.Transparent)
            .indication(interactions, indication)
            .testTag(MIC_TAG)
            .semantics {
                contentDescription = label
                role = Role.Button
                val spoken = if (!enabled) disabledReason else stateLabel ?: holdLabel
                if (spoken != null) stateDescription = spoken
                if (enabled) {
                    onClick { tap(); true }
                }
            }
            .then(
                if (!enabled) Modifier
                else
                    Modifier.pointerInput(Unit) {
                        val density = this
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val origin = down.position
                            val pressed = PressInteraction.Press(origin)
                            interactions.tryEmit(pressed)
                            press.down()
                            var lifted = false
                            var lost = false
                            // Phase 1: up before the hold time is a tap; a long drift gives the touch up.
                            withTimeoutOrNull(KitMicPress.HOLD_AFTER_MS) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        lost = true
                                        break
                                    }
                                    if (!change.pressed) {
                                        lifted = true
                                        break
                                    }
                                    if (change.isConsumed && change.positionChange() != androidx.compose.ui.geometry.Offset.Zero) {
                                        lost = true
                                        break
                                    }
                                    press.move(with(density) { (change.position - origin).getDistance().toDp().value })
                                    if (press.phase == KitMicPress.Phase.Idle) break
                                }
                            }
                            if (lifted) {
                                interactions.tryEmit(PressInteraction.Release(pressed))
                                if (press.up() == KitMicPress.Outcome.Tap) tap()
                                return@awaitEachGesture
                            }
                            if (lost || press.phase == KitMicPress.Phase.Idle) {
                                interactions.tryEmit(PressInteraction.Cancel(pressed))
                                press.cancel()
                                return@awaitEachGesture
                            }
                            // Phase 2: holding. Follow the finger until it lifts.
                            press.holdTimeReached()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    press.cancel()
                                    break
                                }
                                change.consume()
                                if (!change.pressed) {
                                    press.up()
                                    break
                                }
                                press.move(with(density) { (change.position - origin).getDistance().toDp().value })
                            }
                            interactions.tryEmit(PressInteraction.Release(pressed))
                        }
                    },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            MicGlyph,
            contentDescription = null,
            colorFilter = ColorFilter.tint(ink),
            modifier = Modifier.size((tokens.smallIcon + 2).dp),
        )
    }
}

/** Test handle of the mic. */
public const val MIC_TAG: String = "kit-composer-mic"

/** The microphone (24-unit grid): a capsule, its cradle and stand. */
public val MicGlyph: ImageVector =
    ImageVector.Builder("mic", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 3f)
            curveTo(10.34f, 3f, 9f, 4.34f, 9f, 6f)
            lineTo(9f, 12f)
            curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
            curveTo(13.66f, 15f, 15f, 13.66f, 15f, 12f)
            lineTo(15f, 6f)
            curveTo(15f, 4.34f, 13.66f, 3f, 12f, 3f)
            close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
        ) {
            moveTo(6f, 11.5f)
            curveTo(6f, 14.81f, 8.69f, 17.5f, 12f, 17.5f)
            curveTo(15.31f, 17.5f, 18f, 14.81f, 18f, 11.5f)
            moveTo(12f, 17.5f)
            lineTo(12f, 21f)
            moveTo(9f, 21f)
            lineTo(15f, 21f)
        }
    }.build()
