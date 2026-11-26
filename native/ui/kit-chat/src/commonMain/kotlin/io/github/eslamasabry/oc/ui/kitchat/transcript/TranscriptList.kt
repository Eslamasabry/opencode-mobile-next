package io.github.eslamasabry.oc.ui.kitchat.transcript

import io.github.eslamasabry.oc.ui.kit.motion.kitItemMotion
import androidx.compose.foundation.lazy.LazyItemScope
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import io.github.eslamasabry.oc.ui.kit.live.LiveRender
import io.github.eslamasabry.oc.ui.kit.live.liveRenderFor
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeMeasureScope
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenListEdges
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.scrollToIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.frame.KitReportScrolledUnder
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenChromePadding
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.tool.railMeasures
import io.github.eslamasabry.oc.ui.kitchat.turn.KitTurnLiveLine
import io.github.eslamasabry.oc.ui.kitchat.turn.KitTurnLiveTone
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Test-only switch for comparing transcript scroll-anchor mechanisms (DESIGN R4, S1). */
public enum class TranscriptAnchor {
    Today,
    Hybrid,
}

/** Provides [TranscriptAnchor] to [rememberTranscriptListState]; production never provides it. */
public val LocalTranscriptAnchor: ProvidableCompositionLocal<TranscriptAnchor> =
    staticCompositionLocalOf { TranscriptAnchor.Today }

/** Optional per-measure layout probe used by scroll-anchor tests; production never provides it. */
public val LocalTranscriptProbe: ProvidableCompositionLocal<((String) -> Unit)?> =
    staticCompositionLocalOf { null }

/**
 * The transcript list's scroll state (plan 09 §7.3, plan 08 §5.1 follow-scroll). Hoisted per chat
 * entry.
 * - [following] is true at open and after the person's own send; their drag turns it off; reaching
 *   the live edge or the jump control turns it back on.
 * - [awayFromLatest] folds the phrase row (the frame draws it) and is read only by that, so scrolling never
 *   recomposes the screen. The docked "Jump to latest" row reads [following], not this.
 */
