package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.frame.kitPageGutter
import io.github.eslamasabry.oc.ui.kit.frame.kitTouchSink
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import io.github.eslamasabry.oc.ui.words.res.kitUndoAction
import io.github.eslamasabry.oc.ui.words.res.kitUndoFailed
import io.github.eslamasabry.oc.ui.words.res.kitUndoWorking
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** The undo window (Dart `KitUndo`): 8 s, stretched by the person's accessibility timeout. */
public const val UNDO_WINDOW_MS: Long = 8_000L

/** Plan 06 §7.4: the bar reads at most the reading width. */
private const val UNDO_MAX_DP = 560

/**
 * After the window ran out, the bar's place keeps taking touches this long once it has faded (device QA 2026-11-11,
 * sweep B: a tap meant for Undo as the bar left opened what lies under it). Not after the person's own Undo or Dismiss.
 */
public const val UNDO_GUARD_MS: Long = 300L

/**
 * What can be undone right now (Dart `showKitUndo` arguments). [id] tells offers apart: a new offer replaces the old one
 * and restarts its window. [failed] is the already-resolved reason the undo did not happen.
 */
@Immutable
public data class KitUndoOffer(val id: String, val message: String, val working: Boolean = false, val failed: String? = null)

/** Pure rules of [KitUndoBar]. */
public object UndoRules {
    /**
     * How long the offer stays: null (until dismissed) under accessible navigation, a working or failed offer
     * (the person must see the outcome), otherwise the accessibility-recommended stretch of 8 s.
     */
    public fun timeoutMs(accessibleNavigation: Boolean, offer: KitUndoOffer, recommended: (Long) -> Long): Long? =
        if (accessibleNavigation || offer.working || offer.failed != null) null else recommended(UNDO_WINDOW_MS)
}

/**
 * The one undo offer (Dart `showKitUndo`; plan 06 §7.4), as screen state: shown while [offer] is non-null, at the
 * bottom of the root overlay (the shell places it once). One at a time; a new [KitUndoOffer.id] replaces the old one.
 * It times out after 8 s ([onTimeout]), stretched by the person's accessibility timeout; a finger on the bar holds the
 * window and lifting it starts the whole window again. Under accessible navigation it stays until dismissed, with a
 * Dismiss action. While [KitUndoOffer.working] it says "Undoing"; a failure says why and stays. A polite live region;
 * no haptic (MOT-11).
 *
 * Touch (device QA 2026-11-11, sweep B): the bar takes every touch on its face, and its place never moves: it is laid
 * out at rest from its first frame, so Undo is where it will be drawn even while the bar rises. Once [offer] is gone
 * the bar is inert while it fades (Undo no longer runs: its window has closed), and after a timeout its place keeps
 * taking touches for [UNDO_GUARD_MS] more, so a late tap aimed at Undo reaches nothing under it.
 *
 * States: offered, working, failed, accessible (no timeout), leaving (inert)
 * Text: reading (message), frame (actions)
 * Cap: Reading (560)
 * Motion: fades in on the enter spec; Full also rises half its height, paint only (its touch place stays); Off = instant
 * Replaces: showKitUndo (lib/ui/kit/kit_undo.dart)
 */
