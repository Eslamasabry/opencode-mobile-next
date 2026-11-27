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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeMeasureScope
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenListEdges
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
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
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.LocalKitPromptLift
import io.github.eslamasabry.oc.ui.kitchat.tool.railMeasures
import io.github.eslamasabry.oc.ui.kitchat.turn.KitTurnLiveLine
import io.github.eslamasabry.oc.ui.kitchat.turn.KitTurnLiveTone
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    private val followingState: MutableState<Boolean>,
    private val density: Density,
) {
    private var followJob: Job? = null
    private var followDirty: Boolean = false

    /**
     * P12-CHAT-FEEL: backed by the saveable state itself, so rotation and coming back restore the
     * reader's choice as it is now (a copy synced on recomposition went stale and pulled a reader
     * back to the bottom).
     */
    public var following: Boolean
        get() = followingState.value
        set(value) {
            followingState.value = value
            if (!value) cancelFollowScroll()
        }

    /** Index 0 (the newest row) is at the bottom, within the 48 dp follow slack (plan 08 §5.1). */
    public val atLiveEdge: Boolean by derivedStateOf {
        lazy.firstVisibleItemIndex == 0 &&
            lazy.firstVisibleItemScrollOffset <= with(density) { FOLLOW_SLACK_DP.dp.roundToPx() }
    }

    /** Plan 09 §7.3: away once index 0 left the bottom or it is scrolled more than 480 dp. */
    public val awayFromLatest: Boolean by derivedStateOf {
        lazy.firstVisibleItemIndex > 0 ||
            lazy.firstVisibleItemScrollOffset > with(density) { AWAY_DP.dp.roundToPx() }
    }

    private var slotHeight = -1

    /** A bottom slot measured at [height] px; a change re-pins. */
    internal fun slotHeightChanged(height: Int) {
        if (slotHeight >= 0 && height != slotHeight) keepAtBottom()
        slotHeight = height
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
                !following || lazy.isScrollInProgress || followJob?.isActive == true ||
                    (lazy.firstVisibleItemIndex == 0 && lazy.firstVisibleItemScrollOffset == 0)
            }
        if (!leave) lazy.requestScrollToItem(0)
    }

    /** Stops any in-flight smooth follow-scroll immediately (e.g. on user touch/drag). */
    internal fun cancelFollowScroll() {
        followDirty = false
        followJob?.cancel()
        followJob = null
    }

    /**
     * Handles a new item arriving at the newest end while [following] is true:
     * - When [snap] is true (reduced motion, live streaming tail, or the person's own sent prompt
     *   lifting from the composer): settles at index 0 immediately without animation.
     * - Otherwise: leaves the prior newest item anchored across the prepend (no 1-frame snap-up)
     *   and smoothly glides the scroll offset back to index 0, coalescing rapid bursts.
     */
    internal fun onNewestKeyChanged(
        hadPrevious: Boolean,
        snap: Boolean,
        scope: CoroutineScope,
        motion: () -> KitMotionLevel,
    ) {
        if (!following) return
        if (snap) {
            cancelFollowScroll()
            lazy.requestScrollToItem(0)
        } else if (hadPrevious) {
            requestSmoothFollow(scope, motion)
        }
    }

    private fun requestSmoothFollow(scope: CoroutineScope, motion: () -> KitMotionLevel) {
        followDirty = true
        if (followJob?.isActive == true) return
        followJob =
            scope.launch {
                try {
                    drainFollowScroll(motion)
                } finally {
                    followDirty = false
                    if (followJob === coroutineContext[Job]) followJob = null
                }
            }
    }

    private suspend fun drainFollowScroll(motion: () -> KitMotionLevel) {
        while (following && (lazy.canScrollBackward || followDirty)) {
            if (!lazy.canScrollBackward) {
                followDirty = false
                withFrameNanos { }
            }
            if (following && lazy.canScrollBackward) {
                followDirty = false
                if (motion() == KitMotionLevel.Off) {
                    lazy.scrollToItem(0)
                } else {
                    lazy.animateScrollToItem(0)
                }
            }
        }
    }

    /** Follows the newest row and brings it into view on [scope], which outlives the docked row. */
    internal fun jumpToLatest(scope: CoroutineScope, motion: KitMotionLevel) {
        cancelFollowScroll()
        following = true
        scope.launch {
            val viewport = lazy.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            when {
                motion == KitMotionLevel.Off -> lazy.scrollToItem(0)
                lazy.firstVisibleItemIndex <= viewport * 2 -> lazy.animateScrollToItem(0)
                else -> {
                    // jump close, glide the last stretch (never animate across thousands)
                    lazy.scrollToItem(2)
                    lazy.animateScrollToItem(0)
                }
            }
        }
    }

    internal companion object {
        const val AWAY_DP = 480
        const val FOLLOW_SLACK_DP = 48
    }
}