@Stable
public class TranscriptListState
internal constructor(
    public val lazy: LazyListState,
    followingState: MutableState<Boolean>,
    private val density: Density,
    public val anchor: TranscriptAnchor = TranscriptAnchor.Today,
) {
    private val followingState: MutableState<Boolean> = followingState

    /** Plain item count maintained when [anchor] is [TranscriptAnchor.Hybrid]. */
    internal var itemCount: Int = 0

    /** Interval recording from the last Hybrid composition pass (walked only at an order flip). */
    internal var recordedScope: RecordingLazyListScope? = null

    /** Looks up [key] in newest-first order by walking recorded intervals (-1 when not found). */
    internal fun newestFirstIndexOf(key: Any?): Int = recordedScope?.newestFirstIndexOf(key) ?: -1

    /** True while the last measured layout ran in normal order (oldest-first). */
    internal var laidOutNormal by mutableStateOf(false)

    /** Order composed for the upcoming measure pass (plain field). */
    internal var composedNormal: Boolean = false

    /** True while a Hybrid jump-to-newest glide is flipping back to reversed and animating. */
    internal var jumping by mutableStateOf(false)

    /** True while Hybrid holds normal order so a toggled fold expands downward while following stays on. */
    internal var foldHeld by mutableStateOf(false)

    /** Standing bottom room (px) applied while laid out in normal order (plain field). */
    internal var roomPx: Int = 0

    /** Public read of whether the transcript is currently laid out in normal order. */
    public val isNormalOrder: Boolean
        get() = laidOutNormal

    /** Target layout order for Hybrid: normal while reading or holding a fold, and not mid-drag-out-of-reversed. */
    public val orderNormal: Boolean
        get() {
            val wantNormal = !following || foldHeld
            val canFlip = !lazy.isScrollInProgress || laidOutNormal
            return anchor == TranscriptAnchor.Hybrid && wantNormal && canFlip
        }

    /**
     * P12-CHAT-FEEL: backed by the saveable state itself, so rotation and coming back restore the
     * reader's choice as it is now (a copy synced on recomposition went stale and pulled a reader
     * back to the bottom).
     */
    public var following: Boolean
        get() = followingState.value
        set(value) {
            if (foldHeld) foldHeld = false
            followingState.value = value
        }

    /** Keeps normal order while following so a toggled fold expands downward without mounting the jump dock. */
    public fun holdForFold() {
        if (anchor == TranscriptAnchor.Hybrid && following) foldHeld = true
    }

    /** Translates a newest-first item index to the underlying [LazyListState] index. */
    public fun lazyIndex(newestFirst: Int): Int =
        if (laidOutNormal) (itemCount - 1 - newestFirst).coerceAtLeast(0) else newestFirst

    /** True while older rows lie above the viewport (under the top bar). */
    public fun isScrolledUnder(): Boolean =
        if (laidOutNormal) lazy.canScrollBackward else lazy.canScrollForward

    /** The newest row is at the bottom edge, within the 48 dp follow slack (plan 08 §5.1). */
    public val atNewest: Boolean by derivedStateOf {
        val slack = with(density) { FOLLOW_SLACK_DP.dp.roundToPx() }
        if (anchor == TranscriptAnchor.Hybrid) {
            val gap = TranscriptAnchorOps.newestGapPx(lazy.layoutInfo, laidOutNormal, roomPx)
            gap != null && gap <= slack
        } else {
            lazy.firstVisibleItemIndex == 0 && lazy.firstVisibleItemScrollOffset <= slack
        }
    }

    /** Index 0 (the newest row) is at the bottom, within the 48 dp follow slack (plan 08 §5.1). */
    public val atLiveEdge: Boolean
        get() = atNewest

    /** Plan 09 §7.3: away once index 0 left the bottom or it is scrolled more than 480 dp. */
    public val awayFromLatest: Boolean by derivedStateOf {
        if (anchor == TranscriptAnchor.Hybrid) {
            !following && !jumping
        } else {
            lazy.firstVisibleItemIndex > 0 ||
                lazy.firstVisibleItemScrollOffset > with(density) { AWAY_DP.dp.roundToPx() }
        }
    }

    /** Deep enough in history (> 480 dp from newest) to fold the phrase row away. */
    internal val awayForPhraseRow: Boolean by derivedStateOf {
        val awayPx = with(density) { AWAY_DP.dp.roundToPx() }
        if (anchor == TranscriptAnchor.Hybrid) {
            val gap = TranscriptAnchorOps.newestGapPx(lazy.layoutInfo, laidOutNormal, roomPx)
            !(gap != null && gap <= awayPx)
        } else {
            lazy.firstVisibleItemIndex > 0 || lazy.firstVisibleItemScrollOffset > awayPx
        }
    }

    private var slotHeight = -1

    /** A bottom slot measured at [height] px; a change re-pins. */
    internal fun slotHeightChanged(height: Int) {
        if (slotHeight >= 0 && height != slotHeight) keepAtBottom()
        slotHeight = height
    }

    /** Requests a same-frame scroll to the newest row (bottom edge). */
    public fun requestScrollToNewest() {
        when {
            foldHeld -> foldHeld = false
            laidOutNormal -> if (itemCount > 0) lazy.requestScrollToItem(itemCount - 1, MAX_SCROLL_OFFSET)
            else -> lazy.requestScrollToItem(0)
        }
    }

    /** Scrolls to the newest row (bottom edge). */
    public suspend fun scrollToNewest() {
        if (foldHeld) {
            foldHeld = false
            snapshotFlow { laidOutNormal }.first { !it }
        }
        if (laidOutNormal) {
            if (itemCount > 0) lazy.scrollToItem(itemCount - 1, MAX_SCROLL_OFFSET)
        } else {
            lazy.scrollToItem(0)
        }
    }

    /**
     * Restores a saved reading place ([newestFirst] index and [offset] px taken in [takenNormal] order) once
     * Hybrid layout order has settled, translating across reversed and normal orders when they differ.
     */
    public suspend fun scrollToPlace(newestFirst: Int, offset: Int, takenNormal: Boolean) {
        snapshotFlow { isNormalOrder == orderNormal }.first { it }
        when {
            isNormalOrder == takenNormal -> lazy.scrollToItem(lazyIndex(newestFirst), offset)
            !takenNormal -> {
                val target = lazyIndex(newestFirst)
                lazy.scrollToItem(target)
                withFrameNanos { }
                val info = lazy.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.index == target }
                if (item != null) {
                    val bottom = info.beforeContentPadding + item.offset + item.size
                    val line = info.viewportSize.height - info.afterContentPadding + roomPx
                    lazy.scrollBy((bottom - line - offset).toFloat())
                }
            }
            else -> scrollToNewest()
        }
    }

    /**
     * M-005/M-009: keeps the newest row on the bottom edge this frame. While the person follows the conversation (and
     * no scroll is running, so a fling or the jump glide is never cut short), the list snaps to index 0 at offset 0 in
     * the same frame as the call: a bottom slot (an approval card unfolding, the composer's tray, a receipt folding
     * away) changing height every frame never leaves the transcript a frame behind. Reading history (not following) is
     * never moved. Safe to call from measure: its reads are not observed, so scrolling never re-runs the caller.
     */
    public fun keepAtBottom() {
        val leave =
            Snapshot.withoutReadObservation {
                !following || jumping || laidOutNormal || lazy.isScrollInProgress ||
                    (lazy.firstVisibleItemIndex == 0 && lazy.firstVisibleItemScrollOffset == 0)
            }
        if (!leave) lazy.requestScrollToItem(0)
    }

    /** Follows the newest row and brings it into view on [scope], which outlives the docked row. */
    internal fun jumpToLatest(scope: CoroutineScope, motion: KitMotionLevel) {
        if (anchor == TranscriptAnchor.Hybrid) {
            jumping = true
            following = true
            scope.launch {
                try {
                    snapshotFlow { laidOutNormal }.first { !it }
                    TranscriptAnchorOps.jumpToNewest(this@TranscriptListState, motion)
                } finally {
                    jumping = false
                }
            }
        } else {
            following = true
            scope.launch { TranscriptAnchorOps.jumpToNewest(this@TranscriptListState, motion) }
        }
    }

    internal companion object {
        const val AWAY_DP = 480
        const val FOLLOW_SLACK_DP = 48
        const val MAX_SCROLL_OFFSET = 1_000_000
    }
}

@Composable
public fun rememberTranscriptListState(): TranscriptListState {
    val lazy = rememberLazyListState()
    val following = rememberSaveable { mutableStateOf(true) }
    val density = LocalDensity.current
    val anchor = LocalTranscriptAnchor.current
    return remember(lazy, following, density, anchor) { TranscriptListState(lazy, following, density, anchor) }
}

/**
 * The running turn's status as the docked "Jump to latest" row carries it at its start (owner decision O2 = D,
 * 2026-11-24; the lead's rules of 2026-11-25). [words] is the one short status word the list's own live line has for
 * the turn's state, with its tone, its spoken words and its rail: the row draws it whole on one line beside the label
 * or, when it does not fit, the dot alone, and it draws no status while the list's own line is on screen ([key]).
 */
public data class KitTranscriptDockLive(
    public val words: String,
    public val tone: KitTurnLiveTone,
    public val spoken: String,
    public val onRail: Boolean,
    /**
     * The key of the list item that draws the live line this status repeats. While any part of that item rises above
     * the row's clear band (a taller item sits under it, or it is taller than the band) the list's own line is on
     * screen and the row shows the label alone: one live line at a time. Null: no item to look for.
     */
    public val key: Any? = null,
)

/**
 * `LazyColumn(reverseLayout = true)` over block rows, newest first (plan 09 §7.1), with
 * follow-bottom and the "Jump to latest" control. Callers add items with stable String keys and a
 * contentType (§7.2).
 *
 * Follow: growth of the bottom item keeps it glued for free; a NEW item at index 0 would land out
 * of view (the list anchors on the old first key), so while following the list asks for index 0 in
 * the same frame as the insert.
 */
