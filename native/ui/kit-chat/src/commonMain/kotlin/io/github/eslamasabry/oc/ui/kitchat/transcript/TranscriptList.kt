package io.github.eslamasabry.oc.ui.kitchat.transcript

import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.frame.JUMP_PILL_FACE_DP
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.layout.padding
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
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpPill
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenListEdges
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.frame.KitReportScrolledUnder
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenChromePadding
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import kotlinx.coroutines.launch
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole

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
    LaunchedEffect(state) {
        snapshotFlow { state.atLiveEdge }
            .collect { if (it && !state.lazy.isScrollInProgress) state.following = true }
    }
    // Chat edge A (owner 2026-11-09): the frame shows its scrolled-container ground only while older rows lie under the
    // header (the list is reversed: older rows are forward, above).
    KitReportScrolledUnder { state.lazy.canScrollForward }
    // Reading history: the phrase row folds away, so the jump pill rises out of the message box (owner 2026-11-14).
    io.github.eslamasabry.oc.ui.kit.frame.KitReportAwayFromLatest { state.awayFromLatest }
    val chrome = kitScreenChromePadding()
    val insetPadding =
        PaddingValues(
            top = contentPadding.calculateTopPadding() + chrome.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + chrome.calculateBottomPadding(),
        )
    val pin = remember(state) { BottomPin(state) }
    val bottomInset = insetPadding.calculateBottomPadding()
    SideEffect {
        // The bottom chrome (composer, request slot) changed height: re-pin in the same frame as the new padding.
        pin.inset(bottomInset.value)
    }
    Box(modifier) {
        // E20-S30: what the list draws, recorded for the pill's blur (the pill itself is never in it).
        val backdrop = rememberKitFrostState()
        LazyColumn(
            state = state.lazy,
            reverseLayout = true,
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
                    .keepsBottom(pin)
                    .kitFrostSource(backdrop)
                    .kitScreenListEdges()
                    .testTag("transcript-list"),
            content = content,
        )
        // E20-S30 (owner 2026-11-07, "Big gap not required"): no solid lane behind the pill. The words scroll on under
        // the translucent composer; while the pill shows, only a short soft fade (never a block) settles them where
        // the viewport meets the composer, so letters do not run into the pill.
        val chromeBottom = chrome.calculateBottomPadding()
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
        val pageEnds = io.github.eslamasabry.oc.ui.kit.frame.kitScreenBottomGlass() != null
        if (fadeShown && !pageEnds && !io.github.eslamasabry.oc.ui.kit.frame.kitScreenBottomIsSlab())
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(JUMP_FADE_DP.dp + chromeBottom)
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
        KitJumpToLatest(
            state,
            jumpLabel,
            motion,
            backdrop,
            // Bottom centre, its face 8 dp above the composer's veil (the 48 dp target's own margin counts toward it).
            Modifier.align(Alignment.BottomCenter).padding(bottom = chromeBottom + jumpPillLift(LocalKitTokens.current.minTarget).dp),
        )
    }
}

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

    fun viewport(height: Int) {
        if (viewport >= 0 && height != viewport) state.keepAtBottom()
        viewport = height
    }

    fun inset(dp: Float) {
        if (!bottomInset.isNaN() && dp != bottomInset) state.keepAtBottom()
        bottomInset = dp
    }
}

private fun Modifier.keepsBottom(pin: BottomPin): Modifier =
    this.then(Modifier.layout { measurable, constraints ->
        if (constraints.hasBoundedHeight) pin.viewport(constraints.maxHeight)
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    })

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
) {
    val scope = rememberCoroutineScope()
    // The kit's own jump pill (small, chevron and words), shown while away from the newest end.
    KitJumpPill(
        label = label,
        visible = state.awayFromLatest,
        modifier = modifier.testTag("jump-to-latest"),
        backdrop = backdrop,
        onClick = {
                state.following = true
                scope.launch {
                    val lazy = state.lazy
                    val viewport = lazy.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                    when {
                        motion == KitMotionLevel.Off -> lazy.scrollToItem(0)
                        lazy.firstVisibleItemIndex <= viewport * 2 -> lazy.animateScrollToItem(0)
                        else -> {
                            lazy.scrollToItem(
                                2
                            ) // jump close, glide the last stretch (never animate across thousands)
                            lazy.animateScrollToItem(0)
                        }
                    }
                }
            },
    )
}