@Composable
public fun KitUndoBar(
    offer: KitUndoOffer?,
    onUndo: () -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The room the bar takes, px (its own padding included), while an offer stands, and 0 once the offer is gone: a page
     * that scrolls under the bar gives its content that much bottom room, so its last row is never covered.
     */
    onHeight: ((Int) -> Unit)? = null,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val travels = KitMotion.state.travels
    val signals by LocalKitPlatform.current.signals.collectAsState()
    val a11y = LocalAccessibilityManager.current
    val last = remember { LastOffer() }
    if (offer != null) last.offer = offer
    val shown = last.offer
    val live = offer != null
    val liveNow by rememberUpdatedState(live)
    var barPx by remember { mutableIntStateOf(0) }
    val heightNow by rememberUpdatedState(onHeight)
    LaunchedEffect(live, barPx) { heightNow?.invoke(if (live) barPx else 0) }
    DisposableEffect(Unit) { onDispose { heightNow?.invoke(0) } }
    val timedOut by rememberUpdatedState(onTimeout)
    // A finger on the bar holds the window; each touch and each lift start it again ([touches] keys the timer).
    var touching by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    if (offer != null && !touching) {
        val timeout = UndoRules.timeoutMs(signals.accessibleNavigation, offer) { ms ->
            a11y?.calculateRecommendedTimeoutMillis(ms, containsIcons = false, containsText = true, containsControls = true) ?: ms
        }
        if (timeout != null) {
            LaunchedEffect(offer.id, timeout, touches) {
                delay(timeout)
                timedOut()
            }
        }
    }
    // 0 = gone, 1 = at rest: the fade, and at Full the drawn rise. The bar stays composed while it leaves ([present]).
    // A bar already offered when it is first composed stands at once, as before (no entrance on the first frame).
    val face = remember { Animatable(if (live) 1f else 0f) }
    var present by remember { mutableStateOf(live) }
    val acted = remember { Acted() }
    LaunchedEffect(live) {
        if (live) {
            acted.byPerson = false
            present = true
            face.animateTo(1f, specs.enter())
        } else if (present) {
            face.animateTo(0f, specs.exit())
            if (!acted.byPerson) delay(UNDO_GUARD_MS)
            acted.byPerson = false
            present = false
        }
    }
    if (!live && !present) return
    val o = shown ?: return
    val shape = KitShapeId.Panel.shape()
    Row(
        modifier
            .onSizeChanged { barPx = it.height }
            // The rise is drawn only: the bar is laid out at rest, so its touch place never moves (sweep B: a tap on
            // Undo while the bar rose landed half a bar below it). Outside the fade's layer, so nothing is clipped.
            .drawWithContent {
                val drop = if (travels) (1f - face.value) * size.height / 2f else 0f
                translate(top = drop) { this@drawWithContent.drawContent() }
            }
            .graphicsLayer { alpha = face.value }
            .widthIn(max = UNDO_MAX_DP.dp)
            .fillMaxWidth()
            .padding(horizontal = kitPageGutter(), vertical = Space.S2.dp)
            .clip(shape)
            // The whole bar takes its touches: a tap on its words, or beside Undo, never reaches what lies under it
            // (in a chat the newest reply's menu sits exactly there; device QA 2026-11-11, run 5).
            .kitTouchSink()
            .undoTouches { down ->
                touching = down
                touches++
            }
            .background(colors.surface2)
            .border(1.dp, colors.hairline, shape)
            .heightIn(min = tokens.rowHeightOne.dp)
            // Undo's words keep a 12 dp margin from the bar's end (they sat 4 dp from the edge).
            .padding(start = kitPageGutter(), end = Space.S3.dp)
            .testTag("kit-undo")
            .then(
                if (live) Modifier.semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite }
                // Leaving: nothing on it can be used any more, so assistive technology no longer offers it.
                else Modifier.clearAndSetSemantics {}
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        if (o.working) KitWorkingMark(colors.text2, tokens.smallIcon.dp)
        val words =
            when {
                o.working -> stringResource(Res.string.kitUndoWorking)
                o.failed != null -> stringResource(Res.string.kitUndoFailed, o.failed)
                else -> o.message
            }
        KitText(words, Modifier.weight(1f), role = TypeRole.Body, tone = TextTone.Primary, maxLines = 3)
        // Undo and Dismiss run only while the offer stands; while the bar leaves a tap on them is taken and dropped.
        if (!o.working && o.failed == null) {
            KitButton(
                label = stringResource(Res.string.kitUndoAction),
                onClick = {
                    if (liveNow) {
                        acted.byPerson = true
                        onUndo()
                    }
                },
                role = KitButtonRole.Tertiary,
            )
        }
        if (signals.accessibleNavigation || o.failed != null) {
            KitButton(
                label = stringResource(Res.string.kitSheetDismiss),
                onClick = {
                    if (liveNow) {
                        acted.byPerson = true
                        timedOut()
                    }
                },
                role = KitButtonRole.Tertiary,
            )
        }
    }
}

/**
 * Reports a finger landing on the bar ([onTouch] true) and the last finger leaving it (false), on any part of it,
 * without taking the touch from Undo or the sink.
 */
private fun Modifier.undoTouches(onTouch: (Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onTouch(true)
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
            } finally {
                onTouch(false)
            }
        }
    }

/** Whether the offer just went because the person tapped Undo or Dismiss (then no guard is kept). */
private class Acted {
    var byPerson: Boolean = false
}

private class LastOffer {
    var offer: KitUndoOffer? = null
}