@Composable
@Suppress("LongParameterList") // composable part; rule retired for @Composable in the 3.0.1 rule-set review
public fun KitTranscriptList(
    state: TranscriptListState,
    newestKey: String?,
    jumpLabel: String,
    motion: KitMotionLevel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = Space.S4.dp),
    /**
     * The running turn's status, carried at the docked "Jump to latest" row's start while the person reads older rows
     * and the list's own live line is not on screen ([KitTranscriptDockLive]); null: the row shows its label alone.
     */
    dockLive: KitTranscriptDockLive? = null,
    /**
     * Called when the person, reading history, brings the list's oldest item (its top) into view: the transcript loads
     * its earlier messages there by itself. Items added on top never move the row being read (the list keeps its
     * bottom row in place), so this is safe mid-scroll. Null: nothing.
     */
    onReachOldest: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val isHybrid = state.anchor == TranscriptAnchor.Hybrid
    val normal = state.orderNormal
    val recorded = TranscriptPlumbing.record(isHybrid, state, content)
    val lastKey = remember { arrayOfNulls<String>(1) }
    SideEffect {
        if (isHybrid) state.composedNormal = normal
        // Runs after this composition applies and before its layout: same frame as the insert (§7.3
        // rule 2).
        if (newestKey != lastKey[0]) {
            lastKey[0] = newestKey
            if (state.following && newestKey != null) state.requestScrollToNewest()
        }
    }
    val density = LocalDensity.current
    val chrome = kitScreenChromePadding()
    // The chrome's padding follows the bottom block's measured height, which moves on every frame the keyboard
    // travels: it is read where the list is measured (its content padding, its pin) and placed (the docked
    // row, its clear band), never while composing, so the list is not composed again on each of those frames.
    val insetPadding = remember(contentPadding, chrome) { SumPadding(contentPadding, chrome) }
    val pin = remember(state) { BottomPin(state) }
    pin.probe = LocalTranscriptProbe.current
    val listPadding =
        if (isHybrid) remember(insetPadding, pin, density) { SumPadding(insetPadding, EndRoom(pin, density)) }
        else insetPadding
    val personScroll = remember(state, pin) { PersonScroll(state, pin) }
    // Owned by the list, not the docked row: tapping the row turns following on and unmounts it on the next frame,
    // so a scope inside the row would cancel the glide after scrollToItem(2). Also hosts screen-reader scrolls.
    val scope = rememberCoroutineScope()
    val a11yDispatcher = remember { NestedScrollDispatcher() }
    TranscriptPlumbing.FollowEffects(state, personScroll)
    val reachOldest = androidx.compose.runtime.rememberUpdatedState(onReachOldest)
    LaunchedEffect(state) {
        snapshotFlow { TranscriptAnchorOps.reachedOldest(state) }.collect { if (it) reachOldest.value?.invoke() }
    }
    // Chat edge A (owner 2026-11-09): the frame shows its scrolled-container ground only while older rows lie under the
    // header (the list is reversed: older rows are forward, above).
    KitReportScrolledUnder { state.isScrolledUnder() }
    // Reading history: the phrase row folds away, so nothing stands above the message box (owner 2026-11-14).
    io.github.eslamasabry.oc.ui.kit.frame.KitReportAwayFromLatest { state.awayForPhraseRow }
    Box(modifier) {
        LazyColumn(
            state = state.lazy,
            reverseLayout = !normal,
            // C-002: a short conversation sits on the composer, its empty space above (Flutter's reversed list,
            // chat_transcript.dart:153-155). Under reverseLayout, Bottom is where index 0 (the newest row) rests.
            verticalArrangement = Arrangement.Bottom,
            contentPadding = listPadding,
            // A viewport that shrinks or grows (a slot above the composer unfolding beside the list) re-pins before the
            // list measures, so the newest row rides the slot's edge frame by frame.
            // The list ends its own words before the page's bottom block (kitScreenListEdges): the docked "Jump to
            // latest" row and "N earlier messages" lie beside it, whole, on ground with no text under them (owner
            // 2026-11-14). While the row shows, the list also clears the band the row stands on.
            modifier =
                Modifier.fillMaxSize()
                    .then(TranscriptPlumbing.scrollActions(personScroll, a11yDispatcher, scope, motion, isHybrid))
                    .keepsBottom(
                        pin = pin,
                        topPaddingPx = { with(density) { insetPadding.calculateTopPadding().roundToPx() } },
                        bottomPaddingPx = { with(density) { insetPadding.calculateBottomPadding().roundToPx() } },
                        bottomInsetDp = { insetPadding.calculateBottomPadding().value },
                    )
                    .clearBandForDock(state, contentPadding, chrome)
                    .kitScreenListEdges()
                    .testTag("transcript-list"),
            content = { TranscriptPlumbing.replay(recorded, normal, this, content) },
        )
        // Owner decision O2 = D (2026-11-24): KitJumpDock reads [TranscriptListState.following] in its own scope and
        // returns immediately while following, so the row is in the tree exactly while !following and toggling it
        // never recomposes the list.
        KitJumpDock(state, jumpLabel, { state.jumpToLatest(scope, motion) }, dockLive) {
            chrome.calculateBottomPadding() + contentPadding.calculateBottomPadding()
        }
    }
}

private object TranscriptPlumbing {
    /**
     * What [content] adds this pass, for Hybrid only: recorded as intervals (never walked per row in composition),
     * so order flips can look a row up and [TranscriptListState.itemCount] stays plain. Today records nothing: null.
     */
    fun record(
        isHybrid: Boolean,
        state: TranscriptListState,
        content: LazyListScope.() -> Unit,
    ): RecordingLazyListScope? =
        if (!isHybrid) {
            null
        } else {
            RecordingLazyListScope().apply(content).also {
                state.itemCount = it.itemCount
                state.recordedScope = it
            }
        }

    /**
     * How following follows the person. Today: a drag leaves following, and the newest row brings it back once the
     * scroll has stopped. Hybrid: the person path ends every scroll itself (a drag, a fling or a screen-reader
     * scroll).
     */
    @Composable
    fun FollowEffects(state: TranscriptListState, personScroll: PersonScroll) {
        if (state.anchor == TranscriptAnchor.Hybrid) {
            LaunchedEffect(state) {
                snapshotFlow { state.lazy.isScrollInProgress }.collect { scrolling ->
                    if (!scrolling && !personScroll.actionActive) personScroll.end()
                }
            }
        } else {
            LaunchedEffect(state) {
                state.lazy.interactionSource.interactions.collect {
                    if (it is DragInteraction.Start) state.following = false
                }
            }
            // Back at the newest message, the list follows again once the scroll has stopped. Audit 1 (2026-11-14,
            // k6: "Jump to latest" right after a send with the newest message in view): read on the edge alone, a
            // drag that ended there never resumed following (the edge was reached mid-drag and did not change after
            // it), so the next send landed below the rows in view and the list stood "away".
            LaunchedEffect(state) {
                snapshotFlow { !state.lazy.isScrollInProgress && state.atLiveEdge }.collect {
                    if (it) state.following = true
                }
            }
        }
    }

