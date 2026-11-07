package io.github.eslamasabry.oc.ui.kit.input

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.*
import io.github.eslamasabry.oc.ui.kit.choice.rememberKitHaptics
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Flutter kit_wheel.dart: one ordered, controlled, adjustable column. */
public data class KitWheelColumn<T>(
    val values: List<T>,
    val selected: T,
    val label: (T) -> String,
    val onChanged: (T) -> Unit,
    val semanticsLabel: String,
    val flex: Int = 1,
    val testTag: String? = null,
) {
    init {
        require(values.isNotEmpty() && flex > 0)
    }

    public val selectedIndex: Int
        get() = values.indexOf(selected).coerceAtLeast(0)

    public fun labelAt(index: Int): String = label(values[index])

    public fun chooseAt(index: Int) {
        onChanged(values[index])
    }
}

/**
 * A value chosen by rolling it under a band, the way a phone's alarm sets its time (Dart `KitWheel`,
 * lib/ui/kit/kit_wheel.dart): one to three columns, three rows tall, the chosen row in the title size inside a band
 * with an accent line, its neighbours in the headline size in text3 at 0.9 opacity. A tap on a
 * neighbour rolls to it. One focus stop per column; up and down arrows step; screen readers hear "Hour, 9" and adjust
 * it. Text grows up to 1.5× so three rows stay in view; labels shrink rather than cut. No change is sent on opening.
 *
 * States: none — a disabled wheel is hidden instead
 * Text: reading, capped at 1.5×
 * Cap: Control (400)
 * Motion: rolling to a tapped row over QUICK; Off = instant
 * Replaces: KitWheel, KitWheelColumn (lib/ui/kit/kit_wheel.dart); the older choice/KitWheel.kt is the same part
 * without the side rows' 0.9 opacity
 */
