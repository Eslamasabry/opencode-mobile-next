package io.github.eslamasabry.oc.ui.kitchat.transcript

import io.github.eslamasabry.oc.ui.kit.motion.kitItemMotion
import androidx.compose.foundation.lazy.LazyItemScope
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenListEdges
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
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
import io.github.eslamasabry.oc.ui.kitchat.turn.KitTurnLiveLine
import io.github.eslamasabry.oc.ui.kitchat.turn.KitTurnLiveTone
import kotlin.math.roundToInt
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
    followingState: MutableState<Boolean>,
    private val density: Density,
) {
    /**
     * P12-CHAT-FEEL: backed by the saveable state itself, so rotation and coming back restore the
     * reader's choice as it is now (a copy synced on recomposition went stale and pulled a reader
     * back to the bottom).
     */
    public var following: Boolean by followingState

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
    return remember(lazy, following, density) { TranscriptListState(lazy, following, density) }
}

/**
 * The running turn's live line as the docked "Jump to latest" row repeats it at its start (owner decision O2 = D,
 * 2026-11-24): the same words, tone, spoken words and rail as the list's own live line, so the row's line lies where
 * the list's lies at rest.
 */
public data class KitTranscriptDockLive(
    public val words: String,
    public val tone: KitTurnLiveTone,
    public val spoken: String,
    public val onRail: Boolean,
    /** The key of the list item that draws the live line this status repeats; null: none to look for. */
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
     * The running turn's live line, repeated at the docked "Jump to latest" row's start while the person reads
     * older rows ([KitTranscriptDockLive]); null: the row shows its words alone.
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
    val lastKey = remember { arrayOfNulls<String>(1) }
    SideEffect {
        // Runs after this composition applies and before its layout: same frame as the insert (§7.3
        // rule 2).
        if (newestKey != lastKey[0]) {
            lastKey[0] = newestKey
            if (state.following && newestKey != null) state.lazy.requestScrollToItem(0)
        }
    }
    LaunchedEffect(state) {
        state.lazy.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) state.following = false
        }
    }
    // Back at the newest message, the list follows again once the scroll has stopped. Audit 1 (2026-11-14, k6: "Jump
    // to latest" right after a send with the newest message in view): read on the edge alone, a drag that ended there
    // never resumed following (the edge was reached mid-drag and did not change after it), so the next send landed
    // below the rows in view and the list stood "away".
    LaunchedEffect(state) {
        snapshotFlow { state.atLiveEdge && !state.lazy.isScrollInProgress }
            .collect { if (it) state.following = true }
    }
    val reachOldest = androidx.compose.runtime.rememberUpdatedState(onReachOldest)
    LaunchedEffect(state) {
        snapshotFlow {
                val info = state.lazy.layoutInfo
                val top = info.visibleItemsInfo.lastOrNull()
                !state.following && top != null && top.index == info.totalItemsCount - 1
            }
            .collect { if (it) reachOldest.value?.invoke() }
    }
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
        // Owner decision O2 = D (2026-11-24): the docked "Jump to latest" row is in the tree exactly while the person
        // does not follow the newest end (not merely hidden), so it appears and leaves in the same frame as
        // [TranscriptListState.following] changes, with no fade, slide or scale (a cross-fade would show two live
        // lines).
        if (!state.following) {
            // Read at layout, as the list's own content padding is, never while composing.
            KitJumpDock(state, jumpLabel, motion, dockLive) {
                chrome.calculateBottomPadding() + contentPadding.calculateBottomPadding()
            }
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

/**
 * The docked "Jump to latest" row (owner decision O2 = D, 2026-11-24; O4 = the first pixel): the whole content width
 * and one target tall, with no fill, border, plate or glass, its bottom where the newest line rests, so it stands in
 * the live line's place. At its start the running turn's live words ([dockLive]), at its end the label and a down
 * arrow. One tappable runs the jump. Plain tap: no haptic. The list clears what lies under it ([clearBandForDock]).
 *
 * @param lift how far the row's bottom stands above the list's bottom (the chrome's room and the list's own padding),
 * read at layout
 */
@Composable
private fun BoxScope.KitJumpDock(
    state: TranscriptListState,
    label: String,
    motion: KitMotionLevel,
    dockLive: KitTranscriptDockLive?,
    lift: () -> Dp,
) {
    val scope = rememberCoroutineScope()
    val minTarget = LocalKitTokens.current.minTarget.dp
    val jump: () -> Unit = {
        state.following = true
        scope.launch {
            val lazy = state.lazy
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
    Box(Modifier.align(Alignment.BottomCenter).liftAtLayout(lift).fillMaxWidth()) {
        KitTappable(
            onClick = jump,
            modifier =
                Modifier.fillMaxWidth()
                    .height(minTarget)
                    .testTag("jump-to-latest")
                    .semantics(mergeDescendants = true) { contentDescription = label },
            onClickLabel = label,
            shape = KitShapeId.Button,
        ) {
            // On the row's bottom line: where the list's own live line rests.
            Row(
                Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = Space.S4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                DockContent(label, dockLive)
            }
        }
    }
}

/** The row's inside: the live words first (or the room they would take), then the label and the arrow. */
@Composable
private fun RowScope.DockContent(label: String, live: KitTranscriptDockLive?) {
    if (live != null) {
        KitTurnLiveLine(
            live.words,
            Modifier.weight(1f).testTag("transcript-dock-live"),
            tone = live.tone,
            spoken = live.spoken,
            onRail = live.onRail,
        )
    } else {
        Spacer(Modifier.weight(1f))
    }
    KitText(label, role = TypeRole.Secondary, tone = TextTone.Primary)
    Spacer(Modifier.width(Space.S2.dp))
    KitIcon(KitIcons.down, size = KitIconSize.Small, tone = TextTone.Primary)
}

/** The soft edge above the docked row's clear band: the list's content fades out over this, then is gone. */
internal const val DOCK_CLEAR_FADE_DP = 16

/**
 * The clear band under the docked "Jump to latest" row (owner decision O2 = D): while the person does not follow the
 * newest end, the list's content is cleared from one target above the place the newest line rests on down to the list's
 * bottom edge, and fades out over [DOCK_CLEAR_FADE_DP] above that, so no line of text lies under the row. The list is
 * drawn in its own offscreen layer for it, only while the band shows. What it reads, it reads while drawing: no layout
 * changes, so nothing moves.
 */
@Composable
private fun Modifier.clearBandForDock(
    state: TranscriptListState,
    contentPadding: PaddingValues,
    chrome: PaddingValues,
): Modifier {
    val target = LocalKitTokens.current.minTarget.dp
    return graphicsLayer {
        // Offscreen only while the band shows: the clearing works on the list's own pixels, alone.
        compositingStrategy = if (state.following) CompositingStrategy.Auto else CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()
        if (state.following) return@drawWithContent
        // The two paddings the docked row stands on, in whole pixels.
        val rest = (chrome.calculateBottomPadding() + contentPadding.calculateBottomPadding()).roundToPx()
        val bandTop = (size.height.roundToInt() - rest - target.roundToPx()).coerceAtLeast(0)
        val from = (bandTop - DOCK_CLEAR_FADE_DP.dp.roundToPx()).coerceAtLeast(0)
        if (bandTop > from) {
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black,
                    startY = from.toFloat(),
                    endY = bandTop.toFloat(),
                ),
                topLeft = Offset(0f, from.toFloat()),
                size = Size(size.width, (bandTop - from).toFloat()),
                blendMode = BlendMode.DstOut,
            )
        }
        drawRect(
            Color.Black,
            topLeft = Offset(0f, bandTop.toFloat()),
            size = Size(size.width, (size.height - bandTop).toFloat()),
            blendMode = BlendMode.DstOut,
        )
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