    /**
     * The list's own scroll plumbing, one chain: its screen-reader actions (both modes) and, under Hybrid, the
     * person path in front of the list with the dispatcher those actions feed, so a screen-reader scroll is clamped
     * like a drag.
     */
    fun scrollActions(
        personScroll: PersonScroll,
        a11yDispatcher: NestedScrollDispatcher,
        scope: CoroutineScope,
        motion: KitMotionLevel,
        isHybrid: Boolean,
    ): Modifier =
        Modifier.semantics {
            scrollBy { _, y ->
                scope.launch { personScroll.a11yScrollBy(a11yDispatcher, motion, y) }
                true
            }
            scrollToIndex { index ->
                if (index < 0) return@scrollToIndex false
                scope.launch { personScroll.a11yScrollToIndex(index) }
                true
            }
        }
            .then(
                if (isHybrid) {
                    Modifier.nestedScroll(personScroll).nestedScroll(NoopNestedScroll, a11yDispatcher)
                } else {
                    Modifier
                }
            )

    /**
     * What the list composes on [list] this pass: Hybrid replays the recorded intervals in the order laid out
     * ([normal]: newest last); Today composes [content] itself.
     */
    fun replay(
        recorded: RecordingLazyListScope?,
        normal: Boolean,
        list: LazyListScope,
        content: LazyListScope.() -> Unit,
    ) {
        when {
            recorded == null -> content(list)
            normal -> recorded.replayReversed(list)
            else -> recorded.replayForward(list)
        }
    }
}

/**
 * The motion every transcript item takes (audit 1, 2026-11-14: a new prompt drawn under the previous answer's copy and
 * "…" icons, a step row on the bubble's line, "60 sixty1 one" with a blank band under it, each for 6–15 s on a loaded
 * phone). A row that arrives fades in where it belongs, below what is already there; the rows around it take their new
 * places at once. Nothing glides (a row on its way to a new place lies over the row now there) and nothing fades out in
 * place (a leaving row's ghost lies over the row that took its place). [fadeIn] false: the row is already on screen in
 * another form (the person's own prompt replacing the bubble that rose from the field).
 */
@Composable
public fun LazyItemScope.kitTranscriptItemMotion(fadeIn: Boolean = true): Modifier =
    kitItemMotion(fadeIn = fadeIn, fadeOut = false, placement = false)

/**
 * For a part under the transcript that changes height while it animates (M-005: the request slot's `KitReveal`; M-002:
 * the composer tray): whenever its measured height changes, the transcript is kept on its newest row
 * ([TranscriptListState.keepAtBottom]) in the same frame. Measuring is untouched; the part's own motion is unchanged.
 */
public fun Modifier.kitKeepsTranscriptAtBottom(state: TranscriptListState): Modifier =
    this.then(Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        state.slotHeightChanged(placeable.height)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    })

/** Last seen heights; a change while following re-pins (plain fields: written and read only during layout). */
internal class BottomPin(private val state: TranscriptListState) {
    private var viewport = -1
    private var bottomInset = Float.NaN
    var probe: ((String) -> Unit)? = null
    var roomPx: Int = 0
    var lastReq: String = "none"

    val isHybrid: Boolean
        get() = state.anchor == TranscriptAnchor.Hybrid

    val composedNormal: Boolean
        get() = state.composedNormal

    fun setRoom(px: Int) {
        roomPx = px
        state.roomPx = px
    }

    fun syncBounds(insetDp: Float, maxHeight: Int) {
        bottomInset = insetDp
        if (maxHeight >= 0) viewport = maxHeight
    }

    fun flipIfNeeded(topPadPx: Int, bottomPadPx: Int): Boolean {
        val targetNormal = state.composedNormal
        val currentNormal = Snapshot.withoutReadObservation { state.laidOutNormal }
        if (targetNormal == currentNormal) return false
        val req =
            Snapshot.withoutReadObservation {
                TranscriptAnchorOps.computeFlipRequest(state, targetNormal, topPadPx, bottomPadPx)
            }
        state.lazy.requestScrollToItem(req.first, req.second)
        lastReq = "${req.first},${req.second}"
        state.laidOutNormal = targetNormal
        return true
    }

    fun viewport(height: Int) {
        if (viewport >= 0 && height != viewport) state.keepAtBottom()
        viewport = height
    }

    fun inset(dp: Float) {
        if (!bottomInset.isNaN() && dp != bottomInset) state.keepAtBottom()
        bottomInset = dp
    }

    fun emitProbe() {
        val sink = probe ?: return
        val line =
            Snapshot.withoutReadObservation {
                val info = state.lazy.layoutInfo
                val normal = state.laidOutNormal
                val order = if (normal) "N" else "R"
                val top = TranscriptAnchorOps.probeTop(info, normal)
                "order=$order following=${state.following} scrolling=${state.lazy.isScrollInProgress} " +
                    "room=$roomPx req=$lastReq top=$top"
            }
        sink(line)
    }
}

private class EndRoom(private val pin: BottomPin, private val density: Density) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp

    override fun calculateTopPadding(): Dp = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp

    override fun calculateBottomPadding(): Dp = with(density) { pin.roomPx.toDp() }
}

private object NoopNestedScroll : NestedScrollConnection

