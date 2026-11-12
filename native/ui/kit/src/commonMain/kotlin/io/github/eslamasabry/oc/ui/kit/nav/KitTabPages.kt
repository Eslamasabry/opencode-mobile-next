package io.github.eslamasabry.oc.ui.kit.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.choice.rememberKitHaptics
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.pace
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Where a strip's pill and its pages are, as one number: the pages' own scroll. [KitTabStrip] and [KitTabPages] given
 * the same state move as one thing. Made by [rememberKitTabsState].
 */
@Stable
public class KitTabsState internal constructor(internal val pager: PagerState, private val scope: CoroutineScope) {
    /** The tab the host has chosen. */
    public var index: Int by mutableIntStateOf(pager.currentPage)
        internal set

    internal var onChoose: (Int) -> Unit = {}

    /** A tap's travel while it runs. */
    private var travel: TabTravel? by mutableStateOf(null)

    /** What is left of a travel the finger cut short: the pill eases onto the pages instead of jumping to them. */
    private var drift by mutableFloatStateOf(0f)

    /** Above zero while the pages move because the host chose a tab; a page settling then is not the person's choice. */
    internal var driving: Int = 0

    private val pages: Float get() = pager.currentPage + pager.currentPageOffsetFraction

    /**
     * Where the pill is, in tabs: 1.5 is half-way between the second and the third. It changes on every frame of a
     * drag, so read it while laying out or drawing, never while composing.
     */
    public val position: Float
        get() = travel?.at(pages) ?: (pages + drift)

    /** [position] as a lambda that stays the same for the life of the state. */
    internal val place: () -> Float = { position }

    /** The page that is at rest in the middle; unchanged while a finger or a travel moves the pages. */
    public val settled: Int get() = pager.settledPage

    /** A tap on a tab. */
    internal fun choose(tab: Int) {
        if (tab != index) onChoose(tab)
    }

    /**
     * Ends a fling that is still settling, so a tab tapped right after a swipe is obeyed: a settle runs at the finger's
     * priority and would otherwise turn the tap's travel away.
     */
    private suspend fun release() {
        if (pager.isScrollInProgress) pager.stopScroll(MutatePriority.UserInput)
    }

    /** Shows [target] at once (motion Off, or a strip whose tabs changed under it). */
    internal suspend fun jumpTo(target: Int) {
        release()
        settleDrift(null)
        travel = null
        pager.scrollToPage(target)
    }

    /**
     * Slides the pages to [target] on [spec]. A neighbouring tab slides in from where the pages are. A far one has its
     * neighbour brought next to it first, so one page slides in, never a row of them rushing by, while the pill still
     * crosses every tab on the way ([TabTravel]). A finger that takes the pages mid-way keeps them; the pill joins them.
     */
    internal suspend fun travelTo(target: Int, spec: AnimationSpec<Float>) {
        release()
        val seen = position
        settleDrift(null)
        var via = pages
        val far = abs(target - via) >= TabRules.FAR_TAB
        if (far) via = (if (target > via) target - 1 else target + 1).toFloat()
        val mine = TabTravel(seen, via, target.toFloat())
        travel = mine
        try {
            if (far) pager.scrollToPage(via.toInt())
            pager.animateScrollToPage(target, animationSpec = spec)
            // A slide that started right after its neighbour was brought in can stop a pixel short: land on the page.
            if (pager.currentPage != target || pager.currentPageOffsetFraction != 0f) pager.scrollToPage(target)
            if (travel === mine) travel = null
        } catch (cut: CancellationException) {
            // Cut short by a finger or by a newer tap. A newer tap has its own travel by now and starts from where the
            // pill is; a finger has the pages, and the pill eases the rest of the way onto them.
            if (travel === mine) {
                val left = position - pages
                travel = null
                settleDrift(left, spec)
            }
            throw cut
        }
    }

