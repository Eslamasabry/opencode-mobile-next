package io.github.eslamasabry.oc.ui.kitchat.transcript

import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.kitItemMotion
import androidx.compose.foundation.lazy.LazyItemScope
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.frame.JUMP_PILL_FACE_DP
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpPill
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenListEdges
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.frame.KitReportScrolledUnder
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenChromePadding
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import kotlinx.coroutines.launch
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole

/** Test-only switch for comparing transcript scroll-anchor mechanisms (DESIGN R4, S1). */
public enum class TranscriptAnchor {
    Today,
    NormalOrder,
    SameFrame,
}

/** Provides [TranscriptAnchor] to [rememberTranscriptListState]; production never provides it. */
public val LocalTranscriptAnchor: ProvidableCompositionLocal<TranscriptAnchor> =
    staticCompositionLocalOf { TranscriptAnchor.Today }

/**
 * The transcript list's scroll state (plan 09 §7.3, plan 08 §5.1 follow-scroll). Hoisted per chat
 * entry.
 * - [following] is true at open and after the person's own send; their drag turns it off; reaching
 *   the live edge or the jump control turns it back on.
 * - [awayFromLatest] drives the jump control and is read only by it, so scrolling never recomposes
 *   the screen.
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
    private var draggedSinceUnfollowState by mutableStateOf(false)

    /** Plain item count maintained when [anchor] is [TranscriptAnchor.NormalOrder]. */
    internal var itemCount: Int = 0

    internal val draggedSinceUnfollow: Boolean
        get() = draggedSinceUnfollowState

    internal fun markDragWhileReading() {
        draggedSinceUnfollowState = true
    }

    /**
     * P12-CHAT-FEEL: backed by the saveable state itself, so rotation and coming back restore the
     * reader's choice as it is now (a copy synced on recomposition went stale and pulled a reader
     * back to the bottom).
     */
    public var following: Boolean
        get() = followingState.value
        set(value) {
            followingState.value = value
            draggedSinceUnfollowState = false
        }

    /** Translates a newest-first item index to the underlying [LazyListState] index. */
    public fun lazyIndex(newestFirst: Int): Int =
        if (anchor == TranscriptAnchor.NormalOrder) (itemCount - 1 - newestFirst).coerceAtLeast(0)
        else newestFirst

    /** True while older rows lie above the viewport (under the top bar). */
    public fun isScrolledUnder(): Boolean =
        if (anchor == TranscriptAnchor.NormalOrder) lazy.canScrollBackward else lazy.canScrollForward

    /** The newest row is at the bottom edge, within the 48 dp follow slack (plan 08 §5.1). */
    public val atNewest: Boolean by derivedStateOf {
        val slack = with(density) { FOLLOW_SLACK_DP.dp.roundToPx() }
        if (anchor == TranscriptAnchor.NormalOrder) {
            TranscriptAnchorOps.atNewestNormalOrder(lazy, slack)
        } else {
            lazy.firstVisibleItemIndex == 0 && lazy.firstVisibleItemScrollOffset <= slack
        }
    }

    /** Index 0 (the newest row) is at the bottom, within the 48 dp follow slack (plan 08 §5.1). */
    public val atLiveEdge: Boolean
        get() = atNewest

    /** Plan 09 §7.3: away once index 0 left the bottom or it is scrolled more than 480 dp. */
    public val awayFromLatest: Boolean by derivedStateOf {
        if (anchor == TranscriptAnchor.NormalOrder) {
            !following
        } else {
            lazy.firstVisibleItemIndex > 0 ||
                lazy.firstVisibleItemScrollOffset > with(density) { AWAY_DP.dp.roundToPx() }
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
        if (anchor == TranscriptAnchor.NormalOrder) {
            if (itemCount > 0) lazy.requestScrollToItem(itemCount - 1, Int.MAX_VALUE)
        } else {
            lazy.requestScrollToItem(0)
        }
    }

    /** Scrolls to the newest row (bottom edge). */
    public suspend fun scrollToNewest() {
        if (anchor == TranscriptAnchor.NormalOrder) {
            if (itemCount > 0) lazy.scrollToItem(itemCount - 1, Int.MAX_VALUE)
        } else {
            lazy.scrollToItem(0)
        }
    }

    internal fun pinNormalOrder() {
        val target =
            Snapshot.withoutReadObservation {
                if (following && !lazy.isScrollInProgress && itemCount > 0) itemCount - 1 else -1
            }
        if (target >= 0) lazy.requestScrollToItem(target, Int.MAX_VALUE)
    }

    /**
     * M-005/M-009: keeps the newest row on the bottom edge this frame. While the person follows the conversation (and
     * no scroll is running, so a fling or the jump glide is never cut short), the list snaps to index 0 at offset 0 in
     * the same frame as the call: a bottom slot (an approval card unfolding, the composer's tray, a receipt folding
     * away) changing height every frame never leaves the transcript a frame behind. Reading history (not following) is
     * never moved. Safe to call from measure: its reads are not observed, so scrolling never re-runs the caller.
     */
    public fun keepAtBottom() {
        if (anchor == TranscriptAnchor.NormalOrder) {
            pinNormalOrder()
            return
        }
        val leave =
            Snapshot.withoutReadObservation {
                !following || lazy.isScrollInProgress ||
                    (lazy.firstVisibleItemIndex == 0 && lazy.firstVisibleItemScrollOffset == 0)
            }
        if (!leave) lazy.requestScrollToItem(0)
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
    val anchor = LocalTranscriptAnchor.current
    return remember(lazy, following, density, anchor) { TranscriptListState(lazy, following, density, anchor) }
}

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
public fun KitTranscriptList(
    state: TranscriptListState,
    newestKey: String?,
    jumpLabel: String,
    motion: KitMotionLevel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = Space.S4.dp),
    /**
     * Called when the person, reading history, brings the list's oldest item (its top) into view: the transcript loads
     * its earlier messages there by itself. Items added on top never move the row being read (the list keeps its
     * bottom row in place), so this is safe mid-scroll. Null: nothing.
     */
    onReachOldest: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val normalOrder = state.anchor == TranscriptAnchor.NormalOrder
    val recorded =
        if (normalOrder) {
            RecordingLazyListScope().apply(content).also { state.itemCount = it.entries.size }
        } else {
            null
        }
    val lastKey = remember { arrayOfNulls<String>(1) }
    SideEffect {
        // Runs after this composition applies and before its layout: same frame as the insert (§7.3
        // rule 2).
        if (newestKey != lastKey[0]) {
            lastKey[0] = newestKey
            if (state.following && newestKey != null) state.requestScrollToNewest()
        }
    }
    LaunchedEffect(state) {
        state.lazy.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) TranscriptAnchorOps.handleDragStart(state)
        }
    }
    // Back at the newest message, the list follows again once the scroll has stopped. Audit 1 (2026-11-14, k6: "Jump
    // to latest" right after a send with the newest message in view): read on the edge alone, a drag that ended there
    // never resumed following (the edge was reached mid-drag and did not change after it), so the next send landed
    // below the rows in view and the list stood "away".
    LaunchedEffect(state) {
        snapshotFlow { TranscriptAnchorOps.canReenterFollow(state) }.collect { if (it) state.following = true }
    }
    val reachOldest = androidx.compose.runtime.rememberUpdatedState(onReachOldest)
    LaunchedEffect(state) {
        snapshotFlow { TranscriptAnchorOps.reachedOldest(state) }.collect { if (it) reachOldest.value?.invoke() }
    }
    // Chat edge A (owner 2026-11-09): the frame shows its scrolled-container ground only while older rows lie under the
    // header (the list is reversed: older rows are forward, above).
    KitReportScrolledUnder { state.isScrolledUnder() }
    // Reading history: the phrase row folds away, so the jump pill rises out of the message box (owner 2026-11-14).
    io.github.eslamasabry.oc.ui.kit.frame.KitReportAwayFromLatest { state.awayFromLatest }
    val chrome = kitScreenChromePadding()
    // The chrome's padding follows the bottom block's measured height, which moves on every frame the keyboard
    // travels: it is read where the list is measured (its content padding, its pin) and placed (the pill, the
    // fade), never while composing, so the list is not composed again on each of those frames.
    val insetPadding = remember(contentPadding, chrome) { SumPadding(contentPadding, chrome) }
    val pin = remember(state) { BottomPin(state) }
    Box(modifier) {
        // E20-S30: what the list draws, recorded for the pill's blur (the pill itself is never in it).
        val backdrop = rememberKitFrostState()
        LazyColumn(
            state = state.lazy,
            reverseLayout = !normalOrder,
            // C-002: a short conversation sits on the composer, its empty space above (Flutter's reversed list,
            // chat_transcript.dart:153-155). Under reverseLayout, Bottom is where index 0 (the newest row) rests.
            verticalArrangement = Arrangement.Bottom,
            contentPadding = insetPadding,
            // A viewport that shrinks or grows (a slot above the composer unfolding beside the list) re-pins before the
            // list measures, so the newest row rides the slot's edge frame by frame.
            // The list ends its own words before the page's floating controls (kitScreenListEdges): the jump pill and
            // "N earlier messages" lie beside it, whole, on ground with no text under them (owner 2026-11-14). The pill's
            // recording is made of the list as it shows, so its glass bends no words either; it is the list's own, never
            // the page's (the pill lies in the page's recording: reading that would draw the pill into itself).
            modifier =
                Modifier.fillMaxSize()
                    .keepsBottom(pin) { insetPadding.calculateBottomPadding().value }
                    .kitFrostSource(backdrop)
                    .kitScreenListEdges()
                    .testTag("transcript-list"),
            content = {
                if (recorded != null) recorded.replayReversed(this) else content()
            },
        )
        // E20-S30 (owner 2026-11-07, "Big gap not required"): no solid lane behind the pill. The words scroll on under
        // the translucent composer; while the pill shows, only a short soft fade (never a block) settles them where
        // the viewport meets the composer, so letters do not run into the pill.
        val ground = LocalKitColors.current.ground
        val fadeState =
            animateFloatAsState(
                if (state.awayFromLatest) 1f else 0f,
                // KitMotion's STANDARD on the kit's Enter curve, not Compose's default curve.
                if (motion == KitMotionLevel.Off) snap() else KitMotion.specs.enter(),
                label = "jump-fade",
            )
        // Owner 2026-11-09 ("a rough line just below Jump to latest"): the fade ended where the composer's room
        // starts, at its strongest, so wherever nothing lay below it (liquid glass, a live background) it cut straight
        // across. It now settles over its 28 dp and holds on down to the list's edge; the screen's own scroll edge
        // fades it out with the words under the bottom block, so it has no edge of its own.
        // Over a glass slab (owner 2026-11-10) there is no fade at all: the words run sharp under the one pane, and the
        // pill is a drop of glass over them.
        // The fade's value is read only when it draws; composition sees just whether it shows at all (twice per fade),
        // not every frame of it.
        val fadeShown by remember { derivedStateOf { fadeState.value > 0f } }
        // A page that records its body ends the list itself above the pill (kitScreenListEdges): no band of ground there.
        val pageEnds = io.github.eslamasabry.oc.ui.kit.frame.kitScreenBottomGlass() != null || io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenFloating.current
        if (fadeShown && !pageEnds && !io.github.eslamasabry.oc.ui.kit.frame.kitScreenBottomIsSlab())
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightAtLayout { JUMP_FADE_DP.dp + chrome.calculateBottomPadding() }
                    .testTag("transcript-jump-fade")
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                0f to ground.copy(alpha = 0f),
                                1f to ground.copy(alpha = JUMP_FADE_ALPHA),
                                startY = 0f,
                                endY = JUMP_FADE_DP.dp.toPx(),
                            ),
                            alpha = fadeState.value,
                        )
                    }
            )
        val floating = io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenFloating.current
        KitJumpToLatest(
            state,
            jumpLabel,
            motion,
            backdrop,
            if (floating)
                // A floating chat (owner 2026-11-17): the pill's place, centred above the message box, as a round arrow only;
                // its target stands on the block's top.
                Modifier.align(Alignment.BottomCenter).liftAtLayout { chrome.calculateBottomPadding() }
            // Bottom centre, its face 8 dp above the composer's veil (the 48 dp target's own margin counts toward it).
            else {
                val lift = jumpPillLift(LocalKitTokens.current.minTarget).dp
                Modifier.align(Alignment.BottomCenter).liftAtLayout { chrome.calculateBottomPadding() + lift }
            },
            round = floating,
        )
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