private class PersonScroll(
    private val state: TranscriptListState,
    private val pin: BottomPin,
) : NestedScrollConnection {
    var personScrolled: Boolean = false
    var scrolledTowardNewest: Boolean = false
    var jumpedToIndex: Boolean = false
    private var actionSeq: Int = 0
    private var activeActionId: Int = 0
    val actionActive: Boolean
        get() = activeActionId != 0

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput || source == NestedScrollSource.SideEffect) {
            personScrolled = true
        }
        return when {
            available.y > 0f -> {
                scrolledTowardNewest = false
                state.following = false
                Offset.Zero
            }
            available.y < 0f -> {
                scrolledTowardNewest = true
                if (Snapshot.withoutReadObservation { state.laidOutNormal }) {
                    val excess = TranscriptAnchorOps.roomExcess(state.lazy, pin.roomPx, -available.y)
                    if (excess > 0f) Offset(0f, -excess) else Offset.Zero
                } else {
                    Offset.Zero
                }
            }
            else -> Offset.Zero
        }
    }

    fun end() {
        if (!personScrolled) return
        val allowReturn = scrolledTowardNewest
        val fromIndex = jumpedToIndex
        personScrolled = false
        scrolledTowardNewest = false
        jumpedToIndex = false
        when {
            fromIndex -> state.following = state.atNewest
            allowReturn && state.atNewest -> state.following = true
        }
    }

    suspend fun a11yScrollBy(dispatcher: NestedScrollDispatcher, motion: KitMotionLevel, y: Float) {
        if (state.anchor != TranscriptAnchor.Hybrid) {
            scrollTodayBy(motion, y)
            return
        }
        val id = ++actionSeq
        activeActionId = id
        try {
            state.lazy.scroll {
                if (motion == KitMotionLevel.Off) {
                    scrollA11yDelta(dispatcher, y)
                } else {
                    var prev = 0f
                    animate(0f, y) { value, _ ->
                        val stepDelta = value - prev
                        prev = value
                        scrollA11yDelta(dispatcher, stepDelta)
                    }
                }
            }
            withFrameNanos { }
        } finally {
            if (activeActionId == id) {
                activeActionId = 0
                end()
            }
        }
    }

    private suspend fun scrollTodayBy(motion: KitMotionLevel, y: Float) {
        if (y == 0f) return
        state.lazy.scroll {
            if (y > 0f) state.following = false
            if (motion == KitMotionLevel.Off) {
                scrollBy(y)
            } else {
                var prev = 0f
                animate(0f, y) { value, _ ->
                    val stepDelta = value - prev
                    prev = value
                    if (stepDelta != 0f) scrollBy(stepDelta)
                }
            }
        }
        withFrameNanos { }
        if (y < 0f && state.atLiveEdge) state.following = true
    }

    private fun ScrollScope.scrollA11yDelta(dispatcher: NestedScrollDispatcher, delta: Float) {
        if (delta == 0f) return
        val viewportH = Snapshot.withoutReadObservation { state.lazy.layoutInfo.viewportSize.height }
        val maxStep = if (viewportH > 0) viewportH / 2f else abs(delta)
        var remaining = delta
        var hitRoom = false
        while (abs(remaining) > 0f && !hitRoom) {
            val piece = remaining.coerceIn(-maxStep, maxStep)
            remaining -= piece
            val normal = Snapshot.withoutReadObservation { state.laidOutNormal }
            val preOffset = if (normal) Offset(0f, -piece) else Offset(0f, piece)
            val pre = dispatcher.dispatchPreScroll(preOffset, NestedScrollSource.UserInput)
            val v = if (normal) piece + pre.y else piece - pre.y
            if (v != 0f) scrollBy(v)
            if (normal && v > 0f) {
                val overshoot = TranscriptAnchorOps.roomOvershoot(state.lazy, pin.roomPx)
                if (overshoot > 0f) {
                    scrollBy(-overshoot)
                    hitRoom = true
                }
            }
        }
    }

    suspend fun a11yScrollToIndex(index: Int) {
        if (state.anchor != TranscriptAnchor.Hybrid) {
            state.following = false
            state.lazy.scrollToItem(index)
            withFrameNanos { }
            if (state.atLiveEdge) state.following = true
            return
        }
        val id = ++actionSeq
        activeActionId = id
        personScrolled = true
        jumpedToIndex = true
        try {
            state.lazy.scrollToItem(index)
            withFrameNanos { }
            if (Snapshot.withoutReadObservation { state.laidOutNormal }) {
                val overshoot = TranscriptAnchorOps.roomOvershoot(state.lazy, pin.roomPx)
                if (overshoot > 0f) {
                    state.lazy.scroll { scrollBy(-overshoot) }
                    withFrameNanos { }
                }
            }
        } finally {
            if (activeActionId == id) {
                activeActionId = 0
                end()
            }
        }
    }
}

private fun Modifier.keepsBottom(
    pin: BottomPin,
    topPaddingPx: () -> Int,
    bottomPaddingPx: () -> Int,
    bottomInsetDp: () -> Float,
): Modifier =
    this.then(Modifier.layout { measurable, constraints ->
        pin.lastReq = "none"
        if (pin.isHybrid) {
            val room = if (pin.composedNormal && constraints.hasBoundedHeight) constraints.maxHeight else 0
            pin.setRoom(room)
            val flipped = pin.flipIfNeeded(topPaddingPx(), bottomPaddingPx())
            if (!pin.composedNormal) {
                val maxH = if (constraints.hasBoundedHeight) constraints.maxHeight else -1
                if (flipped) {
                    pin.syncBounds(bottomInsetDp(), maxH)
                } else {
                    pin.inset(bottomInsetDp())
                    if (maxH >= 0) pin.viewport(maxH)
                }
            }
        } else {
            // The bottom chrome (composer, request slot) changed height: re-pin before the list measures.
            pin.inset(bottomInsetDp())
            if (constraints.hasBoundedHeight) pin.viewport(constraints.maxHeight)
        }
        val placeable = measurable.measure(constraints)
        pin.emitProbe()
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    })

private object TranscriptAnchorOps {
    fun computeFlipRequest(
        state: TranscriptListState,
        toNormal: Boolean,
        topPadPx: Int,
        bottomPadPx: Int,
    ): Pair<Int, Int> {
        val info = state.lazy.layoutInfo
        return if (toNormal) {
            flipToNormal(state, info, topPadPx)
        } else if (state.jumping) {
            flipToReversedForJump(state, info, bottomPadPx)
        } else {
            0 to 0
        }
    }