    /** Sets what the pill still has to make up to [left] and eases it to nothing on [spec]; null clears it at once. */
    private fun settleDrift(left: Float?, spec: AnimationSpec<Float>? = null) {
        drifting?.cancel()
        drifting = null
        drift = left ?: 0f
        if (left == null || spec == null || left == 0f) return
        drifting =
            scope.launch {
                Animatable(left).animateTo(0f, spec) { drift = value }
                drift = 0f
            }
    }

    private var drifting: Job? = null
}

private const val MOVING: Int = -1

/**
 * The shared state of a finger-linked strip and its pages. [index] is the host's chosen tab of [count]; [onSelected]
 * is called once when a different tab is chosen: by a tap on the strip, or when a swipe comes to rest on another page.
 * A change of [index] slides the pages there. A light tick marks each change the person made (when haptics are on).
 *
 * Motion levels: at Full a tap slides the pages on the spatial spring; at Calm on the quick curve; at Off the tab is
 * shown at once. Dragging pages with a finger works at every level (it is the finger that moves them).
 */
@Composable
public fun rememberKitTabsState(index: Int, count: Int, onSelected: (Int) -> Unit): KitTabsState {
    kitAssert(count > 0 && index in 0 until count) { "rememberKitTabsState: index $index is not one of $count tabs" }
    val safe = index.coerceIn(0, (count - 1).coerceAtLeast(0))
    val pager = rememberPagerState(initialPage = safe) { count }
    val scope = rememberCoroutineScope()
    val state = remember(pager) { KitTabsState(pager, scope) }
    val haptics = rememberKitHaptics()
    val motion = KitMotion.state
    val specs = KitMotion.specs
    val report by rememberUpdatedState(onSelected)
    state.index = safe
    state.onChoose = { tab ->
        haptics(KitHaptic.Select)
        report(tab)
    }
    LaunchedEffect(state, safe, count) {
        if (pager.currentPage == safe && !pager.isScrollInProgress) return@LaunchedEffect
        state.driving++
        try {
            when {
                !TabRules.tapSlides(motion.level) -> state.jumpTo(safe)
                motion.travels -> state.travelTo(safe, specs.spatial())
                else -> state.travelTo(safe, specs.pace(KitPace.Quick))
            }
        } finally {
            state.driving--
        }
    }
    LaunchedEffect(state) {
        // Every time the pages come to rest, not only when the resting page's number changes: a far tap first rests on
        // the tab's neighbour, and a finger that then takes the pages back there must still be heard.
        snapshotFlow { if (pager.isScrollInProgress) MOVING else pager.settledPage }.collect { page ->
            if (page != MOVING && state.driving == 0 && page != state.index) {
                haptics(KitHaptic.Select)
                report(page)
            }
        }
    }
    return state
}

/**
 * The pages under a finger-linked strip: side by side, dragged with a finger, coming to rest on one. Each page keeps
 * its saved state (scroll position, `rememberSaveable`) while another is shown. A list inside a page scrolls up and
 * down as usual; a sideways drag turns the page. At the first and last page the platform's own overscroll answers.
 * [pageTitle] names a page for a screen reader when it comes to rest.
 *
 * States: at rest, dragging, settling
 * Text: none (pages decide)
 * Cap: Fill
 * Motion: follows the finger (class Follow, every level); a released page settles on the spatial spring (Off: at once)
 * Hidden: the swipe; the strip's tabs are its visible twin
 */
@Composable
public fun KitTabPages(
    state: KitTabsState,
    modifier: Modifier = Modifier,
    pageTitle: ((Int) -> String)? = null,
    page: @Composable (Int) -> Unit,
) {
    val specs = KitMotion.specs
    val pager = state.pager
    HorizontalPager(
        state = pager,
        modifier = modifier.testTag("kit-tab-pages"),
        verticalAlignment = Alignment.Top,
        key = { it },
        flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = specs.spatial()),
    ) { i ->
        val title = if (pageTitle != null && state.settled == i) pageTitle(i) else null
        Box(Modifier.fillMaxWidth().then(if (title != null) Modifier.semantics { paneTitle = title } else Modifier)) {
            page(i)
        }
    }
}