/** E20-S30: the gap between the pill and the composer, the soft fade's height and its strongest alpha. */
internal const val JUMP_GAP_DP = 8
internal const val JUMP_FADE_DP = 28
internal const val JUMP_FADE_ALPHA = 0.72f

/** How far above the chrome the pill's target sits so its visible face is [JUMP_GAP_DP] above it. */
internal fun jumpPillLift(minTarget: Int): Int = (JUMP_GAP_DP - (minTarget - JUMP_PILL_FACE_DP) / 2).coerceAtLeast(0)

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

    val isNormalOrder: Boolean
        get() = state.anchor == TranscriptAnchor.NormalOrder

    fun pinNormalOrder() {
        state.pinNormalOrder()
    }

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
        if (pin.isNormalOrder) {
            pin.pinNormalOrder()
        } else {
            // The bottom chrome (composer, request slot) changed height: re-pin before the list measures with the new
            // padding.
            pin.inset(bottomInsetDp())
            if (constraints.hasBoundedHeight) pin.viewport(constraints.maxHeight)
        }
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    })

private object TranscriptAnchorOps {
    fun atNewestNormalOrder(lazy: LazyListState, slackPx: Int): Boolean {
        val info = lazy.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()
        val end = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
        return !lazy.canScrollForward ||
            (last != null && last.index == info.totalItemsCount - 1 && (last.offset + last.size) - end <= slackPx)
    }