    private fun flipToNormal(state: TranscriptListState, info: LazyListLayoutInfo, newTopPad: Int): Pair<Int, Int> {
        val items = info.visibleItemsInfo
        if (items.isEmpty()) return 0 to 0
        val h = info.viewportSize.height
        val prevBottomPad = info.beforeContentPadding
        val picked =
            items.lastOrNull {
                val top = h - prevBottomPad - (it.offset + it.size)
                top <= newTopPad && newTopPad < top + it.size
            } ?: items.last()
        val top = h - prevBottomPad - (picked.offset + picked.size)
        val total = if (state.itemCount > 0) state.itemCount else info.totalItemsCount
        val revIdx = state.newestFirstIndexOf(picked.key).takeIf { it >= 0 } ?: picked.index
        val normalIdx = (total - 1 - revIdx).coerceIn(0, (total - 1).coerceAtLeast(0))
        return normalIdx to (newTopPad - top).coerceAtLeast(0)
    }

    private fun flipToReversedForJump(
        state: TranscriptListState,
        info: LazyListLayoutInfo,
        newBottomPad: Int,
    ): Pair<Int, Int> {
        val items = info.visibleItemsInfo
        val line = info.viewportSize.height - newBottomPad
        val prevTopPad = info.beforeContentPadding
        val picked =
            items.firstOrNull {
                val top = prevTopPad + it.offset
                top < line && line <= top + it.size
            }
        return if (picked == null) {
            0 to 0
        } else {
            val bottom = prevTopPad + picked.offset + picked.size
            val revIdx =
                state.newestFirstIndexOf(picked.key).takeIf { it >= 0 }
                    ?: (info.totalItemsCount - 1 - picked.index)
            revIdx.coerceAtLeast(0) to (bottom - line).coerceAtLeast(0)
        }
    }

    fun roomExcess(lazy: LazyListState, roomPx: Int, deltaDown: Float): Float {
        val info = Snapshot.withoutReadObservation { lazy.layoutInfo }
        val last = info.visibleItemsInfo.lastOrNull()
        return if (last == null || last.index != info.totalItemsCount - 1) {
            0f
        } else {
            val end = info.beforeContentPadding + last.offset + last.size
            val line = info.viewportSize.height - info.afterContentPadding + roomPx
            val allowed = (end - line).coerceAtLeast(0)
            (deltaDown - allowed).coerceAtLeast(0f)
        }
    }

    fun roomOvershoot(lazy: LazyListState, roomPx: Int): Float {
        val info = Snapshot.withoutReadObservation { lazy.layoutInfo }
        val last = info.visibleItemsInfo.lastOrNull()
        return if (last == null || last.index != info.totalItemsCount - 1) {
            0f
        } else {
            val end = info.beforeContentPadding + last.offset + last.size
            val line = info.viewportSize.height - info.afterContentPadding + roomPx
            (line - end).coerceAtLeast(0).toFloat()
        }
    }

    fun probeTop(info: LazyListLayoutInfo, normal: Boolean): String {
        val items = info.visibleItemsInfo
        return when {
            items.isEmpty() -> "none@0"
            normal -> {
                val topPad = info.beforeContentPadding
                val item =
                    items.firstOrNull { topPad + it.offset <= topPad && topPad < topPad + it.offset + it.size }
                        ?: items.first()
                "${item.key}@${topPad + item.offset}"
            }
            else -> {
                val h = info.viewportSize.height
                val bottomPad = info.beforeContentPadding
                val topPad = info.afterContentPadding
                val item =
                    items.lastOrNull {
                        val top = h - bottomPad - (it.offset + it.size)
                        top <= topPad && topPad < top + it.size
                    } ?: items.last()
                val top = h - bottomPad - (item.offset + item.size)
                "${item.key}@$top"
            }
        }
    }

    fun newestGapPx(info: LazyListLayoutInfo, normal: Boolean, roomPx: Int): Int? {
        if (info.totalItemsCount == 0) return 0
        return if (normal) {
            val last = info.visibleItemsInfo.lastOrNull()?.takeIf { it.index == info.totalItemsCount - 1 }
            last?.let {
                val end = info.beforeContentPadding + it.offset + it.size
                val line = info.viewportSize.height - info.afterContentPadding + roomPx
                end - line
            }
        } else {
            val first = info.visibleItemsInfo.firstOrNull()?.takeIf { it.index == 0 }
            first?.let { -it.offset }
        }
    }

    fun reachedOldest(state: TranscriptListState): Boolean {
        val info = state.lazy.layoutInfo
        val atOldest =
            if (state.laidOutNormal) {
                info.visibleItemsInfo.firstOrNull()?.index == 0
            } else {
                val top = info.visibleItemsInfo.lastOrNull()
                top != null && top.index == info.totalItemsCount - 1
            }
        return !state.following && atOldest
    }

    suspend fun jumpToNewest(state: TranscriptListState, motion: KitMotionLevel) {
        val lazy = state.lazy
        val viewport = lazy.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        if (state.laidOutNormal) {
            val last = (state.itemCount - 1).coerceAtLeast(0)
            val lastVisible = lazy.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val end = TranscriptListState.MAX_SCROLL_OFFSET
            when {
                motion == KitMotionLevel.Off -> lazy.scrollToItem(last, end)
                last - lastVisible <= viewport * 2 -> lazy.animateScrollToItem(last, end)
                else -> {
                    lazy.scrollToItem((last - 2).coerceAtLeast(0))
                    lazy.animateScrollToItem(last, end)
                }
            }
        } else {
            when {
                motion == KitMotionLevel.Off -> lazy.scrollToItem(0)
                lazy.firstVisibleItemIndex <= viewport * 2 -> lazy.animateScrollToItem(0)
                else -> {
                    lazy.scrollToItem(2)
                    lazy.animateScrollToItem(0)
                }
            }
        }
    }
}

internal class RecordedInterval(
    val count: Int,
    val key: ((index: Int) -> Any)?,
    val contentType: (index: Int) -> Any?,
    val itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
) {
    fun indexOf(targetKey: Any): Int {
        val keyFn = key ?: return -1
        var found = -1
        var i = 0
        while (i < count && found < 0) {
            if (keyFn(i) == targetKey) found = i
            i++
        }
        return found
    }
}

