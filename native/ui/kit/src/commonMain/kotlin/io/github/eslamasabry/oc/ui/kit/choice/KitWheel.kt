package io.github.eslamasabry.oc.ui.kit.choice

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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * Three-row, snapping value wheel from kit_wheel.dart. One focus stop per column, arrow-key and
 * adjustable semantics, tappable neighbours, and a school-aware selected field band. Reading scales
 * to 1.5× and whole labels shrink only when needed. No mutation is dispatched on opening.
 */
@Composable
public fun KitWheel(
    columns: List<KitWheelColumn<*>>,
    modifier: Modifier = Modifier,
    enabled: KitEnabled = KitEnabled.Enabled,
) {
    require(columns.size in 1..3) // F: lib/ui/kit/kit_wheel.dart:85
    val density = LocalDensity.current
    val scale = minOf(density.fontScale, 1.5f) // F: lib/ui/kit/kit_wheel.dart:90
    val extent = (48f * scale).dp // F: lib/ui/kit/kit_wheel.dart:93
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
            modifier.kitMaxWidth(CapRole.Control).fillMaxWidth().height(extent * 3).clipToBounds()
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
        val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
        return layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index
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
                            (column.values.size - 2).coerceAtLeast(0),
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