    fun handleDragStart(state: TranscriptListState) {
        if (state.anchor == TranscriptAnchor.NormalOrder) {
            if (state.following) state.following = false else state.markDragWhileReading()
        } else {
            state.following = false
        }
    }

    fun canReenterFollow(state: TranscriptListState): Boolean =
        !state.lazy.isScrollInProgress &&
            if (state.anchor == TranscriptAnchor.NormalOrder) {
                state.draggedSinceUnfollow && state.atNewest
            } else {
                state.atLiveEdge
            }

    fun reachedOldest(state: TranscriptListState): Boolean {
        val info = state.lazy.layoutInfo
        val atOldest =
            if (state.anchor == TranscriptAnchor.NormalOrder) {
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
        if (state.anchor == TranscriptAnchor.NormalOrder) {
            val last = (state.itemCount - 1).coerceAtLeast(0)
            val lastVisible = lazy.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            when {
                motion == KitMotionLevel.Off -> lazy.scrollToItem(last, Int.MAX_VALUE)
                last - lastVisible <= viewport * 2 -> lazy.animateScrollToItem(last, Int.MAX_VALUE)
                else -> {
                    lazy.scrollToItem((last - 2).coerceAtLeast(0))
                    lazy.animateScrollToItem(last, Int.MAX_VALUE)
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

private class RecordedItem(
    val key: Any?,
    val contentType: Any?,
    val content: @Composable LazyItemScope.() -> Unit,
)

private class RecordingLazyListScope : LazyListScope {
    val entries: MutableList<RecordedItem> = ArrayList()

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        entries.add(RecordedItem(key, contentType, content))
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        for (i in 0 until count) {
            val k = key?.invoke(i)
            val ct = contentType(i)
            entries.add(RecordedItem(k, ct) { itemContent(i) })
        }
    }

    fun replayReversed(target: LazyListScope) {
        for (i in entries.indices.reversed()) {
            val entry = entries[i]
            target.item(key = entry.key, contentType = entry.contentType, content = entry.content)
        }
    }
}

/**
 * The pill (plan 08 §5.1 "Jump pill"). It alone reads [TranscriptListState.awayFromLatest]. Plain
 * tap: no haptic.
 */
@Composable
private fun KitJumpToLatest(
    state: TranscriptListState,
    label: String,
    motion: KitMotionLevel,
    backdrop: KitFrostState,
    modifier: Modifier,
    round: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val jump: () -> Unit = {
        state.following = true
        scope.launch { TranscriptAnchorOps.jumpToNewest(state, motion) }
    }
    // The kit's own jump pill (small, chevron and words), or a floating chat's round arrow, shown while away from the
    // newest end.
    if (round)
        io.github.eslamasabry.oc.ui.kit.frame.KitJumpButton(label, jump, state.awayFromLatest, modifier.testTag("jump-to-latest"))
    else
        KitJumpPill(
            label = label,
            visible = state.awayFromLatest,
            modifier = modifier.testTag("jump-to-latest"),
            backdrop = backdrop,
            onClick = jump,
        )
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

/** This part's height, [height], read at measure. */
private fun Modifier.heightAtLayout(height: () -> Dp): Modifier =
    layout { measurable, constraints ->
        val h = height().roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
        layout(placeable.width, h) { placeable.place(0, 0) }
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