internal class RecordingLazyListScope : LazyListScope {
    val intervals: MutableList<RecordedInterval> = ArrayList()
    var itemCount: Int = 0
        private set

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        intervals.add(
            RecordedInterval(
                count = 1,
                key = key?.let { k -> { k } },
                contentType = { contentType },
                itemContent = { content() },
            )
        )
        itemCount += 1
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        if (count <= 0) return
        intervals.add(RecordedInterval(count, key, contentType, itemContent))
        itemCount += count
    }

    fun replayForward(target: LazyListScope) {
        for (i in intervals.indices) {
            val iv = intervals[i]
            target.items(iv.count, key = iv.key, contentType = iv.contentType, itemContent = iv.itemContent)
        }
    }

    fun replayReversed(target: LazyListScope) {
        for (i in intervals.indices.reversed()) {
            val iv = intervals[i]
            val last = iv.count - 1
            val content = iv.itemContent
            target.items(
                count = iv.count,
                key = iv.key?.let { k -> { idx -> k(last - idx) } },
                contentType = { idx -> iv.contentType(last - idx) },
                itemContent = { idx -> content(last - idx) },
            )
        }
    }

    fun newestFirstIndexOf(targetKey: Any?): Int {
        if (targetKey == null) return -1
        var offset = 0
        var found = -1
        var i = 0
        while (i < intervals.size && found < 0) {
            val iv = intervals[i]
            val at = iv.indexOf(targetKey)
            if (at >= 0) found = offset + at else offset += iv.count
            i++
        }
        return found
    }
}

/** Test handle of the docked row's status while it carries its word. */
internal const val DOCK_LIVE_TAG: String = "transcript-dock-live"

/** Test handle of the docked row's status when its word gave way and the dot alone stays. */
internal const val DOCK_DOT_TAG: String = "transcript-dock-dot"

/** The docked row's parts, each measured in turn while the row is laid out. */
private enum class DockSlot { WORD, DOT, LEAD, LABEL, ARROW }

/**
 * The row's status in its parts: the word as text (to measure it), the live line that carries it, and the dot alone.
 */
private class DockStatus(
    val wordText: @Composable () -> Unit,
    val line: @Composable () -> Unit,
    val dot: @Composable () -> Unit,
    /** What the live line puts before its words, its lead, its dot and their gap. */
    val lead: Dp,
)

/**
 * The docked "Jump to latest" row (owner decision O2 = D, 2026-11-24; O4 = the first pixel): the whole content width
 * and one target tall, with no fill, border, plate or glass, its bottom where the newest line rests, so it stands in
 * the live line's place. At its start the running turn's status ([dockLive]), at its end the label and a down arrow.
 * One tappable runs the jump. Plain tap: no haptic. The list clears what lies under it ([clearBandForDock]).
 *
 * @param lift how far the row's bottom stands above the list's bottom (the chrome's room and the list's own padding),
 * read at layout
 */
@Composable
private fun BoxScope.KitJumpDock(
    state: TranscriptListState,
    label: String,
    onJump: () -> Unit,
    dockLive: KitTranscriptDockLive?,
    lift: () -> Dp,
) {
    if (state.following) return
    val minTarget = LocalKitTokens.current.minTarget.dp
    // The status in its parts, made anew only when the words or their rail are new.
    val lead = if (dockLive?.onRail == true) railMeasures().column else FlutterKitTokens.LIVE_DOT.dp + Space.S2.dp
    val status = remember(dockLive, lead) {
        dockLive?.let { live ->
            val slot: (String, String) -> (@Composable () -> Unit) = { text, tag ->
                {
                    KitTurnLiveLine(
                        text,
                        Modifier.testTag(tag),
                        tone = live.tone,
                        spoken = live.spoken,
                        onRail = live.onRail,
                    )
                }
            }
            DockStatus(
                wordText = { KitText(live.words, role = TypeRole.Secondary, tabular = true) },
                line = slot(live.words, DOCK_LIVE_TAG),
                dot = slot("", DOCK_DOT_TAG),
                lead = lead,
            )
        }
    }
    Box(Modifier.align(Alignment.BottomCenter).liftAtLayout(lift).fillMaxWidth()) {
        KitTappable(
            onClick = onJump,
            modifier =
                Modifier.fillMaxWidth()
                    .height(minTarget)
                    .testTag("jump-to-latest")
                    .semantics(mergeDescendants = true) { contentDescription = label },
            onClickLabel = label,
            shape = KitShapeId.Button,
        ) {
            DockInside(label, dockLive, status, state, lift)
        }
    }
}

/**
 * The row's inside, laid out where it is measured: the label and the arrow at the end and the status at the start, each
 * at its natural width (the label is never narrowed), all on the row's bottom line. Decided there, with this frame's
 * measurements and the list's own layout of the same frame, so the row is right from its first frame.
 */
@Composable
private fun DockInside(
    label: String,
    live: KitTranscriptDockLive?,
    status: DockStatus?,
    state: TranscriptListState,
    lift: () -> Dp,
) {
    val labelSlot: @Composable () -> Unit = remember(label) {
        { KitText(label, role = TypeRole.Secondary, tone = TextTone.Primary) }
    }
    val arrow: @Composable () -> Unit = { KitIcon(KitIcons.down, size = KitIconSize.Small, tone = TextTone.Primary) }
    val firstLine = LocalKitTypography.current.style(TypeRole.Secondary).lineHeight
    val rail = if (live?.onRail == true) railMeasures() else null
    val above = if (rail != null) (rail.stepLine - rail.first) / 2 else 0.dp
    val firstLinePx = with(LocalDensity.current) { (firstLine.toDp() + above).roundToPx() }
    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val inset = Space.S4.dp.roundToPx()
        val gap = Space.S2.dp.roundToPx()
        val text = subcompose(DockSlot.LABEL, labelSlot).single().measure(Constraints())
        val icon = subcompose(DockSlot.ARROW, arrow).single().measure(Constraints())
        val free = width - 2 * inset - text.width - gap - icon.width
        // Only a row with a status to carry reads the list's layout: an idle row costs nothing while the list scrolls.
        // A reverse list's offsets start at beforeContentPadding above the list's bottom edge; the clear band and its
        // fade end at lift + height + fade above that edge, so the list's own words are readable once their first line
        // sits at or above that edge.
        val own =
            if (status == null || live?.key == null) false
            else {
                val info = state.lazy.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.key == live.key }
                val fromBottom =
                    when {
                        item == null -> 0
                        state.laidOutNormal ->
                            info.viewportSize.height - (info.beforeContentPadding + item.offset + item.size)
                        else -> info.beforeContentPadding + item.offset
                    }
                val fadeTop = lift().roundToPx() + height + DOCK_CLEAR_FADE_DP.dp.roundToPx()
                item != null && fromBottom + item.size - firstLinePx >= fadeTop &&
                    fromBottom < info.viewportSize.height
            }
        val carried = if (status != null && !own) dockStatus(status, free, gap) else null
        layout(width, height) {
            carried?.placeRelative(inset, height - carried.height)
            icon.placeRelative(width - inset - icon.width, height - icon.height)
            text.placeRelative(width - inset - icon.width - gap - text.width, height - text.height)
        }
    }
}