@Composable
public fun rememberTranscriptListState(): TranscriptListState {
    val lazy = rememberLazyListState()
    val following = rememberSaveable { mutableStateOf(true) }
    val density = LocalDensity.current
    return remember(lazy, following, density) { TranscriptListState(lazy, following, density) }
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
 * Follow: growth of the bottom item keeps it glued for free; when a new item arrives at index 0
 * while following, the list keeps the prior newest item anchored across the prepend and smoothly
 * glides back to index 0 (coalescing rapid bursts and cancelling on drag), or settles at once under
 * reduced motion, when [smoothFollow] is false (streaming tail / own send), or when a prompt lift
 * is pending. Reading history (not following) never moves.
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
    /**
     * True (default): smooth follow-scroll when [newestKey] changes while following and motion is on.
     * False: settle on index 0 immediately (e.g. a live streaming reply chunk or the person's own send).
     */
    smoothFollow: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    // Owned by the list, not the docked row: tapping the row turns following on and unmounts it on the next frame,
    // so a scope inside the row would cancel the glide after scrollToItem(2).
    val scope = rememberCoroutineScope()
    TranscriptFollowEffects(state, newestKey, motion, smoothFollow, scope, onReachOldest)
    // Chat edge A (owner 2026-11-09): the frame shows its scrolled-container ground only while older rows lie under the
    // header (the list is reversed: older rows are forward, above).
    KitReportScrolledUnder { state.lazy.canScrollForward }
    // Reading history: the phrase row folds away, so nothing stands above the message box (owner 2026-11-14).
    io.github.eslamasabry.oc.ui.kit.frame.KitReportAwayFromLatest { state.awayFromLatest }
    val chrome = kitScreenChromePadding()
    // The chrome's padding follows the bottom block's measured height, which moves on every frame the keyboard
    // travels: it is read where the list is measured (its content padding, its pin) and placed (the docked
    // row, its clear band), never while composing, so the list is not composed again on each of those frames.
    val insetPadding = remember(contentPadding, chrome) { SumPadding(contentPadding, chrome) }
    val pin = remember(state) { BottomPin(state) }
    Box(modifier) {
        LazyColumn(
            state = state.lazy,
            reverseLayout = true,
            // C-002: a short conversation sits on the composer, its empty space above (Flutter's reversed list,
            // chat_transcript.dart:153-155). Under reverseLayout, Bottom is where index 0 (the newest row) rests.
            verticalArrangement = Arrangement.Bottom,
            contentPadding = insetPadding,
            // A viewport that shrinks or grows (a slot above the composer unfolding beside the list) re-pins before the
            // list measures, so the newest row rides the slot's edge frame by frame.
            // The list ends its own words before the page's bottom block (kitScreenListEdges): the docked "Jump to
            // latest" row and "N earlier messages" lie beside it, whole, on ground with no text under them (owner
            // 2026-11-14). While the row shows, the list also clears the band the row stands on.
            modifier =
                Modifier.fillMaxSize()
                    .keepsBottom(pin) { insetPadding.calculateBottomPadding().value }
                    .clearBandForDock(state, contentPadding, chrome)
                    .kitScreenListEdges()
                    .testTag("transcript-list"),
            content = content,
        )
        // Owner decision O2 = D (2026-11-24): KitJumpDock reads [TranscriptListState.following] in its own scope and
        // returns immediately while following, so the row is in the tree exactly while !following and toggling it
        // never recomposes the list.
        KitJumpDock(state, jumpLabel, { state.jumpToLatest(scope, motion) }, dockLive) {
            chrome.calculateBottomPadding() + contentPadding.calculateBottomPadding()
        }
    }
}

/**
 * Follow-scroll, drag cancellation, live-edge resume, and oldest-row pagination effects for [KitTranscriptList].
 */
@Composable
@Suppress("LongParameterList")
private fun TranscriptFollowEffects(
    state: TranscriptListState,
    newestKey: String?,
    motion: KitMotionLevel,
    smoothFollow: Boolean,
    scope: CoroutineScope,
    onReachOldest: (() -> Unit)?,
) {
    val latestMotion = rememberUpdatedState(motion)
    val promptLift = LocalKitPromptLift.current
    val snapFollow =
        !smoothFollow || motion == KitMotionLevel.Off || KitMotion.state.reduced ||
            promptLift?.hasPendingOffer == true
    val lastKey = remember { arrayOfNulls<String>(1) }
    SideEffect {
        if (newestKey != lastKey[0]) {
            val hadPrevious = lastKey[0] != null
            lastKey[0] = newestKey
            if (newestKey != null) {
                state.onNewestKeyChanged(hadPrevious, snapFollow, scope) { latestMotion.value }
            }
        }
    }
    LaunchedEffect(state) {
        state.lazy.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) {
                state.following = false
                state.cancelFollowScroll()
            }
        }
    }
    // Back at the newest message, the list follows again once the scroll has stopped (Audit 1, k6).
    LaunchedEffect(state) {
        snapshotFlow { state.atLiveEdge && !state.lazy.isScrollInProgress }
            .collect { if (it) state.following = true }
    }
    val reachOldest = rememberUpdatedState(onReachOldest)
    LaunchedEffect(state) {
        snapshotFlow {
                val info = state.lazy.layoutInfo
                val top = info.visibleItemsInfo.lastOrNull()
                !state.following && top != null && top.index == info.totalItemsCount - 1
            }
            .collect { if (it) reachOldest.value?.invoke() }
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

    fun viewport(height: Int) {
        if (viewport >= 0 && height != viewport) state.keepAtBottom()
        viewport = height
    }

    fun inset(dp: Float) {
        if (!bottomInset.isNaN() && dp != bottomInset) state.keepAtBottom()
        bottomInset = dp
    }
}

private fun Modifier.keepsBottom(pin: BottomPin, bottomInsetDp: () -> Float): Modifier =
    this.then(Modifier.layout { measurable, constraints ->
        // The bottom chrome (composer, request slot) changed height: re-pin before the list measures with the new
        // padding.
        pin.inset(bottomInsetDp())
        if (constraints.hasBoundedHeight) pin.viewport(constraints.maxHeight)
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    })

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
                val fromBottom = if (item == null) 0 else info.beforeContentPadding + item.offset
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
