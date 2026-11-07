package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.*
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

@Immutable public data class KitSliderStep<T>(val value: T, val label: String)

/**
 * Flutter kit_step_slider.dart: ordered steps, named value, drag, keys and accessible adjustment.
 */
@Composable
public fun <T> KitStepSlider(
    semanticsLabel: String,
    steps: List<KitSliderStep<T>>,
    selected: T,
    onChange: ((T) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    require(steps.size >= 2) // F: lib/ui/kit/kit_step_slider.dart:43
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val platform = LocalKitPlatform.current
    val haptics = LocalKitAppearance.current.haptics
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val enabled = onChange != null
    val index = steps.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    val latestIndex by rememberUpdatedState(index)
    val latestChange by rememberUpdatedState(onChange)
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    fun choose(next: Int): Boolean {
        val bounded = next.coerceIn(0, steps.lastIndex)
        if (latestChange == null || bounded == latestIndex) return false
        if (haptics) platform.haptic(KitHaptic.Select)
        latestChange?.invoke(steps[bounded].value)
        return true
    }
    val progress by
        animateFloatAsState(index.toFloat(), KitMotion.specs.quick(), label = "step-slider")
    val target = tokens.minTarget.dp
    val thumb = target * .6f // F: lib/ui/kit/kit_step_slider.dart:135
    val line = Space.S1.dp
    val fill = if (enabled) colors.accent else colors.text3
    val railLook =
        kitSurfaceLook(
            KitSurfaceInput(
                SurfaceRole.Track,
                KitShapeId.Pill,
                SurfaceLevel.Surface3,
                ownFill = colors.surface3,
            )
        )
    val thumbLook =
        kitSurfaceLook(
            KitSurfaceInput(
                SurfaceRole.Control,
                KitShapeId.Circle,
                SurfaceLevel.Surface2,
                ownFill = fill,
                color = fill,
            )
        )
    val focusWidth = kitFocusRingWidth()
    Column(
        modifier
            .kitMaxWidth(CapRole.Reading)
            .fillMaxWidth()
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type == KeyEventType.KeyUp) false
                else
                    when (event.key) {
                        Key.DirectionRight -> {
                            choose(latestIndex + if (rtl) -1 else 1)
                            true
                        }
                        Key.DirectionLeft -> {
                            choose(latestIndex + if (rtl) 1 else -1)
                            true
                        }
                        Key.DirectionUp -> {
                            choose(latestIndex + 1)
                            true
                        }
                        Key.DirectionDown -> {
                            choose(latestIndex - 1)
                            true
                        }
                        Key.MoveHome -> {
                            choose(0)
                            true
                        }
                        Key.MoveEnd -> {
                            choose(steps.lastIndex)
                            true
                        }
                        else -> false
                    }
            }
            .focusable(enabled)
            .clearAndSetSemantics {
                contentDescription = semanticsLabel
                stateDescription = steps[index].label
                progressBarRangeInfo =
                    ProgressBarRangeInfo(
                        index.toFloat(),
                        0f..steps.lastIndex.toFloat(),
                        steps.size - 2,
                    )
                if (!enabled) disabled()
                else {
                    setProgress { choose(it.roundToInt()) }
                    requestFocus {
                        focus.requestFocus()
                        true
                    }
                }
            }
    ) {
        // One block for the ends and the track, so the chosen step's name sits right under it, as in Flutter.
        Row(Modifier.semantics {}, verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(target * .7f),
                contentAlignment = Alignment.Center,
            ) { // F: lib/ui/kit/kit_step_slider.dart:139
                KitText(
                    stringResource(Res.string.kitStepSliderLess),
                    Modifier.fillMaxWidth(),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                    textAlign = TextAlign.Center,
                )
            }
            BoxWithConstraints(Modifier.weight(1f).height(target)) {
                val usable = (maxWidth - target).coerceAtLeast(0.dp)
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }
                val targetPx = with(density) { target.toPx() }
                fun nearest(x: Float): Int {
                    if (widthPx <= targetPx) return 0
                    val from = if (rtl) widthPx - x else x
                    return (((from - targetPx / 2) / (widthPx - targetPx)).coerceIn(0f, 1f) *
                            steps.lastIndex)
                        .roundToInt() // F: lib/ui/kit/kit_step_slider.dart:147-152
                }
                Box(
                    Modifier.fillMaxSize().pointerInput(enabled, rtl, steps, widthPx) {
                        if (enabled)
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    focus.requestFocus()
                                    choose(nearest(it.x))
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    choose(nearest(change.position.x))
                                },
                            )
                    }
                ) {
                    Box(
                        Modifier.offset(x = target / 2, y = (target - line) / 2)
                            .width(usable)
                            .height(line)
                            .then(railLook.modifier)
                    ) // F: lib/ui/kit/kit_step_slider.dart:210-215
                    Box(
                        Modifier.offset(x = target / 2, y = (target - line) / 2)
                            .width(usable * (progress / steps.lastIndex))
                            .height(line)
                            .background(fill, RoundedCornerShape(line))
                    ) // F: lib/ui/kit/kit_step_slider.dart:228-235
                    steps.forEachIndexed { i, _ ->
                        // Each step is a 48 dp target of its own (a plain gesture, as Flutter's GestureDetector: the
                        // slider is the one node a screen reader hears).
                        Box(
                            Modifier.offset(x = usable * (i.toFloat() / steps.lastIndex))
                                .size(target)
                                .pointerInput(enabled, i) {
                                    if (enabled) detectTapGestures {
                                        focus.requestFocus()
                                        choose(i)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(Space.S2.dp)
                                    .background(
                                        if (i <= index && enabled) colors.accent else colors.text3,
                                        CircleShape,
                                    )
                            )
                        }
                    }
                    val ring =
                        if (focused) focusWidth
                        else Space.S1.dp * .75f // F: lib/ui/kit/kit_step_slider.dart:174-176
                    Box(
                        Modifier.offset(
                                x = target / 2 + usable * (progress / steps.lastIndex) - thumb / 2,
                                y = (target - thumb) / 2,
                            )
                            .size(thumb)
                            .then(thumbLook.modifier)
                            .then(
                                if (
                                    focused ||
                                        !thumbLook.paints ||
                                        LocalKitStyle.current.style ==
                                            io.github.eslamasabry.oc.ui.design.style.Style.Solid
                                )
                                    Modifier.border(
                                        ring,
                                        if (focused) colors.text1 else colors.surface1,
                                        CircleShape,
                                    )
                                else Modifier
                            )
                    )
                }
            }
            Box(
                Modifier.width(target * .7f),
                contentAlignment = Alignment.Center,
            ) { // F: lib/ui/kit/kit_step_slider.dart:139
                KitText(
                    stringResource(Res.string.kitStepSliderMore),
                    Modifier.fillMaxWidth(),
                    role = TypeRole.Title,
                    tone = TextTone.Secondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        KitText(
            steps[index].label,
            Modifier.fillMaxWidth(),
            role = TypeRole.Secondary,
            textAlign = TextAlign.Center,
        )
    }
}