@Composable
public fun KitWheel(
    columns: List<KitWheelColumn<*>>,
    modifier: Modifier = Modifier,
    enabled: KitEnabled = KitEnabled.Enabled,
) {
    require(columns.size in 1..3) // F: lib/ui/kit/kit_wheel.dart:85
    val density = LocalDensity.current
    val scale = minOf(density.fontScale, 1.5f) // F: lib/ui/kit/kit_wheel.dart:84
    val extent = (48f * scale).dp // F: lib/ui/kit/kit_wheel.dart:87
    val look =
        kitSurfaceLook(
            KitSurfaceInput(
                role = SurfaceRole.Field,
                shape = KitShapeId.Button,
                level = SurfaceLevel.Surface2,
                ownFill = LocalKitColors.current.surface2,
                selected = true,
            )
        )
    val band =
        if (LocalKitStyle.current.style == Style.Solid)
            look.modifier.border(kitFocusRingWidth(), LocalKitColors.current.accent, look.shape)
        else look.modifier
    CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
        Box(
            modifier.kitMaxWidth(CapRole.Control).fillMaxWidth().height(extent * 3).clipToBounds() // F: lib/ui/kit/kit_wheel.dart:103
        ) { // F: lib/ui/kit/kit_wheel.dart:110
            Box(Modifier.fillMaxWidth().offset(y = extent).height(extent).then(band))
            Row(Modifier.fillMaxSize()) {
                columns.forEach { column ->
                    key(column.testTag ?: column.semanticsLabel, column.values) {
                        WheelColumn(
                            column,
                            extent.value,
                            enabled,
                            look.words,
                            Modifier.weight(column.flex.toFloat()).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WheelColumn(
    column: KitWheelColumn<*>,
    extentDp: Float,
    enabled: KitEnabled,
    selectedInk: androidx.compose.ui.graphics.Color?,
    modifier: Modifier,
) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = column.selectedIndex)
    val current by rememberUpdatedState(column)
    val scope = rememberCoroutineScope()
    val motion = LocalKitMotion.current
    val motionSpecs = KitMotion.specs
    val density = LocalDensity.current
    val haptic = rememberKitHaptics()
    var focused by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var userScroll by remember { mutableStateOf(false) }
    val enabledNow by rememberUpdatedState(enabled.isEnabled)
    fun nearest(): Int {
        val layout = list.layoutInfo
        val center = midpoint(layout.viewportStartOffset.toFloat(), layout.viewportEndOffset.toFloat())
        return layout.visibleItemsInfo.minByOrNull { abs(midpoint(it.offset.toFloat(), (it.offset + it.size).toFloat()) - center) }?.index
            ?: current.selectedIndex
    }
    val painted by remember { derivedStateOf { nearest() } }
    fun go(index: Int) {
        if (!enabledNow) return
        val target = index.coerceIn(0, current.values.lastIndex)
        scope.launch {
            syncing = true
            userScroll = false
            try {
                if (motion.reduced) list.scrollToItem(target)
                else {
                    val extentPx = with(density) { extentDp.dp.toPx() }
                    val offset =
                        list.firstVisibleItemIndex * extentPx + list.firstVisibleItemScrollOffset
                    list.animateScrollBy(target * extentPx - offset, motionSpecs.quick())
                }
                if (enabledNow && target != current.selectedIndex) {
                    haptic(KitHaptic.Select)
                    current.chooseAt(target)
                }
            } finally {
                syncing = false
            }
        }
    }
    LaunchedEffect(column.selectedIndex) {
        if (nearest() != column.selectedIndex) {
            syncing = true
            userScroll = false
            try {
                list.scrollToItem(column.selectedIndex)
            } finally {
                syncing = false
            }
        }
    }
    // N-44: once the first rows are laid out, the band must sit on the value the screen says is chosen
    // (the button reads the same value), whatever the opening offset turned out to be.
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        if (nearest() != current.selectedIndex) {
            syncing = true
            userScroll = false
            try {
                list.scrollToItem(current.selectedIndex)
            } finally {
                syncing = false
            }
        }
    }
    LaunchedEffect(list) {
        snapshotFlow { Triple(list.isScrollInProgress, syncing, nearest()) }
            .distinctUntilChanged()
            .collect { (scrolling, controlled, index) ->
                if (!controlled && scrolling) userScroll = true
                if (!controlled && !scrolling && userScroll) {
                    userScroll = false
                    if (enabledNow && index != current.selectedIndex) {
                        haptic(KitHaptic.Select)
                        current.chooseAt(index)
                    }
                }
            }
    }
    val colors = LocalKitColors.current
    val typography = LocalKitTypography.current
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Field, KitShapeId.Button))
    val focus =
        if (focused) Modifier.border(kitFocusRingWidth(), colors.text2, look.shape) else Modifier
    LazyColumn(
        state = list,
        flingBehavior = rememberSnapFlingBehavior(list),
        userScrollEnabled = enabled.isEnabled,
        contentPadding = PaddingValues(vertical = extentDp.dp),
        modifier =
            modifier
                .then(focus)
                .then(column.testTag?.let { Modifier.testTag(it) } ?: Modifier)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown || !enabledNow) false
                    else
                        when (it.key) {
                            Key.DirectionDown -> {
                                go(painted + 1)
                                true
                            }
                            Key.DirectionUp -> {
                                go(painted - 1)
                                true
                            }
                            else -> false
                        }
                }
                .focusable(enabled.isEnabled)
                .semantics {
                    contentDescription = column.semanticsLabel
                    stateDescription = enabled.reason ?: column.labelAt(painted)
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(
                            painted.toFloat(),
                            0f..column.values.lastIndex.toFloat(),
                            (column.values.size - 2).coerceAtLeast(0), // NV: an adjustable range's steps exclude both ends
                        ) // NV: adjustable discrete range excludes endpoints
                    if (!enabled.isEnabled) disabled()
                    else
                        setProgress { target ->
                            go(target.roundToInt())
                            true
                        }
                },
    ) {
        itemsIndexed(column.values) { index, _ ->
            val chosen = index == painted
            val style =
                typography
                    .style(if (chosen) TypeRole.Title else TypeRole.Headline)
                    .copy(
                        color = if (chosen) selectedInk ?: colors.text1 else colors.text3,
                        fontWeight =
                            if (chosen) typography.style(TypeRole.Title).fontWeight
                            else FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                        textAlign = TextAlign.Center,
                    )
            val label = column.labelAt(index)
            val measure = rememberTextMeasurer()
            BoxWithConstraints(
                Modifier.fillMaxWidth()
                    .height(extentDp.dp)
                    .graphicsLayer {
                        // ListWheelScrollView: rows off the centre line are drawn at 0.9 opacity.
                        val info = list.layoutInfo
                        val row = info.visibleItemsInfo.firstOrNull { it.index == index }
                        if (row != null) {
                            val center = midpoint(info.viewportStartOffset.toFloat(), info.viewportEndOffset.toFloat())
                            val off = midpoint(row.offset.toFloat(), (row.offset + row.size).toFloat()) - center
                            alpha = if (abs(off) < midpoint(0f, row.size.toFloat())) 1f else wheelSideOpacity
                        }
                    }
                    .clearAndSetSemantics {}
                    .pointerInput(index, enabled.isEnabled) { detectTapGestures { go(index) } }
                    .padding(horizontal = Space.S2.dp),
                contentAlignment = Alignment.Center,
            ) {
                val width = measure.measure(label, style, softWrap = false, maxLines = 1).size.width
                val fit = minOf(1f, with(density) { maxWidth.toPx() } / width.coerceAtLeast(1))
                BasicText(
                    label,
                    style =
                        style.copy(
                            fontSize = style.fontSize * fit,
                            lineHeight = style.lineHeight * fit,
                        ),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** Rows away from the centre line are drawn at this opacity (Dart `overAndUnderCenterOpacity`). */
internal const val wheelSideOpacity: Float = 0.9f // F: lib/ui/kit/kit_wheel.dart:310


private fun midpoint(a: Float, b: Float): Float = (a + b) / 2f // NV: the middle of a span