/**
 * The status the row carries at its start: the word, whole on one line at its natural width, when it lies beside the
 * label with [gap] to spare ([free]: the px left of the label and the arrow); else the dot alone. The word is measured
 * as text alone, and the line itself is composed only to be shown: a word that gave way leaves nothing running unseen.
 */
private fun SubcomposeMeasureScope.dockStatus(status: DockStatus, free: Int, gap: Int): Placeable {
    val word = subcompose(DockSlot.LEAD, status.wordText).single().measure(Constraints()).width
    val whole = status.lead.roundToPx() + word
    val shown = if (whole + gap <= free) DockSlot.WORD else DockSlot.DOT
    val content = if (shown == DockSlot.WORD) status.line else status.dot
    return subcompose(shown, content).single().measure(Constraints())
}

/** The soft edge above the docked row's clear band: the list's content fades out over this, then is gone. */
internal const val DOCK_CLEAR_FADE_DP = 16

/**
 * The clear band under the docked "Jump to latest" row (owner decision O2 = D): while the person does not follow the
 * newest end, the list's content fades out over [DOCK_CLEAR_FADE_DP] above the row and is cleared from its top down to
 * the list's bottom, so no line of text lies under the row. Over an opaque flat ground (the default look, matching
 * `kitScrollEdges`), the band paints that ground directly (`SrcOver`) with no offscreen layer; over a live background,
 * it clears the list's own pixels (`DstOut`) in an offscreen layer only while shown. Draw phase only: nothing moves.
 */
@Composable
private fun Modifier.clearBandForDock(
    state: TranscriptListState,
    contentPadding: PaddingValues,
    chrome: PaddingValues,
): Modifier {
    val target = LocalKitTokens.current.minTarget.dp
    val appearance = LocalKitAppearance.current
    val style = LocalKitStyle.current
    val ground = LocalKitColors.current.ground
    val liveOn =
        appearance.liveBackground && !appearance.saver &&
            style.spec.tokens.live.cap(appearance.liveStrength) > 0f
    val flat =
        liveRenderFor(appearance.liveStyle, liveOn, shadersSupported = false) == LiveRender.Flat &&
            ground.alpha >= 1f
    val ink = if (flat) ground else Color.Black
    val mode = if (flat) BlendMode.SrcOver else BlendMode.DstOut
    val draw =
        Modifier.drawWithCache {
            val brush = DockFadeBrush()
            onDrawWithContent {
                if (flat) clipRect { this@onDrawWithContent.drawContent() } else drawContent()
                if (state.following) return@onDrawWithContent
                // The two paddings the docked row stands on, in whole pixels.
                val rest = (chrome.calculateBottomPadding() + contentPadding.calculateBottomPadding()).roundToPx()
                val bandTop = (size.height.roundToInt() - rest - target.roundToPx()).coerceAtLeast(0)
                val from = (bandTop - DOCK_CLEAR_FADE_DP.dp.roundToPx()).coerceAtLeast(0)
                if (bandTop > from) {
                    drawRect(
                        brush.of(from.toFloat(), bandTop.toFloat(), ink),
                        topLeft = Offset(0f, from.toFloat()),
                        size = Size(size.width, (bandTop - from).toFloat()),
                        blendMode = mode,
                    )
                }
                if (size.height > bandTop) {
                    drawRect(
                        ink,
                        topLeft = Offset(0f, bandTop.toFloat()),
                        size = Size(size.width, (size.height - bandTop).toFloat()),
                        blendMode = mode,
                    )
                }
            }
        }
    return if (flat) this.then(draw)
    else graphicsLayer {
        // Offscreen only while the band shows over a live background: the clearing works on the list's own pixels.
        compositingStrategy = if (state.following) CompositingStrategy.Auto else CompositingStrategy.Offscreen
    }.then(draw)
}

/** The dock fade's vertical gradient, kept across frames until its span or colour changes. */
private class DockFadeBrush {
    private var start = Float.NaN
    private var end = Float.NaN
    private var ink = Color.Unspecified
    private var brush: Brush? = null

    fun of(startY: Float, endY: Float, color: Color): Brush {
        val kept = brush
        val sameSpan = start == startY && end == endY
        if (kept != null && sameSpan && ink == color) return kept
        start = startY
        end = endY
        ink = color
        return Brush.verticalGradient(
            0f to color.copy(alpha = 0f),
            1f to color,
            startY = startY,
            endY = endY,
        ).also { brush = it }
    }
}

/** [a] plus [b], each side read only when asked (at measure), never while composing. */
private class SumPadding(private val a: PaddingValues, private val b: PaddingValues) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        a.calculateLeftPadding(layoutDirection) + b.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding(): Dp = a.calculateTopPadding() + b.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        a.calculateRightPadding(layoutDirection) + b.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding(): Dp = a.calculateBottomPadding() + b.calculateBottomPadding()
}

/** This part raised by [lift] from its place (as a bottom padding would), read at measure. */
private fun Modifier.liftAtLayout(lift: () -> Dp): Modifier =
    layout { measurable, constraints ->
        val by = lift().roundToPx().coerceAtLeast(0)
        val maxH =
            if (constraints.hasBoundedHeight) (constraints.maxHeight - by).coerceAtLeast(0) else constraints.maxHeight
        val placeable =
            measurable.measure(constraints.copy(minHeight = constraints.minHeight.coerceAtMost(maxH), maxHeight = maxH))
        layout(placeable.width, placeable.height + by) { placeable.place(0, 0) }
    }
