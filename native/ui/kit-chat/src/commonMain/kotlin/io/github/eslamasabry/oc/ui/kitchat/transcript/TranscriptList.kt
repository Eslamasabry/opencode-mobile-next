package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import kotlinx.coroutines.launch

/**
 * The transcript list's scroll state (plan 09 §7.3, plan 08 §5.1 follow-scroll). Hoisted per chat entry.
 * - [following] is true at open and after the person's own send; their drag turns it off; reaching the live edge or the
 *   jump control turns it back on.
 * - [awayFromLatest] drives the jump control and is read only by it, so scrolling never recomposes the screen.
 */
@Stable
public class TranscriptListState internal constructor(
    public val lazy: LazyListState,
    following: Boolean,
    private val density: Density,
) {
    public var following: Boolean by mutableStateOf(following)

    /** Index 0 (the newest row) is at the bottom, within the 48 dp follow slack (plan 08 §5.1). */
    public val atLiveEdge: Boolean by derivedStateOf {
        lazy.firstVisibleItemIndex == 0 && lazy.firstVisibleItemScrollOffset <= with(density) { FOLLOW_SLACK_DP.dp.roundToPx() }
    }

    /** Plan 09 §7.3: away once index 0 left the bottom or it is scrolled more than 480 dp. */
    public val awayFromLatest: Boolean by derivedStateOf {
        lazy.firstVisibleItemIndex > 0 || lazy.firstVisibleItemScrollOffset > with(density) { AWAY_DP.dp.roundToPx() }
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
    val state = remember(lazy, density) { TranscriptListState(lazy, following.value, density) }
    SideEffect { following.value = state.following }
    return state
}

/**
 * `LazyColumn(reverseLayout = true)` over block rows, newest first (plan 09 §7.1), with follow-bottom and the
 * "Jump to latest" control. Callers add items with stable String keys and a contentType (§7.2).
 *
 * Follow: growth of the bottom item keeps it glued for free; a NEW item at index 0 would land out of view (the list
 * anchors on the old first key), so while following the list asks for index 0 in the same frame as the insert.
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
        // Runs after this composition applies and before its layout: same frame as the insert (§7.3 rule 2).
        if (newestKey != lastKey[0]) {
            lastKey[0] = newestKey
            if (state.following && newestKey != null) state.lazy.requestScrollToItem(0)
        }
    }
    LaunchedEffect(state) {
        state.lazy.interactionSource.interactions.collect { if (it is DragInteraction.Start) state.following = false }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.atLiveEdge }.collect { if (it && !state.lazy.isScrollInProgress) state.following = true }
    }
    Box(modifier) {
        LazyColumn(
            state = state.lazy,
            reverseLayout = true,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize().testTag("transcript-list"),
            content = content,
        )
        KitJumpToLatest(state, jumpLabel, motion, Modifier.align(Alignment.BottomCenter).padding(bottom = Space.S4.dp))
    }
}

/** The pill (plan 08 §5.1 "Jump pill"). It alone reads [TranscriptListState.awayFromLatest]. Plain tap: no haptic. */
@Composable
private fun KitJumpToLatest(state: TranscriptListState, label: String, motion: KitMotionLevel, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val full = motion == KitMotionLevel.Full
    AnimatedVisibility(
        visible = state.awayFromLatest,
        enter = fadeIn(tween(MotionTokens.STANDARD)) + (if (full) slideInVertically(tween(MotionTokens.STANDARD)) { it / 4 } else fadeIn()),
        exit = fadeOut(tween(MotionTokens.QUICK)) + (if (full) slideOutVertically(tween(MotionTokens.QUICK)) { it / 4 } else fadeOut()),
        modifier = modifier,
    ) {
        Surface(
            onClick = {
                state.following = true
                scope.launch {
                    val lazy = state.lazy
                    val viewport = lazy.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                    when {
                        motion == KitMotionLevel.Off -> lazy.scrollToItem(0)
                        lazy.firstVisibleItemIndex <= viewport * 2 -> lazy.animateScrollToItem(0)
                        else -> {
                            lazy.scrollToItem(2) // jump close, glide the last stretch (never animate across thousands)
                            lazy.animateScrollToItem(0)
                        }
                    }
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.testTag("jump-to-latest"),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = Space.S4.dp, vertical = Space.S2.dp))
        }
    }
}
